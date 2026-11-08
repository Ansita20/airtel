package com.udyam.digital.security;

import com.udyam.digital.kyc.Mask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last check on every request body before it leaves the phone (help-bot LLM call). Whatever the
 * caller forgot, a full GSTIN, PAN, Udyam number, Aadhaar or mobile number is replaced by its
 * masked form. Document images are never sent: the network code has no path for them.
 * Prices and other numbers are left alone. Pure Java.
 */
public final class OutboundGuard {
    private OutboundGuard() {}

    private static final Pattern GSTIN = Pattern.compile("(?<![0-9A-Za-z])[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z][Zz][0-9A-Za-z](?![0-9A-Za-z])");
    private static final Pattern UDYAM = Pattern.compile("(?i)UDYAM-[A-Z]{2}-[0-9]{2}-[0-9]{7}");
    private static final Pattern PAN = Pattern.compile("(?<![0-9A-Za-z])[A-Za-z]{5}[0-9]{4}[A-Za-z](?![0-9A-Za-z])");
    private static final Pattern AADHAAR = Pattern.compile("(?<![0-9])[2-9][0-9]{3}[ -]?[0-9]{4}[ -]?[0-9]{4}(?![0-9])");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(\\+91[ -]?)?[6-9][0-9]{9}(?![0-9])");

    public static String redact(String s) {
        if (s == null) return null;
        s = replace(GSTIN, s, m -> Mask.gstin(m.toUpperCase()));
        s = replace(UDYAM, s, m -> Mask.udyam(m.toUpperCase()));
        s = replace(PAN, s, m -> Mask.pan(m.toUpperCase()));
        s = replace(AADHAAR, s, Mask::aadhaar);
        s = replace(PHONE, s, m -> {
            String d = m.replaceAll("[^0-9]", "");
            return "******" + d.substring(d.length() - 4);
        });
        return s;
    }

    /** True if nothing would be changed by {@link #redact}. */
    public static boolean isClean(String s) { return s == null || s.equals(redact(s)); }

    private interface Fn { String apply(String match); }

    private static String replace(Pattern p, String s, Fn f) {
        Matcher m = p.matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement(f.apply(m.group())));
        m.appendTail(b);
        return b.toString();
    }
}
