package com.udyam.digital.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.udyam.digital.help.core.BusinessProfile;
import com.udyam.digital.help.core.BusinessProfile.GstinStatus;
import com.udyam.digital.help.core.BusinessProfile.Need;
import com.udyam.digital.help.core.BusinessProfile.Segment;
import com.udyam.digital.help.core.GstinLookup;
import com.udyam.digital.help.core.Recommendation;
import com.udyam.digital.help.core.RulesEngine;

import org.junit.Test;

/** Catalogue rules, GSTIN checks, masking and the LLM guard rails. */
public class RulesAndSafetyTest {

    private final RulesEngine engine = Fixtures.engine();

    @Test public void catalogueLoaded() {
        assertEquals(25, Fixtures.catalogue().all().size());
        assertEquals(5, Fixtures.catalogue().get("P01").installDays);
        assertEquals(21, Fixtures.catalogue().get("P05").installDays);
    }

    @Test public void oneGbpsIsTier1Only() {
        BusinessProfile b = Fixtures.profile(Segment.PROFESSIONAL, "Office", "Lucknow", 20, 1, GstinStatus.ACTIVE);
        b.gstin = "x";
        assertEquals(RulesEngine.ReasonCode.CITY_TIER, engine.eligibility("P03", b, null).get(0).code);
        b.city = "Mumbai";
        assertTrue(engine.eligibility("P03", b, null).isEmpty());
    }

    @Test public void leasedLineNeedsStaffAndGstin() {
        BusinessProfile b = Fixtures.profile(Segment.MANUFACTURER, "Unit", "Surat", 8, 1, GstinStatus.NONE);
        String codes = engine.eligibility("P05", b, null).toString();
        assertTrue(codes, codes.contains("MIN_EMPLOYEES(10)") && codes.contains("GSTIN_REQUIRED"));
        assertTrue(engine.eligibility("P06", b, null).toString().contains("MIN_EMPLOYEES(25)"));
    }

    @Test public void cancelledGstinBlocksGstinProducts() {
        BusinessProfile b = Fixtures.profile(Segment.MICRO, "Shop", "Delhi", 3, 1, GstinStatus.CANCELLED);
        b.gstin = "masked";
        assertEquals(RulesEngine.ReasonCode.GSTIN_INACTIVE, engine.eligibility("P15", b, null).get(0).code);
    }

    @Test public void cctvNeedsBroadbandInBundle() {
        BusinessProfile b = Fixtures.profile(Segment.MICRO, "Shop", "Delhi", 3, 1, GstinStatus.NONE);
        assertEquals(RulesEngine.ReasonCode.NEEDS_ONE_OF, engine.eligibility("P23", b, new Recommendation()).get(0).code);
        Recommendation r = new Recommendation();
        r.add(Fixtures.catalogue().get("P01"), 1);
        assertTrue(engine.eligibility("P23", b, r).isEmpty());
    }

    @Test public void sdWanNeedsThreeSitesAndTenStaff() {
        BusinessProfile b = Fixtures.profile(Segment.MULTI_SITE, "Chain", "Pune", 12, 2, GstinStatus.ACTIVE,
                Need.INTERNET, Need.MULTI_SITE_NETWORK);
        b.gstin = "x";
        Recommendation r = engine.recommend(b);
        assertFalse(r.contains("P10"));
        b.sites = 3;
        assertTrue(engine.recommend(b).contains("P10"));
    }

    @Test public void noFibreGivesAirFiber() {
        BusinessProfile b = Fixtures.profile(Segment.MICRO, "Shop", "Patna", 3, 1, GstinStatus.NONE, Need.INTERNET);
        b.fibreAvailable = false;
        assertEquals("P04", engine.recommend(b).base().product.id);
    }

    @Test public void budgetRerunKeepsConnectionAndRecordsChanges() {
        BusinessProfile b = HelpPersonaTest.kiranaPatna();
        Recommendation r = engine.recommend(b);
        Recommendation cheaper = engine.rerunWithBudget(b, r, 1500);
        assertNotNull("never drops the connection", cheaper.base());
        assertFalse(cheaper.changes.isEmpty());
        assertTrue(cheaper.monthlyTotal() < r.monthlyTotal());
    }

    @Test public void installDaysComeFromSlowestProduct() {
        BusinessProfile b = Fixtures.profile(Segment.MANUFACTURER, "Unit", "Surat", 80, 1, GstinStatus.ACTIVE,
                Need.INTERNET, Need.SIMS);
        b.gstin = "x";
        Recommendation r = engine.recommend(b);
        assertEquals("P05", r.base().product.id);
        assertEquals(21, r.maxInstallDays());
    }

    // ---- GSTIN

