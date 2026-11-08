package com.udyam.digital.kyc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business / person name matching. Normalises (uppercase, no punctuation, single spaces,
 * no "M/S", PVT=PRIVATE, LTD=LIMITED, &=AND, CO=COMPANY) and scores with Jaro-Winkler:
 * >= 0.92 PASS, 0.80-0.92 WARN (ask the user to confirm), < 0.80 FAIL.
 */
public final class NameMatcher {
    private NameMatcher() {}

    public static final double PASS = 0.92;
    public static final double WARN = 0.80;

    public enum Verdict { PASS, WARN, FAIL }

    private static final Pattern PROP = Pattern.compile(
            "^(.*?)\\s*\\(\\s*(?:prop\\.?|proprietor|proprietress)\\s*:?\\s*(.*?)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);

    /** "Sneha Rao (Prop. Saffron Bakers)" -> {"Sneha Rao", "Saffron Bakers"}, else null. */
    public static String[] splitProprietor(String legalName) {
        if (legalName == null) return null;
        Matcher m = PROP.matcher(legalName.trim());
        return m.matches() ? new String[] {m.group(1).trim(), m.group(2).trim()} : null;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String u = s.toUpperCase(Locale.ROOT).replace("&", " AND ");
        u = u.replaceAll("^\\s*(M\\s*/\\s*S\\.?|MESSRS\\.?)\\s+", " ");
        u = u.replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        StringBuilder b = new StringBuilder();
        for (String t : u.split(" ")) {
            if (t.isEmpty()) continue;
            switch (t) {
                case "PVT": t = "PRIVATE"; break;
                case "LTD": t = "LIMITED"; break;
                case "CO": t = "COMPANY"; break;
                default: break;
            }
            if (b.length() > 0) b.append(' ');
            b.append(t);
        }
        return b.toString();
    }

    public static double jaroWinkler(String a, String b) {
        if (a.equals(b)) return 1.0;
        int la = a.length(), lb = b.length();
        if (la == 0 || lb == 0) return 0.0;
        int window = Math.max(0, Math.max(la, lb) / 2 - 1);
        boolean[] ma = new boolean[la], mb = new boolean[lb];
        int matches = 0;
        for (int i = 0; i < la; i++) {
            int lo = Math.max(0, i - window), hi = Math.min(lb - 1, i + window);
            for (int j = lo; j <= hi; j++) {
                if (!mb[j] && a.charAt(i) == b.charAt(j)) {
                    ma[i] = mb[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) return 0.0;
        int t = 0, k = 0;
        for (int i = 0; i < la; i++) {
            if (!ma[i]) continue;
            while (!mb[k]) k++;
            if (a.charAt(i) != b.charAt(k)) t++;
            k++;
        }
        double m = matches;
        double jaro = (m / la + m / lb + (m - t / 2.0) / m) / 3.0;
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(la, lb)) && a.charAt(i) == b.charAt(i); i++) prefix++;
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    /** Similarity of two names after normalisation. */
    public static double score(String a, String b) {
        String na = normalize(a), nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0.0;
        return jaroWinkler(na, nb);
    }

    /** Best score of {@code name} against any candidate (nulls ignored). */
    public static double best(String name, List<String> candidates) {
        double best = 0;
        for (String c : candidates) if (c != null) best = Math.max(best, score(name, c));
        return best;
    }

    /** Names a business can go by: legal name, trade name, and both halves of "X (Prop. Y)". */
    public static List<String> businessNames(String legalName, String tradeName) {
        List<String> out = new ArrayList<>();
        if (legalName != null) out.add(legalName);
        if (tradeName != null) out.add(tradeName);
        String[] p = splitProprietor(legalName);
        if (p != null) out.add(p[1]);
        return out;
    }

    public static Verdict verdict(double score) {
        return score >= PASS ? Verdict.PASS : score >= WARN ? Verdict.WARN : Verdict.FAIL;
    }
}
