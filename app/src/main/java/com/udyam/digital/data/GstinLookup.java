package com.udyam.digital.data;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * GSTIN format + checksum validation and lookup against mock_gstin_registry.csv.
 *
 * Structure: 2-digit state code, 10-char PAN, entity number (1-9/A-Z), 'Z', check char.
 * The 4th PAN char (holder type) is normally one of ABCFGHJLPT; the mock data uses 'X',
 * which we accept.
 */
public final class GstinLookup {

    public enum Status { INVALID_FORMAT, INVALID_CHECKSUM, NOT_FOUND, ACTIVE, CANCELLED, SUSPENDED }

    public static final class Result {
        public final Status status;
        public final String gstin;
        public final String legalName;
        public final String tradeName;
        public final String city;
        public final String state;

        Result(Status status, String gstin, String legalName, String tradeName, String city, String state) {
            this.status = status;
            this.gstin = gstin;
            this.legalName = legalName;
            this.tradeName = tradeName;
            this.city = city;
            this.state = state;
        }

        public boolean isFound() {
            return status == Status.ACTIVE || status == Status.CANCELLED || status == Status.SUSPENDED;
        }

        public boolean isActive() { return status == Status.ACTIVE; }
    }

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern FORMAT = Pattern.compile(
            "^[0-9]{2}[A-Z]{3}[ABCFGHJLPTX][A-Z][0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final Map<String, Result> registry;

    public GstinLookup(Map<String, Result> registry) { this.registry = registry; }

    public static GstinLookup load(Reader in) throws IOException {
        Map<String, Result> map = new HashMap<>();
        for (Map<String, String> r : CsvParser.readRecords(in)) {
            String g = r.get("gstin").toUpperCase(Locale.ROOT);
            Status st;
            switch (r.get("status").toLowerCase(Locale.ROOT)) {
                case "active": st = Status.ACTIVE; break;
                case "cancelled": st = Status.CANCELLED; break;
                case "suspended": st = Status.SUSPENDED; break;
                default: st = Status.NOT_FOUND;
            }
            map.put(g, new Result(st, g, r.get("legal_name"), r.get("trade_name"),
                    cityFromAddress(r.get("principal_place_of_business")), r.get("state")));
        }
        return new GstinLookup(map);
    }

    public int size() { return registry.size(); }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValidFormat(String gstin) {
        if (gstin == null || !FORMAT.matcher(gstin).matches()) return false;
        int state = Integer.parseInt(gstin.substring(0, 2));
        return (state >= 1 && state <= 38) || state == 97 || state == 99;
    }

    /** GSTN check digit: base-36 weighted sum with alternating factors 1, 2. */
    public static char checksumChar(String first14) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int v = CHARS.indexOf(first14.charAt(i));
            int product = v * ((i % 2 == 0) ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARS.charAt((36 - (sum % 36)) % 36);
    }

    public static boolean hasValidChecksum(String gstin) {
        return gstin != null && gstin.length() == 15 && checksumChar(gstin.substring(0, 14)) == gstin.charAt(14);
    }

    public Result lookup(String raw) {
        String g = normalize(raw);
        if (!isValidFormat(g)) return new Result(Status.INVALID_FORMAT, g, null, null, null, null);
        if (!hasValidChecksum(g)) return new Result(Status.INVALID_CHECKSUM, g, null, null, null, null);
        Result r = registry.get(g);
        return r != null ? r : new Result(Status.NOT_FOUND, g, null, null, null, null);
    }

    /** "Shop/Unit 164, Station Road, Kochi, Kerala - 289398" -> "Kochi". */
    static String cityFromAddress(String address) {
        if (address == null) return null;
        String[] parts = address.split(",");
        if (parts.length < 2) return null;
        String city = parts[parts.length - 2].trim();
        String canonical = CityTiers.canonical(city);
        return canonical != null ? canonical : city;
    }

    /** For UI and logs: 27QTBXZ1995X1Z9 -> 27QTB******X1Z9. */
    public static String mask(String gstin) {
        if (gstin == null || gstin.length() < 9) return "***";
        return gstin.substring(0, 5) + "******" + gstin.substring(11);
    }
}
