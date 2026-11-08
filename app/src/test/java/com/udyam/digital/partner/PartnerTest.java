package com.udyam.digital.partner;

import com.udyam.digital.TestData;
import com.udyam.digital.data.CsvParser;
import com.udyam.digital.engine.MiniJson;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Scorer = Python on 20 leads; the city rule; masking; next actions; dashboard math. */
public class PartnerTest {

    private static PropensityScorer scorer;
    private static List<PartnerLead> seed;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    private static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    private static File file(String... paths) {
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) return f;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @BeforeClass public static void load() throws Exception {
        scorer = new PropensityScorer(read(TestData.asset("propensity_model.json")));
        seed = new ArrayList<>();
        for (Object o : (List<Object>) MiniJson.parse(read(TestData.asset("partner_leads.json")))) {
            PartnerLead l = PartnerLead.fromJson((Map<String, Object>) o);
            l.score = scorer.score(l);
            seed.add(l);
        }
    }

    // ------------------------------------------------------------------ scorer = Python
    @SuppressWarnings("unchecked")
    @Test public void scorerMatchesPythonOn20Leads() throws Exception {
        File f = file("src/test/resources/partner/propensity_samples.json", "app/src/test/resources/partner/propensity_samples.json");
        List<Object> samples = (List<Object>) MiniJson.parse(read(f));
        assertEquals(20, samples.size());
        for (Object o : samples) {
            Map<String, Object> s = (Map<String, Object>) o, x = (Map<String, Object>) s.get("fields");
            PartnerLead l = new PartnerLead();
            l.segment = (String) x.get("segment");
            l.businessType = (String) x.get("business_type");
            l.cityTier = ((Number) x.get("city_tier")).intValue();
            l.employees = ((Number) x.get("employees")).intValue();
            l.sites = ((Number) x.get("sites")).intValue();
            l.hasGstin = (Boolean) x.get("has_gstin");
            l.hasUdyam = (Boolean) x.get("has_udyam");
            l.currentInternet = (String) x.get("current_internet");
            l.spend = ((Number) x.get("spend")).intValue();
            l.leadSource = (String) x.get("lead_source");
            l.stage = (String) x.get("stage");
            PropensityScorer.Score got = scorer.score(l);
            String id = (String) s.get("id");
            assertEquals(id + " probability", ((Number) s.get("probability")).doubleValue(), got.probability, 1e-9);
            assertEquals(id + " score", ((Number) s.get("score")).intValue(), got.score);
            assertEquals(id + " reasons", s.get("reasons"), got.reasons);
        }
    }

    @Test public void scoreIsZeroToHundredWithTwoReasons() {
        for (PartnerLead l : seed) {
            assertTrue(l.score.score >= 0 && l.score.score <= 100);
            assertTrue(l.score.reasons.size() <= 2);
        }
        PartnerLead strong = new PartnerLead();
        strong.stage = "Quote accepted";
        strong.hasGstin = true;
        strong.leadSource = "Referral";
        PartnerLead weak = new PartnerLead();
        weak.stage = "Lead";
        assertTrue(scorer.score(strong).score > scorer.score(weak).score);
        assertTrue(scorer.score(strong).reasons.contains("reached quote stage"));
    }

    // ------------------------------------------------------------------ city rule
    @Test public void partnerOnlySeesTheirCity() {
        LeadBook pune = new LeadBook(seed, "Pune");
        assertFalse(pune.visible().isEmpty());
        for (PartnerLead l : pune.visible()) assertEquals("Pune", l.city);
        // Asking for another city doesn't widen it.
        for (PartnerLead.Tab t : PartnerLead.Tab.values()) {
            for (PartnerLead l : pune.list(t, "Mumbai", null, LeadBook.Sort.SCORE, TODAY)) assertEquals("Pune", l.city);
        }
        assertEquals(Arrays.asList("Pune"), pune.cities());

        LeadBook field = new LeadBook(seed, null); // Accel field sales: all cities
        assertTrue(field.cities().size() > 5);
        for (PartnerLead l : field.list(PartnerLead.Tab.STUCK, "Mumbai", null, LeadBook.Sort.SCORE, TODAY)) assertEquals("Mumbai", l.city);
    }

