package com.udyam.digital.kyc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OCR lines (two passes) + photo measurements -> a classified document with extracted fields.
 *
 * Pass 1 reads the scanned photo, pass 2 an enhanced copy (contrast stretch + adaptive
 * threshold). Fields are extracted from each pass and merged: for every field the reading with
 * the higher OCR confidence wins. Identifiers are then repaired by {@link IdCorrector} (only
 * with a unique registry match). Pure Java.
 */
public final class DocumentProcessor {
    private DocumentProcessor() {}

    /** Fields where a disagreement between the two passes points to tampering (C20). */
    static final Set<String> IDENTITY_FIELDS = new HashSet<>(Arrays.asList(
            FieldExtractor.GSTIN, FieldExtractor.UDYAM, FieldExtractor.PAN, FieldExtractor.LEGAL_NAME,
            FieldExtractor.ENTERPRISE, FieldExtractor.NAME, FieldExtractor.FOR_BUSINESS, FieldExtractor.ID_NUMBER,
            FieldExtractor.DOB, FieldExtractor.EXPIRY, FieldExtractor.BILL_DATE, FieldExtractor.LIABILITY_DATE,
            FieldExtractor.REG_DATE));
    static final Set<String> DATE_FIELDS = new HashSet<>(Arrays.asList(
            FieldExtractor.DOB, FieldExtractor.EXPIRY, FieldExtractor.BILL_DATE, FieldExtractor.LIABILITY_DATE,
            FieldExtractor.REG_DATE));
    /** Words in one field whose printed heights differ by more than this ratio look pasted in. */
    static final double FONT_RATIO = 1.6;

    /** Single pass, no registry (older callers and replay files). */
    public static KycDocument process(Slot slot, List<OcrLine> lines, ImageQuality quality, long hash) {
        return process(slot, lines, Collections.<OcrLine>emptyList(), quality, hash, null);
    }

    /**
     * @param pass1    OCR of the scanned photo
     * @param pass2    OCR of the enhanced copy (may be empty)
     * @param registry used to accept identifier repairs; null = never repair
     */
    public static KycDocument process(Slot slot, List<OcrLine> pass1, List<OcrLine> pass2, ImageQuality quality,
                                      long hash, IdCorrector.Registry registry) {
        KycDocument d = new KycDocument(slot);
        d.quality = quality;
        d.hash = hash;
        DocType t = DocumentClassifier.classify(pass1);
        if (t == DocType.UNKNOWN && !pass2.isEmpty()) t = DocumentClassifier.classify(pass2);
        d.type = t;

        FieldExtractor.Extraction a = FieldExtractor.extractDetailed(t, pass1);
        FieldExtractor.Extraction b = pass2.isEmpty() ? new FieldExtractor.Extraction() : FieldExtractor.extractDetailed(t, pass2);
        Set<String> keys = new LinkedHashSet<>(a.values.keySet());
        keys.addAll(b.values.keySet());
        for (String k : keys) {
            String va = a.values.get(k), vb = b.values.get(k);
            float ca = a.confidence.containsKey(k) ? a.confidence.get(k) : 0f;
            float cb = b.confidence.containsKey(k) ? b.confidence.get(k) : 0f;
            boolean useB = va == null || (vb != null && cb > ca);
            d.fields.put(k, useB ? vb : va);
            d.confidence.put(k, useB ? cb : ca);
            if (va != null && vb != null && IDENTITY_FIELDS.contains(k) && !sameReading(k, va, vb)
                    && ca >= KycDocument.SURE && cb >= KycDocument.SURE) {
                // Both passes are sure, yet they read different things: not a blur, a doctored field.
                d.passDisagreement.put(k, va + " | " + vb);
            }
            List<OcrLine> src = useB ? b.sources.get(k) : a.sources.get(k);
            if (IDENTITY_FIELDS.contains(k) && mixedFontSizes(src)) d.fontMismatch.add(k);
        }

        // Identifier repair: unique candidate that passes format/checksum AND is in the registry.
        repair(d, FieldExtractor.GSTIN, IdCorrector.gstin(d.get(FieldExtractor.GSTIN), registry));
        repair(d, FieldExtractor.UDYAM, IdCorrector.udyam(d.get(FieldExtractor.UDYAM), registry));
        repair(d, FieldExtractor.PAN, IdCorrector.pan(d.get(FieldExtractor.PAN), registry));
        if (d.get(FieldExtractor.UDYAM) != null) d.fields.put(FieldExtractor.UDYAM, UdyamValidator.normalize(d.get(FieldExtractor.UDYAM)));
        if (d.get(FieldExtractor.GSTIN) != null) d.fields.put(FieldExtractor.GSTIN, GstinValidator.normalize(d.get(FieldExtractor.GSTIN)));

        for (String k : DATE_FIELDS) if (KycDates.isAmbiguous(d.get(k))) d.ambiguousDates.add(k);

        String aadhaar = d.fields.remove(FieldExtractor.AADHAAR);
        if (aadhaar != null) {
            String firstEight = aadhaar.replaceAll("[^0-9X]", "").substring(0, 8);
            d.unmaskedAadhaar = firstEight.matches(".*[0-9].*");
            // Never keep a full Aadhaar number: only the last 4 digits survive.
            d.fields.put(FieldExtractor.AADHAAR, Mask.aadhaar(aadhaar));
        }
        return d;
    }

    private static void repair(KycDocument d, String field, IdCorrector.Result r) {
        if (d.get(field) == null || !r.corrected) return;
        d.fields.put(field, r.value);
        d.autoCorrected.put(field, r.note);
    }

    /** Two readings of one field that differ only by OCR look-alikes or a near-identical name count as the same. */
    static boolean sameReading(String field, String a, String b) {
        if (IdCorrector.fold(a).equals(IdCorrector.fold(b))) return true;
        if (DATE_FIELDS.contains(field)) {
            java.time.LocalDate x = KycDates.parse(a), y = KycDates.parse(b);
            return x != null && x.equals(y) || x == null || y == null; // an unreadable date is a misread, not a change
        }
        if (field.equals(FieldExtractor.GSTIN) || field.equals(FieldExtractor.UDYAM) || field.equals(FieldExtractor.PAN)
                || field.equals(FieldExtractor.ID_NUMBER)) return false;
        return NameMatcher.score(a, b) >= 0.9;
    }

    /**
     * Words in capitals or digits (so all have full cap height) whose box heights differ by more
     * than {@link #FONT_RATIO}: a value pasted over the original print usually has a different size.
     */
    static boolean mixedFontSizes(List<OcrLine> src) {
        if (src == null) return false;
        for (OcrLine l : src) {
            List<Integer> h = new ArrayList<>();
            for (int i = 0; i < l.words.length && i < l.wordHeights.length; i++) {
                String w = l.words[i];
                if (w.replaceAll("[^\\p{L}\\p{N}]", "").length() < 3) continue;
                if (!w.matches(".*[A-Z0-9].*")) continue; // lower-case-only words are shorter by design
                h.add(l.wordHeights[i]);
            }
            if (h.size() < 2) continue;
            int min = Collections.min(h), max = Collections.max(h);
            if (min > 0 && (double) max / min > FONT_RATIO) return true;
        }
        return false;
    }

    /** Field -> OCR confidence, for the debug report. */
    public static String describeConfidence(KycDocument d) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : d.fields.entrySet()) {
            if (b.length() > 0) b.append(", ");
            b.append(e.getKey()).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", d.confidence(e.getKey())));
        }
        return b.toString();
    }
}
