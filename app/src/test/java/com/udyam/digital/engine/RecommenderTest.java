package com.udyam.digital.engine;

import com.udyam.digital.TestData;
import com.udyam.digital.engine.Recommendation.Line;
import com.udyam.digital.engine.Recommendation.Role;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.InternetType;
import com.udyam.digital.model.Need;
import com.udyam.digital.model.Product;
import com.udyam.digital.model.Segment;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Learned ranking layer: rules still win, only tapped needs ON, at most 3 suggestions OFF, stable and fair. */
public class RecommenderTest {

    private static RulesEngine engine;
    private static ProductScorer scorer;
    private static Map<String, Product> catalogue;

    /** Business types as in smb_leads.csv, with their segment. null = generic tile (segment only). */
    private static final Object[][] TYPES = {
            {"Kirana store", Segment.MICRO}, {"Salon", Segment.MICRO}, {"Bakery", Segment.MICRO},
            {"Sweet shop", Segment.MICRO}, {"Pharmacy", Segment.MICRO}, {"Optician", Segment.MICRO},
            {"Boutique", Segment.MICRO}, {"Hardware store", Segment.MICRO}, {"Mobile repair shop", Segment.MICRO},
            {"Stationery shop", Segment.MICRO}, {"CA firm", Segment.PROF}, {"Law office", Segment.PROF},
            {"Dental clinic", Segment.PROF}, {"Coaching centre", Segment.PROF}, {"Travel agency", Segment.PROF},
            {"Restaurant chain", Segment.MULTI}, {"Gym chain", Segment.MULTI}, {"Pharmacy chain", Segment.MULTI},
            {"Plastic moulding unit", Segment.MFG}, {"Textile unit", Segment.MFG}, {"Printing press", Segment.MFG},
            {null, Segment.MICRO}, {null, Segment.PROF}, {null, Segment.MULTI}, {null, Segment.MFG},
    };
    private static final int[] BUDGETS = {0, 2000, 3000, 5000, 10000, 25000, 50000};

    @BeforeClass
    public static void setUp() throws Exception {
        scorer = TestData.scorer();
        engine = new RulesEngine(TestData.catalogue(), scorer);
        catalogue = new HashMap<>();
        for (Product p : TestData.catalogue()) catalogue.put(p.getId(), p);
    }

    private static BusinessProfile profile(String type, Segment seg, int tier, int employees, int sites,
                                           boolean gst, InternetType net, int budget) {
        return new BusinessProfile()
                .setBusinessType(type).setSegment(seg)
                .setCity(tier == 1 ? "Mumbai" : "Lucknow").setCityTier(tier)
                .setEmployees(employees).setSites(sites)
                .setHasValidGstin(gst).setCurrentInternet(net).setBudgetInr(budget);
    }

    private static List<Line> suggestions(Recommendation rec) {
        List<Line> out = new ArrayList<>();
        for (Line l : rec.getLines()) if (l.getRole() == Role.ADD_ON && l.isSuggested()) out.add(l);
        return out;
    }

    private static List<String> ids(List<Line> lines) {
        List<String> out = new ArrayList<>();
        for (Line l : lines) out.add(l.getProduct().getId());
        return out;
    }

    private static boolean tappedFor(BusinessProfile p, String productId) {
        for (Need n : p.getNeeds()) if (NeedMap.productsFor(n).contains(productId)) return true;
        return false;
    }

    /** Independent re-check of every rule for every line shown (recommended, optional or core). */
    private static List<String> violations(BusinessProfile p, Recommendation rec) {
        List<String> v = catalogueViolations(p, rec);
        for (Line l : rec.getLines()) {
            String id = l.getProduct().getId();
            if (l.getRole() == Role.ADD_ON && l.isSelected() && !tappedFor(p, id) && !l.isSuggested()) {
                v.add(id + " ON in Your plan but neither tapped nor a learned pick");
            }
        }
        return v;
    }

