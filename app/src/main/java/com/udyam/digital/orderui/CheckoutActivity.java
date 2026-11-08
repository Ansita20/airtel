package com.udyam.digital.orderui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.udyam.digital.BuildConfig;
import com.udyam.digital.R;
import com.udyam.digital.engine.Money;
import com.udyam.digital.engine.Pack;
import com.udyam.digital.engine.Recommendation;
import com.udyam.digital.help.HelpActivity;
import com.udyam.digital.kycui.KycActivity;
import com.udyam.digital.order.Order;
import com.udyam.digital.order.OrderLine;
import com.udyam.digital.order.OrderService;
import com.udyam.digital.order.OrderStep;
import com.udyam.digital.ui.AppState;
import com.udyam.digital.ui.HelpButton;
import com.udyam.digital.util.AppClock;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checkout. Before KYC: the plan, quantities, monthly and setup totals, contract months,
 * "Prices exclude GST", terms -> creates the order and opens KYC. After KYC is approved:
 * the demo payment (UPI / card). A debug switch makes the next payment fail.
 */
public class CheckoutActivity extends AppCompatActivity {

    private OrderManager orders;
    private OrderUi ui;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orders = new OrderManager(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        MaterialToolbar bar = new MaterialToolbar(this);
        bar.setTitle(R.string.checkout_title);
        bar.setNavigationIcon(R.drawable.ic_arrow_back);
        bar.setNavigationOnClickListener(v -> finish());
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        ui.root.removeAllViews();
        Order o = orders.load();
        if (o != null && o.isDone(OrderStep.PAYMENT_DONE)) {
            startActivity(new Intent(this, TrackerActivity.class));
            finish();
        } else if (o != null && o.isDone(OrderStep.KYC_COMPLETE)) {
            payment(o);
        } else if (o != null) {
            summary(o.lines, o.monthlyTotal, o.setupTotal, o.contractMonths);
            ui.button(ui.root, getString(R.string.btn_continue_kyc), false, v -> startActivity(new Intent(this, KycActivity.class)));
        } else {
            review();
        }
    }

    /** The plan the owner selected on the plan screen. */
    private List<OrderLine> selectedLines(Recommendation r) {
        List<OrderLine> out = new ArrayList<>();
        for (Recommendation.Line l : r.getLines()) {
            if (!l.isSelected()) continue;
            out.add(new OrderLine(l.getProduct().getId(), l.getProduct().getName(), l.getQuantity(),
                    l.getMonthlyInr(), l.getSetupInr(), l.getProduct().getInstallDays()));
        }
        return out;
    }

    private void review() {
        List<Pack> packs = AppState.get().getPacks();
        if (packs == null || packs.isEmpty()) {
            Toast.makeText(this, R.string.no_order, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Pack pack = packs.get(Math.min(AppState.get().selectedPack(), packs.size() - 1));
        Recommendation r = pack.plan;
        List<OrderLine> lines = selectedLines(r);
        summary(lines, r.getMonthlyTotal(), r.getSetupTotal(), r.getContractMonths());
        CheckBox terms = new CheckBox(this);
        terms.setText(R.string.checkout_terms);
        ui.root.addView(terms);
        MaterialButton go = ui.button(ui.root, getString(R.string.checkout_confirm), false, v -> {
            String id = "UD-2026-" + String.format("%04d", new SecureRandom().nextInt(10000));
            Order o = OrderService.create(id, lines, r.getContractMonths(), AppState.get().profile().getCityTier(), AppClock.today());
            orders.after(o, Collections.singletonList(OrderStep.PLAN_CHOSEN));
            startActivity(new Intent(this, KycActivity.class));
            finish();
        });
        go.setEnabled(false);
        terms.setOnCheckedChangeListener((b, c) -> go.setEnabled(c));
    }

    private void summary(List<OrderLine> lines, int monthly, int setup, int months) {
        ui.title(ui.root, getString(R.string.checkout_products));
        LinearLayout c = ui.card(0, 0);
        for (OrderLine l : lines) {
            ui.body(c, l.quantity + " × " + l.name + "   " + getString(R.string.line_monthly, Money.inr(l.monthlyInr)));
        }
        ui.text(c, getString(R.string.checkout_monthly, Money.inr(monthly)),
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, true);
        ui.body(c, getString(R.string.checkout_setup, Money.inr(setup)));
        ui.body(c, getString(R.string.checkout_contract, months));
        ui.small(c, getString(R.string.checkout_gst));
    }

    private void payment(Order o) {
        summary(o.lines, o.monthlyTotal, o.setupTotal, o.contractMonths);
        ui.title(ui.root, getString(R.string.checkout_pay_title));
        LinearLayout c = ui.card(com.google.android.material.R.attr.colorPrimaryContainer, 0);
        ui.text(c, getString(R.string.checkout_pay_now, Money.inr(o.setupTotal + o.monthlyTotal), Money.inr(o.setupTotal),
                Money.inr(o.monthlyTotal)), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, true);
        ui.small(c, getString(R.string.pay_demo_note));
        ui.button(ui.root, getString(R.string.pay_upi), false, v -> pay(o, "UPI"));
        ui.button(ui.root, getString(R.string.pay_card), true, v -> pay(o, "Card"));
        if (BuildConfig.DEBUG) {
            MaterialSwitch fail = new MaterialSwitch(this);
            fail.setText(R.string.pay_fail_debug);
            fail.setChecked(orders.repo().failNextPayment());
            fail.setOnCheckedChangeListener((b, on) -> orders.repo().setFailNextPayment(on));
            ui.root.addView(fail);
        }
    }

    private void pay(Order o, String method) {
        boolean fail = BuildConfig.DEBUG && orders.repo().failNextPayment();
        List<OrderStep> done = new ArrayList<>();
        OrderService.PayResult r = OrderService.pay(o, method, fail, AppClock.today(), done);
        if (r == OrderService.PayResult.NOT_READY) {
            Toast.makeText(this, R.string.pay_need_kyc, Toast.LENGTH_LONG).show();
            return;
        }
        if (r == OrderService.PayResult.FAILED) {
            orders.repo().setFailNextPayment(false); // the demo fails once, then the retry works
            orders.after(o, done);
            failed(o, method);
            return;
        }
        orders.after(o, done);
        Toast.makeText(this, o.needsVisit() ? R.string.pay_success : R.string.pay_success_novisit, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, TrackerActivity.class).putExtra(TrackerActivity.EXTRA_OPEN_SLOTS, o.needsVisit()));
        finish();
    }

    /** Retry screen: Retry / Try another method / Talk to a person. */
    private void failed(Order o, String method) {
        ui.root.removeAllViews();
        LinearLayout c = ui.card(com.google.android.material.R.attr.colorErrorContainer, 0);
        ui.title(c, "⚠ " + getString(R.string.pay_failed_title)).setTextColor(ui.color(com.google.android.material.R.attr.colorOnErrorContainer));
        ui.body(c, getString(R.string.pay_failed_msg)).setTextColor(ui.color(com.google.android.material.R.attr.colorOnErrorContainer));
        ui.button(ui.root, getString(R.string.pay_retry), false, v -> pay(o, method));
        ui.button(ui.root, getString(R.string.pay_other), true, v -> render());
        ui.button(ui.root, getString(R.string.talk_to_person), true, v -> startActivity(new Intent(this, HelpActivity.class)));
    }
}
