package com.udyam.digital.order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything that changes an order. Pure Java: each call returns the steps it completed,
 * so the app can show a notification for each. Dates come from the caller (AppClock).
 */
public final class OrderService {
    private OrderService() {}

    public enum PayResult { SUCCESS, FAILED, NOT_READY }

    public enum BookResult { BOOKED, RESCHEDULED, LIMIT_REACHED, NOT_READY, NOT_AVAILABLE }

    /** "Choose this plan" + terms accepted. */
    public static Order create(String id, List<OrderLine> lines, int contractMonths, int cityTier, LocalDate today) {
        Order o = new Order(id);
        o.lines.addAll(lines);
        for (OrderLine l : lines) {
            o.monthlyTotal += l.monthlyInr;
            o.setupTotal += l.setupInr;
        }
        o.contractMonths = contractMonths;
        o.cityTier = cityTier;
        o.created = today;
        o.done.put(OrderStep.PLAN_CHOSEN, today);
        return o;
    }

    public static List<OrderStep> onKycApproved(Order o, LocalDate today) {
        List<OrderStep> out = new ArrayList<>();
        o.kycUnderReview = false;
        o.reviewReason = "";
        if (!o.isDone(OrderStep.KYC_COMPLETE)) {
            o.done.put(OrderStep.KYC_COMPLETE, today);
            out.add(OrderStep.KYC_COMPLETE);
        }
        o.promisedActive = OrderTimeline.activeDate(o, today);
        return out;
    }

    /** Manual review: dates are paused and the tracker shows the reason. */
    public static void onKycReview(Order o, String reason) {
        if (o.isDone(OrderStep.KYC_COMPLETE)) return;
        o.kycUnderReview = true;
        o.reviewReason = reason == null ? "" : reason;
        o.promisedActive = null;
    }

    /** Mock payment of setup + first month. */
    public static PayResult pay(Order o, String method, boolean fail, LocalDate today, List<OrderStep> completed) {
        if (!o.isDone(OrderStep.KYC_COMPLETE) || o.isDone(OrderStep.PAYMENT_DONE)) return PayResult.NOT_READY;
        if (fail) {
            o.paymentFailures++;
            return PayResult.FAILED;
        }
        o.paymentMethod = method;
        o.done.put(OrderStep.PAYMENT_DONE, today);
        completed.add(OrderStep.PAYMENT_DONE);
        if (!o.needsVisit()) {
            // SIMs and cloud only: nothing to install on site.
            o.done.put(OrderStep.INSTALL_BOOKED, today);
            o.done.put(OrderStep.TECHNICIAN_VISIT, today);
            completed.add(OrderStep.INSTALL_BOOKED);
            completed.add(OrderStep.TECHNICIAN_VISIT);
        }
        o.promisedActive = OrderTimeline.activeDate(o, today);
        return PayResult.SUCCESS;
    }

    /** Book or reschedule the technician (max 3 reschedules). Updates the promised date. */
    public static BookResult book(Order o, LocalDate date, VisitSlot slot, LocalDate today, List<OrderStep> completed) {
        if (!o.isDone(OrderStep.PAYMENT_DONE) || o.isDone(OrderStep.TECHNICIAN_VISIT)) return BookResult.NOT_READY;
        boolean reschedule = o.visitDate != null;
        // The limit comes first: after 3 changes the owner is sent to a person, whatever slot they tap.
        if (reschedule && o.reschedules >= Order.MAX_RESCHEDULES) return BookResult.LIMIT_REACHED;
        boolean offered = false;
        for (SlotPlanner.Slot s : SlotPlanner.available(today)) offered |= s.date.equals(date) && s.time == slot;
        if (!offered) return BookResult.NOT_AVAILABLE;
        if (reschedule) o.reschedules++;
        o.visitDate = date;
        o.visitSlot = slot;
        if (!o.isDone(OrderStep.INSTALL_BOOKED)) {
            o.done.put(OrderStep.INSTALL_BOOKED, today);
            completed.add(OrderStep.INSTALL_BOOKED);
        }
        o.promisedActive = OrderTimeline.activeDate(o, today);
        return reschedule ? BookResult.RESCHEDULED : BookResult.BOOKED;
    }

    /** A missed visit is rebooked without counting as a reschedule. */
    public static BookResult rebookMissed(Order o, LocalDate date, VisitSlot slot, LocalDate today, List<OrderStep> completed) {
        if (o.visitDate != null && today.isAfter(o.visitDate) && !o.isDone(OrderStep.TECHNICIAN_VISIT)) {
            int keep = o.reschedules;
            o.visitDate = null;
            BookResult r = book(o, date, slot, today, completed);
            o.reschedules = keep;
            return r == BookResult.BOOKED ? BookResult.RESCHEDULED : r;
        }
        return book(o, date, slot, today, completed);
    }

    /** Technician came. */
    public static List<OrderStep> visitDone(Order o, LocalDate today) {
        List<OrderStep> out = new ArrayList<>();
        if (o.isDone(OrderStep.INSTALL_BOOKED) && !o.isDone(OrderStep.TECHNICIAN_VISIT)) {
            o.done.put(OrderStep.TECHNICIAN_VISIT, today);
            out.add(OrderStep.TECHNICIAN_VISIT);
        }
        out.addAll(tick(o, today));
        return out;
    }

    /** Called whenever the app looks at the order: goes ACTIVE once every product is ready. */
    public static List<OrderStep> tick(Order o, LocalDate today) {
        List<OrderStep> out = new ArrayList<>();
        if (!o.isDone(OrderStep.ACTIVE) && o.isDone(OrderStep.TECHNICIAN_VISIT)) {
            LocalDate active = OrderTimeline.activeDate(o, today);
            if (active != null && !today.isBefore(active)) {
                o.done.put(OrderStep.ACTIVE, active);
                out.add(OrderStep.ACTIVE);
            }
        }
        return out;
    }

    /** Debug "Complete next step". */
    public static List<OrderStep> completeNextStep(Order o, LocalDate today) {
        List<OrderStep> out = new ArrayList<>();
        OrderStep s = o.currentStep();
        if (s == null) return out;
        switch (s) {
            case KYC_COMPLETE: out.addAll(onKycApproved(o, today)); break;
            case PAYMENT_DONE: pay(o, "Demo", false, today, out); break;
            case INSTALL_BOOKED: {
                SlotPlanner.Slot first = SlotPlanner.available(today).get(0);
                book(o, first.date, first.time, today, out);
                break;
            }
            case TECHNICIAN_VISIT: out.addAll(visitDone(o, today)); break;
            case ACTIVE:
                o.done.put(OrderStep.ACTIVE, today);
                out.add(OrderStep.ACTIVE);
                break;
            default: break;
        }
        return out;
    }
}
