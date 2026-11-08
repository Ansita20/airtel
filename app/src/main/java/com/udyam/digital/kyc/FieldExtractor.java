package com.udyam.digital.kyc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls fields out of OCR lines with label proximity ("GSTIN", "Legal Name", "Bill Date",
 * "नाम" ...) plus strict regexes: a value sits to the right of its label on the same row, or
 * directly below it (ID cards). Multi-line values (addresses) continue until the next row that
 * starts with another label. Each field keeps the OCR confidence of the lines it came from.
 *
 * Identifiers (GSTIN, Udyam, PAN) are returned AS READ; any repair of OCR look-alikes is done
 * by {@link IdCorrector}, which needs the registry to accept it.
 */
public final class FieldExtractor {
    private FieldExtractor() {}

    // Field keys
    public static final String GSTIN = "gstin", LEGAL_NAME = "legal_name", TRADE_NAME = "trade_name",
            CONSTITUTION = "constitution", ADDRESS = "address", LIABILITY_DATE = "liability_date",
            UDYAM = "udyam_number", ENTERPRISE = "enterprise_name", ENTERPRISE_TYPE = "enterprise_type",
            ACTIVITY = "activity", CITY = "city", STATE = "state", REG_DATE = "registration_date",
            NAME = "name", DESIGNATION = "designation", FOR_BUSINESS = "for_business", ID_NUMBER = "id_number",
            PAN = "pan", DOB = "date_of_birth", EXPIRY = "expiry_date", AADHAAR = "aadhaar",
            BILL_DATE = "bill_date", PIN = "pin";

    /** Watermark and template text that must never be read as a value. */
    private static final Pattern NOISE = Pattern.compile(
            "(?i)(synthetic|specimen|specim|cimen|hetic|not a valid|valid d|docu|dozl|hackath|fictional|ocr testing)");

    /** "UDYAM-MH-00-1234567" with look-alikes allowed anywhere; spaces already removed. */
    private static final Pattern UDYAM_LOOSE = Pattern.compile(
            "U[DO0]YAM[-–—]?([A-Z0-9]{2})[-–—]([A-Z0-9]{2})[-–—]([A-Z0-9]{7})(?![A-Z0-9])");
    private static final Pattern PAN_TOKEN = Pattern.compile("(?<![A-Z0-9])[A-Z0-9]{10}(?![A-Z0-9])");
    private static final Pattern AADHAAR_RE = Pattern.compile("\\b([0-9X]{4})\\s?([0-9X]{4})\\s?([0-9]{4})\\b");
    private static final Pattern PIN_RE = Pattern.compile("\\b[1-9][0-9]{5}\\b");

