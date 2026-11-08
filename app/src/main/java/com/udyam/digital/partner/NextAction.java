package com.udyam.digital.partner;

import com.udyam.digital.i18n.I18n;

import java.util.HashMap;
import java.util.Map;

/** The suggested next step for a lead, from why it is stuck. Text: "next_..." in strings_partner.xml. Pure Java. */
public final class NextAction {
    private NextAction() {}

    private static final Map<String, String> BY_REASON = new HashMap<>();
    static {
        BY_REASON.put("Site not feasible for fibre", "next_offer_airfiber");
        BY_REASON.put("Price too high", "next_basic_pack");
        BY_REASON.put("Quote too complex to understand", "next_walk_through");
        BY_REASON.put("Quote took too long to arrive", "next_instant_quote");
        BY_REASON.put("Found paperwork too long", "next_digilocker");
        BY_REASON.put("Authorised signatory not available", "next_signatory_time");
        BY_REASON.put("Document details did not match", "next_check_kyc_review");
        BY_REASON.put("Owner had no time for a visit", "next_visit_slot");
        BY_REASON.put("Visit not scheduled by sales", "next_book_visit");
        BY_REASON.put("Competitor offered cheaper plan", "next_match_competitor");
        BY_REASON.put("Locked in with current provider", "next_contract_end");
        BY_REASON.put("Only wanted a mobile plan", "next_sim_only");
        BY_REASON.put("Decided to wait", "next_follow_up");
        BY_REASON.put("Installation delayed beyond 21 days", "next_escalate_install");
        BY_REASON.put("Install late", "next_escalate_install");
        BY_REASON.put("Customer cancelled before install", "next_rebook_install");
        BY_REASON.put("KYC: please fix left unfinished", "next_finish_kyc");
        BY_REASON.put("KYC: manual review", "next_review_ticket");
        BY_REASON.put("Help bot handed over", "next_call_back");
        BY_REASON.put("Plan seen, quote not accepted", "next_answer_plan");
    }

    public static I18n.Msg forLead(PartnerLead l) {
        if ("Missing GST or Udyam document".equals(l.reason)) {
            return I18n.msg(!l.hasGstin ? "next_gstin_registration" : "next_udyam_registration");
        }
        String key = l.reason == null ? null : BY_REASON.get(l.reason);
        return I18n.msg(key != null ? key : "next_default");
    }
}
