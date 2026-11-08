package com.udyam.digital.help;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Normalises text for offline matching: lower case, punctuation out, Hinglish spellings unified. */
public final class TextNorm {
    private TextNorm() {}

    public static String basic(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        String lower = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean indic = c >= 0x0900 && c <= 0x0D7F;
            if (Character.isLetterOrDigit(c) || indic) b.append(c);
            else b.append(' ');
        }
        return b.toString().trim().replaceAll("\\s+", " ");
    }

    /** basic() plus synonym folding, e.g. "nhi"/"nahin" -> "nahi". */
    public static String norm(String s, Map<String, List<String>> synonyms) {
        String t = " " + basic(s) + " ";
        for (Map.Entry<String, List<String>> e : synonyms.entrySet()) {
            for (String v : e.getValue()) {
                t = t.replace(" " + v + " ", " " + e.getKey() + " ");
            }
        }
        return t.trim().replaceAll("\\s+", " ");
    }

    /** Whole-word / whole-phrase containment on normalised text. */
    public static boolean containsPhrase(String normText, String normPhrase) {
        if (normPhrase.isEmpty()) return false;
        if ((" " + normText + " ").contains(" " + normPhrase + " ")) return true;
        // Devanagari: allow a suffix (e.g. पेमेंट -> पेमेंटका) for single words.
        char c0 = normPhrase.charAt(0);
        if (c0 >= 0x0900 && c0 <= 0x0D7F && !normPhrase.contains(" ")) {
            return (" " + normText).contains(" " + normPhrase);
        }
        return false;
    }

    public static int tokens(String normPhrase) {
        return normPhrase.isEmpty() ? 0 : normPhrase.split(" ").length;
    }
}
