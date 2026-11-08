package com.udyam.digital.orderui;

import android.content.Context;

import com.udyam.digital.R;
import com.udyam.digital.model.Language;
import com.udyam.digital.order.Order;
import com.udyam.digital.order.OrderStep;
import com.udyam.digital.order.OrderService;
import com.udyam.digital.ui.AppState;
import com.udyam.digital.util.AppClock;

import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Glue between screens and the pure order logic: loads, saves (encrypted), and shows a
 * notification for every step that completes and one the day before the technician visit.
 */
public final class OrderManager {

    private final Context ctx;
    private final OrderRepository repo;

    public OrderManager(Context c) {
        ctx = c.getApplicationContext();
        repo = new OrderRepository(ctx);
        AppClock.setOffsetDays(repo.clockOffset());
    }

    public OrderRepository repo() { return repo; }

    public Order load() {
        Order o = repo.load();
        if (o != null) {
            List<OrderStep> done = OrderService.tick(o, AppClock.today());
            after(o, done);
        }
        return o;
    }

    /** Saves and sends notifications for {@code completed} steps and a pending visit reminder. */
    public void after(Order o, List<OrderStep> completed) {
        Language lang = AppState.language();
        for (OrderStep s : completed) {
            String key = "step:" + s.name();
            if (o.notified.add(key)) {
                OrderStep next = o.currentStep();
                String body = next == null ? stepName(OrderStep.ACTIVE) : ctx.getString(R.string.notif_step_body, o.maskedId(), stepName(next));
                Notifier.show(ctx, 100 + s.ordinal(), ctx.getString(R.string.notif_step, stepName(s)), body);
            }
        }
        LocalDate today = AppClock.today();
        if (o.visitDate != null && !o.isDone(OrderStep.TECHNICIAN_VISIT) && today.equals(o.visitDate.minusDays(1))) {
            String key = "visit:" + o.visitDate;
            if (o.notified.add(key)) {
                Notifier.show(ctx, 200, ctx.getString(R.string.notif_visit),
                        ctx.getString(R.string.notif_visit_body, visitText(o, lang)));
            }
        }
        repo.save(o);
    }

    public String stepName(OrderStep s) {
        switch (s) {
            case PLAN_CHOSEN: return ctx.getString(R.string.step_plan);
            case KYC_COMPLETE: return ctx.getString(R.string.step_kyc);
            case PAYMENT_DONE: return ctx.getString(R.string.step_payment);
            case INSTALL_BOOKED: return ctx.getString(R.string.step_booked);
            case TECHNICIAN_VISIT: return ctx.getString(R.string.step_visit);
            default: return ctx.getString(R.string.step_active);
        }
    }

    /** The language's locale with Western digits (0-9), for dates. */
    public static Locale locale(Language l) { return com.udyam.digital.i18n.I18n.locale(l); }

    /** "Fri 18 Jul". */
    public static String day(LocalDate d, Language l) {
        return d == null ? "—" : d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale(l)));
    }

    /** "Fri 18 Jul, 10am–12pm". */
    public static String visitText(Order o, Language l) {
        return o.visitDate == null ? "—" : day(o.visitDate, l) + ", " + o.visitSlot.label(l);
    }

    /** Called from KYC when the result is known. */
    public void onKycApproved() {
        Order o = repo.load();
        if (o == null) return;
        after(o, OrderService.onKycApproved(o, AppClock.today()));
    }

    public void onKycReview(String reason) {
        Order o = repo.load();
        if (o == null) return;
        OrderService.onKycReview(o, reason);
        repo.save(o);
    }

    public void setClockOffset(int days) {
        AppClock.setOffsetDays(days);
        repo.setClockOffset(days);
    }
}
