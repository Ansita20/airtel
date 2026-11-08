package com.udyam.digital.kyc;

/** Masking for UI and logs. Full identifiers never appear on screen or in logs. */
public final class Mask {
    private Mask() {}

    /** 27ABCXX1234X1Z5 -> 27******34X1Z5 (state code + last 6 shown). */
    public static String gstin(String g) {
        if (g == null || g.length() < 15) return g == null || g.isEmpty() ? "" : "***";
        return g.substring(0, 2) + "******" + g.substring(9);
    }

    /** ABCXX1234F -> ABCXX****F. */
    public static String pan(String p) {
        if (p == null || p.length() != 10) return p == null || p.isEmpty() ? "" : "***";
        return p.substring(0, 5) + "****" + p.charAt(9);
    }

    /** Any Aadhaar number -> XXXX XXXX 1234. Only the last 4 digits ever survive. */
    public static String aadhaar(String a) {
        if (a == null) return "";
        String digits = a.replaceAll("[^0-9]", "");
        return digits.length() < 4 ? "XXXX XXXX XXXX" : "XXXX XXXX " + digits.substring(digits.length() - 4);
    }

    /** UDYAM-MH-00-1234567 -> UDYAM-MH-00-***4567. */
    public static String udyam(String u) {
        if (u == null || u.length() < 19) return u == null || u.isEmpty() ? "" : "***";
        return u.substring(0, 12) + "***" + u.substring(15);
    }
}
