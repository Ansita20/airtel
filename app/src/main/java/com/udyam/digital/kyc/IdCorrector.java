package com.udyam.digital.kyc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checksum- and registry-guided repair of identifiers read by OCR (GSTIN, Udyam number, PAN).
 *
 * Only if the value as read is invalid, every character is allowed to swap with its usual OCR
 * look-alike (O/0, I/1, L/1, S/5, B/8, Z/2, G/6), and only to a character the format allows
 * at that position. A repair is accepted only if EXACTLY ONE candidate passes the format (and
 * the GSTIN checksum) AND exists in the registry. Otherwise the value is left as read, so the
 * owner is asked. Names and dates are never auto-corrected. Pure Java.
 */
public final class IdCorrector {
    private IdCorrector() {}

    /** Registry lookups used to accept a repair. */
    public interface Registry {
        boolean hasGstin(String gstin);
        boolean hasUdyam(String udyam);
        boolean hasPan(String pan);
    }

    /** Outcome of one repair attempt. */
    public static final class Result {
        /** Value to use: the repair if accepted, else the value as read. */
        public final String value;
        public final String original;
        public final boolean corrected;
        /** Candidates that passed format + checksum + registry (1 = accepted). */
        public final int matches;
        /** e.g. "pos 9: O→0". Empty if not corrected. */
        public final String note;

        Result(String value, String original, boolean corrected, int matches, String note) {
            this.value = value;
            this.original = original;
            this.corrected = corrected;
            this.matches = matches;
            this.note = note;
        }

        @Override public String toString() { return corrected ? original + " -> " + value + " (" + note + ")" : value + " (unchanged, " + matches + " matches)"; }
    }

    /**
     * The usual look-alikes, plus 0 read for D, Q or U: seen on the specimen pack (K05's GSTIN
     * prints "U" where ML Kit reads "0"). Extra pairs are safe because a repair still needs a
     * unique checksum + registry match.
     */
    private static final String[][] PAIRS = {{"O", "0"}, {"I", "1"}, {"L", "1"}, {"S", "5"}, {"B", "8"}, {"Z", "2"}, {"G", "6"},
            {"D", "0"}, {"Q", "0"}, {"U", "0"}};
    /** Safety cap on the number of candidates tried. */
    static final int MAX_CANDIDATES = 4096;

    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    /** Characters OCR often confuses with {@code c}. */
    static String partners(char c) {
        StringBuilder b = new StringBuilder();
        String s = String.valueOf(c);
        for (String[] p : PAIRS) {
            if (p[0].equals(s) && b.indexOf(p[1]) < 0) b.append(p[1]);
            if (p[1].equals(s) && b.indexOf(p[0]) < 0) b.append(p[0]);
        }
        return b.toString();
    }

    private interface Slots { boolean allowed(int pos, char c); }
    private interface Accept { boolean ok(String candidate); }

    // ---------------------------------------------------------------- GSTIN
    /** 0-1 digits, 2-6 letters, 7-10 digits, 11 letter, 12 1-9/A-Z, 13 'Z', 14 any. */
    static boolean gstinAllows(int pos, char c) {
        boolean digit = c >= '0' && c <= '9', letter = c >= 'A' && c <= 'Z';
        if (pos < 2 || (pos >= 7 && pos <= 10)) return digit;
        if (pos <= 6 || pos == 11) return letter;
        if (pos == 12) return letter || (digit && c != '0');
        if (pos == 13) return c == 'Z';
        return digit || letter;
    }

    public static Result gstin(String raw, Registry reg) {
        String r = GstinValidator.normalize(raw);
        if (r.length() != 15 || GstinValidator.isValid(r)) return new Result(r, r, false, 0, "");
        return repair(r, IdCorrector::gstinAllows, c -> GstinValidator.isValid(c) && reg != null && reg.hasGstin(c));
    }

    // ---------------------------------------------------------------- Udyam
    /** "UDYAM-" fixed, 6-7 state letters, 9-10 digits, 12-18 digits. */
    static boolean udyamAllows(int pos, char c) {
        if (pos < 6) return c == "UDYAM-".charAt(pos);
        if (pos == 8 || pos == 11) return c == '-';
        if (pos <= 7) return c >= 'A' && c <= 'Z';
        return c >= '0' && c <= '9';
    }

    public static Result udyam(String raw, Registry reg) {
        String r = UdyamValidator.normalize(raw);
        if (r.length() != 19 || UdyamValidator.isValidFormat(r)) return new Result(r, r, false, 0, "");
        return repair(r, IdCorrector::udyamAllows, c -> UdyamValidator.isValidFormat(c) && reg != null && reg.hasUdyam(c));
    }

    // ---------------------------------------------------------------- PAN
    static boolean panAllows(int pos, char c) {
        return pos < 5 || pos == 9 ? c >= 'A' && c <= 'Z' : c >= '0' && c <= '9';
    }

    public static boolean isPanFormat(String p) { return p != null && PAN.matcher(p).matches(); }

    public static Result pan(String raw, Registry reg) {
        String r = raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (r.length() != 10 || isPanFormat(r)) return new Result(r, r, false, 0, "");
        return repair(r, IdCorrector::panAllows, c -> isPanFormat(c) && reg != null && reg.hasPan(c));
    }

    // ---------------------------------------------------------------- core
    private static Result repair(String raw, Slots slots, Accept accept) {
        List<String> options = new ArrayList<>();
        long total = 1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            StringBuilder o = new StringBuilder();
            if (slots.allowed(i, c)) o.append(c);
            for (char p : partners(c).toCharArray()) if (slots.allowed(i, p)) o.append(p);
            if (o.length() == 0) return new Result(raw, raw, false, 0, ""); // nothing plausible here
            options.add(o.toString());
            total *= o.length();
            if (total > MAX_CANDIDATES) return new Result(raw, raw, false, 0, "");
        }
        List<String> hits = new ArrayList<>();
        char[] cur = new char[raw.length()];
        enumerate(options, 0, cur, accept, hits);
        if (hits.size() != 1) return new Result(raw, raw, false, hits.size(), "");
        String fixed = hits.get(0);
        StringBuilder note = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) != fixed.charAt(i)) {
                if (note.length() > 0) note.append(", ");
                note.append("pos ").append(i + 1).append(": ").append(raw.charAt(i)).append('→').append(fixed.charAt(i));
            }
        }
        return new Result(fixed, raw, true, 1, note.toString());
    }

    private static void enumerate(List<String> options, int i, char[] cur, Accept accept, List<String> hits) {
        if (hits.size() > 1) return; // already ambiguous
        if (i == options.size()) {
            String c = new String(cur);
            if (accept.ok(c)) hits.add(c);
            return;
        }
        for (char ch : options.get(i).toCharArray()) {
            cur[i] = ch;
            enumerate(options, i + 1, cur, accept, hits);
        }
    }

    /**
     * Upper-case letters and digits only, with look-alikes folded together (O/D/Q/U->0, I/L->1,
     * S->5, B->8, Z->2, G->6). Two OCR readings that differ only by look-alikes compare equal.
     */
    public static String fold(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
            if (!Character.isLetterOrDigit(c)) continue;
            switch (c) {
                case 'O': case 'D': case 'Q': case 'U': b.append('0'); break;
                case 'I': case 'L': b.append('1'); break;
                case 'S': b.append('5'); break;
                case 'B': b.append('8'); break;
                case 'Z': b.append('2'); break;
                case 'G': b.append('6'); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }
}
