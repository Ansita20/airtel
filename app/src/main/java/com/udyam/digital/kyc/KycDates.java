package com.udyam.digital.kyc;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date parsing for documents and the date rules, all against {@link KycClock#TODAY}. */
public final class KycDates {
    private KycDates() {}

    public static final int ADDRESS_PROOF_MAX_DAYS = 90;
    public static final int ADULT_AGE = 18;

    private static final DateTimeFormatter[] DAY_FORMATS = {
            DateTimeFormatter.ofPattern("uuuu-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            ci("d MMM uuuu"), ci("d MMMM uuuu"), ci("d-MMM-uuuu"),
    };
    private static final DateTimeFormatter[] MONTH_FORMATS = {ci("MMM uuuu"), ci("MMMM uuuu"), ci("MMM-uuuu")};

    private static DateTimeFormatter ci(String p) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH);
    }

    private static final Pattern CANDIDATE = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}|\\d{1,2}[ -][A-Za-z]{3,9}[ -]\\d{4}|[A-Za-z]{3,9}[ -]\\d{4})");

    /** Devanagari digits (०-९) to 0-9; Hindi documents often print dates this way. */
    public static String westernDigits(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= '\u0966' && c <= '\u096F' ? (char) ('0' + (c - '\u0966')) : c);
        return b.toString();
    }

    private static final Pattern NUMERIC_DMY = Pattern.compile("(?<![0-9])(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})(?![0-9])");

    /**
     * True when a numeric date could be read either way round, e.g. 03/04/2026 (3 April or
     * 4 March). Day-first is the Indian convention, but we ask the owner instead of guessing.
     */
    public static boolean isAmbiguous(String text) {
        if (text == null) return false;
        Matcher m = NUMERIC_DMY.matcher(westernDigits(text));
        if (!m.find()) return false;
        int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
        return a != b && a >= 1 && a <= 12 && b >= 1 && b <= 12;
    }

    /** The two readings of an ambiguous date: {day-first, month-first}. Null if not ambiguous. */
    public static LocalDate[] readings(String text) {
        if (!isAmbiguous(text)) return null;
        Matcher m = NUMERIC_DMY.matcher(westernDigits(text));
        m.find();
        int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2)), y = Integer.parseInt(m.group(3));
        return new LocalDate[] {LocalDate.of(y, b, a), LocalDate.of(y, a, b)};
    }

    /**
     * Parses yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy, dd MMM yyyy, or MMM yyyy (taken as
     * the 1st of the month), also with Devanagari digits. Finds the first date inside a longer
     * string. Null if none. Ambiguous numeric dates are read day-first; see {@link #isAmbiguous}.
     */
    public static LocalDate parse(String text) {
        if (text == null) return null;
        Matcher m = CANDIDATE.matcher(westernDigits(text));
        while (m.find()) {
            String s = m.group(1).replaceAll("\\s+", " ");
            for (DateTimeFormatter f : DAY_FORMATS) {
                try { return LocalDate.parse(s, f); } catch (DateTimeParseException ignored) { }
            }
            for (DateTimeFormatter f : MONTH_FORMATS) {
                try { return YearMonth.parse(s, f).atDay(1); } catch (DateTimeParseException ignored) { }
            }
        }
        return null;
    }

    /** Address proof: dated within 90 days before today, not in the future. Exactly 90 days = OK. */
    public static boolean isRecentAddressProof(LocalDate billDate, LocalDate today) {
        if (billDate == null || billDate.isAfter(today)) return false;
        return ChronoUnit.DAYS.between(billDate, today) <= ADDRESS_PROOF_MAX_DAYS;
    }

    public static long daysBefore(LocalDate d, LocalDate today) { return ChronoUnit.DAYS.between(d, today); }

    /** ID still valid today (expiring today is still valid). */
    public static boolean notExpired(LocalDate expiry, LocalDate today) { return !expiry.isBefore(today); }

    /** 18 or older on {@code today}. */
    public static boolean isAdult(LocalDate dob, LocalDate today) {
        return !dob.isAfter(today) && Period.between(dob, today).getYears() >= ADULT_AGE;
    }
}
