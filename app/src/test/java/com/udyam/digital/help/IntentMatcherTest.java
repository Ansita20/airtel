package com.udyam.digital.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Offline matching in English, Hindi (Devanagari) and Hinglish; language detection. */
public class IntentMatcherTest {

    private final IntentMatcher m = new IntentMatcher(Fixtures.kb());

    private void expect(String intent, String message) {
        IntentMatcher.Match r = m.match(message, null);
        assertEquals("'" + message + "' -> " + r, intent, r.intentId);
        assertTrue("confident: " + r, r.isConfident());
    }

    @Test public void advisorAndPlan() {
        expect("plan_choose", "Which plan is right for me?");
        expect("mobile_only", "I only want a mobile SIM");
        expect("mobile_only", "sirf sim chahiye");
        expect("want_1gbps", "Why can't I get 1 Gbps?");
        expect("why_no_leased_line", "Why no leased line for us?");
        expect("fibre_not_available", "Fibre is not available at my shop");
        expect("explain_terms", "What is static IP?");
        expect("explain_terms", "SLA ka matlab kya hai");
        expect("addon_without_broadband", "Can I add CCTV without broadband?");
        expect("multi_site_states", "I have 5 shops in different states");
    }

    @Test public void priceAndQuote() {
        expect("too_expensive", "This is too expensive");
        expect("too_expensive", "बहुत महंगा है");
        expect("competitor_cheaper", "Jio is cheaper than you");
        expect("quote_confusing", "I don't understand the quote");
        expect("quote_confusing", "quote samajh nahi aaya");
        expect("contract_cancel_terms", "What happens after the contract, can I cancel early?");
        expect("gst_on_top", "Is GST added on top?");
        expect("locked_in_provider", "I'm locked in with my current provider");
    }

    @Test public void gstinAndKyc() {
        expect("no_gstin", "I don't have a GSTIN");
        expect("no_gstin", "gst nahi hai mere paas");
        expect("gstin_cancelled", "My GSTIN shows cancelled");
        expect("gstin_invalid", "GSTIN not found, says invalid");
        expect("missing_documents", "Which documents do I need?");
        expect("paperwork_long", "Too much paperwork");
        expect("kyc_name_mismatch", "naam match nahi ho raha");
        expect("address_proof_rejected", "My address proof was rejected");
        expect("signatory_unavailable", "The authorised signatory is not available");
        expect("data_privacy", "Is my data safe? Why do you need my ID?");
    }

    @Test public void payment() {
        expect("payment_failed", "Payment failed");
        expect("payment_failed", "पेमेंट फेल हो गया");
        expect("payment_deducted_no_order", "paisa kat gaya par order nahi bana");
        expect("payment_options", "Can I pay monthly by UPI and get one bill?");
    }

    @Test public void installationAndTracker() {
        expect("install_when", "When will it be installed?");
        expect("install_when", "kab lagega");
        expect("install_late", "Installation is late");
        expect("technician_reschedule", "Technician didn't come, please reschedule");
        expect("cancel_before_install", "I want to cancel my order");
    }

    @Test public void afterActivation() {
        expect("internet_down", "Internet is not working");
        expect("internet_down", "net nahi chal raha");
        expect("internet_down", "इंटरनेट नहीं चल रहा");
        expect("bill_higher", "bill zyada aaya");
        expect("change_quantity", "Add 2 more SIMs");
        expect("upgrade_speed", "I want to upgrade speed");
        expect("whatsapp_sms_dlt", "How do I do DLT registration for SMS?");
        expect("move_address", "We are shifting to a new address");
    }

    @Test public void general() {
        expect("greeting", "hello");
        expect("greeting", "नमस्ते");
        expect("are_you_bot", "Are you a robot?");
        expect("thanks", "thank you");
        expect("off_topic", "What's the cricket score?");
        expect("abuse", "you are stupid");
        expect("human_request", "I want to talk to a person");
        expect("fraud_otp", "Someone called asking for OTP in Accel's name");
    }

    @Test public void gibberishIsNotConfident() {
        IntentMatcher.Match r = m.match("asdf qwerty zzz", null);
        assertNull(r.intentId);
        assertFalse(r.isConfident());
        assertTrue(r.confidence < IntentMatcher.LOW_CONFIDENCE);
    }

    @Test public void hinglishSpellingVariantsFold() {
        expect("internet_down", "net nhi chal rha"); // "rha" stays, "nhi" -> nahi
        expect("too_expensive", "itna mehnga");
    }

    @Test public void languageDetection() {
        assertEquals(Lang.TA, LanguageDetector.detect(Lang.TA, "hello"));          // app setting wins
        assertEquals(Lang.HI, LanguageDetector.detect(null, "इंटरनेट नहीं चल रहा"));
        assertEquals(Lang.TA, LanguageDetector.detect(null, "இணையம் வேலை செய்யவில்லை"));
        assertEquals(Lang.BN, LanguageDetector.detect(null, "ইন্টারনেট কাজ করছে না"));
        assertEquals(Lang.KN, LanguageDetector.detect(null, "ಇಂಟರ್ನೆಟ್ ಕೆಲಸ ಮಾಡುತ್ತಿಲ್ಲ"));
        assertEquals(Lang.HI, LanguageDetector.detect(null, "net nahi chal raha bhai"));
        assertEquals(Lang.EN, LanguageDetector.detect(null, "my internet is down"));
    }

    @Test public void glossaryTermFound() {
        assertEquals("static_ip", m.findTerm("what is static ip"));
        assertEquals("sd_wan", m.findTerm("SD-WAN matlab?"));
        assertEquals("dlt", m.findTerm("dlt kya hai"));
    }
}
