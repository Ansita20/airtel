package com.udyam.digital.help.core;

import com.udyam.digital.help.core.BusinessProfile.Need;
import com.udyam.digital.help.core.BusinessProfile.Segment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Deterministic bundle builder over the catalogue's eligibility rules (city tier, minimum
 * staff and sites, GSTIN, product dependencies). The help bot never decides eligibility or
 * price itself: it calls this class and explains the result.
 */
public final class RulesEngine {

    public enum ReasonCode { CITY_TIER, MIN_EMPLOYEES, MIN_SITES, GSTIN_REQUIRED, GSTIN_INACTIVE, NEEDS_ONE_OF }

    public static final class Reason {
        public final ReasonCode code;
        public final int value;
        public final List<String> productIds;
        public final String status;

        Reason(ReasonCode code, int value, List<String> productIds, String status) {
            this.code = code;
            this.value = value;
            this.productIds = productIds == null ? Collections.<String>emptyList() : productIds;
            this.status = status;
        }

        @Override
        public String toString() {
            return code + (value > 0 ? "(" + value + ")" : "") + (productIds.isEmpty() ? "" : productIds) + (status == null ? "" : status);
        }
    }

    /** Lowest to highest speed, used for upgrades and budget downgrades. */
    static final List<String> SPEED_LADDER = Arrays.asList("P04", "P01", "P02", "P03", "P05", "P06");

    /** Order in which a budget re-run drops optional products (first = dropped first). */
    static final List<String> DROP_ORDER = Arrays.asList(
            "P23", "P22", "P18", "P25", "P17", "P13", "P14", "P12", "P21", "P11", "P09", "P15",
            "P24", "P10", "P20", "P16", "P19");

    private final ProductCatalogue catalogue;

