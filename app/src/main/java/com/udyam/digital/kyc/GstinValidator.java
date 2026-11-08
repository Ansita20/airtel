package com.udyam.digital.kyc;

import com.udyam.digital.data.CsvParser;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * GSTIN format, checksum and registry lookup (mock_gstin_registry.csv).
 * Format: 2-digit state code + 10-char PAN + entity char + 'Z' + check char. The PAN's 4th
 * character is 'X' in this dataset, which we accept alongside the real holder types.
 */
public final class GstinValidator {

    /** One registry row. */
    public static final class GstRecord {
        public final String gstin, legalName, tradeName, constitution, address, state, registrationDate, status;

        public GstRecord(String gstin, String legalName, String tradeName, String constitution, String address,
                         String state, String registrationDate, String status) {
            this.gstin = gstin;
            this.legalName = legalName;
            this.tradeName = tradeName;
            this.constitution = constitution;
            this.address = address;
            this.state = state;
            this.registrationDate = registrationDate;
            this.status = status;
        }

        public boolean isActive() { return "Active".equalsIgnoreCase(status); }
        public boolean isProprietorship() { return constitution != null && constitution.toLowerCase(Locale.ROOT).startsWith("propriet"); }
    }

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern FORMAT =
            Pattern.compile("^[0-9]{2}[A-Z]{3}[ABCFGHJLPTX][A-Z][0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final Map<String, GstRecord> registry;

    public GstinValidator(Map<String, GstRecord> registry) { this.registry = registry; }

    public static GstinValidator load(Reader in) throws IOException {
        Map<String, GstRecord> m = new HashMap<>();
        for (Map<String, String> r : CsvParser.readRecords(in)) {
            String g = normalize(r.get("gstin"));
            m.put(g, new GstRecord(g, r.get("legal_name"), r.get("trade_name"), r.get("constitution"),
                    r.get("principal_place_of_business"), r.get("state"), r.get("registration_date"), r.get("status")));
        }
        return new GstinValidator(m);
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValidFormat(String g) {
        return g != null && g.length() == 15 && FORMAT.matcher(g).matches() && States.fromGstCode(g.substring(0, 2)) != null;
    }

    /**
     * Check character: values 0-9 -> 0-9, A-Z -> 10-35; weight 1 at even index, 2 at odd
     * (0-based) over the first 14; add product/36 + product%36; check = (36 - sum%36) % 36.
     */
    public static char checksumChar(String first14) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int v = CHARS.indexOf(first14.charAt(i));
            if (v < 0) return '?';
            int product = v * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARS.charAt((36 - (sum % 36)) % 36);
    }

    public static boolean hasValidChecksum(String g) {
        return g != null && g.length() == 15 && checksumChar(g.substring(0, 14)) == g.charAt(14);
    }

    /** Format + checksum. */
    public static boolean isValid(String g) { return isValidFormat(g) && hasValidChecksum(g); }

    /** The PAN embedded in a GSTIN (characters 3-12). */
    public static String panOf(String g) { return g == null || g.length() < 12 ? null : g.substring(2, 12); }

    /** Registry row, or null. */
    public GstRecord find(String gstin) { return registry.get(normalize(gstin)); }

    private java.util.Set<String> pans;

    /** True if some registered GSTIN contains this PAN (characters 3-12). */
    public synchronized boolean hasPan(String pan) {
        if (pans == null) {
            pans = new java.util.HashSet<>();
            for (String g : registry.keySet()) if (g.length() >= 12) pans.add(g.substring(2, 12));
        }
        return pan != null && pans.contains(pan);
    }

    /** True when the 2-digit state code agrees with the registry's state. */
    public static boolean stateCodeMatches(GstRecord r) {
        return r != null && States.same(States.fromGstCode(r.gstin.substring(0, 2)), r.state);
    }

    public int size() { return registry.size(); }
}
