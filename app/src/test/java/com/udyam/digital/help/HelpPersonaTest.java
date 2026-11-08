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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Scripted conversations P1-P10 from the spec. Each turn asserts the intent, the score band
 * and the action. Run with -Dpersona.verbose=true to print every turn.
 */
public class HelpPersonaTest {

    private static final boolean VERBOSE = Boolean.getBoolean("persona.verbose");

    private static HelpReply say(HelpSession s, String msg) {
        HelpReply r = s.handle(msg);
        if (VERBOSE) {
            System.out.println("USER: " + msg);
            System.out.println("  -> " + r.intentId + " | " + r.score.score + " " + r.score.band + " " + r.score.priority
                    + " | " + r.action + " | " + r.score.reasons);
            System.out.println("  BOT: " + r.text.replace("\n", " / "));
        }
        assertFalse("raw placeholder left in answer: " + r.text, r.text.matches("(?s).*\\{[a-z_]+\\}.*"));
        return r;
    }

    private static void turn(HelpReply r, String intent, EscalationScorer.Band band, String action) {
        assertEquals("intent", intent, r.intentId);
        assertEquals("band for " + intent + " " + r.score, band, r.score.band);
        if (action != null) assertEquals("action", action, r.action);
    }

    // ---------------------------------------------------------------- P1
    static BusinessProfile kiranaPatna() {
        BusinessProfile b = Fixtures.profile(Segment.MICRO, "Kirana store", "Patna", 3, 1, GstinStatus.NONE,
                Need.INTERNET, Need.SIMS, Need.POS, Need.CCTV, Need.GUEST_WIFI, Need.WHATSAPP);
        b.monthlyBudget = 5000;
        b.currentInternet = "Mobile hotspot";
        b.sims = 2;
        return b;
    }

    @Test public void p1_kiranaPatna() {
        HelpContext c = Fixtures.context(kiranaPatna(), Lang.HI, JourneyStage.QUOTE, null);
        assertTrue("WhatsApp API is skipped without GSTIN", c.recommendation.skipped.containsKey("P15"));
        HelpSession s = Fixtures.session(c);

        HelpReply r = say(s, "WhatsApp wala option kyun nahi dikh raha?");
        turn(r, "product_not_shown", EscalationScorer.Band.BOT, "EXPLAIN_ELIGIBILITY");
        assertTrue(r.text, r.text.contains("GSTIN"));
        assertEquals(Lang.HI, r.language);

        r = say(s, "fibre nahi aata hamare area mein");
        turn(r, "fibre_not_available", EscalationScorer.Band.BOT, "SWITCH_TO_AIRFIBER");
        assertTrue(r.text.contains("AirFiber"));
        s.acceptAction("fibre_not_available");
        assertEquals("P04", c.recommendation.base().product.id);

        int before = c.recommendation.monthlyTotal();
        r = say(s, "itna mehenga kyun");
        turn(r, "too_expensive", EscalationScorer.Band.BOT, "RERUN_ENGINE");
        int after = s.pendingProposal().monthlyTotal();
        assertTrue("re-run is cheaper: " + before + " -> " + after, after < before);
        assertTrue("within Rs 5,000 and 20% lower", after <= 5000 && after <= before * 0.8);

        c.stage = JourneyStage.PAYMENT;
        r = say(s, "payment kat gaya par order nahi bana");
        turn(r, "payment_deducted_no_order", EscalationScorer.Band.HANDOFF, "OFFER_HANDOFF");
        assertNotNull(r.ticket);
        assertEquals(EscalationScorer.Priority.HIGH, r.ticket.priority);
        assertEquals(3, r.ticket.summary.size());
    }

    // ---------------------------------------------------------------- P2
    @Test public void p2_salonLucknowWants1Gbps() {
        BusinessProfile b = Fixtures.profile(Segment.MICRO, "Salon", "Lucknow", 4, 1, GstinStatus.ACTIVE, Need.INTERNET, Need.POS);
        HelpContext c = Fixtures.context(b, null, JourneyStage.QUOTE, Fixtures.activeGstin("09"));
        HelpSession s = Fixtures.session(c);

        HelpReply r = say(s, "1 Gbps chahiye");
        turn(r, "want_1gbps", EscalationScorer.Band.BOT, "EXPLAIN_ELIGIBILITY");
        assertEquals("Hinglish -> Hindi", Lang.HI, r.language);
        assertTrue("explains Tier", r.text.contains("Tier 1") && r.text.contains("Lucknow"));
        assertTrue("offers Fibre 300 instead", r.text.contains("Biz Fibre 300"));
    }

