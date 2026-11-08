package com.udyam.digital.kyc;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** Scan quality gate, enhancement, ID repair, dates, two-pass merge, C20 and the rescan rule. */
public class KycAccuracyTest {

    // ------------------------------------------------------------------ quality gate
    private static int[] page(int w, int h, int paper, int ink) {
        int[] g = new int[w * h];
        Arrays.fill(g, paper);
        for (int y = 20; y < h - 20; y += 12) for (int x = 20; x < w - 20; x++) if ((x / 6) % 2 == 0) g[y * w + x] = ink;
        return g;
    }

    @Test public void qualityGateGivesTheExactReason() {
        int w = 400, h = 300;
        assertEquals(ImageQuality.Problem.NONE, ImageQuality.measure(page(w, h, 230, 20), w, h, 2000, 1500).problem);
        assertEquals(ImageQuality.Problem.TOO_SMALL, ImageQuality.measure(page(w, h, 230, 20), w, h, 1300, 900).problem);
        assertEquals(ImageQuality.Problem.TOO_DARK, ImageQuality.measure(page(w, h, 40, 5), w, h, 2000, 1500).problem);
        // Faded but crisp print (washed-out photo): the darkest ink is light grey.
        assertEquals(ImageQuality.Problem.TOO_BRIGHT, ImageQuality.measure(page(w, h, 250, 170), w, h, 2000, 1500).problem);
        int[] glare = page(w, h, 200, 20);
        for (int y = 0; y < 80; y++) for (int x = 0; x < 120; x++) glare[y * w + x] = 255; // ~8% blown out
        assertEquals(ImageQuality.Problem.GLARE, ImageQuality.measure(glare, w, h, 2000, 1500).problem);
        int[] flat = new int[w * h];
        Arrays.fill(flat, 180);
        for (int i = 0; i < flat.length; i += 50) flat[i] = 150; // faint, soft marks: blurry
        assertEquals(ImageQuality.Problem.BLURRY, ImageQuality.measure(flat, w, h, 2000, 1500).problem);
        assertTrue(new ImageQuality(2000, 1500, 100, 5000, 0.1, 30, false).reasonEn().startsWith("Glare"));
    }

    @Test public void cleanWhiteScanIsNotGlare() {
        int w = 400, h = 300;
        ImageQuality q = ImageQuality.measure(page(w, h, 255, 10), w, h, 2000, 1500);
        assertEquals(0, q.glare, 1e-9);
        assertTrue(q.ok());
    }

    @Test public void idCardsUseTheLongSide() {
        // Specimen ID cards are 1012 x 638.
        assertTrue(new ImageQuality(1012, 638, 200, 5000, 0, 20, true).ok());
        assertFalse("a page needs 1000 on the short side", new ImageQuality(1012, 638, 200, 5000, 0, 20, false).ok());
        assertFalse(new ImageQuality(900, 567, 200, 5000, 0, 20, true).ok());
        assertTrue(ImageQuality.cardShaped(1012, 638));
        assertFalse(ImageQuality.cardShaped(1240, 1754)); // A4
    }

