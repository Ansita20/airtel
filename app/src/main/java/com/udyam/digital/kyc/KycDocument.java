package com.udyam.digital.kyc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** One uploaded document after OCR: its type, extracted fields, how sure we are of each, and photo checks. */
public final class KycDocument {

    /** Below this OCR confidence a field is "unsure": a FAIL based on it asks for a rescan instead. */
    public static final float SURE = 0.80f;

    public final Slot slot;
    public DocType type = DocType.UNKNOWN;
    /** Extracted (and possibly user-edited) fields. Aadhaar numbers are stored masked only. */
    public final Map<String, String> fields = new LinkedHashMap<>();
    /** OCR confidence (0-1) per field: the weakest OCR line the value came from. Missing = 1. */
    public final Map<String, Float> confidence = new HashMap<>();
    /** Fields the owner changed after OCR ("user-corrected"); re-checked against the registry. */
    public final Set<String> userCorrected = new HashSet<>();
    /** Fields the owner looked at and confirmed as read. */
    public final Set<String> confirmed = new HashSet<>();
    /** Identifiers repaired by {@link IdCorrector}, with what changed (e.g. "pos 9: O→0"). */
    public final Map<String, String> autoCorrected = new LinkedHashMap<>();
    /** Dates that read two ways (03/04/2026); the owner must pick one. */
    public final Set<String> ambiguousDates = new HashSet<>();
    /** C20: fields whose two OCR passes disagree, as "first | second". */
    public final Map<String, String> passDisagreement = new LinkedHashMap<>();
    /** C20: fields whose words are printed in clearly different sizes. */
    public final Set<String> fontMismatch = new HashSet<>();
    public ImageQuality quality;
    public long hash;
    /** True if an Aadhaar with a full (unmasked) number was detected; the number itself is never kept. */
    public boolean unmaskedAadhaar;
    /** Name of the encrypted image file, if stored. */
    public String imageFile;

    public KycDocument(Slot slot) { this.slot = slot; }

    public String get(String field) { return fields.get(field); }

    public float confidence(String field) {
        Float c = confidence.get(field);
        return c == null ? 1f : c;
    }

    /** Applies a user edit and marks the field as user-corrected if it changed. */
    public void edit(String field, String value) {
        String old = fields.get(field);
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) fields.remove(field); else fields.put(field, v);
        if (old == null ? !v.isEmpty() : !old.equals(v)) {
            userCorrected.add(field);
            autoCorrected.remove(field);
            ambiguousDates.remove(field);
        }
    }

    /** The owner confirmed this field as read (or picked one reading of an ambiguous date). */
    public void confirm(String field) {
        confirmed.add(field);
        ambiguousDates.remove(field);
    }

    public boolean corrected(String field) { return userCorrected.contains(field); }

    /** A human vouched for the value: typed it, or confirmed it. */
    public boolean vouched(String field) { return userCorrected.contains(field) || confirmed.contains(field); }

    /** OCR was unsure of this field and nobody has confirmed it. */
    public boolean unsure(String field) {
        return fields.containsKey(field) && confidence(field) < SURE && !vouched(field);
    }

    /** The date could be read two ways and the owner hasn't picked one. */
    public boolean ambiguous(String field) { return ambiguousDates.contains(field) && !vouched(field); }

    /** Any sign of tampering for C20. */
    public boolean looksTampered() { return !passDisagreement.isEmpty() || !fontMismatch.isEmpty(); }
}
