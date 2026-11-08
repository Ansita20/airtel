package com.udyam.digital.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The KYC access log: who or what touched which document slot, and when. It can't hold
 * document contents by construction: actor and action are fixed lists, the application id and
 * slot are short codes, and the optional detail is limited to check codes (e.g. "C13 FAIL").
 * Pure Java; {@link SecureAuditLog} stores it encrypted.
 */
public final class AccessLog {

    public enum Actor { OWNER, APP_OCR, APP_STORE, RETENTION_JOB, OWNER_WITHDRAW, RULE_ENGINE, SECURITY_CHECK, DEBUG, PARTNER, FIELD_SALES }

    public enum Action { SAVED, VIEWED, VIEW_DENIED, READ_FOR_CHECKS, CHECKED, DELETED, KEY_DESTROYED, INTEGRITY_WARNING, RETENTION_RUN,
        PARTNER_LOGIN, PARTNER_LOGIN_FAILED, LEAD_OPENED, CALLED, WHATSAPP, MARKED_DONE, ASSIGNED }

    public static final int MAX_ENTRIES = 2000;

    /** The only words allowed in an entry's detail. */
    static final Set<String> WORDS = new HashSet<>(Arrays.asList("PASS", "WARN", "FAIL", "ALL", "AS", "OF", "APPS",
            "RESULTS", "NO", "SCREEN", "LOCK", "CANCELLED", "ROOTED", "TEST-KEYS", "BUILD", "DEBUGGABLE", "RELEASE"));

    public static final class Entry {
        public final String time, appId, slot, detail;
        public final Actor actor;
        public final Action action;

        public Entry(String time, Actor actor, Action action, String appId, String slot, String detail) {
            this.time = time;
            this.actor = actor;
            this.action = action;
            this.appId = code(appId, 16).replace(" ", "");
            this.slot = code(slot, 20).replace(" ", "");
            this.detail = detail(detail);
        }

        /** Upper-case letters, digits, space and _ : - only, cut to {@code max}: never free text. */
        static String code(String s, int max) {
            if (s == null) return "";
            String c = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 _:,-]", "");
            return c.length() > max ? c.substring(0, max) : c;
        }

        /** Only check codes, results, numbers, dates and fixed words survive; anything else becomes "?". */
        static String detail(String s) {
            String c = code(s, 200);
            if (c.isEmpty()) return "";
            StringBuilder b = new StringBuilder();
            for (String t : c.split("((?<=[ ,:])|(?=[ ,:]))")) {
                boolean sep = t.matches("[ ,:]");
                boolean ok = sep || t.matches("C[0-9]{2}|[0-9]+|[0-9]{4}-[0-9]{2}-[0-9]{2}") || WORDS.contains(t);
                b.append(ok ? t : "?");
            }
            String d = b.toString();
            return d.length() > 120 ? d.substring(0, 120) : d;
        }

        String line() { return time + "|" + actor + "|" + action + "|" + appId + "|" + slot + "|" + detail; }

        static Entry parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 6) return null;
            try {
                return new Entry(f[0], Actor.valueOf(f[1]), Action.valueOf(f[2]), f[3], f[4], f[5]);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Override public String toString() {
            return time + "  " + actor + "  " + action + (appId.isEmpty() ? "" : "  app " + appId)
                    + (slot.isEmpty() ? "" : "  " + slot) + (detail.isEmpty() ? "" : "  " + detail);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(Entry e) {
        entries.add(e);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
    }

    public List<Entry> entries() { return Collections.unmodifiableList(entries); }

    public String serialize() {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries) b.append(e.line()).append('\n');
        return b.toString();
    }

    public static AccessLog parse(String text) {
        AccessLog log = new AccessLog();
        if (text == null) return log;
        for (String l : text.split("\n")) {
            Entry e = Entry.parse(l);
            if (e != null) log.add(e);
        }
        return log;
    }
}