    /** Every catalogue rule, the quantity cap, and the learned-score floor. */
    private static List<String> catalogueViolations(BusinessProfile p, Recommendation rec) {
        List<String> v = new ArrayList<>();
        for (Line l : rec.getLines()) {
            Product pr = l.getProduct();
            String id = pr.getId();
            if (!pr.isAvailableInTier(p.getCityTier())) v.add(id + " tier");
            if (p.getEmployees() < pr.getMinEmployees()) v.add(id + " min_employees");
            if (p.getSites() < pr.getMinSites()) v.add(id + " min_sites");
            if (pr.isRequiresGstin() && !p.hasValidGstin()) v.add(id + " gstin");
            if (l.getRole() == Role.ADD_ON && !pr.getRequiresOneOf().isEmpty()) {
                boolean ok = false;
                for (String r : pr.getRequiresOneOf()) ok |= rec.isSelected(r) && rec.line(r).getRole() != Role.ADD_ON;
                if (!ok) v.add(id + " requires_one_of");
            }
            if (l.getQuantity() > Math.max(p.getEmployees(), p.getSites())) v.add(id + " quantity above staff/sites");
            if (l.getRole() == Role.ADD_ON && l.isSelected() && !tappedFor(p, id)
                    && scorer.score(p, id) < RulesEngine.PACK_MIN_SCORE) {
                v.add(id + " added with learned score below 0.10");
            }
        }
        return v;
    }

    private static String fingerprint(Recommendation rec) {
        StringBuilder b = new StringBuilder();
        for (Line l : rec.getLines()) {
            b.append(l.getProduct().getId()).append('x').append(l.getQuantity())
                    .append(l.isSelected() ? "+" : "-").append(l.isRecommended() ? "R" : "").append(l.isSuggested() ? "S" : "")
                    .append('|').append(l.getReason()).append('\n');
        }
        for (Recommendation.Excluded e : rec.getExcluded()) b.append("X").append(e.getProduct().getId()).append('\n');
        return b.toString();
    }

    // 1. Property test: 1,000 random profiles, zero rule violations, only tapped extras ON,
    // at most 3 suggestions, all OFF.
    @Test
    public void thousandRandomProfilesNeverBreakRulesAndShowAtMostThreeSuggestions() {
        Random r = new Random(20261106L);
        Need[] needs = Need.values();
        InternetType[] nets = InternetType.values();
        for (int i = 0; i < 1000; i++) {
            Object[] t = TYPES[r.nextInt(TYPES.length)];
            BusinessProfile p = profile((String) t[0], (Segment) t[1], 1 + r.nextInt(2), 1 + r.nextInt(200),
                    1 + r.nextInt(12), r.nextBoolean(), nets[r.nextInt(nets.length)], BUDGETS[r.nextInt(BUDGETS.length)]);
            int k = r.nextInt(5);
            for (int j = 0; j < k; j++) p.addNeed(needs[r.nextInt(needs.length)]);

            Recommendation rec;
            if (r.nextInt(8) == 0) p.setBudgetInr(0).setBestPlan(true);
            rec = engine.recommend(p);
            List<String> v = violations(p, rec);
            // Filling toward the budget never pushes the plan over it.
            if (p.getBudgetInr() > 0) {
                int untappedOn = 0;
                for (Line l : rec.getLines()) {
                    if (l.getRole() == Role.ADD_ON && l.isSelected() && !tappedFor(p, l.getProduct().getId())) untappedOn++;
                }
                assertTrue("Profile #" + i + " budget fill exceeds budget",
                        untappedOn == 0 || rec.getMonthlyTotal() <= p.getBudgetInr());
            }
            assertTrue("Profile #" + i + " (" + t[0] + ", " + p.getNeeds() + "): " + v, v.isEmpty());
            assertTrue("Profile #" + i + " has > 3 suggestions", suggestions(rec).size() <= RulesEngine.MAX_SUGGESTED);
        }
    }

