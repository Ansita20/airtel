package com.udyam.digital.partner;

import com.udyam.digital.engine.MiniJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard numbers: the funnel (seeded data + this phone's app events), and the cost story.
 * Pure Java.
 */
public final class PartnerDashboard {

    /**
     * Cost estimate inputs. EDIT THESE: they are assumptions, shown as such in the app.
     * DIGITAL_ONLY_COST_INR: cost of an activation that needed no person (marketing + KYC checks + install
     * coordination). ASSISTED_COST_INR: cost of one that needed sales or a partner; defaults to today's
     * blended cost from smb_leads.csv, since today every activation is assisted.
     */
    public static final int DIGITAL_ONLY_COST_INR = 1500;
    public static final Integer ASSISTED_COST_INR = null; // null = use the blended cost from the data (₹15,053)

    public static final class Step {
        public final String name;
        public final int seeded, app;

        Step(String name, int seeded, int app) {
            this.name = name;
            this.seeded = seeded;
            this.app = app;
        }

        public int total() { return seeded + app; }
    }

    public final List<Step> funnel = new ArrayList<>();
    public final int blendedCostInr, activations, leads;
    public final double selfServeShare;
    public final String source;

    @SuppressWarnings("unchecked")
    public PartnerDashboard(String json, int[] appCounts) {
        Map<String, Object> m = (Map<String, Object>) MiniJson.parse(json);
        List<Object> f = (List<Object>) m.get("funnel");
        for (int i = 0; i < f.size(); i++) {
            List<Object> row = (List<Object>) f.get(i);
            funnel.add(new Step((String) row.get(0), ((Number) row.get(1)).intValue(),
                    appCounts != null && i < appCounts.length ? appCounts[i] : 0));
        }
        blendedCostInr = ((Number) m.get("blended_cost_per_activation_inr")).intValue();
        activations = ((Number) m.get("activations")).intValue();
        leads = ((Number) m.get("leads")).intValue();
        selfServeShare = ((Number) m.get("self_serve_share_of_activations")).doubleValue();
        source = (String) m.get("source");
    }

    /** Conversion from the previous step, 0-1 (the first step: 1). */
    public double conversion(int i) {
        if (i == 0) return 1;
        int prev = funnel.get(i - 1).total();
        return prev == 0 ? 0 : (double) funnel.get(i).total() / prev;
    }

    public int assistedCost() { return ASSISTED_COST_INR != null ? ASSISTED_COST_INR : blendedCostInr; }

    /**
     * Share of activations that needed no person. From this phone's own events when it has any
     * activation, else the data's proxy (activations that started self-serve: website / WhatsApp).
     */
    public double noHumanShare(int appActivations, int appActivationsNoHuman) {
        return appActivations > 0 ? (double) appActivationsNoHuman / appActivations : selfServeShare;
    }

    /** ESTIMATE: share x digital-only cost + (1 - share) x assisted cost. */
    public int estimatedCostPerActivation(double noHumanShare) {
        return (int) Math.round(noHumanShare * DIGITAL_ONLY_COST_INR + (1 - noHumanShare) * assistedCost());
    }
}
