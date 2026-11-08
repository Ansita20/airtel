package com.udyam.digital.voice;

import com.udyam.digital.data.CityNames;
import com.udyam.digital.kyc.GstinValidator;
import com.udyam.digital.model.Language;
import com.udyam.digital.model.Question;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns what the speech recogniser heard into an answer to a questionnaire question, in any of
 * the 10 languages. Pure Java (unit-tested).
 *
 * <ul>
 *   <li>Numbers ("3", "तीन", "மூணு", "पाँच हज़ार") pick the range they fall in (3 -> "3–4"); for the
 *       budget, the largest option not above what was said.</li>
 *   <li>Words are matched against the option's label in this language and in English, the
 *       synonyms in {@link VoiceLexicon} and, for cities, every script plus English/old names.
 *       Mixed speech works: "मेरे पास 3 employees हैं", "naan oru salon vachirukken".</li>
 *   <li>Every recogniser alternative counts, weighted by the recogniser's own confidence.
 *       Confidence = best score - half the runner-up's. Below {@link #CONFIDENT} (0.75) the app
 *       asks again with the two best ("Was it 3 or 13?"). Above it, the app still confirms
 *       ("You said: 3 people. Correct?") before moving on.</li>
 * </ul>
 */
public final class VoiceAnswerParser {
    private VoiceAnswerParser() {}

    public static final double CONFIDENT = 0.75;
    /** A phrase match below this doesn't count at all. */
    static final double MIN_MATCH = 0.8;

    /** One possible answer. */
    public static final class Candidate {
        public final String value;
        /** The option's label in the user's language. */
        public final String label;
        /** What was heard for it ("13", "salon"). */
        public final String heard;
        /** The number heard, for number questions; else null. */
        public final Double number;
        public final double score;

        Candidate(String value, String label, String heard, Double number, double score) {
            this.value = value;
            this.label = label;
            this.heard = heard;
            this.number = number;
            this.score = score;
        }

        @Override public String toString() { return value + "(" + String.format(Locale.ROOT, "%.2f", score) + ", heard '" + heard + "')"; }
    }

    public static final class Result {
        /** Best first, one per option. */
        public final List<Candidate> ranked;
        /** Multi-select: every option said. Single: the best one (if any). */
        public final List<String> values;
        /** "Nothing / skip" for an optional multi-select. */
        public final boolean nothing;
        public final double confidence;

        Result(List<Candidate> ranked, List<String> values, boolean nothing, double confidence) {
            this.ranked = ranked;
            this.values = values;
            this.nothing = nothing;
            this.confidence = Math.max(0, Math.min(1, confidence));
        }

        public boolean confident() { return confidence >= CONFIDENT && (nothing || !values.isEmpty()); }
        public boolean understood() { return nothing || !ranked.isEmpty(); }
        public Candidate best() { return ranked.isEmpty() ? null : ranked.get(0); }
        public Candidate second() { return ranked.size() < 2 ? null : ranked.get(1); }

        @Override public String toString() {
            return "values=" + values + (nothing ? " nothing" : "") + " conf=" + String.format(Locale.ROOT, "%.2f", confidence) + " " + ranked;
        }
    }

    /** A choice the user can say: value, label shown, phrases that select it, and an optional number range. */
    public static final class Choice {
        final String value, label;
        final List<String> phrases = new ArrayList<>();
        double low = Double.NaN, high = Double.NaN;

        public Choice(String value, String label, Collection<String> phrases) {
            this.value = value;
            this.label = label;
            for (String p : phrases) {
                String n = VoiceText.norm(p);
                if (!n.isEmpty() && !this.phrases.contains(n)) this.phrases.add(n);
            }
        }
    }

    /** An answer the owner picked by tapping (certain). */
    public static Result chosen(String value) {
        List<String> v = new ArrayList<>();
        v.add(value);
        return new Result(new ArrayList<>(), v, false, 1);
    }

    // ------------------------------------------------------------------ question -> choices
    enum NumberMode { NONE, RANGE, BUDGET }

    static NumberMode mode(Question q) {
        switch (q.getId()) {
            case "employees": case "sites": return NumberMode.RANGE;
            case "budget": return NumberMode.BUDGET;
            default: return NumberMode.NONE;
        }
    }

