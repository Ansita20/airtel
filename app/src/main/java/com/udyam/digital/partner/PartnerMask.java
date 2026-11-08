package com.udyam.digital.partner;

import com.udyam.digital.security.OutboundGuard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Masking for the partner view. Business names keep the first two letters of each word
 * ("Balaji Dental Care LLP" -> "Ba**** De**** Ca** LLP", same rule as train_propensity.py);
 * free text (help-bot summaries) has every GSTIN, PAN, Udyam, Aadhaar and phone number masked.
 * Pure Java.
 */
public final class PartnerMask {
    private PartnerMask() {}

    private static final Set<String> KEEP = new HashSet<>(Arrays.asList("llp", "pvt", "ltd", "&", "and", "co", "co.", "pvt.", "ltd."));

    public static String businessName(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (String w : name.trim().split("\\s+")) {
            if (KEEP.contains(w.toLowerCase(Locale.ROOT)) || w.codePointCount(0, w.length()) <= 2) {
                out.add(w);
            } else {
                int cut = w.offsetByCodePoints(0, 2);
                StringBuilder b = new StringBuilder(w.substring(0, cut));
                for (int i = 2; i < w.codePointCount(0, w.length()); i++) b.append('*');
                out.add(b.toString());
            }
        }
        return String.join(" ", out);
    }

    /** Help-bot summary lines for a partner: IDs and phone numbers masked. */
    public static String text(String s) { return OutboundGuard.redact(s); }
}
