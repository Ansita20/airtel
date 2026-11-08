package com.udyam.digital.order;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Lateness against the date we promised. 1-7 days late: amber banner. More than 7: red
 * banner, "Talk to a person", and +40 on the help bot's escalation score. Pure Java.
 */
public final class Delay {

    public enum Level { NONE, LATE, VERY_LATE }

    public static final int VERY_LATE_AFTER_DAYS = 7;
    public static final int ESCALATION_POINTS = 40;
    public static final String ESCALATION_REASON = "installation delayed";
    /** Re-estimate for a late order: two days from today. */
    public static final int NEW_DATE_DAYS = 2;

    public final Level level;
    public final int daysLate;
    public final LocalDate newDate;
    /** The technician visit date has passed but the visit isn't marked done. */
    public final boolean visitMissed;

    private Delay(Level level, int daysLate, LocalDate newDate, boolean visitMissed) {
        this.level = level;
        this.daysLate = daysLate;
        this.newDate = newDate;
        this.visitMissed = visitMissed;
    }

    public static Delay of(Order o, LocalDate today) {
        boolean missed = o.visitDate != null && today.isAfter(o.visitDate) && !o.isDone(OrderStep.TECHNICIAN_VISIT);
        if (o.isDone(OrderStep.ACTIVE) || o.promisedActive == null || OrderTimeline.paused(o) || !today.isAfter(o.promisedActive)) {
            return new Delay(Level.NONE, 0, null, missed);
        }
        int late = (int) ChronoUnit.DAYS.between(o.promisedActive, today);
        Level lv = late > VERY_LATE_AFTER_DAYS ? Level.VERY_LATE : Level.LATE;
        return new Delay(lv, late, today.plusDays(NEW_DATE_DAYS), missed);
    }

    /** Points for the help bot: +40 only when more than 7 days late. */
    public int escalationPoints() { return level == Level.VERY_LATE ? ESCALATION_POINTS : 0; }
}
