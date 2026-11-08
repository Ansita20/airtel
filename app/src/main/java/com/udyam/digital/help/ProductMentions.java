package com.udyam.digital.help;

import java.util.LinkedHashMap;
import java.util.Map;

/** Which catalogue product a message is about ("WhatsApp wala option" -> P15). */
public final class ProductMentions {
    private ProductMentions() {}

    /** Longest phrases first so "leased line 100" wins over "leased line". */
    private static final Map<String, String> PHRASES = new LinkedHashMap<>();

    static {
        String[][] map = {
                {"leased line 100", "P06"}, {"100 mbps leased", "P06"}, {"leased line", "P05"}, {"lease line", "P05"},
                {"लीज्ड लाइन", "P05"}, {"1 gbps", "P03"}, {"1gbps", "P03"}, {"1 gb", "P03"}, {"gigabit", "P03"},
                {"fibre 1g", "P03"}, {"300 mbps", "P02"}, {"fibre 300", "P02"}, {"100 mbps", "P01"},
                {"airfiber", "P04"}, {"air fiber", "P04"}, {"5g", "P04"}, {"sd wan", "P10"}, {"sdwan", "P10"},
                {"static ip", "P11"}, {"failover", "P09"}, {"backup internet", "P09"}, {"whatsapp", "P15"},
                {"व्हाट्सएप", "P15"}, {"sms", "P14"}, {"toll free", "P13"}, {"tollfree", "P13"},
                {"cloud telephony", "P12"}, {"sip", "P12"}, {"ivr", "P12"}, {"cctv", "P23"}, {"camera", "P23"},
                {"कैमरा", "P23"}, {"wifi", "P22"}, {"wi fi", "P22"}, {"वाईफाई", "P22"}, {"pos", "P19"},
                {"billing machine", "P19"}, {"billing", "P19"}, {"website", "P18"}, {"domain", "P18"},
                {"email", "P16"}, {"mail", "P16"}, {"backup", "P17"}, {"firewall", "P21"},
                {"antivirus", "P20"}, {"endpoint", "P20"}, {"iot", "P24"}, {"machine monitoring", "P24"},
                {"fleet", "P25"}, {"gps", "P25"}, {"sim", "P07"}, {"sims", "P07"}, {"cameras", "P23"}, {"users", "P16"}, {"seats", "P16"}, {"सिम", "P07"}, {"postpaid", "P07"},
                {"user", "P16"}, {"seat", "P16"}
        };
        for (String[] m : map) PHRASES.put(m[0], m[1]);
    }

    /** First product mentioned, or null. */
    public static String find(String message) {
        String t = TextNorm.basic(message);
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String> e : PHRASES.entrySet()) {
            String k = TextNorm.basic(e.getKey());
            if (TextNorm.containsPhrase(t, k) && k.length() > bestLen) {
                best = e.getValue();
                bestLen = k.length();
            }
        }
        return best;
    }

    /** First number in the message, e.g. "add 3 cameras" -> 3; -1 if none. */
    public static int quantity(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{1,3})\\b").matcher(message == null ? "" : message);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v > 0 && v <= 200) return v;
        }
        return -1;
    }
}