    public RulesEngine(ProductCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public ProductCatalogue catalogue() {
        return catalogue;
    }

    /** Why this product can't be sold to this profile inside this bundle. Empty list = eligible. */
    public List<Reason> eligibility(Product p, BusinessProfile b, Recommendation bundle) {
        List<Reason> out = new ArrayList<>();
        int tier = b.cityTier();
        if (!p.cityTiers.contains(tier)) {
            out.add(new Reason(ReasonCode.CITY_TIER, tier, null, null));
        }
        if (b.employees < p.minEmployees) out.add(new Reason(ReasonCode.MIN_EMPLOYEES, p.minEmployees, null, null));
        if (b.sites < p.minSites) out.add(new Reason(ReasonCode.MIN_SITES, p.minSites, null, null));
        if (p.requiresGstin) {
            if (b.gstin == null || b.gstin.isEmpty() || b.gstinStatus == BusinessProfile.GstinStatus.NONE) {
                out.add(new Reason(ReasonCode.GSTIN_REQUIRED, 0, null, null));
            } else if (b.gstinStatus != BusinessProfile.GstinStatus.ACTIVE) {
                out.add(new Reason(ReasonCode.GSTIN_INACTIVE, 0, null, b.gstinStatus.name()));
            }
        }
        if (!p.requiresOneOf.isEmpty()) {
            boolean ok = false;
            if (bundle != null) for (String dep : p.requiresOneOf) if (bundle.contains(dep)) ok = true;
            if (!ok) out.add(new Reason(ReasonCode.NEEDS_ONE_OF, 0, p.requiresOneOf, null));
        }
        return out;
    }

    public List<Reason> eligibility(String productId, BusinessProfile b, Recommendation bundle) {
        return eligibility(catalogue.get(productId), b, bundle);
    }

    public boolean isEligible(String productId, BusinessProfile b, Recommendation bundle) {
        return eligibility(productId, b, bundle).isEmpty();
    }

    /** Builds the recommended bundle for the profile. */
    public Recommendation recommend(BusinessProfile b) {
        Recommendation r = new Recommendation();
        int sites = Math.max(1, b.sites);

        if (b.needs.contains(Need.MOBILE_ONLY)) {
            String sim = b.segment == Segment.MICRO ? "P07" : "P08";
            r.add(catalogue.get(sim), Math.max(1, b.sims));
            return r;
        }

        // 1. Connectivity base, one per site.
        for (String id : baseCandidates(b)) {
            if (isEligible(id, b, r)) {
                r.add(catalogue.get(id), sites);
                break;
            } else {
                r.skipped.put(id, eligibility(id, b, r));
            }
        }

        // 2. Everything else the owner asked for.
        for (Need n : b.needs) {
            String id;
            int qty;
            switch (n) {
                case SIMS:
                    id = b.segment == Segment.MICRO ? "P07" : "P08";
                    qty = b.sims > 0 ? b.sims : sites;
                    break;
                case POS: id = "P19"; qty = b.counters > 0 ? b.counters : sites; break;
                case CCTV: id = "P23"; qty = b.cameras > 0 ? b.cameras : 2 * sites; break;
                case GUEST_WIFI: id = "P22"; qty = sites; break;
                case WHATSAPP: id = "P15"; qty = 1; break;
                case SMS: id = "P14"; qty = 1; break;
                case TOLL_FREE: id = "P13"; qty = 1; break;
                case TELEPHONY: id = "P12"; qty = 1; break;
                case EMAIL: id = "P16"; qty = b.users > 0 ? b.users : b.employees; break;
                case CLOUD_BACKUP: id = "P17"; qty = 1; break;
                case SECURITY: id = "P20"; qty = b.users > 0 ? b.users : b.employees; break;
                case FIREWALL: id = "P21"; qty = sites; break;
                case WEBSITE: id = "P18"; qty = 1; break;
                case BACKUP_INTERNET: id = "P09"; qty = sites; break;
                case MULTI_SITE_NETWORK: id = "P10"; qty = sites; break;
                case STATIC_IP: id = "P11"; qty = 1; break;
                case MACHINE_IOT: id = "P24"; qty = b.devices > 0 ? b.devices : 5; break;
                case FLEET: id = "P25"; qty = b.vehicles > 0 ? b.vehicles : 2; break;
                default: continue; // INTERNET / FAST_INTERNET / LEASED_LINE handled as the base
            }
            List<Reason> why = eligibility(id, b, r);
            if (why.isEmpty()) r.add(catalogue.get(id), qty);
            else r.skipped.put(id, why);
        }

        if (b.siteStates.size() > 1) r.noteKeys.add("note_gst_per_state");
        if (b.monthlyBudget > 0 && r.monthlyTotal() > b.monthlyBudget) {
            return rerunWithBudget(b, r, b.monthlyBudget);
        }
        return r;
    }

    List<String> baseCandidates(BusinessProfile b) {
        if (!b.fibreAvailable) return Collections.singletonList("P04");
        if (b.needs.contains(Need.LEASED_LINE) || (b.segment == Segment.MANUFACTURER && b.employees >= 10)) {
            if (b.needs.contains(Need.FAST_INTERNET)) return Arrays.asList("P06", "P05", "P03", "P02");
            return Arrays.asList("P05", "P03", "P02");
        }
        if (b.needs.contains(Need.FAST_INTERNET)) return Arrays.asList("P03", "P02", "P01");
        if (b.segment == Segment.PROFESSIONAL || b.segment == Segment.MULTI_SITE) return Arrays.asList("P02", "P01");
        return Arrays.asList("P01", "P04");
    }

    /**
     * "Too expensive": brings the bundle down to the budget by first moving to a cheaper
     * connection, then dropping optional products in DROP_ORDER. Never drops the base
     * connection. Records every change.
     */
    public Recommendation rerunWithBudget(BusinessProfile b, Recommendation current, int budget) {
        Recommendation r = current.copy();
        r.changes.clear();
        r.removed.clear();

        // Step 1: cheaper connection, if still eligible and dependencies still hold.
        Recommendation.Line base = r.base();
        while (base != null && r.monthlyTotal() > budget) {
            int idx = SPEED_LADDER.indexOf(base.product.id);
            String cheaper = null;
            for (int i = idx - 1; i >= 0; i--) {
                String cand = SPEED_LADDER.get(i);
                if (cand.equals("P04") && b.fibreAvailable) continue; // AirFiber only if no fibre
                if (isEligible(cand, b, r)) {
                    cheaper = cand;
                    break;
                }
            }
            if (cheaper == null) break;
            Product cp = catalogue.get(cheaper);
            if (cp.monthlyPrice() >= base.product.monthlyPrice()) break;
            r.remove(base.product.id);
            r.removed.remove(r.removed.size() - 1);
            r.set(cp, base.quantity);
            r.changes.add(base.product.name + " -> " + cp.name);
            base = r.base();
        }

        // Step 2: drop optional extras.
        for (String id : DROP_ORDER) {
            if (r.monthlyTotal() <= budget) break;
            if (r.contains(id)) {
                Recommendation.Line l = r.line(id);
                r.remove(id);
                r.changes.add("removed " + l);
            }
        }

        // Step 3: last resort, fewer SIMs.
        for (String sim : Arrays.asList("P08", "P07")) {
            Recommendation.Line l = r.line(sim);
            while (l != null && l.quantity > 1 && r.monthlyTotal() > budget) {
                r.set(l.product, l.quantity - 1);
                r.changes.add("one SIM fewer: " + l.product.name);
                l = r.line(sim);
            }
        }
        return r;
    }

    /** Re-run with a new quantity for one product (add / remove a SIM, user or camera). */
    public Recommendation withQuantity(BusinessProfile b, Recommendation current, String productId, int qty) {
        Recommendation r = current.copy();
        if (qty <= 0) {
            r.remove(productId);
            r.changes.add("removed " + catalogue.get(productId).name);
            return r;
        }
        List<Reason> why = eligibility(productId, b, r);
        if (!why.isEmpty() && !r.contains(productId)) {
            r.skipped.put(productId, why);
            return r;
        }
        r.set(catalogue.get(productId), qty);
        r.changes.add(qty + " x " + catalogue.get(productId).name);
        return r;
    }

    /** Faster connections this profile could switch to, cheapest first. */
    public List<Product> upgrades(BusinessProfile b, Recommendation current) {
        List<Product> out = new ArrayList<>();
        Recommendation.Line base = current.base();
        int from = base == null ? -1 : SPEED_LADDER.indexOf(base.product.id);
        for (int i = from + 1; i < SPEED_LADDER.size(); i++) {
            String id = SPEED_LADDER.get(i);
            if (isEligible(id, b, current)) out.add(catalogue.get(id));
        }
        return out;
    }

    /** The same bundle without products that need a GSTIN. */
    public Recommendation withoutGstinProducts(Recommendation current) {
        Recommendation r = current.copy();
        for (Recommendation.Line l : new ArrayList<>(current.lines())) {
            if (l.product.requiresGstin) {
                r.remove(l.product.id);
                r.changes.add("removed " + l);
            }
        }
        // Dependencies may now be broken (e.g. base was P03): fall back to P02.
        if (r.base() == null && !current.lines().isEmpty()) {
            Recommendation.Line old = current.base();
            if (old != null) {
                r.set(catalogue.get("P02"), old.quantity);
                r.changes.add(old.product.name + " -> " + catalogue.get("P02").name);
            }
        }
        return r;
    }

    public Set<String> gstinProductIds() {
        Set<String> s = new java.util.LinkedHashSet<>();
        for (Product p : catalogue.gstinOnly()) s.add(p.id);
        return s;
    }
}
