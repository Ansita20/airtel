package com.udyam.digital.partner;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.udyam.digital.R;
import com.udyam.digital.engine.Money;
import com.udyam.digital.security.AccessLog;
import com.udyam.digital.security.SecureAuditLog;
import com.udyam.digital.security.SecureScreens;
import com.udyam.digital.util.AppClock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Partner mode (back office) for Accel field sales and local channel partners. Same app,
 * different home: leads the digital journey couldn't finish, ranked by who is most likely to
 * activate, with the next step to take. Masked names everywhere, no documents, every action
 * logged (encrypted access log). Screenshots blocked (SecureScreens).
 */
public class PartnerActivity extends AppCompatActivity {

    /** Extra: the partner's city; absent = Accel field sales (all cities). */
    public static final String EXTRA_CITY = "city";
    /** Demo PIN for partner mode (shown in the README and demo script). */
    public static final String DEMO_PIN = "2468";

    private PartnerData data;
    private LeadBook book;
    private String city;
    private String cityFilter, languageFilter;
    private LeadBook.Sort sort = LeadBook.Sort.SCORE;
    private int tab;
    private LinearLayout page;
    private TabLayout tabs;
    private final LocalDate today = AppClock.today();
    private final com.udyam.digital.model.Language lang = com.udyam.digital.ui.AppState.language();

    public static void open(Context c, String city) {
        Intent i = new Intent(c, PartnerActivity.class);
        if (city != null) i.putExtra(EXTRA_CITY, city);
        c.startActivity(i);
    }

