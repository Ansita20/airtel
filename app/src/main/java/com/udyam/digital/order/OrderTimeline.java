package com.udyam.digital.order;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Expected dates. Pure Java.
 *
 *   product ready = KYC complete date + typical_install_days (catalogue)
 *                   + 2 days in a Tier 2 city for products a technician installs
 *                   and never before the technician visit (technician products) or payment
 *   order ACTIVE  = the latest product ready date
 *
 * SIMs and cloud products need no technician, so they can be active before the fibre.
 * Before KYC is complete the dates are estimates from today; while KYC is under manual
 * review they are paused (null).
 */
public final class OrderTimeline {
    private OrderTimeline() {}

    public static final int TIER2_BUFFER_DAYS = 2;

    /** Installed at the site by a technician. */
    private static final Set<String> TECHNICIAN = new HashSet<>(Arrays.asList(
            "P01", "P02", "P03", "P04", "P05", "P06", "P09", "P10", "P21", "P22", "P23", "P24"));

    public static boolean needsTechnician(String productId) { return TECHNICIAN.contains(productId); }

    /** True when expected dates can't be given (KYC under manual review). */
    public static boolean paused(Order o) { return o.kycUnderReview && !o.isDone(OrderStep.KYC_COMPLETE); }

    public static boolean tier2Buffer(Order o, OrderLine l) { return o.cityTier >= 2 && l.needsTechnician(); }

    public static LocalDate productReady(Order o, OrderLine l, LocalDate today) {
        if (paused(o)) return null;
        LocalDate kyc = o.isDone(OrderStep.KYC_COMPLETE) ? o.doneOn(OrderStep.KYC_COMPLETE) : today;
        LocalDate d = kyc.plusDays(l.installDays + (tier2Buffer(o, l) ? TIER2_BUFFER_DAYS : 0));
        LocalDate paid = o.doneOn(OrderStep.PAYMENT_DONE);
        if (paid != null && paid.isAfter(d)) d = paid;
        if (l.needsTechnician()) {
            LocalDate visit = o.isDone(OrderStep.TECHNICIAN_VISIT) ? o.doneOn(OrderStep.TECHNICIAN_VISIT) : o.visitDate;
            if (visit != null && visit.isAfter(d)) d = visit;
        }
        return d;
    }

    /** When the whole order is active: the latest product ready date (or the actual date once active). */
    public static LocalDate activeDate(Order o, LocalDate today) {
        if (o.isDone(OrderStep.ACTIVE)) return o.doneOn(OrderStep.ACTIVE);
        if (paused(o)) return null;
        LocalDate latest = null;
        for (OrderLine l : o.lines) {
            LocalDate d = productReady(o, l, today);
            if (d != null && (latest == null || d.isAfter(latest))) latest = d;
        }
        return latest == null ? today : latest;
    }

    /** Ready date of the main internet line (fibre / AirFiber / leased line), or null if none. */
    public static LocalDate connectionReady(Order o, LocalDate today) {
        LocalDate best = null;
        for (OrderLine l : o.lines) {
            if (!l.productId.matches("P0[1-6]")) continue;
            LocalDate d = productReady(o, l, today);
            if (d != null && (best == null || d.isAfter(best))) best = d;
        }
        return best;
    }

    /** Expected (or actual) date of a step; null when paused. */
    public static LocalDate stepDate(Order o, OrderStep s, LocalDate today) {
        if (o.isDone(s)) return o.doneOn(s);
        if (paused(o) && s != OrderStep.PLAN_CHOSEN) return null;
        switch (s) {
            case PLAN_CHOSEN: return o.created;
            case KYC_COMPLETE: return today;
            case PAYMENT_DONE: return later(o.doneOn(OrderStep.KYC_COMPLETE), today);
            case INSTALL_BOOKED: return later(o.doneOn(OrderStep.PAYMENT_DONE), today);
            case TECHNICIAN_VISIT: return o.visitDate != null ? o.visitDate : later(o.doneOn(OrderStep.INSTALL_BOOKED), today).plusDays(1);
            default: return activeDate(o, today);
        }
    }

    private static LocalDate later(LocalDate a, LocalDate b) { return a == null || b.isAfter(a) ? b : a; }

    /** Per-product state for the "Your products" list. */
    public enum ProductState { ACTIVE, READY_BY, PAUSED }

    public static ProductState productState(Order o, OrderLine l, LocalDate today) {
        if (paused(o)) return ProductState.PAUSED;
        LocalDate ready = productReady(o, l, today);
        boolean paid = o.isDone(OrderStep.PAYMENT_DONE);
        boolean visited = !l.needsTechnician() || o.isDone(OrderStep.TECHNICIAN_VISIT);
        return paid && visited && !today.isBefore(ready) ? ProductState.ACTIVE : ProductState.READY_BY;
    }
}
