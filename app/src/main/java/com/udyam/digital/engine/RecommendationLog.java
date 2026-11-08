package com.udyam.digital.engine;

import com.udyam.digital.model.BusinessProfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PACK_CHOSEN and PROCEEDED_TO_KYC carry the pack name (YOUR_PLAN, BASIC, PREMIUM,
 * PREMIUM_PLUS) in the product_id column.
 *
 * Privacy-safe log of what the plan screen showed and what the owner did with it, used to
 * retrain the ranking offline (tools/train_recommender.py, step 6).
 *
 * Only coarse business facts are written: business type, segment, city tier, employee and
 * site bands, GSTIN yes/no. Never names, GSTIN numbers, phone numbers, cities or free text.
 * The file lives in app-private storage and rows older than 90 days are deleted on start-up.
 * Pure Java.
 */
public final class RecommendationLog {

    /** SHOWN = switched on when the plan appeared; SUGGESTED = shown OFF as "often add". */
    public enum Event { SHOWN, SUGGESTED, TOGGLED_ON, TOGGLED_OFF, PACK_CHOSEN, PROCEEDED_TO_KYC, ABANDONED }

    public static final long RETENTION_MS = 90L * 24 * 60 * 60 * 1000;
    public static final String HEADER =
            "session_id,timestamp,business_type,segment,city_tier,employee_band,sites_band,has_gstin,product_id,event";

    private final File file;

    public RecommendationLog(File file) { this.file = file; }

    public File file() { return file; }

    public synchronized void log(String sessionId, BusinessProfile p, String productId, Event event, long nowMs) {
        String row = String.join(",",
                csv(sessionId),
                Instant.ofEpochMilli(nowMs).toString(),
                csv(p.getBusinessType() == null ? "" : p.getBusinessType()),
                csv(p.getSegment().leadName),
                String.valueOf(p.getCityTier()),
                employeeBand(p.getEmployees()),
                sitesBand(p.getSites()),
                p.hasValidGstin() ? "Y" : "N",
                productId == null ? "" : productId,
                event.name());
        boolean fresh = !file.exists() || file.length() == 0;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            if (fresh) w.write(HEADER + "\n");
            w.write(row + "\n");
        } catch (IOException ignored) {
            // Logging must never break the plan screen.
        }
    }

    /** Deletes rows older than 90 days. Returns how many rows were removed. */
    public synchronized int prune(long nowMs) {
        if (!file.exists()) return 0;
        List<String> keep = new ArrayList<>();
        int removed = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header
            String l;
            while ((l = r.readLine()) != null) {
                String[] f = l.split(",", 3);
                long ts;
                try {
                    ts = Instant.parse(f[1]).toEpochMilli();
                } catch (RuntimeException e) {
                    ts = 0; // unreadable row: drop it
                }
                if (nowMs - ts <= RETENTION_MS) keep.add(l); else removed++;
            }
        } catch (IOException e) {
            return 0;
        }
        if (removed == 0) return 0;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            w.write(HEADER + "\n");
            for (String l : keep) w.write(l + "\n");
        } catch (IOException ignored) {
        }
        return removed;
    }

    public static String employeeBand(int n) {
        if (n <= 2) return "1-2";
        if (n <= 4) return "3-4";
        if (n <= 9) return "5-9";
        if (n <= 24) return "10-24";
        if (n <= 49) return "25-49";
        return "50+";
    }

    public static String sitesBand(int n) {
        if (n <= 1) return "1";
        if (n == 2) return "2";
        if (n <= 4) return "3-4";
        if (n <= 9) return "5-9";
        return "10+";
    }

    private static String csv(String s) {
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
