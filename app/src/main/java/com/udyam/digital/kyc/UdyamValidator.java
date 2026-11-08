package com.udyam.digital.kyc;

import com.udyam.digital.data.UdyamLookup;

import java.util.Locale;
import java.util.regex.Pattern;

/** Udyam number format (UDYAM-XX-00-1234567; district 00 is valid here) + registry lookup. */
public final class UdyamValidator {

    private static final Pattern FORMAT = Pattern.compile("^UDYAM-[A-Z]{2}-[0-9]{2}-[0-9]{7}$");

    private final UdyamLookup registry;

    public UdyamValidator(UdyamLookup registry) { this.registry = registry; }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("\\s+", "").replace('—', '-').replace('–', '-');
        return s;
    }

    public static boolean isValidFormat(String u) {
        return u != null && FORMAT.matcher(u).matches() && States.fromUdyamCode(u.substring(6, 8)) != null;
    }

    /** State named by the 2-letter code in the number. */
    public static String stateOf(String u) {
        return u == null || u.length() < 8 ? null : States.fromUdyamCode(u.substring(6, 8));
    }

    public UdyamLookup.Record find(String u) { return registry.find(normalize(u)); }
}