    private AccessLog.Actor actor() { return city == null ? AccessLog.Actor.FIELD_SALES : AccessLog.Actor.PARTNER; }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        city = getIntent().getStringExtra(EXTRA_CITY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));

        MaterialToolbar bar = new MaterialToolbar(this);
        bar.setTitle(R.string.partner_title);
        bar.setSubtitle(city == null ? getString(R.string.partner_field_sales) : city);
        bar.setNavigationIcon(R.drawable.ic_arrow_back);
        bar.setNavigationContentDescription(R.string.partner_exit);
        bar.setNavigationOnClickListener(v -> finish());
        root.addView(bar);

        TextView banner = new TextView(this);
        banner.setText(getString(R.string.partner_banner, city == null ? getString(R.string.partner_all_cities) : city));
        banner.setTypeface(Typeface.DEFAULT_BOLD);
        banner.setPadding(dp(16), dp(8), dp(16), dp(8));
        banner.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD6F5E0));
        banner.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF000000));
        root.addView(banner);

        tabs = new TabLayout(this);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        for (int t : new int[] {R.string.partner_tab_stuck, R.string.partner_tab_person, R.string.partner_tab_install, R.string.partner_tab_dashboard}) {
            tabs.addTab(tabs.newTab().setText(t));
        }
        root.addView(tabs);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        TextView loading = new TextView(this);
        loading.setText(R.string.partner_loading);
        loading.setPadding(dp(16), dp(16), dp(16), dp(16));
        page.addView(loading);
        Handler ui = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                PartnerData d = new PartnerData(this);
                ui.post(() -> ready(d));
            } catch (Exception e) {
                ui.post(() -> loading.setText(getString(R.string.partner_load_failed, e.getMessage())));
            }
        });
    }

    private void ready(PartnerData d) {
        data = d;
        book = new LeadBook(d.leads, city);
        updateTabCounts();
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { tab = t.getPosition(); render(); }
            @Override public void onTabUnselected(TabLayout.Tab t) { }
            @Override public void onTabReselected(TabLayout.Tab t) { }
        });
        render();
    }

    private void updateTabCounts() {
        PartnerLead.Tab[] ts = PartnerLead.Tab.values();
        int[] names = {R.string.partner_tab_stuck, R.string.partner_tab_person, R.string.partner_tab_install};
        for (int i = 0; i < ts.length; i++) {
            TabLayout.Tab t = tabs.getTabAt(i);
            if (t != null) t.setText(getString(names[i]) + " (" + book.count(ts[i]) + ")");
        }
    }

    private void render() {
        page.removeAllViews();
        if (tab == 3) dashboard(); else leadList(PartnerLead.Tab.values()[tab]);
    }

    // ================================================================== lead list
    private void leadList(PartnerLead.Tab t) {
        ChipGroup sorts = new ChipGroup(this);
        sorts.setSingleSelection(true);
        sorts.setPadding(dp(12), dp(4), dp(12), 0);
        addSort(sorts, R.string.partner_sort_score, LeadBook.Sort.SCORE);
        addSort(sorts, R.string.partner_sort_value, LeadBook.Sort.PLAN_VALUE);
        addSort(sorts, R.string.partner_sort_waiting, LeadBook.Sort.WAITING);
        // Filters on the same row as the sort.
        if (city == null) addFilter(sorts, getString(R.string.partner_city), cityFilter, book.cities(), v -> { cityFilter = v; render(); });
        addFilter(sorts, getString(R.string.partner_language), languageFilter, book.languages(), v -> { languageFilter = v; render(); });
        page.addView(sorts);

        List<PartnerLead> rows = book.list(t, cityFilter, languageFilter, sort, today);
        if (rows.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.partner_none);
            none.setPadding(dp(16), dp(24), dp(16), dp(16));
            page.addView(none);
            return;
        }
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new Rows(rows));
        page.addView(list, new LinearLayout.LayoutParams(-1, -1));
    }

    private void addSort(ChipGroup g, int label, LeadBook.Sort s) {
        Chip c = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
        c.setText(label);
        c.setCheckable(true);
        c.setChecked(sort == s);
        c.setOnClickListener(v -> { sort = s; render(); });
        g.addView(c);
    }

    private interface Pick { void on(String value); }

    private void addFilter(ChipGroup g, String name, String current, List<String> values, Pick pick) {
        Chip c = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
        c.setText(name + ": " + (current == null ? getString(R.string.partner_all) : current) + " ▾");
        c.setOnClickListener(v -> {
            String[] items = new String[values.size() + 1];
            items[0] = getString(R.string.partner_all);
            for (int i = 0; i < values.size(); i++) items[i + 1] = values.get(i);
            new MaterialAlertDialogBuilder(this).setTitle(name)
                    .setItems(items, (d, i) -> pick.on(i == 0 ? null : items[i])).show();
        });
        g.addView(c);
    }

    private final class Rows extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<PartnerLead> rows;

        Rows(List<PartnerLead> rows) { this.rows = rows; }

        @Override public int getItemCount() { return rows.size(); }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            MaterialCardView card = new MaterialCardView(PartnerActivity.this, null,
                    com.google.android.material.R.attr.materialCardViewOutlinedStyle);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(dp(12), dp(6), dp(12), dp(6));
            card.setLayoutParams(lp);
            card.setRadius(dp(14));
            LinearLayout row = new LinearLayout(PartnerActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout text = new LinearLayout(PartnerActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            TextView score = new TextView(PartnerActivity.this);
            score.setGravity(Gravity.CENTER);
            score.setTextSize(20);
            score.setTypeface(Typeface.DEFAULT_BOLD);
            score.setMinWidth(dp(52));
            row.addView(score);
            card.addView(row);
            return new RecyclerView.ViewHolder(card) { };
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            PartnerLead l = rows.get(i);
            MaterialCardView card = (MaterialCardView) h.itemView;
            LinearLayout row = (LinearLayout) card.getChildAt(0), text = (LinearLayout) row.getChildAt(0);
            TextView score = (TextView) row.getChildAt(1);
            text.removeAllViews();
            line(text, l.maskedName + (l.live ? "  · " + getString(R.string.partner_live) : ""), true, false);
            line(text, l.businessType + " · " + l.city + " · " + l.language, false, true);
            line(text, "⚑ " + l.reasonText(lang), false, false);
            String value = l.planValueInr > 0 ? Money.inr(l.planValueInr) + getString(R.string.partner_per_month) : getString(R.string.partner_no_plan);
            line(text, getString(R.string.partner_row_meta, l.stageText(lang), value, l.waitingDays(today))
                    + (l.status == PartnerLead.Status.ASSIGNED ? "\n→ " + l.assignedTo : ""), false, true);
            score.setText(String.valueOf(l.score.score));
            score.setContentDescription(getString(R.string.partner_score_cd, l.score.score));
            card.setOnClickListener(v -> detail(l));
        }
    }

    private void line(LinearLayout parent, String s, boolean bold, boolean muted) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(bold ? 17 : 14);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        if (muted) t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF555555));
        t.setPadding(0, dp(2), 0, 0);
        parent.addView(t);
    }

    // ================================================================== lead detail
    private void detail(PartnerLead l) {
        SecureAuditLog.record(this, actor(), AccessLog.Action.LEAD_OPENED, l.id, l.city, "");
        BottomSheetDialog sheet = SecureScreens.secure(new BottomSheetDialog(this));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(24));
        line(box, l.maskedName, true, false);
        line(box, l.businessType + " · " + l.city + " · " + l.language + " · " + l.id, false, true);
        section(box, getString(R.string.partner_score_title, l.score.score),
                l.score.reasons.isEmpty() ? getString(R.string.partner_no_reasons) : getString(R.string.partner_why, String.join(", ", l.score.reasons)));
        section(box, getString(R.string.partner_plan_seen), l.plan.isEmpty() ? getString(R.string.partner_no_plan)
                : String.join(", ", l.plan) + (l.planValueInr > 0 ? "\n" + getString(R.string.partner_est_value, Money.inr(l.planValueInr)) : ""));
        section(box, getString(R.string.partner_stuck_at), l.stageText(lang) + " - " + l.reasonText(lang));
        section(box, getString(R.string.partner_bot_summary), l.botSummary.isEmpty() ? getString(R.string.partner_no_chat) : String.join("\n", l.botSummary));
        TextView next = new TextView(this);
        next.setText("➜ " + NextAction.forLead(l).in(lang));
        next.setTextSize(17);
        next.setTypeface(Typeface.DEFAULT_BOLD);
        next.setPadding(dp(12), dp(10), dp(12), dp(10));
        next.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD6F5E0));
        next.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF000000));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = dp(14);
        box.addView(next, nlp);

        ChipGroup actions = new ChipGroup(this);
        actions.setPadding(0, dp(12), 0, 0);
        button(actions, getString(R.string.partner_call), v -> mock(l, AccessLog.Action.CALLED, R.string.partner_call_mock));
        button(actions, getString(R.string.partner_whatsapp), v -> mock(l, AccessLog.Action.WHATSAPP, R.string.partner_whatsapp_mock));
        button(actions, getString(R.string.partner_done), v -> {
            data.markDone(l);
            SecureAuditLog.record(this, actor(), AccessLog.Action.MARKED_DONE, l.id, l.city, "");
            sheet.dismiss();
            updateTabCounts();
            render();
        });
        button(actions, getString(R.string.partner_assign, l.city), v -> {
            List<String> ps = LeadBook.partnersIn(l.city);
            new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.partner_assign, l.city))
                    .setItems(ps.toArray(new String[0]), (d, i) -> {
                        data.assign(l, ps.get(i));
                        SecureAuditLog.record(this, actor(), AccessLog.Action.ASSIGNED, l.id, l.city, "");
                        Toast.makeText(this, getString(R.string.partner_assigned, ps.get(i)), Toast.LENGTH_SHORT).show();
                        sheet.dismiss();
                        render();
                    }).show();
        });
        box.addView(actions);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        sheet.setContentView(sv);
        sheet.show();
    }

    private void section(LinearLayout box, String title, String body) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, 0);
        box.addView(t);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(15);
        box.addView(b);
    }

    private void button(ViewGroup g, String label, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        g.addView(b);
    }

    /** Call / WhatsApp are mocks in the demo: nothing is dialled or sent, but the action is logged. */
    private void mock(PartnerLead l, AccessLog.Action a, int msg) {
        SecureAuditLog.record(this, actor(), a, l.id, l.city, "");
        Toast.makeText(this, getString(msg, l.maskedName), Toast.LENGTH_LONG).show();
    }

    // ================================================================== dashboard
    private void dashboard() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(box);
        page.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        PartnerDashboard d = data.dashboard;

        // Funnel
        LinearLayout f = card(box, getString(R.string.partner_funnel));
        TextView caption = caption(f, getString(R.string.partner_tap_bar));
        List<BarChartView.Bar> bars = new ArrayList<>();
        for (int i = 0; i < d.funnel.size(); i++) {
            PartnerDashboard.Step s = d.funnel.get(i);
            String pct = i == 0 ? null : String.format(Locale.ROOT, "%.0f%% of previous step", d.conversion(i) * 100);
            bars.add(new BarChartView.Bar(s.name, s.total(), String.format(Locale.ROOT, "%,d", s.total()) + (pct == null ? "" : " · "
                    + String.format(Locale.ROOT, "%.0f%%", d.conversion(i) * 100)),
                    (pct == null ? "" : pct + "; ") + String.format(Locale.ROOT, "%,d from the data + %d on this phone", s.seeded, s.app)));
        }
        BarChartView chart = new BarChartView(this);
        chart.setBars(bars, caption::setText);
        f.addView(chart);
        caption(f, getString(R.string.partner_funnel_source, d.source));

        // Help bot
        LinearLayout h = card(box, getString(R.string.partner_help));
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tile(tiles, String.valueOf(data.help.chats()), getString(R.string.partner_chats));
        tile(tiles, data.help.chats() == 0 ? "-" : data.help.percentResolvedByBot() + "%", getString(R.string.partner_solved));
        tile(tiles, String.valueOf(data.help.handoffs()), getString(R.string.partner_handoffs));
        h.addView(tiles);
        Map<String, Integer> reasons = data.help.handoffsByReason();
        if (reasons.isEmpty()) {
            caption(h, getString(R.string.partner_no_handoffs));
        } else {
            TextView hc = caption(h, getString(R.string.partner_handoffs_by_reason));
            List<BarChartView.Bar> hb = new ArrayList<>();
            for (Map.Entry<String, Integer> e : reasons.entrySet()) {
                hb.add(new BarChartView.Bar(PartnerMask.text(e.getKey()), e.getValue(), String.valueOf(e.getValue()), null));
            }
            BarChartView hv = new BarChartView(this);
            hv.setBars(hb, hc::setText);
            h.addView(hv);
        }

        // Cost story
        LinearLayout c = card(box, getString(R.string.partner_cost));
        double share = d.noHumanShare(data.appActivations, data.appActivationsNoHuman);
        int est = d.estimatedCostPerActivation(share);
        LinearLayout ct = new LinearLayout(this);
        ct.setOrientation(LinearLayout.HORIZONTAL);
        tile(ct, Money.inr(d.blendedCostInr), getString(R.string.partner_cost_today));
        tile(ct, String.format(Locale.ROOT, "%.0f%%", share * 100), getString(R.string.partner_no_human));
        tile(ct, "≈ " + Money.inr(est), getString(R.string.partner_cost_estimate));
        c.addView(ct);
        caption(c, getString(R.string.partner_cost_today_src, Money.inr(d.blendedCostInr), d.activations));
        caption(c, data.appActivations > 0 ? getString(R.string.partner_no_human_app, data.appActivationsNoHuman, data.appActivations)
                : getString(R.string.partner_no_human_proxy));
        caption(c, getString(R.string.partner_cost_formula, String.format(Locale.ROOT, "%.0f%%", share * 100),
                Money.inr(PartnerDashboard.DIGITAL_ONLY_COST_INR), Money.inr(d.assistedCost())));
    }

    private LinearLayout card(LinearLayout parent, String title) {
        MaterialCardView card = new MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        card.setRadius(dp(16));
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        in.addView(t);
        card.addView(in);
        parent.addView(card, lp);
        return in;
    }

    private TextView caption(LinearLayout parent, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF555555));
        t.setPadding(0, dp(6), 0, dp(6));
        t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        parent.addView(t);
        return t;
    }

    /** A headline number with its label (stat tile). */
    private void tile(LinearLayout row, String value, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(0, dp(8), dp(8), dp(4));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(v);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(12);
        l.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF555555));
        t.addView(l);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    }
}
