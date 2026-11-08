package com.udyam.digital.partner;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One lead in the partner view. Everything here is already masked: business name like
 * "Ba**** De**** Ca** LLP", no contact name, phone, GSTIN or Udyam number, no documents.
 */
public final class PartnerLead {

    public enum Tab { STUCK, PERSON, INSTALL }

    public enum Status { OPEN, DONE, ASSIGNED }

    // Scoring inputs (profile + journey, no personal data)
    public String segment = "Micro retail & services", businessType = "Other", currentInternet = "Mobile hotspot",
            leadSource = "Website form", stage = "Contacted";
    public int cityTier = 2, employees = 1, sites = 1, spend;
    public boolean hasGstin, hasUdyam;

    // Shown in the list
    public String id = "", maskedName = "", city = "", language = "English", reason = "";
    public Tab tab = Tab.STUCK;
    public LocalDate lastActivity;
    public int planValueInr;
    public final List<String> plan = new ArrayList<>();
    public final List<String> products = new ArrayList<>();
    /** Masked help-bot summary lines, if the lead used the help chat. */
    public final List<String> botSummary = new ArrayList<>();
    /** True for the live lead built from this phone's own app events. */
    public boolean live;

    // Filled by the scorer / partner actions
    public PropensityScorer.Score score;
    public Status status = Status.OPEN;
    public String assignedTo;

    /** For "Install late": how many days. */
    public int lateDays;

    private static String slug(String s) { return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", ""); }

    /** Why the lead is listed, in the reader's language (the English reason is the id). */
    public String reasonText(com.udyam.digital.model.Language lang) {
        if ("Install late".equals(reason)) return com.udyam.digital.i18n.I18n.get(lang, "reason_install_late", lateDays);
        String key = "reason_" + slug(reason == null ? "" : reason);
        return com.udyam.digital.i18n.I18n.has(key) ? com.udyam.digital.i18n.I18n.get(lang, key) : reason;
    }

    /** The journey stage, in the reader's language. */
    public String stageText(com.udyam.digital.model.Language lang) {
        String key = "stage_" + slug(stage == null ? "" : stage);
        return com.udyam.digital.i18n.I18n.has(key) ? com.udyam.digital.i18n.I18n.get(lang, key) : stage;
    }

    public int waitingDays(LocalDate today) {
        return lastActivity == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(lastActivity, today));
    }

    /** From one record of partner_leads.json (parsed by MiniJson). */
    @SuppressWarnings("unchecked")
    public static PartnerLead fromJson(Map<String, Object> o) {
        PartnerLead l = new PartnerLead();
        l.id = (String) o.get("id");
        l.maskedName = (String) o.get("name");
        l.businessType = (String) o.get("type");
        l.segment = (String) o.get("segment");
        l.city = (String) o.get("city");
        l.cityTier = num(o.get("city_tier"));
        l.language = (String) o.get("language");
        l.employees = num(o.get("employees"));
        l.sites = num(o.get("sites"));
        l.hasGstin = Boolean.TRUE.equals(o.get("has_gstin"));
        l.hasUdyam = Boolean.TRUE.equals(o.get("has_udyam"));
        l.currentInternet = (String) o.get("current_internet");
        l.spend = num(o.get("spend"));
        l.leadSource = (String) o.get("lead_source");
        l.stage = (String) o.get("stage");
        l.reason = (String) o.get("reason");
        l.tab = Tab.valueOf((String) o.get("tab"));
        l.lastActivity = LocalDate.parse((String) o.get("last_activity"));
        l.planValueInr = num(o.get("plan_value"));
        for (Object p : (List<Object>) o.get("plan")) l.plan.add((String) p);
        for (Object p : (List<Object>) o.get("products")) l.products.add((String) p);
        return l;
    }

    private static int num(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
}