    // 2. Determinism.
    @Test
    public void sameProfileAlwaysGivesSamePlan() {
        for (int i = 0; i < 20; i++) {
            BusinessProfile a = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 3000)
                    .addNeed(Need.CCTV, Need.BILLING_POS);
            BusinessProfile b = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 3000)
                    .addNeed(Need.BILLING_POS, Need.CCTV);
            assertEquals(fingerprint(engine.recommend(a)), fingerprint(engine.recommend(b)));
        }
    }

    // 3. Fairness: the profile is built only from questionnaire fields, so contact name and state
    // cannot reach the engine. Two leads that differ only in those give the same plan.
    private static BusinessProfile fromLead(Map<String, String> lead) {
        return profile(lead.get("business_type"), Segment.valueOf(lead.get("segment")),
                Integer.parseInt(lead.get("city_tier")), Integer.parseInt(lead.get("employee_count")),
                Integer.parseInt(lead.get("num_sites")), "Y".equals(lead.get("has_gstin")),
                InternetType.valueOf(lead.get("current_internet")), Integer.parseInt(lead.get("budget")));
    }

    @Test
    public void contactNameAndStateDoNotChangeThePlan() {
        Map<String, String> a = new HashMap<>();
        a.put("business_type", "CA firm"); a.put("segment", "PROF"); a.put("city_tier", "1");
        a.put("employee_count", "12"); a.put("num_sites", "1"); a.put("has_gstin", "Y");
        a.put("current_internet", "BUSINESS_BROADBAND"); a.put("budget", "0");
        a.put("contact_name", "Prakash Shah"); a.put("state", "Tamil Nadu");
        Map<String, String> b = new HashMap<>(a);
        b.put("contact_name", "Fatima Khan"); b.put("state", "Bihar");
        assertEquals(fingerprint(engine.recommend(fromLead(a))), fingerprint(engine.recommend(fromLead(b))));
    }

    // 4a. Salon, Lucknow (Tier 2), 4 staff, no GSTIN, budget Rs 3,000, taps nothing:
    // the salon's usual extras are switched on only as far as the budget allows.
    @Test
    public void personaSalonLucknow() {
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 3000);
        Recommendation rec = engine.recommend(p);
        assertFalse(rec.contains("P03"));
        assertFalse(rec.contains("P15"));
        assertFalse(rec.contains("P14"));
        List<String> sug = ids(suggestions(rec));
        assertFalse("Salon should see suggestions", sug.isEmpty());
        for (String id : sug) {
            assertTrue("Unexpected salon suggestion " + id, id.equals("P19") || id.equals("P22") || id.equals("P23"));
        }
        assertFalse("Within the Rs 3,000 budget", rec.isOverBudget());
        assertTrue("Something fits the remaining budget", rec.isSelected("P19") || rec.isSelected("P23"));
    }

    // "Best plan": 300 Mbps instead of 100 Mbps, and every "often add" pick switched on.
    @Test
    public void bestPlanUpgradesFibreAndSwitchesOnSuggestions() {
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 0)
                .setBestPlan(true);
        Recommendation rec = engine.recommend(p);
        assertEquals("P02", rec.getPrimary().getProduct().getId());
        assertFalse(suggestions(rec).isEmpty());
        for (Line l : suggestions(rec)) assertTrue(l.getProduct().getId() + " should be ON", l.isSelected());
        BusinessProfile plain = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 0);
        assertTrue(rec.getMonthlyTotal() > engine.recommend(plain).getMonthlyTotal());
    }

    // Salon who taps UPI billing: that one is ON, it is not repeated as a suggestion.
    @Test
    public void tappedNeedIsOnAndNotAlsoSuggested() {
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 0)
                .addNeed(Need.BILLING_POS);
        Recommendation rec = engine.recommend(p);
        Line billing = rec.line("P19");
        assertTrue(billing.isRecommended());
        assertTrue(billing.isSelected());
        assertFalse(billing.isSuggested());
        assertTrue(billing.getReason().startsWith("You said you want UPI billing"));
        assertFalse(ids(suggestions(rec)).contains("P19"));
    }

    // 4b. CA firm, Chennai, 12 staff, GSTIN -> business email suggested, about one seat per person.
    @Test
    public void personaCaFirmChennai() {
        BusinessProfile p = profile("CA firm", Segment.PROF, 1, 12, 1, true, InternetType.BUSINESS_BROADBAND, 0);
        Recommendation rec = engine.recommend(p);
        Line email = rec.line("P16");
        assertNotNull(email);
        assertTrue("P16 should be a learned pick", email.isSuggested());
        assertTrue("Your plan preselects the top picks", email.isSelected());
        assertTrue("P16 qty " + email.getQuantity() + " should be close to 12 staff",
                email.getQuantity() >= 6 && email.getQuantity() <= 12);
        assertTrue("Why line should carry real social proof: " + email.getReason(),
                email.getReason().contains("of 13 businesses like yours"));
    }

    // 4c. Restaurant chain, 7 sites, 60 staff, GSTIN -> failover and SD-WAN suggested first.
    @Test
    public void personaRestaurantChain() {
        BusinessProfile p = profile("Restaurant chain", Segment.MULTI, 1, 60, 7, true, InternetType.BUSINESS_BROADBAND, 0);
        Recommendation rec = engine.recommend(p);
        List<String> sug = ids(suggestions(rec));
        assertEquals("P09", sug.get(0));
        assertEquals("P10", sug.get(1));
        assertEquals(3, sug.size());
    }

    // 3+ sites rule: two sites do not trigger it; SD-WAN is skipped when not eligible (no GSTIN).
    @Test
    public void chainRuleOnlyFromThreeSitesAndOnlyIfEligible() {
        BusinessProfile two = profile("Restaurant chain", Segment.MULTI, 1, 60, 2, true, InternetType.BUSINESS_BROADBAND, 0);
        assertFalse(ids(suggestions(engine.recommend(two))).contains("P10"));
        BusinessProfile noGst = profile("Restaurant chain", Segment.MULTI, 1, 60, 5, false, InternetType.BUSINESS_BROADBAND, 0);
        List<String> sug = ids(suggestions(engine.recommend(noGst)));
        assertEquals("P09", sug.get(0));
        assertFalse("SD-WAN needs GSTIN", sug.contains("P10"));
    }

    // 4d. Plastic moulding unit, 80 staff, GSTIN. P05 is the core link when the owner taps
    // "Always-on line" (core logic is rules-only and unchanged).
    @Test
    public void personaPlasticMouldingUnit() {
        BusinessProfile p = profile("Plastic moulding unit", Segment.MFG, 2, 80, 1, true, InternetType.LEASED_LINE, 0)
                .addNeed(Need.DEDICATED_LINE);
        Recommendation rec = engine.recommend(p);
        assertEquals("P05", rec.getPrimary().getProduct().getId());
        assertTrue("Firewall eligible", rec.contains("P21"));
        assertTrue("Machine IoT eligible", rec.contains("P24"));
    }

    // 4e. Only a mobile plan -> P07/P08 alone.
    @Test
    public void personaOnlyMobilePlan() {
        for (Object[] t : TYPES) {
            BusinessProfile p = profile((String) t[0], (Segment) t[1], 1, 8, 2, true, InternetType.MOBILE_HOTSPOT, 0)
                    .addNeed(Need.MOBILE_SIM);
            Recommendation rec = engine.recommend(p);
            assertFalse(rec.getLines().isEmpty());
            for (Line l : rec.getLines()) {
                String id = l.getProduct().getId();
                assertTrue(t[0] + " got " + id, id.equals("P07") || id.equals("P08"));
            }
        }
    }

    // 5. Missing / broken JSON and unknown business types fall back without crashing.
    @Test
    public void brokenJsonGivesEmptyScorerAndEngineStillWorks() throws Exception {
        ProductScorer broken = ProductScorer.loadOrEmpty(new StringReader("{ not json"));
        assertTrue(broken.isEmpty());
        RulesEngine rulesOnly = new RulesEngine(TestData.catalogue(), broken);
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 0)
                .addNeed(Need.CCTV);
        Recommendation rec = rulesOnly.recommend(p);
        assertNotNull(rec.getPrimary());
        assertTrue("Tapped need still ON without scores", rec.line("P23").isRecommended());
        assertTrue(rec.isSelected("P23"));
        assertTrue("No learned suggestions without scores", suggestions(rec).isEmpty());
        assertNull(broken.proof(p, "P23"));
    }

    @Test
    public void unknownBusinessTypeFallsBackToSegment() {
        BusinessProfile known = profile(null, Segment.PROF, 1, 12, 1, true, InternetType.BUSINESS_BROADBAND, 0);
        BusinessProfile unknown = profile("Space station", Segment.PROF, 1, 12, 1, true, InternetType.BUSINESS_BROADBAND, 0);
        for (String pid : new String[] {"P16", "P20", "P17"}) {
            assertEquals(scorer.score(known, pid), scorer.score(unknown, pid), 1e-9);
            assertTrue(scorer.score(unknown, pid) > 0);
        }
        assertEquals(fingerprint(engine.recommend(known)), fingerprint(engine.recommend(unknown)));
    }

    @Test
    public void shippedScoresCarryEvaluationWithZeroViolations() {
        assertFalse(scorer.isEmpty());
        assertNotNull(scorer.eval().get("winner"));
        assertEquals(0.0, (Double) scorer.eval().get("violations"), 0);
        double cutoff = scorer.suggestCutoff();
        assertTrue("cutoff " + cutoff, cutoff >= 0 && cutoff <= 0.6);
    }

    // Privacy-safe log: only bands and flags, and rows older than 90 days are deleted.
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void logHasNoPersonalDataAndPrunesAfter90Days() throws Exception {
        File f = new File(tmp.getRoot(), "log.csv");
        RecommendationLog log = new RecommendationLog(f);
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 0)
                .setGstin("27ABCDE1234F1Z5").setCity("Lucknow");
        long now = 1_800_000_000_000L;
        log.log("s-old", p, "P23", RecommendationLog.Event.SHOWN, now - RecommendationLog.RETENTION_MS - 1);
        log.log("s-new", p, "P23", RecommendationLog.Event.TOGGLED_OFF, now);
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.startsWith(RecommendationLog.HEADER));
        assertFalse("GSTIN must never be logged", text.contains("27ABCDE1234F1Z5"));
        assertFalse("City must never be logged", text.contains("Lucknow"));
        assertTrue(text.contains(",3-4,1,N,P23,TOGGLED_OFF"));

        assertEquals(1, log.prune(now));
        String after = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertFalse(after.contains("s-old"));
        assertTrue(after.contains("s-new"));
    }

    // ----- Packs: Your plan / Basic / Premium / Premium+ -----

    private static Pack pack(List<Pack> packs, Pack.Kind k) {
        for (Pack p : packs) if (p.kind == k) return p;
        return null;
    }

    @Test
    public void salonWithRs5000BudgetPacks() {
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 5000);
        List<Pack> packs = engine.packs(p);
        assertEquals(Pack.Kind.YOUR_PLAN, packs.get(0).kind);
        assertTrue("Your plan " + packs.get(0).monthly() + " must be <= 5,000", packs.get(0).monthly() <= 5000);
        assertEquals(0, packs.get(0).overBudgetInr);
        Pack plus = pack(packs, Pack.Kind.PREMIUM_PLUS);
        assertTrue("Premium+ must be <= 6,000 or hidden", plus == null || plus.monthly() <= 6000);
        for (Pack k : packs) {
            List<String> v = catalogueViolations(p, k.plan);
            assertTrue(k.kind + ": " + v, v.isEmpty());
        }
    }

    @Test
    public void everyPackObeysAllRulesForRandomProfiles() {
        Random r = new Random(7);
        Need[] needs = Need.values();
        InternetType[] nets = InternetType.values();
        for (int i = 0; i < 400; i++) {
            Object[] t = TYPES[r.nextInt(TYPES.length)];
            int budget = BUDGETS[r.nextInt(BUDGETS.length)];
            BusinessProfile p = profile((String) t[0], (Segment) t[1], 1 + r.nextInt(2), 1 + r.nextInt(200),
                    1 + r.nextInt(12), r.nextBoolean(), nets[r.nextInt(nets.length)], budget);
            for (int j = r.nextInt(4); j > 0; j--) p.addNeed(needs[r.nextInt(needs.length)]);
            List<Pack> packs = engine.packs(p);
            assertEquals(Pack.Kind.YOUR_PLAN, packs.get(0).kind);
            for (int a = 0; a < packs.size(); a++) {
                Pack k = packs.get(a);
                List<String> v = catalogueViolations(p, k.plan);
                assertTrue("#" + i + " " + k.kind + ": " + v, v.isEmpty());
                if (budget > 0 && a > 0) {
                    assertTrue("#" + i + " " + k.kind + " more than 20% over budget",
                            k.monthly() <= budget * (1 + RulesEngine.PACK_MAX_OVER));
                }
                for (int b = 0; b < a; b++) {
                    assertFalse("#" + i + " duplicate packs " + packs.get(b).kind + "/" + k.kind, k.sameAs(packs.get(b)));
                }
            }
        }
    }

    @Test
    public void premiumUpgradesInternetSimsAndAddsFailover() {
        BusinessProfile p = profile("Kirana store", Segment.MICRO, 2, 4, 1, true, InternetType.HOME_BROADBAND, 0);
        Pack premium = pack(engine.packs(p), Pack.Kind.PREMIUM);
        assertNotNull(premium);
        assertEquals("P01 -> P02", "P02", premium.plan.getPrimary().getProduct().getId());
        assertTrue("P07 -> P08", premium.plan.isSelected("P08"));
        assertFalse(premium.plan.contains("P07"));

        BusinessProfile office = profile("CA firm", Segment.PROF, 1, 12, 1, true, InternetType.BUSINESS_BROADBAND, 0);
        Pack officePremium = pack(engine.packs(office), Pack.Kind.PREMIUM);
        assertEquals("P02 -> P03 in Tier 1", "P03", officePremium.plan.getPrimary().getProduct().getId());
        assertTrue("Failover when eligible and kept by >=10%", officePremium.plan.isSelected("P09"));

        BusinessProfile tier2 = profile("CA firm", Segment.PROF, 2, 12, 1, true, InternetType.BUSINESS_BROADBAND, 0);
        assertEquals("No 1 Gbps outside Tier 1", "P02",
                pack(engine.packs(tier2), Pack.Kind.PREMIUM).plan.getPrimary().getProduct().getId());
    }

    @Test
    public void basicHasOnlyTappedExtras() {
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.HOME_BROADBAND, 0)
                .addNeed(Need.CCTV);
        Pack basic = pack(engine.packs(p), Pack.Kind.BASIC);
        assertNotNull(basic);
        for (Line l : basic.plan.getLines()) {
            if (l.getRole() == Role.ADD_ON && l.isSelected()) assertEquals("P23", l.getProduct().getId());
        }
    }

    @Test
    public void identicalPacksAreHidden() {
        // Mobile only: Basic == Your plan, Premium+ == Premium.
        BusinessProfile p = profile("Salon", Segment.MICRO, 2, 4, 1, false, InternetType.MOBILE_HOTSPOT, 0)
                .addNeed(Need.MOBILE_SIM);
        List<Pack> packs = engine.packs(p);
        assertEquals(2, packs.size());
        assertEquals(Pack.Kind.PREMIUM, packs.get(1).kind);
        assertTrue(packs.get(1).plan.isSelected("P08"));
    }
}
