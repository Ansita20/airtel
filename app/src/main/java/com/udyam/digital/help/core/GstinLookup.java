package com.udyam.digital.help.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mock GSTIN lookup backed by assets/mock_gstin_registry.csv, plus a real format and
 * checksum check. The README says the 4th PAN character is always 'X' in this data,
 * which is accepted here.
 */
public final class GstinLookup {

    public enum Format { VALID, EMPTY, BAD_LENGTH, BAD_PATTERN, BAD_STATE_CODE, BAD_CHECKSUM }

    public static final class Record {
        public final String gstin, legalName, tradeName, constitution, address, state, registrationDate;
        public final BusinessProfile.GstinStatus status;

        Record(Map<String, String> r) {
            gstin = r.get("gstin");
            legalName = r.get("legal_name");
            tradeName = r.get("trade_name");
            constitution = r.get("constitution");
            address = r.get("principal_place_of_business");
            state = r.get("state");
            registrationDate = r.get("registration_date");
            String s = r.get("status") == null ? "" : r.get("status").trim().toUpperCase(Locale.ROOT);
            status = s.equals("ACTIVE") ? BusinessProfile.GstinStatus.ACTIVE
                    : s.equals("SUSPENDED") ? BusinessProfile.GstinStatus.SUSPENDED
                    : BusinessProfile.GstinStatus.CANCELLED;
        }
    }

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern SHAPE = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final Map<String, Record> registry = new HashMap<>();

    public static GstinLookup load(InputStream in) throws IOException {
        GstinLookup g = new GstinLookup();
        for (Map<String, String> r : Csv.read(in)) {
            Record rec = new Record(r);
            g.registry.put(rec.gstin, rec);
        }
        return g;
    }

    public static String normalise(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static Format checkFormat(String raw) {
        String g = normalise(raw);
        if (g.isEmpty()) return Format.EMPTY;
        if (g.length() != 15) return Format.BAD_LENGTH;
        if (!SHAPE.matcher(g).matches()) return Format.BAD_PATTERN;
        int state = Integer.parseInt(g.substring(0, 2));
        if (state < 1 || state > 38 && state != 97 && state != 99) return Format.BAD_STATE_CODE;
        return checksumChar(g) == g.charAt(14) ? Format.VALID : Format.BAD_CHECKSUM;
    }

    static char checksumChar(String g) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int v = CHARS.indexOf(g.charAt(i));
            int p = v * (i % 2 == 0 ? 1 : 2);
            sum += p / 36 + p % 36;
        }
        return CHARS.charAt((36 - sum % 36) % 36);
    }

    /** Null when not in the registry. */
    public Record lookup(String raw) {
        return registry.get(normalise(raw));
    }

    public int size() {
        return registry.size();
    }
}
