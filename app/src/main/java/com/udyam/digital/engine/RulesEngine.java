package com.udyam.digital.engine;

import com.udyam.digital.engine.Recommendation.Line;
import com.udyam.digital.engine.Recommendation.Role;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.Need;
import com.udyam.digital.model.Product;
import com.udyam.digital.model.Segment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a BusinessProfile into a priced bundle. Pure Java, no Android imports.
 *
 * Design: RULES decide what is ALLOWED -> LEARNED SCORES decide the ORDER -> the USER's
 * stated needs and budget decide what is SHOWN. This is the "knowledge-based + learned
 * ranking" hybrid used by real telecom B2B recommenders (e.g. VodafoneZiggo's SMB tool:
 * a tree model ranks products, a rule layer blocks incompatible combinations).
 *
 * Steps:
 *  1. Hard-filter the catalogue by city tier, min_employees, min_sites, requires_gstin.
 *  2. CORE (rules only): one primary link (P01/P02/P03/P05, or P04 when the site has no fixed
 *     line) if the business wants broadband; mobile lines (P07 micro, P08 otherwise) if it
 *     wants SIMs. Someone who only wants a mobile plan gets P07/P08 alone, no extras.
 *  3. Extras the owner TAPPED are switched ON ("You asked for"). Nothing else is switched on,
 *     so the price only contains what was asked for.
 *  4. "Businesses like yours often add" (at most MAX_SUGGESTED, all OFF): for 3+ sites,
 *     failover (P09) and SD-WAN (P10) first if eligible; then untapped extras by
 *        final = learned_score - BUDGET_PENALTY (if switching it on would exceed the budget)
 *     with final >= the cutoff from product_scores.json (chosen on April-May data).
 *     Every other eligible extra is listed under "You can also add", OFF.
 *  5. requires_one_of is enforced: a product whose dependency is missing is never shown.
 *  6. Quantities: typical quantity from real bundles (product_scores.json default_qty),
 *     capped by staff / sites, else the built-in rule in quantity(). Editable on screen.
 *  7. "Why" = the need it matches + "x of y businesses like yours kept this" (max 2 parts,
 *     only facts from the JSON or catalogue), else the catalogue-based text.
 *  8. If still over budget, switch off lowest-priority extras; they stay as switches.
 *  9. Packs (see Pack): "Your plan" switches on the learned picks that fit the budget;
 *     Basic / Premium / Premium+ are offered as options, up to 20% over budget.
 */
public final class RulesEngine {

    // Ranking constants, kept in one place. BUDGET_PENALTY is a design choice, not learned.
    // (The old +0.30 bonus for tapped needs is retired: tapped needs are now always switched on.)
    public static final double BUDGET_PENALTY = 0.20;
    public static final int MAX_SUGGESTED = 3;
    /** Learned additions (picks, Premium extras) need at least this share of similar businesses keeping them. */
    public static final double PACK_MIN_SCORE = 0.10;
    /** Suggested packs may be at most this far over the budget (20%). */
    public static final double PACK_MAX_OVER = 0.20;
    /** Businesses with this many sites see failover and SD-WAN first. */
    public static final int CHAIN_MIN_SITES = 3;
    private static final List<String> CHAIN_FIRST = Arrays.asList("P09", "P10");

    /** Main links and mobile lines: chosen by rules in step 2, never ranked. */
    private static final Set<String> CORE = new HashSet<>(Arrays.asList(
            "P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08"));

    private final Map<String, Product> byId = new LinkedHashMap<>();
    private final ProductScorer scorer;

    /** Rules only: no learned scores, so only tapped needs are recommended. */
    public RulesEngine(List<Product> catalogue) {
        this(catalogue, ProductScorer.empty());
    }

    public RulesEngine(List<Product> catalogue, ProductScorer scorer) {
        for (Product p : catalogue) byId.put(p.getId(), p);
        this.scorer = scorer == null ? ProductScorer.empty() : scorer;
    }

    public ProductScorer scorer() { return scorer; }