    static List<Choice> choices(Question q, Language l, Collection<String> only) {
        List<Choice> out = new ArrayList<>();
        List<Question.Option> opts = q.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            Question.Option o = opts.get(i);
            if (only != null && !only.contains(o.getValue())) continue;
            List<String> p = new ArrayList<>();
            addLabel(p, o.label(l));
            addLabel(p, o.label(Language.EN));
            p.addAll(VoiceLexicon.synonyms(o.getValue()));
            if ("city".equals(q.getId())) {
                for (Language any : Language.values()) p.add(CityNames.of(o.getValue()).get(any));
                p.addAll(VoiceLexicon.cityAliases(o.getValue()));
            }
            if (mode(q) != NumberMode.NONE) p.removeIf(s -> s.matches(".*[0-9].*")); // numbers are handled as numbers
            Choice c = new Choice(o.getValue(), o.label(l), p);
            if (mode(q) == NumberMode.RANGE) {
                c.low = Double.parseDouble(o.getValue());
                // "2" is the "1–2" tile: everything below the next option's start.
                c.high = i + 1 < opts.size() ? Double.parseDouble(opts.get(i + 1).getValue()) - 1 : Double.MAX_VALUE;
                if (i == 0) c.low = 1;
            } else if (mode(q) == NumberMode.BUDGET && !"0".equals(o.getValue())) {
                c.low = Double.parseDouble(o.getValue());
            }
            out.add(c);
        }
        return out;
    }

    private static void addLabel(List<String> p, String label) {
        if (label == null) return;
        p.add(label);
        if (label.contains("/")) for (String part : label.split("/")) p.add(part.trim());
    }

    // ------------------------------------------------------------------ parsing
    /** Weight of the i-th alternative: the recogniser's confidence if it gave one, else 0.9, 0.6, 0.5 ... */
    static double weight(float[] conf, int i) {
        if (conf != null && i < conf.length && conf[i] > 0) return conf[i];
        return i == 0 ? 0.9 : Math.max(0.3, 0.7 - 0.1 * i);
    }

    public static Result parse(Question q, List<String> heard, float[] conf, Language l) {
        return parse(q, heard, conf, l, null);
    }

    /** @param only restrict to these option values (the "Was it 3 or 13?" follow-up), or null */
    public static Result parse(Question q, List<String> heard, float[] conf, Language l, Collection<String> only) {
        List<Choice> cs = choices(q, l, only);
        boolean multi = q.getType() == Question.Type.MULTI;
        boolean optional = multi && q.getMinPicks() == 0;
        NumberMode mode = mode(q);
        boolean hasNone = false;
        for (Choice c : cs) hasNone |= c.value.equals("NONE");
        return parseChoices(cs, heard, conf, l, multi, optional, mode, hasNone);
    }

    /** Generic: any list of choices (also used for the language screen). */
    public static Result parseChoices(List<Choice> cs, List<String> heard, float[] conf, Language l) {
        return parseChoices(cs, heard, conf, l, false, false, NumberMode.NONE, false);
    }

    static Result parseChoices(List<Choice> cs, List<String> heard, float[] conf, Language l, boolean multi, boolean optional,
                               NumberMode mode, boolean noneOption) {
        Map<String, Candidate> best = new LinkedHashMap<>();
        List<String> multiValues = new ArrayList<>();
        double multiConf = 0;
        boolean nothing = false;
        double nothingConf = 0;
        for (int i = 0; heard != null && i < heard.size(); i++) {
            String raw = heard.get(i);
            String text = VoiceText.norm(raw);
            if (text.isEmpty()) continue;
            double w = weight(conf, i);

            // "Nothing" for an optional multi-select (the extras): "कुछ नहीं", "no", "skip".
            if (optional && (anyPhrase(text, VoiceLexicon.nothing(l)) || yesNo(text, l) == YesNo.NO)) {
                if (!nothing || w > nothingConf) { nothing = true; nothingConf = w * 0.95; }
                continue;
            }

            Map<String, double[]> scores = new LinkedHashMap<>(); // value -> {score}
            Map<String, String> heardAs = new LinkedHashMap<>();
            Map<String, Double> numbers = new LinkedHashMap<>();

            if (mode != NumberMode.NONE) {
                NumberWords.Found f = NumberWords.parse(raw, l);
                if (f != null) {
                    Choice c = forNumber(cs, f.value, mode);
                    if (c != null) {
                        double m = mode == NumberMode.BUDGET && c.low != f.value ? 0.9 : 1.0;
                        scores.put(c.value, new double[] {m});
                        heardAs.put(c.value, format(f.value));
                        numbers.put(c.value, f.value);
                    }
                }
            }
            for (Choice c : cs) {
                if (scores.containsKey(c.value)) continue;
                double m = 0;
                String what = null;
                for (String p : c.phrases) {
                    double s = VoiceText.contains(text, p);
                    // Prefer the longer phrase when two match fully ("mobile shop" over "mobile").
                    double ranked = s >= 1 ? 1 + Math.min(0.05, p.length() / 400.0) : s;
                    if (ranked > m) { m = ranked; what = p; }
                }
                if (m >= MIN_MATCH) {
                    scores.put(c.value, new double[] {Math.min(1, m) + (m > 1 ? (m - 1) : 0)});
                    heardAs.put(c.value, what);
                }
            }
            // An exact phrase beats near-misses: "दवाई की दुकान" must not compete with "नाई की दुकान" just
            // because both end in "की दुकान". Fuzzy matches count only when nothing matched exactly.
            boolean exact = false;
            for (double[] v : scores.values()) exact |= v[0] >= 1;
            if (exact) scores.values().removeIf(v -> v[0] < 1);
            heardAs.keySet().retainAll(scores.keySet());
            // "No" to "Internet now?" means none.
            if (scores.isEmpty() && noneOption && yesNo(text, l) == YesNo.NO) {
                scores.put("NONE", new double[] {0.9});
                heardAs.put("NONE", raw);
            }
            // "Both" / "all" on a multi-select.
            if (multi && scores.isEmpty() && anyPhrase(text, VoiceLexicon.all(l))) {
                for (Choice c : cs) { scores.put(c.value, new double[] {0.95}); heardAs.put(c.value, raw); }
            }

            if (multi && multiValues.isEmpty() && !scores.isEmpty()) {
                multiValues.addAll(scores.keySet());
                double min = 1;
                for (double[] s : scores.values()) min = Math.min(min, Math.min(1, s[0]));
                multiConf = w * min;
            }
            for (Map.Entry<String, double[]> e : scores.entrySet()) {
                double s = Math.min(1.0, e.getValue()[0]) * w + Math.max(0, e.getValue()[0] - 1); // tiny tie-break kept
                Candidate old = best.get(e.getKey());
                if (old == null || s > old.score) {
                    Choice c = find(cs, e.getKey());
                    best.put(e.getKey(), new Candidate(e.getKey(), c.label, heardAs.get(e.getKey()), numbers.get(e.getKey()), s));
                }
            }
        }

        List<Candidate> ranked = new ArrayList<>(best.values());
        Collections.sort(ranked, (a, b) -> Double.compare(b.score, a.score));
        if (multi) {
            if (nothing && multiValues.isEmpty()) return new Result(ranked, multiValues, true, nothingConf);
            return new Result(ranked, multiValues, false, multiConf);
        }
        if (ranked.isEmpty()) return new Result(ranked, new ArrayList<>(), false, 0);
        double s1 = Math.min(1, ranked.get(0).score), s2 = ranked.size() > 1 ? Math.min(1, ranked.get(1).score) : 0;
        List<String> v = new ArrayList<>();
        v.add(ranked.get(0).value);
        return new Result(ranked, v, false, s1 - 0.5 * s2);
    }

    private static Choice find(List<Choice> cs, String value) {
        for (Choice c : cs) if (c.value.equals(value)) return c;
        throw new IllegalStateException(value);
    }

    private static Choice forNumber(List<Choice> cs, double n, NumberMode mode) {
        if (mode == NumberMode.RANGE) {
            for (Choice c : cs) if (!Double.isNaN(c.low) && n >= c.low && n <= c.high) return c;
            return null;
        }
        // Budget: the largest option not above the amount said (so the plan stays within it).
        Choice pick = null;
        for (Choice c : cs) {
            if (Double.isNaN(c.low)) continue;
            if (c.low <= n && (pick == null || c.low > pick.low)) pick = c;
        }
        if (pick == null) for (Choice c : cs) if (!Double.isNaN(c.low) && (pick == null || c.low < pick.low)) pick = c;
        return n < 100 ? null : pick; // "5" alone is not a budget
    }

    static String format(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static boolean anyPhrase(String text, List<String> phrases) {
        for (String p : phrases) if (!p.isEmpty() && VoiceText.contains(text, p) >= 1) return true;
        return false;
    }

    // ------------------------------------------------------------------ yes / no
    public enum YesNo { YES, NO, UNKNOWN }

    /** Yes / no in this language or English. "Not right" / "सही नहीं" is NO: a no-word wins. */
    public static YesNo yesNo(String normalised, Language l) {
        List<String> tok = VoiceText.tokens(normalised);
        boolean no = false, yes = false;
        for (String w : VoiceLexicon.no(l)) if (w.contains(" ") ? normalised.contains(w) : tok.contains(w)) no = true;
        for (String w : VoiceLexicon.yes(l)) if (w.contains(" ") ? normalised.contains(w) : tok.contains(w)) yes = true;
        if (no) return YesNo.NO;
        return yes ? YesNo.YES : YesNo.UNKNOWN;
    }

    /** Yes / no over all recogniser alternatives: the first one that says either. */
    public static YesNo yesNo(List<String> heard, Language l) {
        if (heard != null) for (String h : heard) {
            YesNo y = yesNo(VoiceText.norm(h), l);
            if (y != YesNo.UNKNOWN) return y;
        }
        return YesNo.UNKNOWN;
    }

    // ------------------------------------------------------------------ GSTIN
    /** A GSTIN spoken letter by letter ("27 A B C X X 1 2 3 4 X 1 Z 5"), or null. Format-checked only. */
    public static String gstin(List<String> heard) {
        if (heard == null) return null;
        for (String h : heard) {
            String t = VoiceText.norm(h).replace(" ", "").toUpperCase(Locale.ROOT);
            for (int i = 0; i + 15 <= t.length(); i++) {
                String g = t.substring(i, i + 15);
                if (GstinValidator.isValidFormat(g)) return g;
            }
        }
        return null;
    }
}