    // ---------------------------------------------------------------- P3
    @Test public void p3_dentalClinicPuneNameMismatchTwice() {
        BusinessProfile b = Fixtures.profile(Segment.PROFESSIONAL, "Dental clinic", "Pune", 8, 1, GstinStatus.ACTIVE,
                Need.INTERNET, Need.EMAIL, Need.SECURITY, Need.CLOUD_BACKUP);
        HelpContext c = Fixtures.context(b, Lang.EN, JourneyStage.KYC, Fixtures.activeGstin("27"));
        c.mismatchField = "Legal name on GST certificate";
        HelpSession s = Fixtures.session(c);

        s.onKycCheckFailed(HelpContext.KycCheck.NAME_MISMATCH);
        HelpReply r = say(s, "The name on my document does not match");
        turn(r, "kyc_name_mismatch", EscalationScorer.Band.BOT, "OPEN_KYC_UPLOAD");
        assertTrue(r.text.contains("Legal name on GST certificate"));
        s.acceptAction("kyc_name_mismatch"); // user re-uploads

        s.onKycCheckFailed(HelpContext.KycCheck.NAME_MISMATCH); // second mismatch from the KYC check
        r = say(s, "I re-uploaded but it says name mismatch again");
        turn(r, "kyc_name_mismatch", EscalationScorer.Band.HANDOFF, "OPEN_KYC_UPLOAD");
        assertNotNull(r.ticket);
    }

    // ---------------------------------------------------------------- P4
    @Test public void p4_restaurantChainTwoStatesMarathiOffline() {
        BusinessProfile b = Fixtures.profile(Segment.MULTI_SITE, "Restaurant chain", "Mumbai", 60, 7, GstinStatus.ACTIVE,
                Need.INTERNET, Need.SIMS, Need.POS, Need.CCTV, Need.BACKUP_INTERNET, Need.MULTI_SITE_NETWORK);
        b.siteStates = new LinkedHashSet<>(Arrays.asList("Maharashtra", "Gujarat"));
        HelpContext c = Fixtures.context(b, Lang.MR, JourneyStage.QUOTE, Fixtures.activeGstin("27"));
        c.llmAvailable = false;
        assertTrue("SD-WAN eligible: 7 sites, 60 staff, GSTIN", c.recommendation.contains("P10"));
        HelpSession s = Fixtures.session(c);

        HelpReply r = say(s, "We have 7 restaurants in Maharashtra and Gujarat. How does it work for different states?");
        turn(r, "multi_site_states", EscalationScorer.Band.BOT, null);
        assertTrue("per-state GST note", r.text.contains("GSTIN"));
        assertTrue("SD-WAN mentioned", r.text.contains("SD-WAN"));
        assertTrue("+15 language points", r.score.reasons.toString().contains("+15 no offline answers"));
        assertEquals(15, r.score.score);
        assertEquals("Marathi offline falls back to Hindi", Lang.HI, r.language);
        assertTrue("offers a Marathi-speaking agent", r.text.contains("Marathi"));
        assertEquals(EscalationScorer.Priority.HIGH, r.score.priority);
    }

    // ---------------------------------------------------------------- P5
    @Test public void p5_textileSuratLeasedLine25DaysLate() {
        BusinessProfile b = Fixtures.profile(Segment.MANUFACTURER, "Textile unit", "Surat", 80, 1, GstinStatus.ACTIVE,
                Need.INTERNET, Need.SIMS, Need.EMAIL, Need.MACHINE_IOT);
        HelpContext c = Fixtures.context(b, Lang.GU, JourneyStage.INSTALL, Fixtures.activeGstin("24"));
        assertEquals("P05", c.recommendation.base().product.id);
        c.orderStatus = HelpContext.OrderStatus.VISIT_SCHEDULED;
        c.orderId = "UD-24017";
        c.orderDate = c.today.minusDays(25);
        c.expectedDate = c.orderDate.plusDays(21);
        HelpSession s = Fixtures.session(c);

        HelpReply r = say(s, "My leased line was promised in 21 days. It's been 25 days and it is still not installed!");
        turn(r, "install_late", EscalationScorer.Band.HANDOFF, "SHOW_TRACKER");
        assertEquals(EscalationScorer.Priority.HIGH, r.score.priority);
        assertNotNull(r.ticket);
        assertTrue(r.ticket.summary.get(2).contains("Textile unit"));
    }

