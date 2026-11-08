package com.udyam.digital.kyc;

import com.udyam.digital.engine.MiniJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Accuracy proof for the 12 specimen applicants: compares the pipeline's verdicts with the
 * answer key (kyc/expected_results.json) and counts false rejects and false accepts. Used by the
 * debug "Run all specimens" screen and by the JUnit replay test, so both report the same way.
 *
 * Answer key, either shape:
 * <pre>
 *   { "K01": {"overall": "Approved", "checks_failed": []}, ... }
 *   { "applicants": { "K01": {"overall": "APPROVED", "must_flag": []} } }
 * </pre>
 * overall: Approved / Please fix / Manual review (any case, spaces or underscores). Empty = not
 * compared. An applicant matches when the overall verdict is equal AND every listed check is
 * flagged (FAIL or WARN). Extra flags are listed but don't count as a mismatch.
 */
public final class SpecimenReport {

    public static final class Expected {
        public final KycApplication.Outcome overall;
        public final List<String> checks;

        Expected(KycApplication.Outcome overall, List<String> checks) {
            this.overall = overall;
            this.checks = checks;
        }
    }

    public static final class Line {
        public final String applicant;
        /** Null when the answer key has no verdict for this applicant. */
        public final Expected expected;
        public final KycApplication.Outcome actual;
        public final List<String> flagged;
        public final List<String> missing = new ArrayList<>();
        public final List<String> extra = new ArrayList<>();
        public final String message;

        Line(String applicant, Expected expected, KycApplication.Outcome actual, List<String> flagged, String message) {
            this.applicant = applicant;
            this.expected = expected;
            this.actual = actual;
            this.flagged = flagged;
            this.message = message;
        }

        public boolean compared() { return expected != null; }
        public boolean match() { return compared() && expected.overall == actual && missing.isEmpty(); }
        /** A good applicant (expected Approved) that we did not approve. */
        public boolean falseReject() { return compared() && expected.overall == KycApplication.Outcome.APPROVED && actual != KycApplication.Outcome.APPROVED; }
        /** A problem applicant that we approved. */
        public boolean falseAccept() { return compared() && expected.overall != KycApplication.Outcome.APPROVED && actual == KycApplication.Outcome.APPROVED; }
    }

    public final List<Line> lines = new ArrayList<>();

    public int compared() { int n = 0; for (Line l : lines) if (l.compared()) n++; return n; }
    public int matched() { int n = 0; for (Line l : lines) if (l.match()) n++; return n; }
    public int falseRejects() { int n = 0; for (Line l : lines) if (l.falseReject()) n++; return n; }
    public int falseAccepts() { int n = 0; for (Line l : lines) if (l.falseAccept()) n++; return n; }
    public double accuracy() { return compared() == 0 ? 0 : (double) matched() / compared(); }

    public List<Line> mismatches() {
        List<Line> m = new ArrayList<>();
        for (Line l : lines) if (l.compared() && !l.match()) m.add(l);
        return m;
    }

