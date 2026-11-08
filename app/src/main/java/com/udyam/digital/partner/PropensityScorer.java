package com.udyam.digital.partner;

import com.udyam.digital.engine.MiniJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Which lead to call": a 0-100 score and the top 2 reasons, from propensity_model.json
 * (written by tools/train_propensity.py). Mirrors the script's feature building exactly;
 * PropensityScorerTest checks it gives the same probability and score on 20 leads.
 *
 * Score = the lead's percentile among the April-May training leads (80+ = top 20%; the top 20%
 * activated at 3.5x the average rate in the June test). Reasons = the two features pushing the
 * score up the most ("has GSTIN", "reached quote stage"). Pure Java.
 */
public final class PropensityScorer {

    public static final class Score {
        public final double probability;
        public final int score;
        public final List<String> reasons;

        Score(double probability, int score, List<String> reasons) {
            this.probability = probability;
            this.score = score;
            this.reasons = reasons;
        }
    }

    private final String model;
    private final List<String> columns = new ArrayList<>();
    private final Map<String, double[]> numeric = new HashMap<>(); // mean, std
    private final List<String> businessTypes = new ArrayList<>();
    private final double intercept;
    private final double[] coef, colMean, cuts;
    private final String[] reasonLabel;
    // tree (only if the script chose it)
    private int[] tFeature, tLeft, tRight;
    private double[] tThreshold, tP1;

    @SuppressWarnings("unchecked")
    public PropensityScorer(String json) {
        Map<String, Object> m = (Map<String, Object>) MiniJson.parse(json);
        model = (String) m.get("model");
        for (Object c : (List<Object>) m.get("columns")) columns.add((String) c);
        Map<String, Object> num = (Map<String, Object>) m.get("numeric");
        for (Map.Entry<String, Object> e : num.entrySet()) {
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            numeric.put(e.getKey(), new double[] {d(v.get("mean")), d(v.get("std"))});
        }
        Map<String, Object> cats = (Map<String, Object>) m.get("categories");
        for (Object t : (List<Object>) cats.get("business_type")) if (!"Other".equals(t)) businessTypes.add((String) t);
        intercept = d(m.get("intercept"));
        Map<String, Object> w = (Map<String, Object>) m.get("coefficients"), mean = (Map<String, Object>) m.get("column_means"),
                reasons = (Map<String, Object>) m.get("reasons");
        coef = new double[columns.size()];
        colMean = new double[columns.size()];
        reasonLabel = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            coef[i] = d(w.get(columns.get(i)));
            colMean[i] = d(mean.get(columns.get(i)));
            reasonLabel[i] = (String) reasons.get(columns.get(i));
        }
        List<Object> c = (List<Object>) m.get("score_cuts");
        cuts = new double[c.size()];
        for (int i = 0; i < cuts.length; i++) cuts[i] = d(c.get(i));
        Map<String, Object> tree = (Map<String, Object>) m.get("tree");
        if (tree != null) {
            tFeature = ints((List<Object>) tree.get("feature"));
            tLeft = ints((List<Object>) tree.get("left"));
            tRight = ints((List<Object>) tree.get("right"));
            tThreshold = doubles((List<Object>) tree.get("threshold"));
            tP1 = doubles((List<Object>) tree.get("p1"));
        }
    }

    public String modelName() { return model; }

    /** The model's columns for one lead, in order. Same rules as Design.matrix in the script. */
    double[] features(PartnerLead l) {
        double[] x = new double[columns.size()];
        String type = businessTypes.contains(l.businessType) ? l.businessType : "Other";
        for (int i = 0; i < x.length; i++) {
            String c = columns.get(i);
            switch (c) {
                case "log_employees": x[i] = z(c, Math.log1p(l.employees)); break;
                case "log_sites": x[i] = z(c, Math.log1p(l.sites)); break;
                case "log_spend": x[i] = z(c, Math.log1p(l.spend)); break;
                case "city_tier2": x[i] = l.cityTier == 2 ? 1 : 0; break;
                case "has_gstin": x[i] = l.hasGstin ? 1 : 0; break;
                case "has_udyam": x[i] = l.hasUdyam ? 1 : 0; break;
                default: {
                    int eq = c.indexOf('=');
                    String key = c.substring(0, eq), val = c.substring(eq + 1), have;
                    switch (key) {
                        case "segment": have = l.segment; break;
                        case "business_type": have = type; break;
                        case "current_internet": have = l.currentInternet; break;
                        case "lead_source": have = l.leadSource; break;
                        default: have = l.stage;
                    }
                    x[i] = val.equals(have) ? 1 : 0;
                }
            }
        }
        return x;
    }

    private double z(String col, double v) {
        double[] ms = numeric.get(col);
        return (v - ms[0]) / ms[1];
    }

    public Score score(PartnerLead l) {
        double[] x = features(l);
        double p;
        if (tFeature != null && "decision_tree".equals(model)) {
            int n = 0;
            while (tLeft[n] != -1) n = x[tFeature[n]] <= tThreshold[n] ? tLeft[n] : tRight[n];
            p = tP1[n];
        } else {
            double s = intercept;
            for (int i = 0; i < x.length; i++) s += coef[i] * x[i];
            p = 1.0 / (1.0 + Math.exp(-s));
        }
        int score = 0;
        while (score < cuts.length && cuts[score] <= p) score++; // searchsorted(side="right")
        // Top 2 reasons: largest positive contributions coef * (x - training mean).
        List<double[]> pos = new ArrayList<>();
        for (int i = 0; i < x.length; i++) {
            double c = coef[i] * (x[i] - colMean[i]);
            if (c > 0) pos.add(new double[] {c, i});
        }
        Collections.sort(pos, (a, b) -> Double.compare(b[0], a[0]));
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < pos.size() && i < 2; i++) reasons.add(reasonLabel[(int) pos.get(i)[1]]);
        return new Score(p, score, reasons);
    }

    private static double d(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }

    private static int[] ints(List<Object> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = (int) d(l.get(i));
        return a;
    }

    private static double[] doubles(List<Object> l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = d(l.get(i));
        return a;
    }
}
