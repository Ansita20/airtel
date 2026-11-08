package com.udyam.digital.order;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class OrderTest {

    static final LocalDate TODAY = LocalDate.of(2026, 7, 15); // a Wednesday

    static OrderLine fibre100() { return new OrderLine("P01", "Biz Fibre 100", 1, 999, 0, 5); }
    static OrderLine fibre1g() { return new OrderLine("P03", "Biz Fibre 1G", 1, 3999, 0, 7); }
    static OrderLine sims(int n) { return new OrderLine("P07", "Business Postpaid 299", n, 299 * n, 0, 1); }
    static OrderLine sdwan() { return new OrderLine("P10", "Managed SD-WAN", 3, 7500, 15000, 14); }
    static OrderLine leased() { return new OrderLine("P05", "Internet Leased Line 20", 1, 8000, 10000, 21); }
    static OrderLine email() { return new OrderLine("P16", "Cloud Email & Office Suite", 4, 500, 0, 1); }

    static Order order(int tier, OrderLine... lines) {
        return OrderService.create("UD-2026-4821", Arrays.asList(lines), 12, tier, TODAY);
    }

    static Order approvedAndPaid(int tier, OrderLine... lines) {
        Order o = order(tier, lines);
        OrderService.onKycApproved(o, TODAY);
        assertEquals(OrderService.PayResult.SUCCESS, OrderService.pay(o, "UPI", false, TODAY, new ArrayList<>()));
        return o;
    }

    // ----------------------------------------------------------------- timeline
    @Test public void productDatesFollowTheCatalogue() {
        Order o = order(1, fibre100(), sims(2), fibre1g(), sdwan(), leased());
        OrderService.onKycApproved(o, TODAY);
        assertEquals(TODAY.plusDays(5), OrderTimeline.productReady(o, o.lines.get(0), TODAY));
        assertEquals(TODAY.plusDays(1), OrderTimeline.productReady(o, o.lines.get(1), TODAY));
        assertEquals(TODAY.plusDays(7), OrderTimeline.productReady(o, o.lines.get(2), TODAY));
        assertEquals(TODAY.plusDays(14), OrderTimeline.productReady(o, o.lines.get(3), TODAY));
        assertEquals(TODAY.plusDays(21), OrderTimeline.productReady(o, o.lines.get(4), TODAY));
        assertEquals("overall = latest product", TODAY.plusDays(21), OrderTimeline.activeDate(o, TODAY));
    }

    @Test public void tier2AddsTwoDaysForTechnicianProductsOnly() {
        Order o = order(2, fibre100(), sims(2));
        OrderService.onKycApproved(o, TODAY);
        assertEquals(TODAY.plusDays(7), OrderTimeline.productReady(o, o.lines.get(0), TODAY)); // 5 + 2
        assertEquals(TODAY.plusDays(1), OrderTimeline.productReady(o, o.lines.get(1), TODAY)); // SIMs: no buffer
        assertTrue(OrderTimeline.tier2Buffer(o, o.lines.get(0)));
        assertEquals(TODAY.plusDays(7), OrderTimeline.activeDate(o, TODAY));
    }

    @Test public void simsGoActiveBeforeTheFibre() {
        Order o = approvedAndPaid(1, fibre100(), sims(2), email());
        List<OrderStep> c = new ArrayList<>();
        OrderService.book(o, TODAY.plusDays(3), VisitSlot.MORNING, TODAY, c);
        LocalDate day1 = TODAY.plusDays(1);
        assertEquals(OrderTimeline.ProductState.ACTIVE, OrderTimeline.productState(o, o.lines.get(1), day1));
        assertEquals(OrderTimeline.ProductState.ACTIVE, OrderTimeline.productState(o, o.lines.get(2), day1));
        assertEquals(OrderTimeline.ProductState.READY_BY, OrderTimeline.productState(o, o.lines.get(0), day1));
    }

    @Test public void simOnlyOrderNeedsNoVisit() {
        Order o = approvedAndPaid(2, sims(3));
        assertTrue(o.isDone(OrderStep.INSTALL_BOOKED));
        assertTrue(o.isDone(OrderStep.TECHNICIAN_VISIT));
        assertEquals(OrderStep.ACTIVE, o.currentStep());
        assertTrue(OrderService.tick(o, TODAY.plusDays(1)).contains(OrderStep.ACTIVE));
    }

    @Test public void kycUnderReviewPausesDates() {
        Order o = order(1, fibre100());
        OrderService.onKycReview(o, "Name on the certificate doesn't match");
        assertNull(OrderTimeline.activeDate(o, TODAY));
        assertEquals(OrderTimeline.ProductState.PAUSED, OrderTimeline.productState(o, o.lines.get(0), TODAY));
        assertEquals(OrderStep.KYC_COMPLETE, o.currentStep());
        assertEquals(Delay.Level.NONE, Delay.of(o, TODAY.plusDays(30)).level);
    }

    // ----------------------------------------------------------------- delays
    private static Delay lateBy(int days) {
        Order o = approvedAndPaid(1, fibre100());
        OrderService.book(o, TODAY.plusDays(2), VisitSlot.MORNING, TODAY, new ArrayList<>());
        LocalDate promised = o.promisedActive;
        assertEquals(TODAY.plusDays(5), promised);
        return Delay.of(o, promised.plusDays(days));
    }

    @Test public void delayDetection() {
        Delay d0 = lateBy(0), d1 = lateBy(1), d7 = lateBy(7), d8 = lateBy(8);
        assertEquals(Delay.Level.NONE, d0.level);
        assertEquals(0, d0.escalationPoints());
        assertEquals(Delay.Level.LATE, d1.level);
        assertEquals(1, d1.daysLate);
        assertEquals(0, d1.escalationPoints());
        assertEquals(Delay.Level.LATE, d7.level);
        assertEquals("7 days is still amber", 0, d7.escalationPoints());
        assertEquals(Delay.Level.VERY_LATE, d8.level);
        assertEquals(40, d8.escalationPoints());
        assertEquals("new date = 2 days from today", TODAY.plusDays(5 + 8 + 2), d8.newDate);
    }

    @Test public void missedVisitIsDetectedAndCanBeRebooked() {
        Order o = approvedAndPaid(1, fibre100());
        OrderService.book(o, TODAY.plusDays(1), VisitSlot.EVENING, TODAY, new ArrayList<>());
        LocalDate later = TODAY.plusDays(3);
        assertTrue(Delay.of(o, later).visitMissed);
        SlotPlanner.Slot s = SlotPlanner.available(later).get(0);
        assertEquals(OrderService.BookResult.RESCHEDULED, OrderService.rebookMissed(o, s.date, s.time, later, new ArrayList<>()));
        assertEquals("a missed visit doesn't use up a reschedule", 0, o.reschedules);
        assertFalse(Delay.of(o, later).visitMissed);
    }

    // ----------------------------------------------------------------- slots
    @Test public void slotsAreSevenDaysThreeADayNoSundays() {
        List<SlotPlanner.Slot> slots = SlotPlanner.available(TODAY);
        for (SlotPlanner.Slot s : slots) {
            assertNotEquals(DayOfWeek.SUNDAY, s.date.getDayOfWeek());
            assertTrue(s.date.isAfter(TODAY) && !s.date.isAfter(TODAY.plusDays(7)));
        }
        assertEquals("6 working days x 3", 18, slots.size());
    }

    @Test public void reschedulingUpdatesDatesAndStopsAtThree() {
        Order o = approvedAndPaid(1, fibre100());
        List<OrderStep> c = new ArrayList<>();
        assertEquals(OrderService.BookResult.BOOKED, OrderService.book(o, TODAY.plusDays(1), VisitSlot.MORNING, TODAY, c));
        assertTrue(c.contains(OrderStep.INSTALL_BOOKED));
        assertEquals(TODAY.plusDays(5), o.promisedActive);
        // Move the visit after the install-days date: the active date follows the visit.
        assertEquals(OrderService.BookResult.RESCHEDULED, OrderService.book(o, TODAY.plusDays(6), VisitSlot.AFTERNOON, TODAY, c));
        assertEquals(TODAY.plusDays(6), o.promisedActive);
        assertEquals(TODAY.plusDays(6), OrderTimeline.stepDate(o, OrderStep.TECHNICIAN_VISIT, TODAY));
        assertEquals(OrderService.BookResult.RESCHEDULED, OrderService.book(o, TODAY.plusDays(2), VisitSlot.MORNING, TODAY, c));
        assertEquals(OrderService.BookResult.RESCHEDULED, OrderService.book(o, TODAY.plusDays(3), VisitSlot.MORNING, TODAY, c));
        assertEquals(3, o.reschedules);
        assertEquals("4th reschedule blocked", OrderService.BookResult.LIMIT_REACHED,
                OrderService.book(o, TODAY.plusDays(4), VisitSlot.MORNING, TODAY, c));
        assertEquals(TODAY.plusDays(3), o.visitDate);
    }

    @Test public void sundayCannotBeBooked() {
        Order o = approvedAndPaid(1, fibre100());
        LocalDate sunday = TODAY.plusDays(4); // 19 July 2026
        assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());
        assertEquals(OrderService.BookResult.NOT_AVAILABLE, OrderService.book(o, sunday, VisitSlot.MORNING, TODAY, new ArrayList<>()));
    }

    // ----------------------------------------------------------------- payment
    @Test public void paymentFailThenRetrySucceeds() {
        Order o = order(1, fibre100());
        assertEquals("pay only after KYC", OrderService.PayResult.NOT_READY, OrderService.pay(o, "UPI", false, TODAY, new ArrayList<>()));
        OrderService.onKycApproved(o, TODAY);
        List<OrderStep> c = new ArrayList<>();
        assertEquals(OrderService.PayResult.FAILED, OrderService.pay(o, "UPI", true, TODAY, c));
        assertEquals(1, o.paymentFailures);
        assertFalse(o.isDone(OrderStep.PAYMENT_DONE));
        assertEquals(OrderService.PayResult.SUCCESS, OrderService.pay(o, "Card", false, TODAY, c));
        assertTrue(o.isDone(OrderStep.PAYMENT_DONE));
        assertEquals("Card", o.paymentMethod);
        assertEquals(OrderStep.INSTALL_BOOKED, o.currentStep());
    }

    // ----------------------------------------------------------------- whole journey + persistence
    @Test public void debugStepsReachActive() {
        Order o = order(2, fibre100(), sims(2));
        LocalDate day = TODAY;
        int guard = 0;
        while (o.currentStep() != null && guard++ < 10) OrderService.completeNextStep(o, day);
        assertNull(o.currentStep());
        assertEquals(6, o.doneCount());
    }

    @Test public void orderSurvivesSaveAndLoad() {
        Order o = approvedAndPaid(2, fibre100(), sims(2));
        OrderService.book(o, TODAY.plusDays(2), VisitSlot.EVENING, TODAY, new ArrayList<>());
        o.notified.add("step:PAYMENT_DONE");
        Order back = OrderJson.read(OrderJson.write(o));
        assertEquals(OrderJson.write(o), OrderJson.write(back));
        assertEquals("UD-****-4821", back.maskedId());
        assertEquals(VisitSlot.EVENING, back.visitSlot);
        assertEquals(o.promisedActive, back.promisedActive);
        assertEquals(OrderStep.TECHNICIAN_VISIT, back.currentStep());
        assertEquals(2, back.lines.size());
    }
}