    // ------------------------------------------------------------------ answer key
    public static KycApplication.Outcome parseOverall(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (t.isEmpty()) return null;
        if (t.equals("APPROVED")) return KycApplication.Outcome.APPROVED;
        if (t.equals("PLEASE_FIX")) return KycApplication.Outcome.PLEASE_FIX;
        if (t.equals("MANUAL_REVIEW")) return KycApplication.Outcome.MANUAL_REVIEW;
        throw new IllegalArgumentException("Unknown overall result '" + s + "' (use Approved, Please fix or Manual review)");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Expected> parseExpected(String json) {
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(json);
        Object nested = root.get("applicants");
        Map<String, Object> apps = nested instanceof Map ? (Map<String, Object>) nested : root;
        Map<String, Expected> out = new TreeMap<>();
        for (Map.Entry<String, Object> e : apps.entrySet()) {
            if (!e.getKey().matches("K\\d{2}") || !(e.getValue() instanceof Map)) continue;
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            KycApplication.Outcome o = parseOverall((String) v.get("overall"));
            if (o == null) continue; // not filled in yet
            Object codes = v.containsKey("checks_failed") ? v.get("checks_failed") : v.get("must_flag");
            List<String> c = new ArrayList<>();
            if (codes instanceof List) for (Object x : (List<Object>) codes) c.add(String.valueOf(x).trim().toUpperCase(Locale.ROOT));
            out.put(e.getKey(), new Expected(o, c));
        }
        return out;
    }

    // ------------------------------------------------------------------ compare
    public static SpecimenReport compare(List<SpecimenRunner.Row> rows, Map<String, Expected> expected) {
        SpecimenReport r = new SpecimenReport();
        for (SpecimenRunner.Row row : rows) {
            Set<String> flagged = new LinkedHashSet<>();
            for (String c : row.codes) flagged.add(c.substring(0, 3));
            Expected e = expected.get(row.applicant);
            Line l = new Line(row.applicant, e, row.overall, new ArrayList<>(flagged), row.message);
            if (e != null) {
                for (String c : e.checks) if (!flagged.contains(c)) l.missing.add(c);
                for (String c : flagged) if (!e.checks.contains(c)) l.extra.add(c);
            }
            r.lines.add(l);
        }
        return r;
    }

    static String label(KycApplication.Outcome o) {
        if (o == null) return "-";
        switch (o) {
            case APPROVED: return "Approved";
            case PLEASE_FIX: return "Please fix";
            case MANUAL_REVIEW: return "Manual review";
            default: return o.name();
        }
    }

    /** Markdown report. {@code mapping} = "file -> applicant slot" lines; {@code appendix} = extra sections (raw OCR), may be empty. */
    public String markdown(String source, List<String> mapping, List<SpecimenRunner.Row> rows, String appendix) {
        StringBuilder b = new StringBuilder();
        b.append("# KYC specimen report\n\n");
        b.append("Source: ").append(source).append(" · today = ").append(KycClock.TODAY).append("\n\n");
        b.append("## File mapping\n\n");
        for (String m : mapping) b.append("- ").append(m).append('\n');
        b.append("\n## Summary\n\n");
        if (compared() == 0) {
            b.append("Answer key is empty: fill in kyc/expected_results.json to get accuracy.\n\n");
        } else {
            b.append(String.format(Locale.ROOT, "- Compared: %d of %d applicants%n- Matched: %d (accuracy %.0f%%)%n"
                            + "- False rejects (good applicant not approved): %d%n- False accepts (problem applicant approved): %d%n%n",
                    compared(), lines.size(), matched(), accuracy() * 100, falseRejects(), falseAccepts()));
        }
        b.append("## Per applicant\n\n");
        b.append("| Applicant | Expected | Actual | Result | Flagged checks | Notes |\n|---|---|---|---|---|---|\n");
        for (Line l : lines) {
            String exp = l.compared() ? label(l.expected.overall) + (l.expected.checks.isEmpty() ? "" : " " + l.expected.checks) : "(not in key)";
            String res = !l.compared() ? "-" : l.match() ? "PASS" : "FAIL";
            List<String> notes = new ArrayList<>();
            if (!l.missing.isEmpty()) notes.add("missing " + l.missing);
            if (!l.extra.isEmpty() && l.compared()) notes.add("also flagged " + l.extra);
            if (l.falseReject()) notes.add("FALSE REJECT");
            if (l.falseAccept()) notes.add("FALSE ACCEPT");
            b.append("| ").append(l.applicant).append(" | ").append(exp).append(" | ").append(label(l.actual)).append(" | ")
                    .append(res).append(" | ").append(String.join(", ", l.flagged)).append(" | ").append(String.join("; ", notes)).append(" |\n");
        }
        b.append("\n## Reasons\n\n");
        for (SpecimenRunner.Row row : rows) {
            b.append("### ").append(row.applicant).append(" - ").append(label(row.overall)).append("\n\n");
            List<CheckResult> sorted = new ArrayList<>(row.results);
            Collections.sort(sorted, (x, y) -> x.code.compareTo(y.code));
            for (CheckResult c : sorted) {
                if (c.status == CheckResult.Status.PASS) continue;
                b.append("- ").append(c.code).append(' ').append(c.status).append(c.rescan ? " (rescan)" : "")
                        .append(c.fixable ? "" : " (not fixable)")
                        .append(String.format(Locale.ROOT, " conf %.2f", c.confidence)).append(": ").append(c.en).append('\n');
            }
            b.append('\n');
        }
        if (appendix != null && !appendix.isEmpty()) b.append(appendix);
        return b.toString();
    }
}
