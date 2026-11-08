package com.udyam.digital.voice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Normalises what a speech recogniser returns so the same word compares equal however it was
 * spelt: lower case, Unicode NFC, every Indian script's digits to 0-9, spelling marks that vary
 * between recognisers removed (nukta, chandrabindu, Gurmukhi addak/tippi, Malayalam chillus,
 * zero-width joiners), punctuation to spaces. Pure Java.
 */
public final class VoiceText {
    private VoiceText() {}

    /** First code point of 0 in each script: Devanagari, Bengali, Gurmukhi, Gujarati, Tamil, Telugu, Kannada, Malayalam. */
    private static final int[] ZEROS = {0x0966, 0x09E6, 0x0A66, 0x0AE6, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66};

    public static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            int digit = -1;
            for (int z : ZEROS) if (c >= z && c <= z + 9) digit = c - z;
            if (digit >= 0) { b.append((char) ('0' + digit)); continue; }
            switch (c) {
                case '़': case '়': case '਼': case '઼': // nukta
                case 'ੱ': // Gurmukhi addak
                case 'ঁ': // Bengali chandrabindu
                case '‌': case '‍': // zero-width (non-)joiner
                    continue;
                case 'ँ': b.append('ं'); continue; // Devanagari chandrabindu -> anusvara
                case 'ઁ': b.append('ં'); continue; // Gujarati
                case 'ੰ': b.append('ਂ'); continue; // Gurmukhi tippi -> bindi
                case 'ൺ': b.append("ണ്"); continue; // Malayalam chillu letters -> consonant + virama
                case 'ൻ': b.append("ന്"); continue;
                case 'ർ': b.append("ര്"); continue;
                case 'ൽ': b.append("ല്"); continue;
                case 'ൾ': b.append("ള്"); continue;
                case '₹': b.append(' '); continue;
                default:
            }
            if (Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK
                    || Character.getType(c) == Character.COMBINING_SPACING_MARK) {
                b.append(c);
            } else if (c == ',' && i > 0 && i + 1 < t.length() && Character.isDigit(t.charAt(i - 1)) && Character.isDigit(t.charAt(i + 1))) {
                // "5,000": thousands separator, keep the number whole
            } else {
                b.append(' ');
            }
        }
        // "5k", "3लोग": split digits from letters.
        String out = b.toString().replaceAll("(?<=[0-9])(?=[^0-9\\s])|(?<=[^0-9\\s])(?=[0-9])", " ");
        return out.replaceAll("\\s+", " ").trim();
    }

    public static List<String> tokens(String normalised) {
        return normalised.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(normalised.split(" ")));
    }

    /** True if the text is (mostly) Latin letters, where word boundaries matter. */
    public static boolean latin(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i)) && s.charAt(i) > 0x24F) return false;
        return true;
    }

    /** 1 - edit distance / longer length, on code points. */
    public static double similarity(String a, String b) {
        if (a.equals(b)) return 1;
        int[] x = a.codePoints().toArray(), y = b.codePoints().toArray();
        if (x.length == 0 || y.length == 0) return 0;
        int[] prev = new int[y.length + 1], cur = new int[y.length + 1];
        for (int j = 0; j <= y.length; j++) prev[j] = j;
        for (int i = 1; i <= x.length; i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length; j++) {
                int cost = x[i - 1] == y[j - 1] ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return 1.0 - (double) prev[y.length] / Math.max(x.length, y.length);
    }

    /**
     * How well {@code phrase} occurs in {@code text} (both normalised): 1 if it is there whole
     * (on word boundaries for Latin script; inside a word is fine for Indian scripts, which glue
     * on endings like "சலூன்ல"), else the best fuzzy match over windows of words.
     */
    public static double contains(String text, String phrase) {
        if (phrase.isEmpty() || text.isEmpty()) return 0;
        if (latin(phrase) || phrase.codePointCount(0, phrase.length()) < 3) {
            if ((" " + text + " ").contains(" " + phrase + " ")) return 1;
        } else if (text.contains(phrase)) {
            return 1;
        }
        List<String> t = tokens(text);
        int n = tokens(phrase).size();
        double best = 0;
        for (int k = Math.max(1, n - 1); k <= n + 1; k++) {
            for (int i = 0; i + k <= t.size(); i++) {
                String w = String.join(" ", t.subList(i, i + k));
                best = Math.max(best, similarity(w, phrase));
                // Indian-script word with an ending glued on: compare its start with the phrase.
                if (!latin(phrase) && w.length() > phrase.length()) best = Math.max(best, similarity(w.substring(0, phrase.length()), phrase) * 0.95);
            }
        }
        return best;
    }
}
