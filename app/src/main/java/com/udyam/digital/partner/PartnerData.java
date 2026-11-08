package com.udyam.digital.partner;

import android.content.Context;
import android.content.SharedPreferences;

import com.udyam.digital.engine.MiniJson;
import com.udyam.digital.help.HelpActivity;
import com.udyam.digital.help.HelpStats;
import com.udyam.digital.kyc.KycApplication;
import com.udyam.digital.kycui.KycStore;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.InternetType;
import com.udyam.digital.order.Order;
import com.udyam.digital.order.OrderStep;
import com.udyam.digital.orderui.OrderRepository;
import com.udyam.digital.ui.AppState;
import com.udyam.digital.util.AppClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the partner view: the masked seed leads (dropped June leads from smb_leads.csv), plus
 * live leads from this phone's own app events (KYC left unfinished or in manual review, help-bot
 * handoff, install more than 7 days late, plan seen but quote not accepted). Scores every lead
 * and restores what partners already did (done / assigned). Never touches documents: only the
 * KYC outcome is read, not a single field or photo.
 */
public final class PartnerData {

    public final List<PartnerLead> leads = new ArrayList<>();
    public final PropensityScorer scorer;
    public final PartnerDashboard dashboard;
    public final HelpStats help;
    public final int appActivations, appActivationsNoHuman;

    private final SharedPreferences state;

    @SuppressWarnings("unchecked")
    public PartnerData(Context ctx) throws IOException {
        scorer = new PropensityScorer(asset(ctx, "propensity_model.json"));
        for (Object o : (List<Object>) MiniJson.parse(asset(ctx, "partner_leads.json"))) leads.add(PartnerLead.fromJson((Map<String, Object>) o));
        help = HelpActivity.stats(ctx);

        // ---- this phone's live journey
        KycApplication kyc = null;
        try { kyc = new KycStore(ctx).load(); } catch (RuntimeException ignored) { }
        Order order = new OrderRepository(ctx).load();
        int[] sessions = logSessions(ctx);
        boolean activated = order != null && order.isDone(OrderStep.ACTIVE);
        boolean handoff = help.handoffs() > 0;
        boolean manual = kyc != null && kyc.outcome == KycApplication.Outcome.MANUAL_REVIEW;
        appActivations = activated ? 1 : 0;
        appActivationsNoHuman = activated && !handoff && !manual ? 1 : 0;

        LocalDate today = AppClock.today();
        if (kyc != null && kyc.outcome == KycApplication.Outcome.PLEASE_FIX && !kyc.documents.isEmpty()) {
            leads.add(live(order, PartnerLead.Tab.STUCK, "KYC: please fix left unfinished", "Quote accepted", today));
        }
        if (manual) leads.add(live(order, PartnerLead.Tab.PERSON, "KYC: manual review", "Quote accepted", today));
        if (handoff) {
            PartnerLead l = live(order, PartnerLead.Tab.PERSON, "Help bot handed over", order != null ? "Quote accepted" : "Needs assessed", today);
            for (Map.Entry<String, Integer> e : help.handoffsByReason().entrySet()) {
                l.botSummary.add(PartnerMask.text(e.getKey() + " (" + e.getValue() + "x)"));
            }
            leads.add(l);
        }
        if (order != null && order.promisedActive != null && !activated && today.isAfter(order.promisedActive.plusDays(7))) {
            PartnerLead late = live(order, PartnerLead.Tab.INSTALL, "Install late", "KYC complete", today);
            late.lateDays = (int) ChronoUnit.DAYS.between(order.promisedActive, today);
            leads.add(late);
        }
        if (sessions[2] > 0 && sessions[3] == 0 && order == null) {
            leads.add(live(null, PartnerLead.Tab.STUCK, "Plan seen, quote not accepted", "Needs assessed", today));
        }

        state = ctx.getSharedPreferences("partner_state", Context.MODE_PRIVATE);
        for (PartnerLead l : leads) {
            l.score = scorer.score(l);
            String s = state.getString(l.id, null);
            if (s == null) continue;
            if (s.equals("DONE")) l.status = PartnerLead.Status.DONE;
            else if (s.startsWith("ASSIGNED:")) { l.status = PartnerLead.Status.ASSIGNED; l.assignedTo = s.substring(9); }
        }

        // Funnel from this phone: plan sessions, quotes accepted, KYC approved, activated.
        int kycDone = kyc != null && kyc.outcome == KycApplication.Outcome.APPROVED ? 1 : 0;
        dashboard = new PartnerDashboard(asset(ctx, "partner_dashboard.json"),
                new int[] {sessions[1], sessions[2], sessions[3], kycDone, appActivations});
    }

