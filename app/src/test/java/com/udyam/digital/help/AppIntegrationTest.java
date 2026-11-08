package com.udyam.digital.help;

import com.udyam.digital.help.core.BusinessProfile;
import com.udyam.digital.kyc.CheckResult;

import org.junit.Test;

import static org.junit.Assert.*;

/** The bridge between the main app and the help bot. */
public class AppIntegrationTest {

    private static CheckResult fail(String code, String key) {
        return new CheckResult(code, CheckResult.Status.FAIL, false, CheckResult.FixAt.NONE, null,
                com.udyam.digital.i18n.I18n.msg(key, "27******34X1Z5", "x"));
    }

    @Test
    public void kycCheckCodesMapToHelpIssues() {
        assertEquals(HelpContext.KycCheck.GSTIN_CANCELLED, AppHelpContext.map(fail("C02", "kyc_c02_cancelled")));
        assertEquals(HelpContext.KycCheck.GSTIN_SUSPENDED, AppHelpContext.map(fail("C02", "kyc_c02_suspended")));
        assertEquals(HelpContext.KycCheck.GSTIN_NOT_FOUND, AppHelpContext.map(fail("C02", "kyc_c02_notfound")));
        assertEquals(HelpContext.KycCheck.NAME_MISMATCH, AppHelpContext.map(fail("C04", "kyc_c04_fail")));
        assertEquals(HelpContext.KycCheck.ADDRESS_PROOF_OLD, AppHelpContext.map(fail("C13", "kyc_c13_missing")));
        assertEquals(HelpContext.KycCheck.UDYAM_MISSING, AppHelpContext.map(fail("C05", "kyc_c05_empty")));
        assertEquals(HelpContext.KycCheck.SIGNATORY_ID_UNREADABLE, AppHelpContext.map(fail("C19", "kyc_c19_unclear")));
        assertNull(AppHelpContext.map(fail("C16", "kyc_c16_same")));
    }

    @Test
    public void manualKycReviewAddsThirtyToTheEscalationScore() {
        BusinessProfile p = Fixtures.profile(BusinessProfile.Segment.MICRO, "Salon", "Lucknow", 4, 1,
                BusinessProfile.GstinStatus.NONE, BusinessProfile.Need.INTERNET);
        HelpContext plain = Fixtures.context(p, Lang.EN, JourneyStage.KYC, null);
        int before = Fixtures.session(plain).handle("my documents are stuck").score.score;

        HelpContext review = Fixtures.context(p.copy(), Lang.EN, JourneyStage.KYC, null);
        review.appEscalationPoints = AppHelpContext.KYC_MANUAL_REVIEW_POINTS;
        review.appEscalationReason = "KYC sent for manual review (ticket KYC-TEST)";
        HelpReply r = Fixtures.session(review).handle("my documents are stuck");
        assertEquals(before + 30, Math.min(100, r.score.score));
        assertTrue(r.score.reasons.toString(), r.score.reasons.toString().contains("manual review"));
    }

    // "How does the app work?" and friends, in English, Hindi and Hinglish.
    private static void expect(String msg, Lang lang, String intent) {
        BusinessProfile p = Fixtures.profile(BusinessProfile.Segment.MICRO, "Salon", "Lucknow", 4, 1,
                BusinessProfile.GstinStatus.NONE, BusinessProfile.Need.INTERNET);
        HelpReply r = Fixtures.session(Fixtures.context(p, lang, JourneyStage.ADVISOR, null)).handle(msg);
        assertEquals("'" + msg + "'", intent, r.intentId);
        assertFalse("answer for '" + msg + "' must be filled in: " + r.text, r.text.contains("{"));
    }

    @Test
    public void explainsHowTheAppWorks() {
        expect("How does this app work?", Lang.EN, "app_how_works");
        expect("how to use the app", Lang.EN, "app_how_works");
        expect("ऐप कैसे काम करता है?", Lang.HI, "app_how_works");
        expect("app kaise use kare", Lang.HI, "app_how_works");
        expect("How is my plan chosen?", Lang.EN, "app_plan_chosen");
        expect("plan kaise banta hai", Lang.HI, "app_plan_chosen");
        expect("What is premium plus?", Lang.EN, "app_packs");
        expect("बेसिक और प्रीमियम क्या हैं?", Lang.HI, "app_packs");
        expect("What are the KYC steps?", Lang.EN, "app_kyc_steps");
        expect("kyc kaise kare", Lang.HI, "app_kyc_steps");
        expect("How do I delete my documents?", Lang.EN, "app_delete_data");
        expect("दस्तावेज़ कैसे मिटाएँ?", Lang.HI, "app_delete_data");
        expect("I want to change my answers", Lang.EN, "app_change_answers");
        expect("Can the app read aloud?", Lang.EN, "app_read_aloud");
        expect("padhkar sunao", Lang.HI, "app_read_aloud");
    }

    @Test
    public void privacyAnswerMatchesTheRealApp() {
        String a = Fixtures.kb().get("data_privacy").answer(Lang.EN);
        assertTrue(a, a.contains("30 days after your KYC is decided"));
        assertTrue(a, a.contains("encrypted"));
        assertFalse(a, a.contains("90 days after activation"));
    }

    // The bot answers from the order the app passes in.
    private static HelpContext withOrder() {
        BusinessProfile p = Fixtures.profile(BusinessProfile.Segment.MICRO, "Salon", "Lucknow", 4, 1,
                BusinessProfile.GstinStatus.NONE, BusinessProfile.Need.INTERNET);
        HelpContext c = Fixtures.context(p, Lang.EN, JourneyStage.INSTALL, null);
        c.orderStatus = HelpContext.OrderStatus.VISIT_SCHEDULED;
        c.orderId = "UD-****-4821";
        c.orderDate = HelpContext.DEMO_TODAY;
        c.expectedDate = HelpContext.DEMO_TODAY.plusDays(7);
        c.connectionReady = HelpContext.DEMO_TODAY.plusDays(7);
        c.currentStepLabel = "Technician visit";
        c.visitSlot = "Fri 17 Jul, 10am–12pm";
        return c;
    }

    @Test
    public void whereIsMyOrderGivesTheCurrentStepAndDate() {
        HelpReply r = Fixtures.session(withOrder()).handle("Where is my order?");
        assertEquals("install_when", r.intentId);
        assertTrue(r.text, r.text.contains("UD-****-4821"));
        assertTrue(r.text, r.text.contains("Technician visit"));
        assertFalse(r.text, r.text.contains("{"));
    }

    @Test
    public void whenWillInternetStartGivesTheFibreDate() {
        HelpReply r = Fixtures.session(withOrder()).handle("When will internet start?");
        assertEquals("internet_start", r.intentId);
        assertFalse(r.text, r.text.contains("{"));
        assertFalse(r.text, r.text.contains(" - "));
    }

    @Test
    public void changeVisitOffersTheSlotPicker() {
        HelpReply r = Fixtures.session(withOrder()).handle("I want to change the visit time");
        assertEquals("technician_reschedule", r.intentId);
        assertEquals("RESCHEDULE_VISIT", r.action);
        assertTrue(r.text, r.text.contains("Fri 17 Jul"));
    }
}
