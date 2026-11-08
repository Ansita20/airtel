package com.udyam.digital.engine;

import com.udyam.digital.TestData;
import com.udyam.digital.data.CityTiers;
import com.udyam.digital.data.GstinLookup;
import com.udyam.digital.engine.Recommendation.Line;
import com.udyam.digital.engine.Recommendation.Role;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.InternetType;
import com.udyam.digital.model.Language;
import com.udyam.digital.model.Need;
import com.udyam.digital.model.Segment;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RulesEngineTest {

    private static RulesEngine engine;

    @BeforeClass
    public static void setUp() throws Exception {
        engine = new RulesEngine(TestData.catalogue());
    }

    private static BusinessProfile business(Segment seg, String city, int employees, int sites,
                                            boolean gstin, InternetType internet) {
        return new BusinessProfile()
                .setSegment(seg)
                .setCity(city)
                .setCityTier(CityTiers.tierOf(city))
                .setEmployees(employees)
                .setSites(sites)
                .setHasValidGstin(gstin)
                .setCurrentInternet(internet);
    }

    private static void assertExcludedWith(Recommendation rec, String id, String reasonFragment) {
        assertFalse(id + " should not be in the plan", rec.contains(id));
        Recommendation.Excluded e = rec.excluded(id);
        assertNotNull(id + " should be listed as excluded", e);
        assertTrue("Reason for " + id + " was: " + e.getReason(), e.getReason().contains(reasonFragment));
    }

    // 1. Tier 2 business asking for 1 Gbps: P03 is Tier 1 only.
    @Test
    public void tier2AskingFor1GbpsGetsFastestAvailableFibre() {
        BusinessProfile p = business(Segment.PROF, "Indore", 20, 1, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.FAST_INTERNET);
        Recommendation rec = engine.recommend(p);

        assertExcludedWith(rec, "P03", "Tier 2");
        assertEquals("P02", rec.getPrimary().getProduct().getId());
        assertTrue(rec.getPrimary().getReason().contains("300 Mbps"));
    }

    // 2. 3-person shop asking for guaranteed uptime: leased line needs 10+ employees.
    @Test
    public void threePersonShopGetsNoLeasedLine() {
        BusinessProfile p = business(Segment.MICRO, "Pune", 3, 1, true, InternetType.HOME_BROADBAND)
                .addNeed(Need.DEDICATED_LINE, Need.BILLING_POS);
        Recommendation rec = engine.recommend(p);

        assertExcludedWith(rec, "P05", "at least 10 employees");
        assertFalse(rec.contains("P06"));
        assertEquals("P01", rec.getPrimary().getProduct().getId());
        assertEquals("P07", rec.line("P07").getProduct().getId());
        assertEquals(3, rec.line("P07").getQuantity());
        assertTrue(rec.isSelected("P19"));
    }

    // 3. No GSTIN: no WhatsApp API, SMS or cloud telephony.
    @Test
    public void noGstinBlocksWhatsappSmsAndTelephony() {
        BusinessProfile p = business(Segment.PROF, "Delhi", 8, 1, false, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.WHATSAPP, Need.BULK_SMS, Need.CLOUD_PHONE, Need.EMAIL_OFFICE);
        Recommendation rec = engine.recommend(p);

        assertExcludedWith(rec, "P15", "GSTIN");
        assertExcludedWith(rec, "P14", "GSTIN");
        assertExcludedWith(rec, "P12", "GSTIN");
        assertTrue("Non-GST products still offered", rec.isSelected("P16"));
        assertEquals(8, rec.line("P16").getQuantity());
    }

    // 4. 5-site chain: SD-WAN (min 3 sites, 10 employees, GSTIN) is eligible.
    @Test
    public void fiveSiteChainIsSdWanEligible() {
        BusinessProfile p = business(Segment.MULTI, "Bengaluru", 15, 5, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.MULTI_SITE_NETWORK, Need.BACKUP_INTERNET);
        Recommendation rec = engine.recommend(p);

        Line sdwan = rec.line("P10");
        assertNotNull("SD-WAN should be recommended", sdwan);
        assertTrue(sdwan.isSelected());
        assertEquals(5, sdwan.getQuantity());
        assertEquals("P02", rec.getPrimary().getProduct().getId());
        assertEquals(5, rec.getPrimary().getQuantity());
        assertEquals(5, rec.line("P09").getQuantity());
        assertEquals("P08", rec.line("P08").getProduct().getId());
    }

    // 5. Failover without broadband: P09 needs fibre or a leased line, P04 doesn't count.
    @Test
    public void failoverWithoutBroadbandIsExcluded() {
        BusinessProfile p = business(Segment.MICRO, "Lucknow", 4, 1, false, InternetType.MOBILE_HOTSPOT)
                .addNeed(Need.BACKUP_INTERNET);
        Recommendation rec = engine.recommend(p);

        assertEquals("P04", rec.getPrimary().getProduct().getId());
        assertExcludedWith(rec, "P09", "Works only with Biz Fibre or a leased line");
    }

    // 6. Failover with fibre is fine: one router per site.
    @Test
    public void failoverWithFibreIsIncludedPerSite() {
        BusinessProfile p = business(Segment.PROF, "Kochi", 6, 2, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.BACKUP_INTERNET);
        Recommendation rec = engine.recommend(p);

        assertTrue(rec.isSelected("P09"));
        assertEquals(2, rec.line("P09").getQuantity());
        assertEquals(2 * 1500, rec.line("P09").getSetupInr());
    }

    // 7. Tier 1 office with 12 staff and GSTIN can get 1 Gbps.
    @Test
    public void tier1OfficeGetsGigFibre() {
        BusinessProfile p = business(Segment.PROF, "Mumbai", 12, 1, true, InternetType.HOME_BROADBAND)
                .addNeed(Need.FAST_INTERNET);
        Recommendation rec = engine.recommend(p);

        assertEquals("P03", rec.getPrimary().getProduct().getId());
        assertFalse(rec.isExcluded("P03"));
    }

    // 8. Manufacturer with 30 staff: leased line, machine IoT, firewall; static IP needs fibre.
    @Test
    public void manufacturerGetsLeasedLineAndIot() {
        BusinessProfile p = business(Segment.MFG, "Surat", 30, 1, true, InternetType.LEASED_LINE)
                .addNeed(Need.DEDICATED_LINE, Need.MACHINE_MONITORING, Need.CYBER_SECURITY, Need.REMOTE_ACCESS);
        Recommendation rec = engine.recommend(p);

        assertEquals("P05", rec.getPrimary().getProduct().getId());
        assertEquals(6, rec.line("P24").getQuantity()); // 30 staff / 5
        assertTrue(rec.isSelected("P21"));
        assertTrue(rec.isSelected("P20"));
        assertExcludedWith(rec, "P11", "Biz Fibre"); // requires P01|P02|P03
        assertEquals(24, rec.getContractMonths());     // P21 and P24 are 24-month
    }

    // 9. SD-WAN not offered to a 2-site business.
    @Test
    public void twoSitesAreNotSdWanEligible() {
        BusinessProfile p = business(Segment.MULTI, "Jaipur", 20, 2, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.MULTI_SITE_NETWORK);
        Recommendation rec = engine.recommend(p);

        assertExcludedWith(rec, "P10", "at least 3 sites");
    }

    // 10. Over budget: lowest-priority add-ons switched off first, kept as optional toggles.
    @Test
    public void overBudgetMovesLowestPriorityAddOnsToOptional() {
        BusinessProfile p = business(Segment.MICRO, "Nagpur", 2, 1, true, InternetType.HOME_BROADBAND)
                .addNeed(Need.BILLING_POS, Need.TOLL_FREE, Need.BULK_SMS, Need.WEBSITE)
                .setBudgetInr(2500);
        Recommendation rec = engine.recommend(p);

        // Core: P01 999 + 2 x P07 299 = 1597. Add-ons: P19 599, P13 1500, P14 1500, P18 299.
        assertTrue(rec.contains("P13"));
        assertFalse("Toll-free (lowest priority) switched off", rec.isSelected("P13"));
        assertFalse("SMS switched off", rec.isSelected("P14"));
        assertTrue(rec.line("P13").isMovedForBudget());
        assertNotNull(rec.line("P13").getNote());
        assertTrue("Website still fits", rec.isSelected("P18"));
        assertTrue("Billing is high priority", rec.isSelected("P19"));
        assertEquals(1597 + 599 + 299, rec.getMonthlyTotal());
        assertFalse(rec.isOverBudget());

        // Switching an optional add-on back on updates the total live.
        rec.line("P13").setSelected(true);
        assertEquals(1597 + 599 + 299 + 1500, rec.getMonthlyTotal());
        assertTrue(rec.isOverBudget());
    }

    // 11. Totals: monthly at price_min x qty, setup per unit, contract = longest minimum.
    @Test
    public void totalsArePricedAtMinWithSetupAndMaxContract() {
        BusinessProfile p = business(Segment.MULTI, "Chennai", 15, 5, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.MULTI_SITE_NETWORK, Need.BACKUP_INTERNET, Need.CCTV);
        Recommendation rec = engine.recommend(p);

        int expectedMonthly = 1499 * 5   // P02 x 5 sites
                + 599 * 15               // P08 x 15 employees
                + 499 * 5                // P09 x 5
                + 2500 * 5               // P10 x 5
                + 199 * 10;              // P23 x 2 per site
        int expectedSetup = 1500 * 5 + 5000 * 5 + 1800 * 10;
        assertEquals(expectedMonthly, rec.getMonthlyTotal());
        assertEquals(expectedSetup, rec.getSetupTotal());
        assertEquals(24, rec.getContractMonths());
    }

    // 12. Cancelled GSTIN from the registry blocks GST-only products.
    @Test
    public void cancelledGstinFromRegistryBlocksGstProducts() throws Exception {
        GstinLookup lookup = TestData.gstin();
        GstinLookup.Result r = lookup.lookup("23QYBXS1811Q1ZD");
        assertTrue(r.isFound());
        assertFalse(r.isActive());

        BusinessProfile p = business(Segment.MICRO, r.city, 5, 1, r.isActive(), InternetType.HOME_BROADBAND)
                .addNeed(Need.WHATSAPP);
        Recommendation rec = engine.recommend(p);
        assertExcludedWith(rec, "P15", "GSTIN");
    }

    // 13. No internet today -> 5G AirFiber, and CCTV still works with it.
    @Test
    public void noInternetGetsAirFiberAndCctvStillWorks() {
        BusinessProfile p = business(Segment.MICRO, "Patna", 2, 1, false, InternetType.NONE)
                .addNeed(Need.CCTV, Need.GUEST_WIFI);
        Recommendation rec = engine.recommend(p);

        assertEquals("P04", rec.getPrimary().getProduct().getId());
        assertTrue(rec.isSelected("P23")); // requires_one_of includes P04
        assertTrue(rec.isSelected("P22"));
        assertEquals(12, rec.getContractMonths()); // P04 is 6 months, CCTV / Wi-Fi are 12
    }

    // 14. Small office without enough staff for firewall: endpoint security still offered.
    @Test
    public void smallOfficeGetsEndpointButNotFirewall() {
        BusinessProfile p = business(Segment.PROF, "Hyderabad", 5, 1, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.CYBER_SECURITY);
        Recommendation rec = engine.recommend(p);

        assertTrue(rec.isSelected("P20"));
        assertEquals(5, rec.line("P20").getQuantity());
        assertExcludedWith(rec, "P21", "at least 10 employees");
    }

    // 15. Hindi profile gets Hindi reasons.
    @Test
    public void hindiProfileGetsHindiReasons() {
        BusinessProfile p = business(Segment.MICRO, "Bhopal", 3, 1, false, InternetType.HOME_BROADBAND)
                .addNeed(Need.WHATSAPP)
                .setLanguage(Language.HI);
        Recommendation rec = engine.recommend(p);

        assertTrue(rec.excluded("P15").getReason().contains("GSTIN ज़रूरी"));
        assertTrue(rec.getPrimary().getReason().contains("फ़ाइबर"));
    }

    // 16. Primary link can never be switched off.
    @Test
    public void primaryCannotBeToggledOff() {
        BusinessProfile p = business(Segment.MICRO, "Mysuru", 2, 1, false, InternetType.HOME_BROADBAND);
        Recommendation rec = engine.recommend(p);
        Line primary = rec.getPrimary();
        assertEquals(Role.PRIMARY, primary.getRole());
        primary.setSelected(false);
        assertTrue(primary.isSelected());
        assertNull(rec.excluded("P01"));
    }

    // 17. Budget that even core can't fit: everything optional, flagged as over budget.
    @Test
    public void budgetBelowCoreIsFlagged() {
        BusinessProfile p = business(Segment.PROF, "Delhi", 10, 1, true, InternetType.BUSINESS_BROADBAND)
                .addNeed(Need.EMAIL_OFFICE)
                .setBudgetInr(2000);
        Recommendation rec = engine.recommend(p);

        assertFalse(rec.isSelected("P16"));
        assertTrue(rec.isOverBudget()); // P02 1499 + P08 10 x 599
    }

    // Core services: a business can want SIMs, broadband, or both.

    private static boolean hasRole(Recommendation rec, Role role) {
        for (Line l : rec.getLines()) if (l.getRole() == role) return true;
        return false;
    }

    @Test
    public void simOnlyGetsMobileLinesAndNoBroadband() {
        BusinessProfile p = business(Segment.MICRO, "Pune", 3, 1, false, InternetType.MOBILE_HOTSPOT)
                .addNeed(Need.MOBILE_SIM);
        Recommendation rec = engine.recommend(p);

        assertNull(rec.getPrimary());
        assertTrue(rec.contains("P07"));
    }

    @Test
    public void broadbandOnlyGetsPrimaryLinkAndNoMobileLines() {
        BusinessProfile p = business(Segment.MICRO, "Pune", 3, 1, false, InternetType.MOBILE_HOTSPOT)
                .addNeed(Need.BROADBAND);
        Recommendation rec = engine.recommend(p);

        assertNotNull(rec.getPrimary());
        assertFalse(hasRole(rec, Role.MOBILE));
    }

    @Test
    public void simAndBroadbandTogetherGetBoth() {
        BusinessProfile p = business(Segment.PROF, "Pune", 8, 1, true, InternetType.HOME_BROADBAND)
                .addNeed(Need.MOBILE_SIM)
                .addNeed(Need.BROADBAND);
        Recommendation rec = engine.recommend(p);

        assertNotNull(rec.getPrimary());
        assertTrue(rec.contains("P08"));
    }

    @Test
    public void fastInternetImpliesBroadbandEvenWithOnlySimPicked() {
        BusinessProfile p = business(Segment.PROF, "Pune", 8, 1, true, InternetType.HOME_BROADBAND)
                .addNeed(Need.MOBILE_SIM)
                .addNeed(Need.FAST_INTERNET);
        Recommendation rec = engine.recommend(p);

        assertNotNull(rec.getPrimary());
        assertTrue(rec.contains("P08"));
    }

    /** Whatever the business looks like, asking for SIM + broadband always yields both, even on a tight budget. */
    @Test
    public void simAndBroadbandAlwaysBothRecommendedForEveryKindOfBusiness() {
        String[] cities = {"Mumbai", "Indore", "Other"};
        int[] employees = {2, 3, 5, 10, 25, 50};
        int[] sites = {1, 2, 5, 10};
        int[] budgets = {0, 2000, 50000};
        for (Segment seg : Segment.values())
            for (String city : cities)
                for (int e : employees)
                    for (int st : sites)
                        for (boolean gst : new boolean[] {true, false})
                            for (InternetType net : InternetType.values())
                                for (int budget : budgets) {
                                    BusinessProfile p = business(seg, city, e, st, gst, net)
                                            .setBudgetInr(budget)
                                            .addNeed(Need.MOBILE_SIM, Need.BROADBAND, Need.CCTV, Need.BILLING_POS);
                                    Recommendation rec = engine.recommend(p);
                                    String where = seg + " " + city + " emp=" + e + " sites=" + st
                                            + " gst=" + gst + " net=" + net + " budget=" + budget;
                                    assertNotNull("No broadband for " + where, rec.getPrimary());
                                    assertTrue("Broadband switched off for " + where, rec.getPrimary().isSelected());
                                    assertTrue("No SIM lines for " + where, hasRole(rec, Role.MOBILE));
                                }
    }
}