    public Product product(String id) { return byId.get(id); }

    /** "Your plan": tapped needs + top learned picks, within budget. */
    public Recommendation recommend(BusinessProfile profile) {
        return build(profile, Pack.Kind.YOUR_PLAN);
    }

    /**
     * "Your plan" first, then Basic / Premium / Premium+ as options. A suggested pack more than
     * PACK_MAX_OVER above the budget is hidden, and so is one identical to an earlier pack.
     */
    public List<Pack> packs(BusinessProfile profile) {
        int budget = profile.getBudgetInr();
        List<Pack> out = new ArrayList<>();
        Recommendation your = build(profile, Pack.Kind.YOUR_PLAN);
        out.add(new Pack(Pack.Kind.YOUR_PLAN, your, budget > 0 ? Math.max(0, your.getMonthlyTotal() - budget) : 0));
        for (Pack.Kind k : new Pack.Kind[] {Pack.Kind.BASIC, Pack.Kind.PREMIUM, Pack.Kind.PREMIUM_PLUS}) {
            Recommendation r = build(profile, k);
            if (budget > 0 && r.getMonthlyTotal() > budget * (1 + PACK_MAX_OVER)) continue;
            Pack pack = new Pack(k, r, budget > 0 ? Math.max(0, r.getMonthlyTotal() - budget) : 0);
            boolean duplicate = false;
            for (Pack o : out) duplicate |= o.sameAs(pack);
            if (!duplicate) out.add(pack);
        }
        return out;
    }