    @Test public void sortingAndLanguageFilter() {
        LeadBook book = new LeadBook(seed, null);
        List<PartnerLead> byScore = book.list(PartnerLead.Tab.PERSON, null, null, LeadBook.Sort.SCORE, TODAY);
        for (int i = 1; i < byScore.size(); i++) assertTrue(byScore.get(i - 1).score.score >= byScore.get(i).score.score);
        List<PartnerLead> byValue = book.list(PartnerLead.Tab.PERSON, null, null, LeadBook.Sort.PLAN_VALUE, TODAY);
        for (int i = 1; i < byValue.size(); i++) assertTrue(byValue.get(i - 1).planValueInr >= byValue.get(i).planValueInr);
        List<PartnerLead> byWait = book.list(PartnerLead.Tab.STUCK, null, null, LeadBook.Sort.WAITING, TODAY);
        for (int i = 1; i < byWait.size(); i++) assertTrue(byWait.get(i - 1).waitingDays(TODAY) >= byWait.get(i).waitingDays(TODAY));
        for (PartnerLead l : book.list(PartnerLead.Tab.STUCK, null, "Tamil", LeadBook.Sort.SCORE, TODAY)) assertEquals("Tamil", l.language);
        // Done leads leave the list.
        PartnerLead first = byScore.get(0);
        first.status = PartnerLead.Status.DONE;
        assertFalse(book.list(PartnerLead.Tab.PERSON, null, null, LeadBook.Sort.SCORE, TODAY).contains(first));
        first.status = PartnerLead.Status.OPEN;
    }

    // ------------------------------------------------------------------ masking
    @Test public void businessNamesAreMasked() {
        assertEquals("Ba**** De**** Ca** LLP", PartnerMask.businessName("Balaji Dental Care LLP"));
        assertEquals("Tr***** Ba** Ho*** Pvt Ltd", PartnerMask.businessName("Trinity Bake House Pvt Ltd"));
        assertEquals("Sh** & Co", PartnerMask.businessName("Shah & Co"));
        assertEquals("", PartnerMask.businessName(null));
        assertEquals("GSTIN 27******34X1Z5, call ******3210", PartnerMask.text("GSTIN 27ABCXX1234X1Z5, call 9876543210"));
    }

    @Test public void javaMaskingMatchesTheShippedSeed() throws Exception {
        File csv = file("../../smb_leads.csv", "../smb_leads.csv", "../../../smb_leads.csv");
        Assume.assumeNotNull(csv);
        Map<String, String> names = new HashMap<>(), contacts = new HashMap<>();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8)) {
            for (Map<String, String> row : CsvParser.readRecords(r)) {
                names.put(row.get("lead_id"), row.get("business_name"));
                contacts.put(row.get("lead_id"), row.get("contact_name"));
            }
        }
        String raw = read(TestData.asset("partner_leads.json"));
        for (PartnerLead l : seed) {
            assertEquals(l.id, PartnerMask.businessName(names.get(l.id)), l.maskedName);
            assertFalse("full name shipped for " + l.id, raw.contains("\"" + names.get(l.id) + "\""));
            assertFalse("contact shipped for " + l.id, raw.contains(contacts.get(l.id)));
        }
    }

    // ------------------------------------------------------------------ next action, dashboard
    @Test public void nextActions() {
        PartnerLead l = new PartnerLead();
        l.reason = "Site not feasible for fibre";
        assertEquals("Offer AirFiber - fibre not feasible", NextAction.forLead(l).in(com.udyam.digital.model.Language.EN));
        l.reason = "Missing GST or Udyam document";
        l.hasGstin = false;
        assertEquals("Help with GSTIN registration", NextAction.forLead(l).in(com.udyam.digital.model.Language.EN));
        l.hasGstin = true;
        assertEquals("Help with Udyam registration", NextAction.forLead(l).in(com.udyam.digital.model.Language.EN));
        for (PartnerLead s : seed) assertFalse(NextAction.forLead(s).in(com.udyam.digital.model.Language.EN).isEmpty());
    }

    @Test public void dashboardMath() throws Exception {
        PartnerDashboard d = new PartnerDashboard(read(TestData.asset("partner_dashboard.json")), new int[] {1, 1, 0, 0, 0});
        assertEquals(5, d.funnel.size());
        assertEquals(3407, d.funnel.get(0).total());
        assertEquals(368, d.funnel.get(4).total());
        assertEquals(368.0 / 588, d.conversion(4), 1e-9);
        assertEquals(15053, d.blendedCostInr);
        // Estimate: share x digital + (1 - share) x assisted.
        double s = d.noHumanShare(0, 0);
        assertEquals(d.selfServeShare, s, 1e-12);
        assertEquals(Math.round(s * PartnerDashboard.DIGITAL_ONLY_COST_INR + (1 - s) * 15053), d.estimatedCostPerActivation(s));
        assertEquals(1.0, d.noHumanShare(2, 2), 0);
        assertEquals(PartnerDashboard.DIGITAL_ONLY_COST_INR, d.estimatedCostPerActivation(1.0));
    }
}
