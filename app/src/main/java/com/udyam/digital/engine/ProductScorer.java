package com.udyam.digital.engine;

import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.Segment;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Learned scores from product_scores.json (made offline by tools/train_recommender.py).
 *
 *   RULES decide what is ALLOWED -> LEARNED SCORES decide the ORDER -> the USER's needs
 *   and budget decide what is SHOWN.
 *
 * This class only answers "how often do businesses like this one keep product X?". It never
 * decides eligibility; RulesEngine filters first. Lookup order: business type, then segment,
 * then 0. A missing or broken file gives an empty scorer, and the engine falls back to
 * needs-only behaviour. Pure Java.
 */
public final class ProductScorer {

    /** "kept of" counts for the social-proof line, from real customers. */
    public static final class Proof {
        public final int kept;
        public final int of;
        Proof(int kept, int of) { this.kept = kept; this.of = of; }
    }

    /** Typical quantity from real bundles: per site (median), per employee (ratio), or fixed (median). */
    public static final class Qty {
        public final String per;
        public final double value;
        Qty(String per, double value) { this.per = per; this.value = value; }
    }

    private static final class Entry {
        final double score;
        final int kept;
        final int of;
        Entry(double score, int kept, int of) { this.score = score; this.kept = kept; this.of = of; }
    }

    private final Map<String, Map<String, Entry>> byType;
    private final Map<String, Map<String, Entry>> bySegment;
    private final Map<String, Map<String, Qty>> defaultQty;
    private final Map<String, Object> eval;
    private final String version;
    private final String ranker;
    private final int minProof;
    private final double suggestCutoff;

    /** Used when the JSON has no "suggest_cutoff". */
    public static final double DEFAULT_SUGGEST_CUTOFF = 0.15;

    private ProductScorer(Map<String, Map<String, Entry>> byType, Map<String, Map<String, Entry>> bySegment,
                          Map<String, Map<String, Qty>> defaultQty, Map<String, Object> eval,
                          String version, String ranker, int minProof, double suggestCutoff) {
        this.byType = byType;
        this.bySegment = bySegment;
        this.defaultQty = defaultQty;
        this.eval = eval;
        this.version = version;
        this.ranker = ranker;
        this.minProof = minProof;
        this.suggestCutoff = suggestCutoff;
    }

    public static ProductScorer empty() {
        return new ProductScorer(Collections.<String, Map<String, Entry>>emptyMap(),
                Collections.<String, Map<String, Entry>>emptyMap(),
                Collections.<String, Map<String, Qty>>emptyMap(),
                Collections.<String, Object>emptyMap(), null, null, 5, DEFAULT_SUGGEST_CUTOFF);
    }

    /** Parses the JSON; throws on malformed input. */
    @SuppressWarnings("unchecked")
    public static ProductScorer load(Reader reader) throws IOException {
        StringBuilder b = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) > 0) b.append(buf, 0, n);
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(b.toString());

        Map<String, Map<String, Qty>> qty = new HashMap<>();
        Map<String, Object> dq = map(root.get("default_qty"));
        for (Map.Entry<String, Object> seg : dq.entrySet()) {
            Map<String, Qty> m = new HashMap<>();
            for (Map.Entry<String, Object> p : map(seg.getValue()).entrySet()) {
                Map<String, Object> q = map(p.getValue());
                String per = String.valueOf(q.get("per"));
                Object v = q.containsKey("ratio") ? q.get("ratio") : q.get("median");
                if (v instanceof Double) m.put(p.getKey(), new Qty(per, (Double) v));
            }
            qty.put(seg.getKey(), m);
        }
        Object minProof = root.get("min_proof");
        Object cutoff = root.get("suggest_cutoff");
        return new ProductScorer(table(root.get("by_business_type")), table(root.get("by_segment")), qty,
                map(root.get("eval")), str(root.get("version")), str(root.get("ranker")),
                minProof instanceof Double ? ((Double) minProof).intValue() : 5,
                cutoff instanceof Double ? (Double) cutoff : DEFAULT_SUGGEST_CUTOFF);
    }

    /** Never throws: a missing or broken file means "no learned scores". */
    public static ProductScorer loadOrEmpty(Reader reader) {
        try {
            return load(reader);
        } catch (RuntimeException | IOException e) {
            return empty();
        }
    }

    private static Map<String, Map<String, Entry>> table(Object o) {
        Map<String, Map<String, Entry>> out = new HashMap<>();
        for (Map.Entry<String, Object> group : map(o).entrySet()) {
            Map<String, Entry> m = new HashMap<>();
            for (Map.Entry<String, Object> p : map(group.getValue()).entrySet()) {
                Map<String, Object> e = map(p.getValue());
                m.put(p.getKey(), new Entry(num(e.get("score")), (int) num(e.get("kept")), (int) num(e.get("of"))));
            }
            out.put(group.getKey(), m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Collections.<String, Object>emptyMap();
    }

    private static double num(Object o) { return o instanceof Double ? (Double) o : 0; }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    public boolean isEmpty() { return byType.isEmpty() && bySegment.isEmpty(); }

    private Entry entry(BusinessProfile p, String productId) {
        String type = p.getBusinessType();
        if (type != null) {
            Map<String, Entry> t = byType.get(type);
            if (t != null && t.containsKey(productId)) return t.get(productId);
        }
        Map<String, Entry> s = bySegment.get(p.getSegment().leadName);
        return s == null ? null : s.get(productId);
    }

    /** Learned score in [0, 1]: business type if known, otherwise segment, otherwise 0. */
    public double score(BusinessProfile p, String productId) {
        Entry e = entry(p, productId);
        return e == null ? 0 : e.score;
    }

    /** Social proof at the same level as the score, or null when fewer than min_proof businesses back it. */
    public Proof proof(BusinessProfile p, String productId) {
        Entry e = entry(p, productId);
        if (e == null || e.of < minProof || e.kept <= 0) return null;
        return new Proof(e.kept, e.of);
    }

    /** Typical quantity for this segment, or null to use the engine's built-in rule. */
    public Qty defaultQty(Segment segment, String productId) {
        Map<String, Qty> m = defaultQty.get(segment.leadName);
        return m == null ? null : m.get(productId);
    }

    /** The "eval" block (winner, recall_at_3, ...), for the debug screen. */
    public Map<String, Object> eval() { return Collections.unmodifiableMap(new LinkedHashMap<>(eval)); }

    public String version() { return version; }

    /** Minimum learned score for a "Businesses like yours often add" suggestion (chosen on Apr-May data). */
    public double suggestCutoff() { return suggestCutoff; }

    public String ranker() { return ranker; }
}
