package com.udyam.digital.security;

import java.time.LocalDate;

/**
 * How long each kind of data is kept. Pure Java (unit-tested at day 29 / 30 boundaries); the
 * daily {@link RetentionWorker} applies it.
 *
 * <ul>
 *   <li>KYC documents (photos + extracted fields): deleted {@value #DOCUMENT_DAYS} days after the
 *       verdict - approved, or not approved (manual review). Deleting also destroys the
 *       application's key, so any leftover copy is unreadable.</li>
 *   <li>Applications never submitted: deleted {@value #ABANDONED_DAYS} days after the last change.</li>
 *   <li>Verification result + masked IDs: kept for the contract period (from the order; default
 *       {@value #DEFAULT_CONTRACT_MONTHS} months), then deleted.</li>
 *   <li>Recommendation event log: {@value #EVENT_LOG_DAYS} days.</li>
 * </ul>
 */
public final class RetentionPolicy {
    private RetentionPolicy() {}

    public static final int DOCUMENT_DAYS = 30;
    public static final int ABANDONED_DAYS = 90;
    public static final int EVENT_LOG_DAYS = 90;
    public static final int DEFAULT_CONTRACT_MONTHS = 12;

    /**
     * @param decidedOn   date of the verdict, or null if never submitted
     * @param lastChanged date of the last change (for never-submitted applications)
     */
    public static boolean deleteDocuments(LocalDate decidedOn, LocalDate lastChanged, LocalDate today) {
        if (decidedOn != null) return !today.isBefore(decidedOn.plusDays(DOCUMENT_DAYS));
        return lastChanged != null && !today.isBefore(lastChanged.plusDays(ABANDONED_DAYS));
    }

    /** Last day the result + masked IDs may be kept. */
    public static LocalDate keepResultUntil(LocalDate decidedOn, int contractMonths) {
        return decidedOn.plusMonths(contractMonths > 0 ? contractMonths : DEFAULT_CONTRACT_MONTHS);
    }

    public static boolean deleteResult(LocalDate keepUntil, LocalDate today) {
        return keepUntil != null && today.isAfter(keepUntil);
    }

    /** Event log rows at or older than 90 days are removed. */
    public static long eventLogCutoffMs(long nowMs) {
        return nowMs - EVENT_LOG_DAYS * 24L * 60 * 60 * 1000;
    }
}