    @Test public void gstinChecksumAcceptsRegistryNumbers() {
        assertEquals(GstinLookup.Format.VALID, GstinLookup.checkFormat(Fixtures.activeGstin("27")));
        assertEquals(GstinLookup.Format.VALID, GstinLookup.checkFormat("32ttnxb7384t1zw"));
        assertEquals(GstinLookup.Format.BAD_CHECKSUM, GstinLookup.checkFormat("32TTNXB7384T1ZX"));
        assertEquals(GstinLookup.Format.BAD_LENGTH, GstinLookup.checkFormat("32TTNXB7384"));
        assertEquals(GstinLookup.Format.BAD_PATTERN, GstinLookup.checkFormat("3ATTNXB7384T1ZW"));
    }

    @Test public void registryLookupHasStatuses() {
        GstinLookup g = Fixtures.gstin();
        assertEquals(3534, g.size());
        assertEquals(GstinStatus.ACTIVE, g.lookup(Fixtures.activeGstin("32")).status);
        assertEquals(GstinStatus.SUSPENDED, g.lookup("23QYBXS1811Q1ZD").status);
        assertEquals(GstinStatus.CANCELLED, g.lookup("37IHKXV5167L1ZS").status);
        assertNull(g.lookup("27AAAXA0000A1Z5"));
    }

    @Test public void gstinInvalidTwiceAdds20() {
        HelpContext c = Fixtures.context(HelpPersonaTest.kiranaPatna(), Lang.EN, JourneyStage.KYC, null);
        HelpSession s = Fixtures.session(c);
        HelpReply r = s.handle("my GSTIN is 32TTNXB7384T1ZX, it says invalid");
        assertEquals("gstin_invalid", r.intentId);
        assertTrue(r.text, r.text.contains("typing mistake"));
        r = s.handle("my GSTIN is 32TTNXB7384T1ZY still invalid");
        assertTrue(r.score.reasons.toString(), r.score.reasons.toString().contains("+20 GSTIN failed 2 or more times"));
    }

    // ---- masking and privacy

    @Test public void gstinMaskMatchesSpec() {
        assertEquals("27******34X1Z5", Masking.gstin("27XXXXX1234X1Z5"));
    }

    @Test public void freeTextMasksOtpAadhaarPanPhone() {
        String m = Masking.freeText("otp 482913 aadhaar 1234 5678 9012 pan ABCDE1234F call 9876543210");
        assertFalse(m.contains("482913"));
        assertFalse(m.contains("9012"));
        assertFalse(m.contains("ABCDE1234F"));
        assertFalse(m.contains("9876543210"));
    }

    @Test public void contextSentOutsideIsMaskedAndNameless() {
        BusinessProfile b = Fixtures.profile(Segment.PROFESSIONAL, "Clinic", "Pune", 8, 1, GstinStatus.ACTIVE, Need.INTERNET);
        b.contactName = "Anita Desai";
        String raw = Fixtures.activeGstin("27");
        HelpContext c = Fixtures.context(b, Lang.EN, JourneyStage.KYC, raw);
        String json = MiniJson.write(c.toMaskedMap());
        assertFalse(json.contains(raw));
        assertFalse(json.contains("Anita"));
        String body = LlmClient.requestBody(Fixtures.systemPrompt(), "kb", json, Masking.freeText("my otp is 123456"), "draft");
        assertFalse(body.contains(raw));
        assertFalse(body.contains("123456"));
        assertTrue(body.contains(LlmClient.MODEL));
    }

    @Test public void llmAnswerWithInventedNumberIsRejected() {
        assertTrue(AnswerComposer.numbersGrounded("Your plan is Rs 2,096 a month.", "Rs 2,096 total"));
        assertFalse(AnswerComposer.numbersGrounded("Special discount: Rs 999 only!", "Rs 2,096 total"));
    }

    @Test public void llmFallsBackWhenNotConfigured() {
        HelpContext c = Fixtures.context(HelpPersonaTest.kiranaPatna(), Lang.EN, JourneyStage.QUOTE, null);
        c.llmAvailable = true;
        HelpSession s = Fixtures.session(c);
        HelpReply r = s.handle("This is too expensive");
        String out = s.composer().polish(new LlmClient(""), Fixtures.systemPrompt(), r.entry, c, "too expensive", r.text, Lang.EN);
        assertEquals(r.text, out);
    }

    @Test public void systemPromptHasRequiredRules() {
        String p = Fixtures.systemPrompt();
        assertTrue(p.contains("{language}"));
        assertTrue(p.contains("Never invent prices"));
        assertTrue(p.contains("OTPs"));
        assertTrue(p.contains("80"));
    }

    @Test public void statsTrackHandoffs() {
        HelpStats stats = new HelpStats();
        HelpContext c = Fixtures.context(HelpPersonaTest.kiranaPatna(), Lang.EN, JourneyStage.ACTIVE, null);
        HelpSession s = new HelpSession(Fixtures.kb(), engine, Fixtures.gstin(), c, stats);
        s.handle("someone called asking for my OTP");
        assertEquals(1, stats.chats());
        assertEquals(1, stats.handoffs());
        assertEquals(0, stats.percentResolvedByBot());
        HelpStats copy = HelpStats.fromJson(stats.toJson());
        assertEquals(stats.handoffsByReason(), copy.handoffsByReason());
    }
}