    public void markDone(PartnerLead l) {
        l.status = PartnerLead.Status.DONE;
        state.edit().putString(l.id, "DONE").apply();
    }

    public void assign(PartnerLead l, String partner) {
        l.status = PartnerLead.Status.ASSIGNED;
        l.assignedTo = partner;
        state.edit().putString(l.id, "ASSIGNED:" + partner).apply();
    }

    /** A lead from this phone: profile fields only (masked name), never documents. */
    private static PartnerLead live(Order order, PartnerLead.Tab tab, String reason, String stage, LocalDate today) {
        BusinessProfile p = AppState.get().profile();
        PartnerLead l = new PartnerLead();
        l.live = true;
        l.id = "APP-" + tab.name().charAt(0) + Math.abs(reason.hashCode() % 1000);
        l.tab = tab;
        l.reason = reason;
        l.stage = stage;
        l.leadSource = "Website form";
        if (p != null) {
            if (p.getSegment() != null) l.segment = p.getSegment().leadName;
            if (p.getBusinessType() != null) l.businessType = p.getBusinessType();
            if (p.getCity() != null) l.city = p.getCity();
            l.cityTier = p.getCityTier() > 0 ? p.getCityTier() : 2;
            l.employees = Math.max(1, p.getEmployees());
            l.sites = Math.max(1, p.getSites());
            l.hasGstin = p.hasValidGstin();
            l.currentInternet = internet(p.getCurrentInternet());
            if (p.getLanguage() != null) l.language = p.getLanguage().englishName;
        }
        // A business type (data, as in the lead file), never the owner's name.
        l.maskedName = l.businessType.equals("Other") ? "-" : l.businessType;
        l.lastActivity = today;
        if (order != null) {
            l.planValueInr = order.monthlyTotal;
            for (com.udyam.digital.order.OrderLine line : order.lines) l.plan.add(line.name);
        }
        return l;
    }

    private static String internet(InternetType t) {
        if (t == null) return "Mobile hotspot";
        switch (t) {
            case NONE: return "None";
            case HOME_BROADBAND: return "Home broadband";
            case BUSINESS_BROADBAND: return "Business broadband";
            case LEASED_LINE: return "Leased line";
            case MIXED: return "Mixed per site";
            default: return "Mobile hotspot";
        }
    }

    /** {rows, sessions, sessions with the plan shown, sessions that went on to KYC} from the usage log. */
    private static int[] logSessions(Context ctx) {
        Set<String> all = new HashSet<>(), shown = new HashSet<>(), kyc = new HashSet<>();
        int rows = 0;
        File f = new File(ctx.getFilesDir(), "recommendation_log.csv");
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                r.readLine();
                String line;
                while ((line = r.readLine()) != null) {
                    String[] c = line.split(",");
                    if (c.length < 10) continue;
                    rows++;
                    all.add(c[0]);
                    if ("SHOWN".equals(c[c.length - 1]) || "SUGGESTED".equals(c[c.length - 1])) shown.add(c[0]);
                    if ("PROCEEDED_TO_KYC".equals(c[c.length - 1])) kyc.add(c[0]);
                }
            } catch (IOException ignored) {
            }
        }
        return new int[] {rows, all.size(), shown.size(), kyc.size()};
    }

    private static String asset(Context ctx, String name) throws IOException {
        try (InputStream in = ctx.getAssets().open(name)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** For tests and the city picker. */
    public static Map<String, Integer> countByCity(List<PartnerLead> leads) {
        Map<String, Integer> m = new HashMap<>();
        for (PartnerLead l : leads) m.merge(l.city, 1, Integer::sum);
        return m;
    }
}
