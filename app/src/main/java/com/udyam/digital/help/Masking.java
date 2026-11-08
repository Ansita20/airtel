package com.udyam.digital.help;

/** Masks identifiers before they are stored, logged, shown or sent to the LLM. */
public final class Masking {
    private Masking() {}

    /** "27XXXXX1234X1Z5" -> "27******34X1Z5": state code and last 6 characters only. */
    public static String gstin(String g) {
        if (g == null) return null;
        String s = g.replaceAll("\\s", "");
        if (s.length() < 8) return "******";
        return s.substring(0, 2) + "******" + s.substring(s.length() - 6);
    }

    /** "UDYAM-MH-00-1234567" -> "UDYAM-MH-00-****567". */
    public static String udyam(String u) {
        if (u == null) return null;
        if (u.length() < 4) return "****";
        int keep = 3;
        int dash = u.lastIndexOf('-');
        String head = dash >= 0 ? u.substring(0, dash + 1) : "";
        String tail = u.substring(Math.max(dash + 1, u.length() - keep));
        return head + "****" + tail;
    }

    /** "+919876543210" -> "+91******3210". */
    public static String phone(String p) {
        if (p == null) return null;
        String d = p.replaceAll("[^0-9+]", "");
        if (d.length() < 4) return "****";
        String prefix = d.startsWith("+") && d.length() > 7 ? d.substring(0, 3) : "";
        return prefix + "******" + d.substring(d.length() - 4);
    }

    /** Hides anything in free text that looks like an OTP, Aadhaar, PAN, GSTIN or phone. */
    public static String freeText(String t) {
        if (t == null) return null;
        String s = t;
        s = s.replaceAll("\\b[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z][Zz][0-9A-Za-z]\\b", "[GSTIN]");
        s = s.replaceAll("\\b[A-Za-z]{5}[0-9]{4}[A-Za-z]\\b", "[PAN]");
        s = s.replaceAll("\\b[0-9]{4}\\s?[0-9]{4}\\s?[0-9]{4}\\b", "[AADHAAR]");
        s = s.replaceAll("(\\+91[\\s-]?)?\\b[6-9][0-9]{9}\\b", "[PHONE]");
        s = s.replaceAll("\\b[0-9]{4,6}\\b", "[NUMBER]");
        return s;
    }
}
