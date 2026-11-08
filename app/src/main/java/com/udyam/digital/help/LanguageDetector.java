package com.udyam.digital.help;

/**
 * App setting first; if none, the script of the message (Devanagari, Tamil, Bengali...);
 * Latin text with Hinglish markers counts as Hindi.
 */
public final class LanguageDetector {
    private LanguageDetector() {}

    private static final String[] HINGLISH = {
            "nahi", "nahin", "nhi", "kyun", "kyu", "kya", "hai", "hain", "mera", "meri", "mujhe",
            "kab", "kaise", "kitna", "kitni", "chahiye", "wala", "karo", "karna", "gaya", "raha",
            "hoga", "abhi", "bhai", "aap", "hamare", "hamara", "zyada", "jyada", "mehenga", "baat"
    };

    public static Lang detect(Lang appSetting, String message) {
        if (appSetting != null) return appSetting;
        Lang byScript = byScript(message);
        if (byScript != null) return byScript;
        return looksHinglish(message) ? Lang.HI : Lang.EN;
    }

    /** Language from the message's script; null for Latin or empty text. Devanagari -> Hindi. */
    public static Lang byScript(String m) {
        if (m == null) return null;
        int[] counts = new int[Lang.values().length];
        for (int k = 0; k < m.length(); k++) {
            char c = m.charAt(k);
            Lang l = scriptOf(c);
            if (l != null) counts[l.ordinal()]++;
        }
        int best = -1, bestN = 0;
        for (int k = 0; k < counts.length; k++) {
            if (counts[k] > bestN) {
                bestN = counts[k];
                best = k;
            }
        }
        return best < 0 ? null : Lang.values()[best];
    }

    static Lang scriptOf(char c) {
        if (c >= 0x0900 && c <= 0x097F) return Lang.HI;   // Devanagari (Hindi, Marathi)
        if (c >= 0x0980 && c <= 0x09FF) return Lang.BN;   // Bengali
        if (c >= 0x0A00 && c <= 0x0A7F) return Lang.PA;   // Gurmukhi
        if (c >= 0x0A80 && c <= 0x0AFF) return Lang.GU;   // Gujarati
        if (c >= 0x0B80 && c <= 0x0BFF) return Lang.TA;   // Tamil
        if (c >= 0x0C00 && c <= 0x0C7F) return Lang.TE;   // Telugu
        if (c >= 0x0C80 && c <= 0x0CFF) return Lang.KN;   // Kannada
        if (c >= 0x0D00 && c <= 0x0D7F) return Lang.ML;   // Malayalam
        return null;
    }

    public static boolean looksHinglish(String m) {
        if (m == null) return false;
        String t = " " + m.toLowerCase().replaceAll("[^a-z]+", " ") + " ";
        int hits = 0;
        for (String w : HINGLISH) if (t.contains(" " + w + " ")) hits++;
        int words = t.trim().isEmpty() ? 0 : t.trim().split(" ").length;
        // Two Hinglish words, or one in a very short message ("1 Gbps chahiye").
        return hits >= 2 || (hits == 1 && words <= 4);
    }
}