    // ---------------------------------------------------------------- P6
    @Test public void p6_caFirmChennaiTamilPrivacy() {
        BusinessProfile b = Fixtures.profile(Segment.PROFESSIONAL, "CA firm", "Chennai", 6, 1, GstinStatus.ACTIVE,
                Need.INTERNET, Need.EMAIL, Need.CLOUD_BACKUP);
        HelpContext c = Fixtures.context(b, Lang.TA, JourneyStage.KYC, Fixtures.activeGstin("33"));
        HelpSession s = Fixtures.session(c);

        HelpReply r = say(s, "is my PAN safe?");
        turn(r, "data_privacy", EscalationScorer.Band.BOT, null);
        assertTrue(r.text.contains("masked"));
        assertTrue(r.text.contains("30 days")); // documents deleted 30 days after KYC is decided
        assertTrue("never asks for OTP", r.text.contains("never ask for your OTP"));
    }

    // ---------------------------------------------------------------- P7
    private static HelpContext pharmacyKochi(boolean llm) {
        BusinessProfile b = Fixtures.profile(Segment.MICRO, "Pharmacy", "Kochi", 4, 1, GstinStatus.ACTIVE,
                Need.INTERNET, Need.SIMS, Need.POS);
        HelpContext c = Fixtures.context(b, Lang.ML, JourneyStage.ACTIVE, Fixtures.activeGstin("32"));
        c.llmAvailable = llm;
        c.orderStatus = HelpContext.OrderStatus.ACTIVE;
        return c;
    }

    @Test public void p7_pharmacyKochiSlowInternetOffline() {
        HelpSession s = Fixtures.session(pharmacyKochi(false));
        HelpReply r = say(s, "speed slow");
        turn(r, "internet_down", EscalationScorer.Band.BOT, "TROUBLESHOOT");
        assertTrue("troubleshooting steps first", r.text.contains("router"));
        assertTrue("mentions failover", r.text.contains("Failover"));
        r = say(s, "still not working");
        turn(r, "internet_down", EscalationScorer.Band.CALLBACK, "TROUBLESHOOT");
        r = say(s, "still not working");
        turn(r, "internet_down", EscalationScorer.Band.HANDOFF, "TROUBLESHOOT");
    }

    @Test public void p7_pharmacyKochiSlowInternetWithLlm() {
        HelpSession s = Fixtures.session(pharmacyKochi(true));
        turn(say(s, "speed slow"), "internet_down", EscalationScorer.Band.BOT, "TROUBLESHOOT");
        HelpReply r = say(s, "still not working");
        turn(r, "internet_down", EscalationScorer.Band.CALLBACK, "TROUBLESHOOT");
        assertEquals(45, r.score.score);
        r = say(s, "still not working");
        turn(r, "internet_down", EscalationScorer.Band.HANDOFF, "TROUBLESHOOT");
        assertEquals(70, r.score.score);
    }

    // ---------------------------------------------------------------- P8
    @Test public void p8_fraudOtpImmediateHandoff() {
        HelpContext c = Fixtures.context(kiranaPatna(), Lang.EN, JourneyStage.ACTIVE, null);
        HelpSession s = Fixtures.session(c);
        HelpReply r = say(s, "someone called for my OTP");
        turn(r, "fraud_otp", EscalationScorer.Band.HANDOFF, "OFFER_HANDOFF");
        assertEquals(100, r.score.score);
        assertNotNull(r.ticket);
        assertTrue(r.text.contains("do NOT share"));
    }

    // ---------------------------------------------------------------- P9
    @Test public void p9_asksForAgentTwice() {
        HelpContext c = Fixtures.context(kiranaPatna(), Lang.HI, JourneyStage.QUOTE, null);
        HelpSession s = Fixtures.session(c);
        HelpReply r = say(s, "agent se baat karao");
        turn(r, "human_request", EscalationScorer.Band.BOT, "OFFER_HANDOFF");
        assertNull("first ask: one attempt to help", r.ticket);
        assertTrue("button always offered", r.quickReplies.contains(Fixtures.kb().phrase("chip_talk_person", Lang.HI)));
        r = say(s, "agent se baat karao");
        turn(r, "human_request", EscalationScorer.Band.HANDOFF, "OFFER_HANDOFF");
        assertNotNull(r.ticket);
    }

