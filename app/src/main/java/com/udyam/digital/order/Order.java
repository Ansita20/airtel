package com.udyam.digital.order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One order (one active order per device). Pure Java; saved encrypted by OrderRepository. */
public final class Order {

    public static final int MAX_RESCHEDULES = 3;

    public final String id;
    public final List<OrderLine> lines = new ArrayList<>();
    public int monthlyTotal;
    public int setupTotal;
    public int contractMonths;
    public int cityTier = 2;
    public LocalDate created;

    /** Date each completed step was done. */
    public final Map<OrderStep, LocalDate> done = new EnumMap<>(OrderStep.class);

    // KYC
    public boolean kycUnderReview;
    public String reviewReason = "";

    // Payment
    public int paymentFailures;
    public String paymentMethod = "";

    // Technician visit
    public LocalDate visitDate;
    public VisitSlot visitSlot;
    public int reschedules;

    /** The date we promised the connection would be active. Late = today is after this. */
    public LocalDate promisedActive;

    /** Notifications already shown (so each one appears once). */
    public final Set<String> notified = new HashSet<>();

    public Order(String id) { this.id = id; }

    /** "UD-2026-4821" -> "UD-****-4821". */
    public String maskedId() {
        int dash = id.lastIndexOf('-');
        return dash < 0 ? id : "UD-****-" + id.substring(dash + 1);
    }

    public boolean isDone(OrderStep s) { return done.containsKey(s); }

    public LocalDate doneOn(OrderStep s) { return done.get(s); }

    /** First step that isn't done, or null when the order is active. */
    public OrderStep currentStep() {
        for (OrderStep s : OrderStep.values()) if (!done.containsKey(s)) return s;
        return null;
    }

    public OrderStep.Status status(OrderStep s) {
        if (done.containsKey(s)) return OrderStep.Status.DONE;
        return s == currentStep() ? OrderStep.Status.CURRENT : OrderStep.Status.UPCOMING;
    }

    public boolean needsVisit() {
        for (OrderLine l : lines) if (l.needsTechnician()) return true;
        return false;
    }

    public int doneCount() { return done.size(); }

    public List<OrderLine> lines() { return Collections.unmodifiableList(lines); }
}
