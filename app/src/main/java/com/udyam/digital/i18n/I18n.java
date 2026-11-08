package com.udyam.digital.i18n;

import com.udyam.digital.model.Language;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every user-facing text comes from res/values-&lt;lang&gt;/strings*.xml, also for the pure-Java
 * engines (plan reasons, KYC checks, labels). On the phone the app installs a resource-backed
 * source (UdyamApp); in JVM tests the same XML files are read from disk (system property
 * "udyam.res", set in build.gradle), so tests see exactly the shipped text.
 *
 * Arguments can be {@link Msg} (a nested text, translated in the same language) and
 * {@link LocalDate} (formatted with that language's month names). Digits stay 0-9.
 */
public final class I18n {
    private I18n() {}

    /** Looks up one string (format pattern) by key in one language; null if missing. */
    public interface Source { String pattern(String key, Language language); }

    private static volatile Source source;

    public static void install(Source s) { source = s; }

    private static Source source() {
        Source s = source;
        if (s == null) {
            synchronized (I18n.class) {
                if (source == null) source = new XmlSource(defaultResDir());
                s = source;
            }
        }
        return s;
    }

    static File defaultResDir() {
        String p = System.getProperty("udyam.res");
        if (p != null) return new File(p);
        for (String c : new String[] {"src/main/res", "app/src/main/res"}) if (new File(c).isDirectory()) return new File(c);
        throw new IllegalStateException("No string resources: set I18n.install() or -Dudyam.res");
    }

    /** True if an English string exists for {@code key}. */
    public static boolean has(String key) { return source().pattern(key, Language.EN) != null; }

    /** The raw pattern (unformatted) in {@code l}, falling back to English; null if missing. */
    public static String pattern(String key, Language l) {
        String p = source().pattern(key, l);
        return p != null ? p : source().pattern(key, Language.EN);
    }

    /** A text to translate later, with its own arguments. */
    public static class Msg {
        public final String key;
        public final Object[] args;

        public Msg(String key, Object... args) {
            this.key = key;
            this.args = args;
        }

        public String in(Language l) { return get(l, key, args); }

        @Override public String toString() { return in(Language.EN); }
    }

    public static Msg msg(String key, Object... args) { return new Msg(key, args); }

    /** The text for {@code key} in {@code l} (English if that language lacks it), formatted with {@code args}. */
    public static String get(Language l, String key, Object... args) {
        String p = source().pattern(key, l);
        if (p == null && l != Language.EN) p = source().pattern(key, Language.EN);
        if (p == null) throw new IllegalArgumentException("Missing string: " + key);
        if (args == null || args.length == 0) return p.contains("%%") ? p.replace("%%", "%") : p;
        Object[] a = new Object[args.length];
        for (int i = 0; i < args.length; i++) a[i] = resolve(args[i], l);
        return String.format(locale(l), p, a);
    }

    private static Object resolve(Object o, Language l) {
        if (o instanceof Msg) return ((Msg) o).in(l);
        if (o instanceof LocalDate) return date((LocalDate) o, l);
        return o;
    }

    /** Locale for formatting: the language with Western digits (0-9) kept everywhere. */
    public static Locale locale(Language l) {
        return new Locale.Builder().setLanguage(l.tag).setRegion("IN").setUnicodeLocaleKeyword("nu", "latn").build();
    }

    private static final Map<Language, DateTimeFormatter> DATES = new EnumMap<>(Language.class);

    /** "26 June 2026", "26 जून 2026", "26 ஜூன் 2026": the locale's month names, Western digits. */
    public static synchronized String date(LocalDate d, Language l) {
        DateTimeFormatter f = DATES.get(l);
        if (f == null) {
            f = DateTimeFormatter.ofPattern("d MMMM yyyy", locale(l));
            DATES.put(l, f);
        }
        return d.format(f);
    }
}