    Recommendation build(BusinessProfile profile, Pack.Kind kind) {
        Texts t = Texts.of(profile.getLanguage());
        Recommendation rec = new Recommendation(profile.getBudgetInr());

        // 1. Hard filter: productId -> reason it can't be sold to this business.
        Map<String, String> blocked = new LinkedHashMap<>();
        for (Product p : byId.values()) {
            String reason = hardFilter(p, profile, t);
            if (reason != null) blocked.put(p.getId(), reason);
        }

        // 2. CORE: main link + mobile lines (rules only).
        boolean broadband = wantsBroadband(profile);
        boolean mobileWanted = wantsMobile(profile);
        if (broadband) addPrimary(profile, blocked, rec, t);

        String mobileId = profile.getSegment() == Segment.MICRO ? "P07" : "P08";
        Product mobile = byId.get(mobileId);
        if (mobileWanted && mobile != null && !blocked.containsKey(mobileId)) {
            int qty = quantity(mobile, profile);
            rec.addLine(new Line(mobile, qty, Role.MOBILE, t.why(mobileId, qty)));
        }

        // Only a mobile plan wanted: show P07/P08 alone. (280 leads dropped because they only
        // wanted a mobile plan and were shown more.)
        boolean mobileOnly = mobileWanted && !broadband && !tappedAnyExtra(profile);

        // 3 + 4. Extras: tapped ones ON; up to 3 learned suggestions OFF; the rest OFF.
        if (!mobileOnly) addExtras(profile, blocked, rec, t);

        // 5. requires_one_of, repeated until stable in case removals cascade
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Line l : new ArrayList<>(rec.getLines())) {
                List<String> req = l.getProduct().getRequiresOneOf();
                if (req.isEmpty() || l.getRole() == Role.PRIMARY) continue;
                boolean satisfied = false;
                for (String r : req) {
                    if (rec.contains(r)) { satisfied = true; break; }
                }
                if (!satisfied) {
                    rec.removeLine(l);
                    rec.exclude(l.getProduct(), t.fmt("excl.requiresOneOf", describeRequirement(req, t)));
                    changed = true;
                }
            }
        }

        // 6 + 7 are applied in quantity() and Line pricing (price_min x qty, setup x qty).

        // 8. "Your plan" must fit the budget: switch off lowest-priority add-ons until it does.
        // (Other packs are shown with their over-budget amount instead.)
        if (kind == Pack.Kind.YOUR_PLAN && profile.getBudgetInr() > 0
                && rec.getMonthlyTotal() > profile.getBudgetInr()) {
            List<Line> addOns = new ArrayList<>();
            for (Line l : rec.getLines()) if (l.getRole() == Role.ADD_ON && l.isSelected()) addOns.add(l);
            Collections.sort(addOns, new Comparator<Line>() {
                @Override public int compare(Line a, Line b) {
                    int c = Integer.compare(NeedMap.priority(a.getProduct().getId()),
                            NeedMap.priority(b.getProduct().getId()));
                    // Among equal priority, drop the more expensive one first.
                    return c != 0 ? c : Integer.compare(b.getMonthlyInr(), a.getMonthlyInr());
                }
            });
            String note = t.fmt("budget.moved", Money.inr(profile.getBudgetInr()));
            for (Line l : addOns) {
                if (rec.getMonthlyTotal() <= profile.getBudgetInr()) break;
                l.moveToOptional(note);
            }
        }

        // 9. What this pack switches on beyond the tapped needs.
        applyPack(kind, profile, blocked, rec, t, mobileOnly);
        return rec;
    }

    /**
     * YOUR_PLAN: learned picks ON while the total stays within the budget (all of them if no
     * budget). BASIC: nothing more. PREMIUM: next internet tier, Rs 599 SIMs, failover (P09)
     * if eligible, and the learned picks. PREMIUM_PLUS: Premium + every other eligible extra
     * with score >= PACK_MIN_SCORE. Learned additions below PACK_MIN_SCORE are never added.
     */
    private void applyPack(Pack.Kind kind, BusinessProfile profile, Map<String, String> blocked,
                           Recommendation rec, Texts t, boolean mobileOnly) {
        rec.setKind(kind);
        int budget = profile.getBudgetInr();
        switch (kind) {
            case YOUR_PLAN:
                for (Line l : rec.getLines()) {
                    if (l.isSuggested() && !l.isSelected()
                            && (budget <= 0 || rec.getMonthlyTotal() + l.getMonthlyInr() <= budget)) {
                        l.setSelected(true);
                    }
                }
                return;
            case BASIC:
                return;
            case PREMIUM:
            case PREMIUM_PLUS:
                upgradeCore(profile, blocked, rec, t);
                if (mobileOnly) return;
                Line backup = rec.line("P09");
                if (backup != null && (backup.isRecommended() || scorer.score(profile, "P09") >= PACK_MIN_SCORE)) {
                    backup.setSelected(true);
                }
                for (Line l : rec.getLines()) if (l.isSuggested()) l.setSelected(true);
                if (kind == Pack.Kind.PREMIUM_PLUS) {
                    for (Line l : rec.getLines()) {
                        if (l.getRole() == Role.ADD_ON && !l.isSelected()
                                && scorer.score(profile, l.getProduct().getId()) >= PACK_MIN_SCORE) {
                            l.setSelected(true);
                        }
                    }
                }
                return;
        }
    }

    /** Premium: 100 -> 300 Mbps fibre (300 -> 1 Gbps in Tier 1), Rs 299 -> Rs 599 SIMs, if eligible. */
    private void upgradeCore(BusinessProfile profile, Map<String, String> blocked, Recommendation rec, Texts t) {
        Line primary = rec.getPrimary();
        if (primary != null) {
            String from = primary.getProduct().getId();
            String to = from.equals("P01") ? "P02"
                    : from.equals("P02") && profile.getCityTier() == 1 ? "P03" : null;
            if (to != null && !blocked.containsKey(to) && byId.get(to) != null) {
                String reason = t.get("primary.upgrade")
                        + (profile.getSites() > 1 ? t.fmt("primary.perSite", profile.getSites()) : "");
                rec.replaceLine(primary, new Line(byId.get(to), primary.getQuantity(), Role.PRIMARY, reason));
            }
        }
        Line sim = rec.line("P07");
        if (sim != null && !blocked.containsKey("P08") && byId.get("P08") != null) {
            Line upgraded = new Line(byId.get("P08"), sim.getQuantity(), Role.MOBILE, t.why("P08", sim.getQuantity()));
            rec.replaceLine(sim, upgraded);
        }
    }

    private static final class Candidate {
        final Product product;
        final Need need;     // tapped need it matches, or null
        final double score;  // learned score
        final int quantity;

        Candidate(Product product, Need need, double score, int quantity) {
            this.product = product;
            this.need = need;
            this.score = score;
            this.quantity = quantity;
        }
    }

    /** Steps 3-4: rules filter -> tapped needs ON -> learned suggestions OFF -> everything else OFF. */
    private void addExtras(BusinessProfile profile, Map<String, String> blocked, Recommendation rec, Texts t) {
        List<Candidate> tapped = new ArrayList<>();
        List<Candidate> others = new ArrayList<>();
        for (Product p : byId.values()) {
            String pid = p.getId();
            if (CORE.contains(pid) || rec.contains(pid)) continue;
            Need need = matchedNeed(profile, pid);
            if (blocked.containsKey(pid)) {
                if (need != null) rec.exclude(p, blocked.get(pid)); // explain only what was asked for
                continue;
            }
            List<String> req = p.getRequiresOneOf();
            if (!req.isEmpty() && !containsAny(rec, req)) {
                if (need != null) rec.exclude(p, t.fmt("excl.requiresOneOf", describeRequirement(req, t)));
                continue;
            }
            Candidate c = new Candidate(p, need, scorer.score(profile, pid), capQuantity(quantityFor(p, profile), profile));
            (need != null ? tapped : others).add(c);
        }

        // 3. What the owner asked for: ON. (Step 8 switches off the lowest-priority ones if over budget.)
        for (Candidate c : tapped) {
            Line l = new Line(c.product, c.quantity, Role.ADD_ON, whyLine(c, profile, t));
            l.setRecommended(true);
            rec.addLine(l);
        }

        // 4. Suggestions, OFF. 3+ sites first get failover / SD-WAN, then the learned order.
        final int budget = profile.getBudgetInr();
        final int onTotal = rec.getMonthlyTotal();
        final Map<String, Double> fin = new HashMap<>();
        for (Candidate c : others) {
            int monthly = c.product.getPriceMinInr() * c.quantity;
            boolean over = budget > 0 && onTotal + monthly > budget;
            fin.put(c.product.getId(), c.score - (over ? BUDGET_PENALTY : 0));
        }
        Collections.sort(others, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                int c = Double.compare(fin.get(b.product.getId()), fin.get(a.product.getId()));
                if (c != 0) return c;
                c = Integer.compare(NeedMap.priority(b.product.getId()), NeedMap.priority(a.product.getId()));
                return c != 0 ? c : a.product.getId().compareTo(b.product.getId());
            }
        });
        List<Candidate> suggested = new ArrayList<>();
        if (profile.getSites() >= CHAIN_MIN_SITES) {
            for (String id : CHAIN_FIRST) {
                for (Candidate c : others) {
                    if (c.product.getId().equals(id) && c.score >= PACK_MIN_SCORE) suggested.add(c);
                }
            }
        }
        if (!scorer.isEmpty()) {
            double cutoff = scorer.suggestCutoff();
            for (Candidate c : others) {
                if (suggested.size() >= MAX_SUGGESTED) break;
                if (!suggested.contains(c) && fin.get(c.product.getId()) >= cutoff && c.score >= PACK_MIN_SCORE) {
                    suggested.add(c);
                }
            }
        }
        while (suggested.size() > MAX_SUGGESTED) suggested.remove(suggested.size() - 1);

        for (Candidate c : suggested) {
            Line l = new Line(c.product, c.quantity, Role.ADD_ON, whyLine(c, profile, t));
            l.setSelected(false);
            l.setSuggested(true);
            rec.addLine(l);
        }
        for (Candidate c : others) {
            if (suggested.contains(c)) continue;
            Line l = new Line(c.product, c.quantity, Role.ADD_ON, t.why(c.product.getId(), c.quantity));
            l.setSelected(false);
            rec.addLine(l);
        }
    }

    /** Max 2 parts, only facts: the need the owner tapped, and real "kept" counts; else catalogue text. */
    private String whyLine(Candidate c, BusinessProfile profile, Texts t) {
        List<String> parts = new ArrayList<>();
        if (c.need != null) parts.add(t.fmt("why.asked", t.get("need." + c.need.name())));
        ProductScorer.Proof proof = scorer.proof(profile, c.product.getId());
        if (proof != null) parts.add(t.fmt("why.proof", proof.kept, proof.of));
        if (parts.isEmpty()) return t.why(c.product.getId(), c.quantity);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) b.append(t.get("why.join"));
            b.append(parts.get(i));
        }
        return b.toString();
    }

    private static Need matchedNeed(BusinessProfile profile, String productId) {
        for (Need n : Need.values()) {
            if (profile.hasNeed(n) && NeedMap.productsFor(n).contains(productId)) return n;
        }
        return null;
    }

    private static boolean containsAny(Recommendation rec, List<String> ids) {
        for (String id : ids) if (rec.contains(id)) return true;
        return false;
    }

    /** Any need beyond the two core services (SIM / broadband). */
    private static boolean tappedAnyExtra(BusinessProfile p) {
        for (Need n : p.getNeeds()) {
            if (n != Need.MOBILE_SIM && n != Need.BROADBAND) return true;
        }
        return false;
    }

    /** Step 6: typical quantity from real bundles, capped by staff / sites; else the built-in rule. */
    int quantityFor(Product p, BusinessProfile profile) {
        ProductScorer.Qty q = scorer.defaultQty(profile.getSegment(), p.getId());
        if (q == null || q.value <= 0) return quantity(p, profile);
        int employees = Math.max(1, profile.getEmployees());
        int sites = Math.max(1, profile.getSites());
        switch (q.per) {
            case "site":
                return clamp((int) Math.round(q.value * sites), 1, 4 * sites);
            case "employee":
                return clamp((int) Math.ceil(q.value * employees), 1, employees);
            default:
                return clamp((int) Math.round(q.value), 1, Math.max(employees, sites));
        }
    }

    /** Never more units than the business has people or sites. */
    static int capQuantity(int q, BusinessProfile profile) {
        return clamp(q, 1, Math.max(Math.max(1, profile.getEmployees()), Math.max(1, profile.getSites())));
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static boolean namedCoreService(BusinessProfile p) {
        return p.hasNeed(Need.BROADBAND) || p.hasNeed(Need.MOBILE_SIM);
    }

    /** Broadband if chosen, if a speed/uptime need implies it, or if no core service was named. */
    public static boolean wantsBroadband(BusinessProfile p) {
        return !namedCoreService(p) || p.hasNeed(Need.BROADBAND)
                || p.hasNeed(Need.FAST_INTERNET) || p.hasNeed(Need.DEDICATED_LINE);
    }

    /** Mobile SIM lines if chosen, or if no core service was named. */
    public static boolean wantsMobile(BusinessProfile p) {
        return !namedCoreService(p) || p.hasNeed(Need.MOBILE_SIM);
    }

    /** Step 1. Returns null when the product can be sold to this profile. */
    String hardFilter(Product p, BusinessProfile profile, Texts t) {
        if (!p.isAvailableInTier(profile.getCityTier())) {
            return t.fmt("excl.tier", profile.getCityTier());
        }
        if (profile.getEmployees() < p.getMinEmployees()) {
            return t.fmt("excl.minEmployees", p.getMinEmployees(), profile.getEmployees());
        }
        if (profile.getSites() < p.getMinSites()) {
            return t.fmt("excl.minSites", p.getMinSites(), profile.getSites());
        }
        if (p.isRequiresGstin() && !profile.hasValidGstin()) {
            return t.get("excl.gstin");
        }
        return null;
    }

    /** Step 2. */
    private void addPrimary(BusinessProfile profile, Map<String, String> blocked, Recommendation rec, Texts t) {
        String id = null;
        String reason = null;

        if (profile.hasNeed(Need.DEDICATED_LINE)) {
            if (!blocked.containsKey("P05")) {
                id = "P05";
                reason = t.get("primary.leased");
            } else {
                rec.exclude(byId.get("P05"), blocked.get("P05"));
            }
        }

        if (id == null && !profile.getCurrentInternet().hasFixedLine()) {
            if (profile.hasNeed(Need.FAST_INTERNET)) {
                String why = blocked.containsKey("P03") ? blocked.get("P03") : t.get("excl.needsFibre");
                rec.exclude(byId.get("P03"), why);
            }
            if (!blocked.containsKey("P04")) {
                id = "P04";
                reason = t.fmt("primary.airfiber", byId.get("P04").getInstallDays());
            }
        }

        if (id == null && profile.hasNeed(Need.FAST_INTERNET)) {
            if (!blocked.containsKey("P03")) {
                id = "P03";
                reason = t.get("primary.gig");
            } else {
                rec.exclude(byId.get("P03"), blocked.get("P03"));
                id = "P02";
                reason = t.get("primary.fastFallback");
            }
        }

        if (id == null && profile.wantsBestPlan() && !blocked.containsKey("P02")) {
            id = "P02"; // "Best plan": the faster everyday fibre, even for a small team
            reason = t.get("primary.best");
        }

        if (id == null) {
            boolean bigger = profile.getEmployees() >= 10
                    || profile.getSegment() == Segment.MULTI
                    || profile.getSegment() == Segment.MFG;
            id = bigger ? "P02" : "P01";
            reason = t.fmt(bigger ? "primary.p02" : "primary.p01", profile.getEmployees());
        }

        if (blocked.containsKey(id) || byId.get(id) == null) {
            id = null;
            for (String fallback : new String[] {"P02", "P01", "P04"}) {
                if (!blocked.containsKey(fallback) && byId.get(fallback) != null) {
                    id = fallback;
                    reason = t.get("primary.generic");
                    break;
                }
            }
            if (id == null) return;
        }

        Product p = byId.get(id);
        if (profile.getSites() > 1) reason += t.fmt("primary.perSite", profile.getSites());
        rec.addLine(new Line(p, quantity(p, profile), Role.PRIMARY, reason));
    }

    /** Step 6: how many units of a product this business needs. */
    static int quantity(Product p, BusinessProfile profile) {
        int employees = Math.max(1, profile.getEmployees());
        int sites = Math.max(1, profile.getSites());
        switch (p.getId()) {
            case "P23": return 2 * sites;                                  // 2 cameras per site
            case "P12": return Math.max(2, ceilDiv(employees, 5));          // 1 channel per 5 people
            case "P24": return Math.max(2, ceilDiv(employees, 5));          // machines ~ staff / 5
            case "P25": return sites;                                       // ~1 vehicle per site
            default: break;
        }
        switch (p.getUnit()) {
            case "per site":
            case "per counter":
            case "per IP":
                return sites;
            case "per user":
            case "per line":
            case "per device":
                return employees;
            default:
                return 1; // per account, per number, per pack
        }
    }

    private static int ceilDiv(int a, int b) { return (a + b - 1) / b; }

    /** "P01|P02|P03|P05|P06" -> "Biz Fibre or a leased line". */
    private static String describeRequirement(List<String> req, Texts t) {
        boolean fibre = false, air = false, leased = false;
        for (String r : req) {
            if (r.equals("P01") || r.equals("P02") || r.equals("P03")) fibre = true;
            else if (r.equals("P04")) air = true;
            else if (r.equals("P05") || r.equals("P06")) leased = true;
        }
        List<String> parts = new ArrayList<>();
        if (fibre) parts.add(t.get("req.fibre"));
        if (air) parts.add(t.get("req.air"));
        if (leased) parts.add(t.get("req.leased"));
        if (parts.isEmpty()) parts.addAll(req);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) b.append(t.get("req.or"));
            b.append(parts.get(i));
        }
        return b.toString();
    }
}
