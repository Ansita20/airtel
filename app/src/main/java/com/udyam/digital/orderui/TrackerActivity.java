package com.udyam.digital.orderui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.udyam.digital.R;
import com.udyam.digital.engine.Money;
import com.udyam.digital.help.HelpActivity;
import com.udyam.digital.kycui.KycActivity;
import com.udyam.digital.model.Language;
import com.udyam.digital.order.Delay;
import com.udyam.digital.order.Order;
import com.udyam.digital.order.OrderLine;
import com.udyam.digital.order.OrderService;
import com.udyam.digital.order.OrderStep;
import com.udyam.digital.order.OrderTimeline;
import com.udyam.digital.order.SlotPlanner;
import com.udyam.digital.ui.AppState;
import com.udyam.digital.ui.HelpButton;
import com.udyam.digital.ui.LanguageActivity;
import com.udyam.digital.util.AppClock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Order tracker: when the connection will be ready, the 6-step timeline, each product's own
 * status, delay banners, technician slots. Long-press the title for the demo debug panel.
 */
public class TrackerActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_SLOTS = "open_slots";

    private OrderManager orders;
    private OrderUi ui;
    private Language lang;
    private ActivityResultLauncher<String> notifPermission;

    private static final int PRIMARY = androidx.appcompat.R.attr.colorPrimary;
    private static final int OUTLINE = com.google.android.material.R.attr.colorOutline;
    private static final int ON_SURFACE_VAR = com.google.android.material.R.attr.colorOnSurfaceVariant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orders = new OrderManager(this);
        lang = AppState.language();
        notifPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> { });

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        MaterialToolbar bar = new MaterialToolbar(this);
        bar.setTitle(R.string.order_tracker);
        bar.setNavigationIcon(R.drawable.ic_arrow_back);
        bar.setNavigationOnClickListener(v -> finish());
        bar.setOnLongClickListener(v -> { debugPanel(); return true; });
        HelpButton.addTo(bar, this);
        page.addView(bar);
        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        ui = new OrderUi(this, content);
        content.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(24));
        askNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SLOTS, false)) {
            getIntent().removeExtra(EXTRA_OPEN_SLOTS);
            Order o = orders.load();
            if (o != null) slotPicker(o);
        }
    }

    /** Android 13+: ask once, with a one-line reason. */
    private void askNotifications() {
        if (Build.VERSION.SDK_INT < 33 || Notifier.allowed(this) || orders.repo().notificationsAsked()) return;
        orders.repo().setNotificationsAsked();
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.notif_why)
                .setPositiveButton(R.string.notif_allow, (d, w) -> notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS))
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    private String day(LocalDate d) { return OrderManager.day(d, lang); }

    private void render() {
        ui.root.removeAllViews();
        Order o = orders.load();
        if (o == null) {
            ui.body(ui.root, getString(R.string.no_order));
            ui.button(ui.root, getString(R.string.pack_choose), false, v -> {
                startActivity(new Intent(this, LanguageActivity.class).putExtra(LanguageActivity.EXTRA_NO_REDIRECT, true));
                finish();
            });
            return;
        }
        LocalDate today = AppClock.today();
        LocalDate active = OrderTimeline.activeDate(o, today);
        Delay delay = Delay.of(o, today);

        // Top card
        LinearLayout top = ui.card(com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int onTop = ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer);
        String headline = o.isDone(OrderStep.ACTIVE) ? getString(R.string.trk_active_since, day(o.doneOn(OrderStep.ACTIVE)))
                : active == null ? getString(R.string.trk_paused)
                : getString(R.string.trk_ready_by, day(delay.level != Delay.Level.NONE ? delay.newDate : active));
        ui.title(top, headline).setTextColor(onTop);
        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(6);
        bar.setProgressCompat(o.doneCount(), false);
        bar.setTrackCornerRadius(ui.dp(6));
        bar.setTrackThickness(ui.dp(10));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = ui.dp(10);
        top.addView(bar, blp);
        ui.small(top, getString(R.string.trk_order_id, o.maskedId(), Math.min(6, o.doneCount() + 1))).setTextColor(onTop);
        if (o.cityTier >= 2 && o.needsVisit()) ui.small(top, getString(R.string.trk_tier2)).setTextColor(onTop);

        // Delay banners
        if (delay.level == Delay.Level.LATE) {
            LinearLayout b = ui.card(com.google.android.material.R.attr.colorTertiaryContainer, 0);
            ui.body(b, "⏳ " + getString(R.string.late_amber, delay.daysLate, day(delay.newDate)))
                    .setTextColor(ui.color(com.google.android.material.R.attr.colorOnTertiaryContainer));
        } else if (delay.level == Delay.Level.VERY_LATE) {
            LinearLayout b = ui.card(com.google.android.material.R.attr.colorErrorContainer, 0);
            ui.body(b, "⚠ " + getString(R.string.late_red, delay.daysLate, day(delay.newDate)))
                    .setTextColor(ui.color(com.google.android.material.R.attr.colorOnErrorContainer));
            ui.button(b, getString(R.string.talk_to_person), false, v -> startActivity(new Intent(this, HelpActivity.class)));
        }
        if (delay.visitMissed) {
            LinearLayout b = ui.card(com.google.android.material.R.attr.colorTertiaryContainer, 0);
            ui.body(b, getString(R.string.visit_missed, OrderManager.visitText(o, lang)))
                    .setTextColor(ui.color(com.google.android.material.R.attr.colorOnTertiaryContainer));
            ui.button(b, getString(R.string.btn_rebook), false, v -> slotPicker(o));
        }

        // Timeline
        LinearLayout tl = ui.card(0, 0);
        for (OrderStep s : OrderStep.values()) timelineRow(tl, o, s, today);

        // Products
        ui.title(ui.root, getString(R.string.products_title));
        LinearLayout pc = ui.card(0, 0);
        for (OrderLine l : o.lines) {
            String name = (l.quantity > 1 ? l.quantity + " × " : "") + l.name;
            String line;
            switch (OrderTimeline.productState(o, l, today)) {
                case ACTIVE: line = "✅ " + getString(R.string.prod_active, name); break;
                case PAUSED: line = "⏸ " + getString(R.string.prod_paused, name); break;
                default: line = "🕒 " + getString(R.string.prod_ready, name, day(OrderTimeline.productReady(o, l, today)));
            }
            ui.body(pc, line);
        }

        // Buttons
        OrderStep cur = o.currentStep();
        if (cur == OrderStep.KYC_COMPLETE) {
            ui.button(ui.root, getString(R.string.btn_continue_kyc), false, v -> startActivity(new Intent(this, KycActivity.class)));
        } else if (cur == OrderStep.PAYMENT_DONE) {
            ui.button(ui.root, getString(R.string.btn_pay), false, v -> startActivity(new Intent(this, CheckoutActivity.class)));
        } else if (cur == OrderStep.INSTALL_BOOKED) {
            ui.button(ui.root, getString(R.string.btn_book_visit), false, v -> slotPicker(o));
        }
        if (o.visitDate != null && !o.isDone(OrderStep.TECHNICIAN_VISIT) && !delay.visitMissed) {
            ui.button(ui.root, getString(R.string.btn_change_visit), true, v -> slotPicker(o));
        }
        ui.button(ui.root, getString(R.string.btn_help), true, v -> startActivity(new Intent(this, HelpActivity.class)));
        ui.button(ui.root, getString(R.string.btn_view_plan), true, v -> viewPlan(o));
    }

    private void timelineRow(LinearLayout parent, Order o, OrderStep s, LocalDate today) {
        OrderStep.Status st = o.status(s);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        TextView icon = new TextView(this);
        icon.setText(st == OrderStep.Status.DONE ? "✔" : st == OrderStep.Status.CURRENT ? "●" : "○");
        icon.setTextSize(20);
        icon.setTextColor(ui.color(st == OrderStep.Status.UPCOMING ? OUTLINE : PRIMARY));
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(32), -2));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(orders.stepName(s));
        name.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        if (st == OrderStep.Status.CURRENT) name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        if (st == OrderStep.Status.UPCOMING) name.setTextColor(ui.color(ON_SURFACE_VAR));
        mid.addView(name);
        TextView sub = new TextView(this);
        sub.setText(stepLine(o, s, today));
        sub.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        sub.setTextColor(ui.color(ON_SURFACE_VAR));
        mid.addView(sub);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        TextView date = new TextView(this);
        LocalDate d = OrderTimeline.stepDate(o, s, today);
        date.setText(d == null ? "—" : day(d));
        date.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        date.setTextColor(ui.color(st == OrderStep.Status.UPCOMING ? ON_SURFACE_VAR : PRIMARY));
        row.addView(date);
        parent.addView(row);
    }

    /** The plain line under each step. */
    private String stepLine(Order o, OrderStep s, LocalDate today) {
        boolean done = o.isDone(s);
        switch (s) {
            case PLAN_CHOSEN: return getString(R.string.line_plan, o.lines.size(), Money.inr(o.monthlyTotal));
            case KYC_COMPLETE:
                if (done) return getString(R.string.line_kyc_done);
                return o.kycUnderReview ? getString(R.string.line_kyc_review, o.reviewReason) : getString(R.string.line_kyc_todo);
            case PAYMENT_DONE: return done ? getString(R.string.line_pay_done, o.paymentMethod) : getString(R.string.line_pay_todo);
            case INSTALL_BOOKED:
                if (!o.needsVisit()) return getString(R.string.line_book_none);
                return o.visitDate != null ? getString(R.string.line_visit, OrderManager.visitText(o, lang)) : getString(R.string.line_book_todo);
            case TECHNICIAN_VISIT:
                if (!o.needsVisit()) return getString(R.string.line_book_none);
                if (done) return getString(R.string.line_visit_done);
                return o.visitDate != null ? getString(R.string.line_visit, OrderManager.visitText(o, lang)) : getString(R.string.line_book_todo);
            default:
                if (done) return getString(R.string.line_active);
                LocalDate a = OrderTimeline.activeDate(o, today);
                return a == null ? getString(R.string.trk_paused) : getString(R.string.line_active_todo, day(a));
        }
    }

    // ------------------------------------------------------------------ technician slots
    private void slotPicker(Order o) {
        LocalDate today = AppClock.today();
        Delay delay = Delay.of(o, today);
        if (o.visitDate != null && !delay.visitMissed && o.reschedules >= Order.MAX_RESCHEDULES) {
            limitReached();
            return;
        }
        List<SlotPlanner.Slot> slots = SlotPlanner.available(today);
        String[] labels = new String[slots.size()];
        for (int i = 0; i < slots.size(); i++) labels[i] = day(slots.get(i).date) + " · " + slots.get(i).time.label(lang);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.slots_title)
                .setItems(labels, (d, i) -> {
                    SlotPlanner.Slot s = slots.get(i);
                    List<OrderStep> done = new ArrayList<>();
                    OrderService.BookResult r = delay.visitMissed
                            ? OrderService.rebookMissed(o, s.date, s.time, today, done)
                            : OrderService.book(o, s.date, s.time, today, done);
                    orders.after(o, done);
                    String when = OrderManager.visitText(o, lang);
                    switch (r) {
                        case BOOKED: toast(getString(R.string.slot_booked, when)); break;
                        case RESCHEDULED: toast(getString(R.string.slot_rescheduled, when, o.reschedules)); break;
                        case LIMIT_REACHED: limitReached(); break;
                        default: break;
                    }
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void limitReached() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.slot_limit)
                .setPositiveButton(R.string.talk_to_person, (d, w) -> startActivity(new Intent(this, HelpActivity.class)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void viewPlan(Order o) {
        StringBuilder b = new StringBuilder();
        for (OrderLine l : o.lines) {
            b.append(l.quantity).append(" × ").append(l.name).append(" — ")
                    .append(getString(R.string.line_monthly, Money.inr(l.monthlyInr))).append('\n');
        }
        b.append('\n').append(getString(R.string.checkout_monthly, Money.inr(o.monthlyTotal)))
                .append('\n').append(getString(R.string.checkout_setup, Money.inr(o.setupTotal)))
                .append('\n').append(getString(R.string.checkout_contract, o.contractMonths))
                .append('\n').append(getString(R.string.checkout_gst));
        new MaterialAlertDialogBuilder(this).setTitle(R.string.btn_view_plan).setMessage(b.toString())
                .setPositiveButton(R.string.upload_ok, null).show();
    }

    // ------------------------------------------------------------------ debug (long-press the title)
    private void debugPanel() {
        String[] items = {getString(R.string.dbg_adv1), getString(R.string.dbg_adv7), getString(R.string.dbg_next), getString(R.string.dbg_reset)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dbg_title) + "\n" + getString(R.string.dbg_today, AppClock.today(), AppClock.offsetDays()))
                .setItems(items, (d, i) -> {
                    Order o = orders.load();
                    switch (i) {
                        case 0: orders.setClockOffset(AppClock.offsetDays() + 1); break;
                        case 1: orders.setClockOffset(AppClock.offsetDays() + 7); break;
                        case 2: if (o != null) orders.after(o, OrderService.completeNextStep(o, AppClock.today())); break;
                        default:
                            orders.repo().clear();
                            orders.setClockOffset(0);
                            break;
                    }
                    if (i < 2 && o != null) orders.after(o, new ArrayList<>()); // visit reminder, ACTIVE tick
                    render();
                })
                .show();
    }
}