    @Test public void talkToPersonButtonIsNeverBlocked() {
        HelpContext c = Fixtures.context(kiranaPatna(), Lang.EN, JourneyStage.ADVISOR, null);
        HelpSession s = Fixtures.session(c);
        say(s, "hello");
        assertEquals(0, s.lastScore().score);
        HelpReply r = s.requestHuman();
        assertNotNull("ticket even at score 0", r.ticket);
        assertEquals("button", r.ticket.trigger);
    }

    // ---------------------------------------------------------------- P10
    @Test public void p10_fairnessSameProfileDifferentNames() {
        List<String> script = Arrays.asList(
                "WhatsApp wala option kyun nahi dikh raha?", "itna mehenga kyun", "galat", "bakwas, kitni baar!!",
                "agent se baat karao", "agent se baat karao");
        List<String> a = runWithName("Sunita Devi", script);
        List<String> b = runWithName("Mohammed Irfan", script);
        List<String> c = runWithName("Gurpreet Kaur", script);
        assertEquals(a, b);
        assertEquals(a, c);
    }

    private static List<String> runWithName(String name, List<String> script) {
        BusinessProfile p = kiranaPatna();
        p.contactName = name;
        HelpSession s = Fixtures.session(Fixtures.context(p, Lang.HI, JourneyStage.QUOTE, null));
        List<String> out = new ArrayList<>();
        for (String m : script) {
            HelpReply r = s.handle(m);
            // Ticket ids are unique per ticket, so compare everything except them.
            out.add(r.intentId + "|" + r.score.score + "|" + r.score.band + "|" + r.score.priority + "|"
                    + r.text.replaceAll("HT-\\d+-\\d+", "HT"));
        }
        return out;
    }

    // ---------------------------------------------------------------- extra checks
    @Test public void ticketHasNoNameAndOnlyMaskedGstin() {
        BusinessProfile b = Fixtures.profile(Segment.PROFESSIONAL, "Dental clinic", "Pune", 8, 1, GstinStatus.ACTIVE, Need.INTERNET);
        b.contactName = "Priya Nair";
        String raw = Fixtures.activeGstin("27");
        HelpContext c = Fixtures.context(b, Lang.EN, JourneyStage.KYC, raw);
        HelpSession s = Fixtures.session(c);
        String json = s.requestHuman().ticket.toJson();
        assertFalse(json.contains("Priya"));
        assertFalse(json.contains(raw));
        assertTrue(json.contains(Masking.gstin(raw)));
    }

    @Test public void kiranaOwnerOnSmallBundleStillReachesPerson() {
        HelpContext c = Fixtures.context(kiranaPatna(), Lang.HI, JourneyStage.ACTIVE, null);
        c.orderStatus = HelpContext.OrderStatus.ACTIVE;
        assertTrue(c.monthlyBundleValue() < 15000);
        HelpSession s = Fixtures.session(c);
        say(s, "net nahi chal raha");
        say(s, "abhi bhi nahi chal raha");
        HelpReply r = say(s, "abhi bhi nahi chal raha");
        assertEquals(EscalationScorer.Band.HANDOFF, r.score.band);
        assertEquals(EscalationScorer.Priority.NORMAL, r.score.priority);
        assertNotNull(r.ticket);
    }

    @Test public void notUnderstoodCountsAsFailedAnswer() {
        HelpSession s = Fixtures.session(Fixtures.context(kiranaPatna(), Lang.EN, JourneyStage.QUOTE, null));
        HelpReply r = say(s, "asdf qwerty zzz");
        turn(r, "fallback_unknown", EscalationScorer.Band.BOT, null);
        assertEquals(15, r.score.score);
        assertTrue(r.quickReplies.contains("Installation"));
    }

    @Test public void thanksAfterFixLowersScore() {
        HelpSession s = Fixtures.session(pharmacyKochi(true));
        say(s, "speed slow");
        HelpReply r = say(s, "still not working");
        int high = r.score.score;
        r = say(s, "ok thanks, working now");
        assertTrue(r.score.score < high);
        assertEquals("thanks", r.intentId);
    }
}