    private static final Map<String, String[]> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(GSTIN, new String[] {"gstin", "gst number", "registration number"});
        LABELS.put(LEGAL_NAME, new String[] {"legal name", "legal", "वैध नाम", "विधिक नाम"});
        LABELS.put(TRADE_NAME, new String[] {"trade name", "trade"});
        LABELS.put(CONSTITUTION, new String[] {"constitution"});
        LABELS.put(ADDRESS, new String[] {"address of principal", "place of business", "supply address", "address",
                "service address", "installation address", "पता"});
        LABELS.put(LIABILITY_DATE, new String[] {"date of liability", "date of validity", "valid from"});
        LABELS.put(UDYAM, new String[] {"udyam reg", "udyam registration number"});
        LABELS.put(ENTERPRISE, new String[] {"enterprise name", "name of enterprise", "enterprise"});
        LABELS.put(ENTERPRISE_TYPE, new String[] {"type of enterprise", "enterprise type"});
        LABELS.put(ACTIVITY, new String[] {"major activity"});
        LABELS.put(CITY, new String[] {"city"});
        LABELS.put(STATE, new String[] {"state"});
        LABELS.put(REG_DATE, new String[] {"date of registration", "date of udyam registration"});
        LABELS.put(NAME, new String[] {"name", "billed to", "account holder", "consumer name", "customer name",
                "नाम", "उपभोक्ता का नाम"});
        LABELS.put(DESIGNATION, new String[] {"designation"});
        LABELS.put(FOR_BUSINESS, new String[] {"for business"});
        LABELS.put(ID_NUMBER, new String[] {"id no", "id number", "card no", "licence no", "passport no", "epic no"});
        LABELS.put(PAN, new String[] {"pan", "pan no", "pan number", "permanent account number"});
        LABELS.put(DOB, new String[] {"date of birth", "dob", "birth date", "जन्म तिथि", "जन्म की तारीख"});
        LABELS.put(EXPIRY, new String[] {"valid till", "valid upto", "valid up to", "expiry", "date of expiry", "expires"});
        LABELS.put(BILL_DATE, new String[] {"bill date", "issue date", "date of issue", "statement date", "bill period",
                "बिल तिथि", "बिल की तारीख"});
        LABELS.put("_type", new String[] {"type of registration"});
        LABELS.put("_other", new String[] {"consumer no", "units consumed", "amount due", "photo", "signature"});
    }

    /** Fields relevant to each document type (others are dropped). */
    private static String[] fieldsFor(DocType t) {
        switch (t) {
            case GST_CERTIFICATE: return new String[] {GSTIN, LEGAL_NAME, TRADE_NAME, CONSTITUTION, ADDRESS, LIABILITY_DATE};
            case UDYAM_CERTIFICATE: return new String[] {UDYAM, ENTERPRISE, ENTERPRISE_TYPE, ACTIVITY, CITY, STATE, REG_DATE};
            case UTILITY_BILL: case TELEPHONE_BILL: case BANK_STATEMENT: case RENT_AGREEMENT:
                return new String[] {NAME, ADDRESS, BILL_DATE, PIN};
            case UNKNOWN: return new String[] {};
            default: return new String[] {NAME, DESIGNATION, FOR_BUSINESS, ID_NUMBER, PAN, DOB, EXPIRY, AADHAAR};
        }
    }

    /**
     * Drops watermark and template text: known stamp words, and lines whose box is much taller
     * than a normal line (the diagonal "SPECIMEN" stamp produces tall, slanted boxes).
     */
    public static List<OcrLine> clean(List<OcrLine> raw) {
        List<Integer> heights = new ArrayList<>();
        for (OcrLine l : raw) heights.add(l.height());
        Collections.sort(heights);
        int median = heights.isEmpty() ? 20 : heights.get(heights.size() / 2);
        // Straighten tilted scans: median angle of ordinary text lines (the stamp is ~30 degrees).
        List<Float> angles = new ArrayList<>();
        for (OcrLine l : raw) {
            if (Math.abs(l.angle) < 15 && (l.right - l.left) > 2 * l.height() && !NOISE.matcher(l.text).find()) angles.add(l.angle);
        }
        Collections.sort(angles);
        double deg = angles.isEmpty() ? 0 : angles.get(angles.size() / 2);
        double sin = Math.sin(Math.toRadians(-deg)), cos = Math.cos(Math.toRadians(-deg));
        boolean rotate = Math.abs(deg) >= 1.0;
        List<OcrLine> lines = new ArrayList<>();
        for (OcrLine l : raw) {
            if (l.text.isEmpty() || NOISE.matcher(l.text).find()) continue;
            if (Math.abs(l.angle - deg) > 15) continue; // the diagonal watermark
            OcrLine x = l;
            if (rotate) {
                int w = l.right - l.left, h = l.bottom - l.top;
                double cx = (l.left + l.right) / 2.0, cy = (l.top + l.bottom) / 2.0;
                double nx = cx * cos - cy * sin, ny = cx * sin + cy * cos;
                int nh = (int) Math.max(8, h - w * Math.abs(Math.sin(Math.toRadians(deg))));
                x = l.withBox((int) (nx - w / 2.0), (int) (ny - nh / 2.0), (int) (nx + w / 2.0), (int) (ny + nh / 2.0));
            }
            if (x.height() > 2.2 * median && !rotate) continue;
            lines.add(x);
        }
        return lines;
    }

    /** Fields found on one document, with the OCR confidence of each and the lines it came from. */
    public static final class Extraction {
        public final Map<String, String> values = new LinkedHashMap<>();
        public final Map<String, Float> confidence = new HashMap<>();
        public final Map<String, List<OcrLine>> sources = new HashMap<>();
    }

    public static Map<String, String> extract(DocType type, List<OcrLine> raw) {
        return extractDetailed(type, raw).values;
    }

    public static Extraction extractDetailed(DocType type, List<OcrLine> raw) {
        List<OcrLine> lines = clean(raw);
        Collections.sort(lines, new Comparator<OcrLine>() {
            @Override public int compare(OcrLine a, OcrLine b) {
                int c = Integer.compare(a.centerY(), b.centerY());
                return c != 0 ? c : Integer.compare(a.left, b.left);
            }
        });
        Map<String, String> out = new LinkedHashMap<>();
        for (String f : fieldsFor(type)) {
            String v = labelled(f, lines);
            if (v != null && !v.isEmpty()) out.put(f, v);
        }
        String all = joinAll(lines);
        // Strict regexes over the labelled value first, then the whole page.
        if (type == DocType.GST_CERTIFICATE) {
            String g = locateGstin(out.containsKey(GSTIN) ? out.get(GSTIN) : all);
            if (g == null) g = locateGstin(all);
            if (g != null) out.put(GSTIN, g); else out.remove(GSTIN);
        }
        if (type == DocType.UDYAM_CERTIFICATE) {
            String u = locateUdyam(out.containsKey(UDYAM) ? out.get(UDYAM) : "");
            if (u == null) u = locateUdyam(all);
            if (u != null) out.put(UDYAM, u); else out.remove(UDYAM);
        }
        if (out.containsKey(ADDRESS)) {
            Matcher pin = PIN_RE.matcher(out.get(ADDRESS));
            String last = null;
            while (pin.find()) last = pin.group();
            if (last != null) out.put(PIN, last);
        } else {
            out.remove(PIN);
        }
        boolean isId = type != DocType.GST_CERTIFICATE && type != DocType.UDYAM_CERTIFICATE
                && type != DocType.UNKNOWN && !type.name().endsWith("BILL")
                && type != DocType.BANK_STATEMENT && type != DocType.RENT_AGREEMENT;
        if (isId) {
            String p = locatePan(out.containsKey(PAN) ? out.get(PAN) : "");
            if (p == null) p = locatePan(all);
            if (p != null) out.put(PAN, p); else out.remove(PAN);
            if (type == DocType.AADHAAR) {
                Matcher a = AADHAAR_RE.matcher(all);
                if (a.find()) out.put(AADHAAR, a.group(1) + " " + a.group(2) + " " + a.group(3));
            }
        } else {
            out.remove(PAN);
        }
        Extraction x = new Extraction();
        x.values.putAll(out);
        for (Map.Entry<String, String> e : out.entrySet()) {
            List<OcrLine> src = sourceLines(e.getValue(), lines);
            x.sources.put(e.getKey(), src);
            x.confidence.put(e.getKey(), confidenceOf(src));
        }
        return x;
    }

    /**
     * The OCR lines a value was built from: lines whose text (look-alikes folded) is part of
     * the value; failing that, the lines that contain the value.
     */
    static List<OcrLine> sourceLines(String value, List<OcrLine> lines) {
        String v = IdCorrector.fold(value);
        List<OcrLine> parts = new ArrayList<>(), containers = new ArrayList<>();
        if (v.isEmpty()) return parts;
        for (OcrLine l : lines) {
            String t = IdCorrector.fold(l.text);
            if (t.isEmpty()) continue;
            if (t.length() >= Math.max(3, v.length() / 4) && v.contains(t)) parts.add(l);
            else if (t.contains(v)) containers.add(l);
        }
        if (!parts.isEmpty()) return parts;
        OcrLine best = null;
        for (OcrLine l : containers) if (best == null || l.confidence > best.confidence) best = l;
        List<OcrLine> one = new ArrayList<>();
        if (best != null) one.add(best);
        return one;
    }

    /** The weakest line wins; 0.5 if we can't tell where the value came from. */
    static float confidenceOf(List<OcrLine> src) {
        if (src.isEmpty()) return 0.5f;
        float c = 1f;
        for (OcrLine l : src) c = Math.min(c, l.confidence);
        return c;
    }

    private static String joinAll(List<OcrLine> lines) {
        StringBuilder b = new StringBuilder();
        for (OcrLine l : lines) b.append(l.text).append('\n');
        return b.toString();
    }

    /** Which field a line is a label for, or null. Longest matching label wins. */
    static String labelOf(OcrLine l) {
        String t = l.text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u0900-\\u097F ]", " ").replaceAll("\\s+", " ").trim();
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String[]> e : LABELS.entrySet()) {
            for (String lab : e.getValue()) {
                boolean hit = t.equals(lab) || t.startsWith(lab + " ") && t.length() <= lab.length() + 12;
                if (hit && lab.length() > bestLen) {
                    best = e.getKey();
                    bestLen = lab.length();
                }
            }
        }
        if (best != null) return best;
        // Only if nothing matched exactly: OCR slips such as "Valid tilI" (same length, very close).
        double bestScore = 0;
        for (Map.Entry<String, String[]> e : LABELS.entrySet()) {
            for (String lab : e.getValue()) {
                if (lab.length() < 6 || Math.abs(t.length() - lab.length()) > 2) continue;
                double sc = NameMatcher.jaroWinkler(t, lab);
                if (sc >= 0.93 && sc > bestScore) {
                    best = e.getKey();
                    bestScore = sc;
                }
            }
        }
        return best;
    }

    private static String labelled(String field, List<OcrLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            OcrLine label = lines.get(i);
            if (!field.equals(labelOf(label))) continue;
            // Same row, to the right.
            List<OcrLine> row = new ArrayList<>();
            for (OcrLine o : lines) {
                if (o != label && o.left > label.right && sameRow(label, o) && labelOf(o) == null) row.add(o);
            }
            if (!row.isEmpty()) {
                StringBuilder v = new StringBuilder(joinRow(row));
                OcrLine first = row.get(0);
                if (field.equals(ADDRESS)) appendContinuation(v, first, label, lines);
                return clean(v.toString());
            }
            // Directly below (label above value, as on ID cards).
            OcrLine below = null;
            for (OcrLine o : lines) {
                if (o.top >= label.bottom - 2 && o.top - label.bottom < 2.5 * label.height()
                        && Math.abs(o.left - label.left) < 3 * label.height() && labelOf(o) == null) {
                    if (below == null || o.top < below.top) below = o;
                }
            }
            if (below != null) return clean(below.text);
        }
        return null;
    }

    /** Address lines continue under the first value line until a row that starts with another label. */
    private static void appendContinuation(StringBuilder v, OcrLine firstValue, OcrLine label, List<OcrLine> lines) {
        OcrLine prev = firstValue;
        while (true) {
            OcrLine next = null;
            for (OcrLine o : lines) {
                if (o.top > prev.top + prev.height() / 2 && Math.abs(o.left - firstValue.left) < 2 * firstValue.height()
                        && labelOf(o) == null && (next == null || o.top < next.top)) {
                    next = o;
                }
            }
            if (next == null || next.top - prev.bottom > 3 * prev.height()) return;
            // Stop if another field's label sits on the left of this row.
            for (OcrLine o : lines) {
                String lab = labelOf(o);
                if (lab != null && !lab.equals(ADDRESS) && o.right < next.left && sameRow(o, next)) return;
            }
            String prevText = v.toString();
            char last = prevText.isEmpty() ? ' ' : prevText.charAt(prevText.length() - 1);
            char first = next.text.charAt(0);
            // "…Visakhapatnam, A" + "ndhra Pradesh" and "Kerala - 75" + "2083" were split mid-word.
            boolean glue = Character.isLetter(last) && Character.isLowerCase(first)
                    || Character.isDigit(last) && Character.isDigit(first) && !prevText.matches(".*\\b[0-9]{6}$");
            v.append(glue ? "" : " ").append(next.text);
            prev = next;
        }
    }

    private static boolean sameRow(OcrLine a, OcrLine b) {
        int tol = Math.max(a.height(), b.height()) * 6 / 10;
        return Math.abs(a.centerY() - b.centerY()) <= tol;
    }

    private static String joinRow(List<OcrLine> row) {
        Collections.sort(row, new Comparator<OcrLine>() {
            @Override public int compare(OcrLine a, OcrLine b) { return Integer.compare(a.left, b.left); }
        });
        StringBuilder b = new StringBuilder();
        for (OcrLine o : row) {
            if (b.length() > 0) b.append(' ');
            b.append(o.text);
        }
        return b.toString();
    }

    private static String clean(String s) { return s.replaceAll("\\s+", " ").trim(); }

    /**
     * Finds a GSTIN as printed (not repaired). A 15-character run that is already valid wins;
     * otherwise the first run that would fit the GSTIN pattern once look-alikes are swapped
     * for the character type each position needs. {@link IdCorrector#gstin} decides on repairs.
     */
    public static String locateGstin(String text) {
        if (text == null) return null;
        String plausible = null;
        for (String line : text.toUpperCase(Locale.ROOT).split("\n")) {
            String t = line.replaceAll("\\s", "");
            for (int i = 0; i + 15 <= t.length(); i++) {
                String raw = t.substring(i, i + 15);
                if (GstinValidator.isValid(raw)) return raw;
                if (plausible == null && GstinValidator.isValidFormat(fixGstin(raw))) plausible = raw;
            }
        }
        return plausible;
    }

    /** Positions 0-1, 7-10 are digits; 2-6, 11 letters; 13 is 'Z'. */
    static String fixGstin(String g) {
        char[] c = g.toCharArray();
        for (int i = 0; i < c.length; i++) {
            boolean digit = i < 2 || (i >= 7 && i <= 10);
            boolean letter = (i >= 2 && i <= 6) || i == 11;
            if (digit) c[i] = toDigit(c[i]);
            if (letter) c[i] = toLetter(c[i]);
            if (i == 13 && c[i] == '2') c[i] = 'Z';
        }
        return new String(c);
    }

    private static final Pattern UDYAM_TAIL = Pattern.compile(
            "(?:^|[^A-Z0-9])-?([A-Z0-9]{2})[-–—]([0-9OILSBZG]{2})[-–—]([0-9OILSBZG]{7})(?![A-Z0-9])");

    /**
     * Finds a Udyam number as printed (look-alikes not repaired). The stamp sometimes hides the
     * "UDYAM" prefix: "-MH-00-9516696" -> "UDYAM-MH-00-9516696" (the prefix is fixed text).
     */
    public static String locateUdyam(String text) {
        if (text == null) return null;
        String t = text.toUpperCase(Locale.ROOT).replaceAll("[ \\t]+", "");
        Matcher m = UDYAM_LOOSE.matcher(t);
        if (m.find()) return "UDYAM-" + m.group(1) + "-" + m.group(2) + "-" + m.group(3);
        Matcher tail = UDYAM_TAIL.matcher(t);
        while (tail.find()) {
            String u = "UDYAM-" + tail.group(1) + "-" + tail.group(2) + "-" + tail.group(3);
            if (UdyamValidator.isValidFormat(fitClasses(u, true))) return u;
        }
        return null;
    }

    /** Finds a PAN as printed: 10 characters with at least 2 digits that fit AAAAA9999A once look-alikes are swapped. */
    public static String locatePan(String text) {
        if (text == null) return null;
        Matcher m = PAN_TOKEN.matcher(text.toUpperCase(Locale.ROOT));
        String plausible = null;
        while (m.find()) {
            String raw = m.group();
            if (IdCorrector.isPanFormat(raw)) return raw;
            if (plausible == null && raw.replaceAll("[^0-9]", "").length() >= 2 && IdCorrector.isPanFormat(fitClasses(raw, false))) plausible = raw;
        }
        return plausible;
    }

    /** Swaps look-alikes so each position has the character type it needs (Udyam or PAN). Plausibility only. */
    static String fitClasses(String s, boolean udyam) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            boolean digit = udyam ? i >= 9 && i != 11 : i >= 5 && i <= 8;
            boolean letter = udyam ? i == 6 || i == 7 : i < 5 || i == 9;
            if (digit) c[i] = toDigit(c[i]);
            if (letter) c[i] = toLetter(c[i]);
        }
        return new String(c);
    }

    /** Legacy helper kept for tests: finds a Udyam number and swaps O for 0 in the digit groups. */
    public static String findUdyam(String text) {
        String u = locateUdyam(text);
        if (u == null) return null;
        String[] p = u.split("-");
        return p[0] + "-" + p[1] + "-" + p[2].replace('O', '0') + "-" + p[3].replace('O', '0');
    }

    private static char toDigit(char c) {
        switch (c) { case 'O': case 'D': case 'Q': return '0'; case 'I': case 'L': return '1';
            case 'S': return '5'; case 'B': return '8'; case 'Z': return '2'; case 'G': return '6'; default: return c; }
    }

    private static char toLetter(char c) {
        switch (c) { case '0': return 'O'; case '1': return 'I'; case '5': return 'S'; case '8': return 'B';
            case '2': return 'Z'; case '6': return 'G'; default: return c; }
    }
}