    // ------------------------------------------------------------------ enhancement
    @Test public void enhancementGivesBlackInkOnWhiteEvenWithAShadow() {
        int w = 200, h = 100;
        int[] g = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) g[y * w + x] = x < 100 ? 120 : 220; // half in shadow
        for (int x = 10; x < 190; x += 20) for (int y = 40; y < 60; y++) g[y * w + x] = x < 100 ? 40 : 90; // strokes
        int[] e = ImageEnhancer.enhance(g, w, h);
        for (int v : e) assertTrue(v == 0 || v == 255);
        assertEquals("stroke in shadow is ink", 0, e[50 * w + 30]);
        assertEquals("stroke in light is ink", 0, e[50 * w + 150]);
        assertEquals("shadowed paper stays paper", 255, e[10 * w + 50]);
    }

    // ------------------------------------------------------------------ ID repair
    private static IdCorrector.Registry registry(String... ids) {
        Set<String> s = new HashSet<>(Arrays.asList(ids));
        return new IdCorrector.Registry() {
            @Override public boolean hasGstin(String g) { return s.contains(g); }
            @Override public boolean hasUdyam(String u) { return s.contains(u); }
            @Override public boolean hasPan(String p) { return s.contains(p); }
        };
    }

    @Test public void gstinRepairNeedsChecksumAndRegistry() {
        String real = "07CPCXN0398P1ZT";
        assertTrue(GstinValidator.isValid(real));
        IdCorrector.Result r = IdCorrector.gstin("07CPCXNO398P1ZT", registry(real));
        assertTrue(r.corrected);
        assertEquals(real, r.value);
        assertEquals("pos 8: O→0", r.note);
        // Same misread, but the corrected GSTIN isn't registered: leave it, ask the owner.
        IdCorrector.Result none = IdCorrector.gstin("07CPCXNO398P1ZT", registry());
        assertFalse(none.corrected);
        assertEquals("07CPCXNO398P1ZT", none.value);
        // Valid as read: never touched.
        assertFalse(IdCorrector.gstin(real, registry(real)).corrected);
    }

    @Test public void gstinWithBadChecksumAndNoRegistryMatchIsNotChanged() {
        // K06's certificate GSTIN: checksum fails and no look-alike version is registered.
        IdCorrector.Result r = IdCorrector.gstin("08YKZXS8189L1ZO", registry("27QHJXK0658U1ZL"));
        assertFalse(r.corrected);
        assertEquals(0, r.matches);
    }

    @Test public void twoRegisteredCandidatesMeansNoRepair() {
        // Build two registered GSTINs that differ only at an ambiguous position (12: '1' vs 'I'/'L').
        String base = "27QHJXK0658U";
        List<String> valid = new ArrayList<>();
        for (char c : new char[] {'1', 'I', 'L'}) {
            String first14 = base + c + "Z";
            valid.add(first14 + GstinValidator.checksumChar(first14));
        }
        // Read "1" at position 13 with a wrong check character; two of the three are registered.
        String read = base + "1Z" + (valid.get(0).charAt(14) == 'A' ? 'B' : 'A');
        IdCorrector.Result r = IdCorrector.gstin(read, registry(valid.get(1), valid.get(2)));
        assertFalse(r.corrected);
    }

    @Test public void udyamAndPanRepair() {
        IdCorrector.Result u = IdCorrector.udyam("UDYAM-MH-O0-95I6696", registry("UDYAM-MH-00-9516696"));
        assertTrue(u.corrected);
        assertEquals("UDYAM-MH-00-9516696", u.value);
        assertFalse(IdCorrector.udyam("UDYAM-MH-O0-95I6696", registry()).corrected);
        IdCorrector.Result p = IdCorrector.pan("QHJXK0G58U", registry("QHJXK0658U"));
        assertTrue(p.corrected);
        assertEquals("QHJXK0658U", p.value);
    }

    @Test public void locatorsReturnTheValueAsPrinted() {
        assertEquals("UDYAM-KL-O0-3078801", FieldExtractor.locateUdyam("Udyam reg. number UDYAM - KL - O0 - 3078801"));
        assertEquals("UDYAM-MH-00-9516696", FieldExtractor.locateUdyam("-MH-00-9516696"));
        assertEquals("ABCXX1234F", FieldExtractor.locatePan("PAN: ABCXX1234F"));
        assertNull("plain words are not PANs", FieldExtractor.locatePan("PROPRIETOR AUTHORISED"));
    }

    // ------------------------------------------------------------------ dates
    @Test public void datesInEveryFormatIncludingDevanagariDigits() {
        LocalDate d = LocalDate.of(2026, 6, 26);
        assertEquals(d, KycDates.parse("26.06.2026"));
        assertEquals(d, KycDates.parse("26/6/2026"));
        assertEquals(d, KycDates.parse("२६/०६/२०२६"));
        assertEquals(d, KycDates.parse("बिल तिथि: २६-०६-२०२६"));
        assertEquals(LocalDate.of(2026, 6, 1), KycDates.parse("June 2026"));
    }

    @Test public void ambiguousDatesAreDetected() {
        assertTrue(KycDates.isAmbiguous("03/04/2026"));
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 4, 3), LocalDate.of(2026, 3, 4)}, KycDates.readings("03/04/2026"));
        assertFalse("13 can only be a day", KycDates.isAmbiguous("13/04/2026"));
        assertFalse("same both ways", KycDates.isAmbiguous("04/04/2026"));
        assertFalse("ISO is clear", KycDates.isAmbiguous("2026-04-03"));
    }

    // ------------------------------------------------------------------ two-pass merge, C20
    private static OcrLine line(String text, int top, float conf) {
        return new OcrLine(text, 100, top, 100 + text.length() * 12, top + 20, 0f, conf, new String[0], new int[0]);
    }

    private static List<OcrLine> bill(String name, float nameConf, String date) {
        return new ArrayList<>(Arrays.asList(
                line("Electricity bill", 20, 0.99f),
                line("Bill date", 100, 0.99f), new OcrLine(date, 400, 100, 560, 120, 0f, 0.95f, new String[0], new int[0]),
                line("Name", 160, 0.99f), new OcrLine(name, 400, 160, 700, 180, 0f, nameConf, new String[0], new int[0])));
    }

    @Test public void higherConfidenceReadingWins() {
        KycDocument d = DocumentProcessor.process(Slot.ADDRESS_PROOF, bill("Kaveri lnsurance", 0.62f, "2026-06-26"),
                bill("Kaveri Insurance", 0.91f, "2026-06-26"), null, 0, null);
        assertEquals("Kaveri Insurance", d.get(FieldExtractor.NAME));
        assertEquals(0.91f, d.confidence(FieldExtractor.NAME), 1e-6);
        assertFalse("look-alike misread is not tampering", d.looksTampered());
    }

    @Test public void confidentPassesThatDisagreeLookTampered() {
        KycDocument d = DocumentProcessor.process(Slot.ADDRESS_PROOF, bill("Kaveri Stays", 0.95f, "2026-06-26"),
                bill("Kaveri Stays", 0.95f, "2026-01-26"), null, 0, null);
        assertTrue(d.passDisagreement.containsKey(FieldExtractor.BILL_DATE));
        assertTrue(d.looksTampered());
    }

    @Test public void mixedFontSizesInOneFieldLookPasted() {
        OcrLine pasted = new OcrLine("Nisha ARORA", 400, 160, 700, 190, 0f, 0.95f, new String[] {"Nisha", "ARORA"}, new int[] {20, 38});
        OcrLine normal = new OcrLine("Nisha Arora", 400, 160, 700, 180, 0f, 0.95f, new String[] {"Nisha", "Arora"}, new int[] {20, 22});
        assertTrue(DocumentProcessor.mixedFontSizes(Collections.singletonList(pasted)));
        assertFalse(DocumentProcessor.mixedFontSizes(Collections.singletonList(normal)));
    }

    // ------------------------------------------------------------------ decisions
    private static KycApplication k11Like(float billDateConfidence) throws Exception {
        KycApplication app = new KycApplication("T");
        app.noGstin = true;
        app.udyam = "UDYAM-MH-00-9516696";
        KycDocument ad = new KycDocument(Slot.ADDRESS_PROOF);
        ad.type = DocType.UTILITY_BILL;
        ad.fields.put(FieldExtractor.BILL_DATE, "2025-10-31"); // 257 days old: C13 FAIL if we trust it
        ad.confidence.put(FieldExtractor.BILL_DATE, billDateConfidence);
        app.documents.put(Slot.ADDRESS_PROOF, ad);
        return app;
    }

    private static CheckResult c13(KycApplication app) throws Exception {
        for (CheckResult r : KycTestData.engine().run(app, KycClock.TODAY)) if (r.code.equals("C13")) return r;
        throw new AssertionError("no C13");
    }

    @Test public void unsureFieldAsksForRescanInsteadOfFailing() throws Exception {
        CheckResult sure = c13(k11Like(0.95f));
        assertEquals(CheckResult.Status.FAIL, sure.status);
        assertFalse(sure.rescan);
        assertTrue(sure.en.contains("90 days"));

        CheckResult unsure = c13(k11Like(0.55f));
        assertTrue(unsure.rescan);
        assertTrue(unsure.fixable);
        assertTrue(unsure.en.startsWith("We couldn't read the bill date"));

        KycApplication confirmed = k11Like(0.55f);
        confirmed.doc(Slot.ADDRESS_PROOF).confirm(FieldExtractor.BILL_DATE);
        assertFalse("owner confirmed it: the real verdict stands", c13(confirmed).rescan);
    }

    @Test public void ambiguousDateMustBePicked() throws Exception {
        KycApplication app = k11Like(0.99f);
        KycDocument ad = app.doc(Slot.ADDRESS_PROOF);
        ad.fields.put(FieldExtractor.BILL_DATE, "03/04/2026");
        ad.ambiguousDates.add(FieldExtractor.BILL_DATE);
        CheckResult r = c13(app);
        assertTrue(r.rescan);
        assertTrue(r.en, r.en.contains("3 April 2026 or 4 March 2026"));
        // Same message in Hindi and Tamil: the locale's month names, Western digits.
        assertTrue(r.hi, r.hi.contains("3 अप्रैल 2026"));
        assertTrue(r.message(com.udyam.digital.model.Language.TA).contains("2026"));
    }

    @Test public void tamperingSendsToManualReview() throws Exception {
        KycApplication app = k11Like(0.99f);
        app.doc(Slot.ADDRESS_PROOF).fields.put(FieldExtractor.BILL_DATE, "2026-06-26");
        app.doc(Slot.ADDRESS_PROOF).passDisagreement.put(FieldExtractor.NAME, "Kaveri Stays | Ganga Stays");
        List<CheckResult> res = KycTestData.engine().run(app, KycClock.TODAY);
        boolean c20 = false;
        for (CheckResult r : res) if (r.code.equals("C20")) { c20 = true; assertEquals(CheckResult.Status.WARN, r.status); assertFalse(r.fixable); }
        assertTrue(c20);
        assertEquals(KycApplication.Outcome.MANUAL_REVIEW, KycRuleEngine.decide(app, res).outcome);
    }

    // ------------------------------------------------------------------ answer key
    @Test public void answerKeyAcceptsBothShapes() {
        Map<String, SpecimenReport.Expected> a = SpecimenReport.parseExpected(
                "{\"K01\": {\"overall\": \"Approved\", \"checks_failed\": []}, \"K02\": {\"overall\": \"Please fix\", \"checks_failed\": [\"c13\"]}, \"K03\": {\"overall\": \"\"}}");
        assertEquals(2, a.size());
        assertEquals(KycApplication.Outcome.PLEASE_FIX, a.get("K02").overall);
        assertEquals(Collections.singletonList("C13"), a.get("K02").checks);
        Map<String, SpecimenReport.Expected> b = SpecimenReport.parseExpected(
                "{\"applicants\": {\"K04\": {\"overall\": \"MANUAL_REVIEW\", \"must_flag\": [\"C02\"]}}}");
        assertEquals(KycApplication.Outcome.MANUAL_REVIEW, b.get("K04").overall);
    }

    @Test public void reportCountsFalseRejectsAndAccepts() {
        List<SpecimenRunner.Row> rows = new ArrayList<>();
        Map<String, SpecimenReport.Expected> key = SpecimenReport.parseExpected(
                "{\"K01\": {\"overall\": \"Approved\"}, \"K02\": {\"overall\": \"Please fix\", \"checks_failed\": [\"C13\"]}}");
        rows.add(new SpecimenRunner.Row("K01", KycApplication.Outcome.PLEASE_FIX, Collections.singletonList("C09 WARN"), "", Collections.<CheckResult>emptyList()));
        rows.add(new SpecimenRunner.Row("K02", KycApplication.Outcome.APPROVED, Collections.<String>emptyList(), "", Collections.<CheckResult>emptyList()));
        SpecimenReport r = SpecimenReport.compare(rows, key);
        assertEquals(1, r.falseRejects());
        assertEquals(1, r.falseAccepts());
        assertEquals(0, r.matched());
        assertTrue(r.markdown("test", Collections.<String>emptyList(), rows, "").contains("FALSE ACCEPT"));
    }
}
