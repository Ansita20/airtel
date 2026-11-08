package com.udyam.digital.help;

import com.udyam.digital.help.core.BusinessProfile;
import com.udyam.digital.help.core.Recommendation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the help bot knows about this user. Identifiers are kept MASKED only; the raw
 * GSTIN never enters this object.
 */
public final class HelpContext {

    /** README: use 2026-07-15 as "today" for all date checks in the synthetic data. */
    public static final LocalDate DEMO_TODAY = LocalDate.of(2026, 7, 15);

    public enum KycCheck { NAME_MISMATCH, ADDRESS_PROOF_OLD, GSTIN_NOT_FOUND, GSTIN_CANCELLED, GSTIN_SUSPENDED, UDYAM_MISSING, SIGNATORY_ID_UNREADABLE }

    public enum KycStatus { NOT_STARTED, IN_PROGRESS, FAILED, VERIFIED }

    public enum OrderStatus { NONE, PLACED, KYC_VERIFIED, VISIT_SCHEDULED, INSTALLING, ACTIVE, CANCELLED }

    // --- who / what ---
    /** Language picked in app settings; null if never set. */
    public Lang appLanguage;
    public BusinessProfile profile;
    public Recommendation recommendation;
    public JourneyStage stage = JourneyStage.ADVISOR;

    // --- KYC ---
    public KycStatus kycStatus = KycStatus.NOT_STARTED;
    public final List<KycCheck> failedChecks = new ArrayList<>();
    /** Field that failed the last name check, e.g. "Legal name on GST certificate". */
    public String mismatchField = "";
    public int docsDone = 0;
    public int docsTotal = 4;
    private String maskedGstin;

    // --- order / tracker ---
    public OrderStatus orderStatus = OrderStatus.NONE;
    public String orderId = "";
    public LocalDate orderDate;
    public LocalDate expectedDate;
    public LocalDate today = DEMO_TODAY;
    public String visitSlot = "";

    // --- session counters ---
    public int messagesThisSession = 0;
    public int failedBotAnswers = 0;
    /** Counters named in KB conditions: gstin_invalid, name_mismatch, payment_failed, fix_failed:<intent>. */
    public final Map<String, Integer> counters = new HashMap<>();
    /** Flags named in KB conditions, e.g. money_taken_no_order. */
    public final Map<String, Boolean> flags = new HashMap<>();
    /** Past support contacts by intent id (persisted across sessions by the app). */
    public final Map<String, Integer> priorSupportContacts = new HashMap<>();
    public boolean llmAvailable = false;

    /**
     * Points added by the rest of the app, e.g. +30 when KYC was sent for manual review.
     * Counted like an open issue so the agent sees why.
     */
    public int appEscalationPoints = 0;
    public String appEscalationReason = "";

    /** From the app's order: the current step in the app language, and the internet line's ready date. */
    public String currentStepLabel = "";
    public LocalDate connectionReady;

    public void setGstin(String rawGstin) {
        maskedGstin = rawGstin == null || rawGstin.isEmpty() ? null : Masking.gstin(rawGstin);
        if (profile != null) profile.gstin = maskedGstin; // profile only ever holds the masked form
    }

    public String maskedGstin() {
        return maskedGstin;
    }

    public int counter(String name) {
        Integer v = counters.get(name);
        return v == null ? 0 : v;
    }

    public void bump(String name) {
        counters.put(name, counter(name) + 1);
    }

    public boolean flag(String name) {
        Boolean b = flags.get(name);
        return b != null && b;
    }

    public int monthlyBundleValue() {
        return recommendation == null ? 0 : recommendation.monthlyTotal();
    }

    /** Days past the expected install date (0 if not late or unknown). */
    public int daysLate() {
        if (expectedDate == null || today == null) return 0;
        return (int) Math.max(0, ChronoUnit.DAYS.between(expectedDate, today));
    }

    /** Days since the order was placed. */
    public int daysSinceOrder() {
        if (orderDate == null || today == null) return 0;
        return (int) Math.max(0, ChronoUnit.DAYS.between(orderDate, today));
    }

    public boolean installPending() {
        return orderStatus == OrderStatus.PLACED || orderStatus == OrderStatus.KYC_VERIFIED
                || orderStatus == OrderStatus.VISIT_SCHEDULED || orderStatus == OrderStatus.INSTALLING;
    }

    /**
     * What may leave the device (LLM, agent ticket): masked IDs, no contact name, no phone,
     * no raw documents. The fairness rule is enforced here too: name is never included.
     */
    public Map<String, Object> toMaskedMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stage", stage.name());
        if (profile != null) {
            m.put("business_type", profile.businessType);
            m.put("segment", profile.segment.name());
            m.put("city", profile.city);
            m.put("city_tier", profile.cityTier());
            m.put("employees", profile.employees);
            m.put("sites", profile.sites);
            m.put("gstin", maskedGstin == null ? "none" : maskedGstin);
            m.put("gstin_status", profile.gstinStatus.name());
        }
        if (recommendation != null) {
            List<Object> lines = new ArrayList<>();
            for (Recommendation.Line l : recommendation.lines()) {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("product", l.product.name);
                lm.put("qty", l.quantity);
                lm.put("monthly_rs", l.monthly());
                lm.put("install_days", l.product.installDays);
                lines.add(lm);
            }
            m.put("bundle", lines);
            m.put("monthly_total_rs", recommendation.monthlyTotal());
            m.put("setup_total_rs", recommendation.setupTotal());
        }
        m.put("kyc_status", kycStatus.name());
        List<Object> fails = new ArrayList<>();
        for (KycCheck k : failedChecks) fails.add(k.name());
        m.put("kyc_failed_checks", fails);
        m.put("order_status", orderStatus.name());
        if (expectedDate != null) m.put("expected_install_date", expectedDate.toString());
        m.put("days_late", daysLate());
        return m;
    }
}
