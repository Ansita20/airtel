package com.udyam.digital.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.udyam.digital.R;
import com.udyam.digital.data.DataRepository;
import com.udyam.digital.engine.Money;
import com.udyam.digital.engine.Pack;
import com.udyam.digital.engine.Recommendation;
import com.udyam.digital.engine.Recommendation.Line;
import com.udyam.digital.engine.RecommendationLog;
import com.udyam.digital.engine.Texts;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.Product;

import java.util.List;
import java.util.UUID;

/**
 * Screen 3. Top card: the selected plan ("Your plan" by default: tapped needs + top learned
 * picks, within budget). "More options": Basic / Premium / Premium+, each with its monthly
 * total, the difference vs Your plan and what it adds; suggested packs may be up to 20% over
 * budget (shown in amber) and are never preselected. Tapping "Choose this plan" makes a pack
 * the selected plan; switches and quantities still work on it. Pack choice is logged.
 */
public class PlanActivity extends AppCompatActivity {

    private static final String KEY_SESSION = "session";
    private static final String KEY_PACK = "pack";
    private static final String KEY_PACKS_OPEN = "packsOpen";

    private List<Pack> packs;
    private int selected;
    private Recommendation rec;
    private String sessionId;
    private boolean proceeded;
    private boolean packsOpen;
    private Texts texts;

    private TextView packLabel;
    private TextView totalMonthly;
    private TextView setupText;
    private TextView budgetText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packs = AppState.get().getPacks();
        if (packs == null || packs.isEmpty()) { // e.g. process was killed; start again
            startActivity(new Intent(this, LanguageActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        setContentView(R.layout.activity_plan);
        boolean firstShow = savedInstanceState == null;
        sessionId = firstShow ? UUID.randomUUID().toString() : savedInstanceState.getString(KEY_SESSION);
        selected = firstShow ? 0 : Math.min(savedInstanceState.getInt(KEY_PACK, 0), packs.size() - 1);
        packsOpen = !firstShow && savedInstanceState.getBoolean(KEY_PACKS_OPEN, false);
        texts = Texts.of(AppState.language());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        HelpButton.addTo(toolbar, this);
        // 🔊 Reads the plan and its monthly price as words ("तीन हज़ार एक सौ रुपये महीना").
        speaker = new Speaker(this, AppState.language(), null);
        android.view.MenuItem read = toolbar.getMenu().add(R.string.listen);
        read.setIcon(null);
        read.setTitle("🔊");
        read.setContentDescription(getString(R.string.listen));
        read.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        read.setOnMenuItemClickListener(i -> { readPlan(); return true; });
        packLabel = findViewById(R.id.pack_label);
        totalMonthly = findViewById(R.id.total_monthly);
        setupText = findViewById(R.id.setup_text);
        budgetText = findViewById(R.id.budget_text);

        TextView howBody = findViewById(R.id.how_body);
        howBody.setText("• " + getString(R.string.how_rules) + "\n• " + getString(R.string.how_on)
                + "\n• " + getString(R.string.how_rank)
                + "\n• " + getString(R.string.how_switch) + "\n\n" + getString(R.string.privacy_note));
        findViewById(R.id.how_card).setOnClickListener(v ->
                howBody.setVisibility(howBody.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        findViewById(R.id.packs_header).setOnClickListener(v -> {
            packsOpen = !packsOpen;
            renderPacks();
        });

        findViewById(R.id.cta_kyc).setOnClickListener(v -> {
            if (!proceeded) log(rec.getKind().name(), RecommendationLog.Event.PROCEEDED_TO_KYC);
            proceeded = true;
            startActivity(new Intent(this, com.udyam.digital.orderui.CheckoutActivity.class));
        });
        findViewById(R.id.cta_tracker).setOnClickListener(v ->
                startActivity(new Intent(this, com.udyam.digital.orderui.TrackerActivity.class)));
        findViewById(R.id.cta_chat).setOnClickListener(v ->
                startActivity(new Intent(this, com.udyam.digital.help.HelpActivity.class)));
        findViewById(R.id.start_over).setOnClickListener(v -> {
            Intent i = new Intent(this, LanguageActivity.class).putExtra(LanguageActivity.EXTRA_NO_REDIRECT, true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        showPack(selected, firstShow);
        if (firstShow && com.udyam.digital.voice.VoiceMode.readAloud(this) && !com.udyam.digital.voice.VoiceMode.screenReader(this)) {
            totalMonthly.postDelayed(this::readPlan, 600);
        }
    }

    private Speaker speaker;

    /** Plan name and monthly price, spoken in the chosen language. */
    private void readPlan() {
        if (rec == null) return;
        com.udyam.digital.model.Language l = AppState.language();
        speaker.speak(packLabel.getText() + ". " + com.udyam.digital.voice.NumberWords.rupeesPerMonth(rec.getMonthlyTotal(), l) + ".");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_SESSION, sessionId);
        out.putInt(KEY_PACK, selected);
        out.putBoolean(KEY_PACKS_OPEN, packsOpen);
    }

    @Override
    protected void onDestroy() {
        // Leaving the plan for good without going to KYC counts as abandoned.
        if (rec != null && isFinishing() && !proceeded) log(null, RecommendationLog.Event.ABANDONED);
        if (speaker != null) speaker.shutdown();
        super.onDestroy();
    }

    private void log(String productId, RecommendationLog.Event event) {
        RecommendationLog log = DataRepository.get().log();
        BusinessProfile p = AppState.get().profile();
        if (log != null && sessionId != null) log.log(sessionId, p, productId, event, System.currentTimeMillis());
    }

    private String packName(Pack.Kind k) {
        switch (k) {
            case BASIC: return getString(R.string.pack_basic);
            case PREMIUM: return getString(R.string.pack_premium);
            case PREMIUM_PLUS: return getString(R.string.pack_premium_plus);
            default: return getString(R.string.plan_title);
        }
    }

    /** Makes pack {@code index} the selected plan and redraws everything below the total. */
    private void showPack(int index, boolean logIt) {
        selected = index;
        AppState.get().setSelectedPack(index);
        rec = packs.get(index).plan;
        packLabel.setText(packName(rec.getKind()));
        if (logIt) {
            if (index != 0) log(rec.getKind().name(), RecommendationLog.Event.PACK_CHOSEN);
            for (Line l : rec.getLines()) {
                if (l.isSelected()) log(l.getProduct().getId(), RecommendationLog.Event.SHOWN);
                if (l.isSuggested() && !l.isSelected()) log(l.getProduct().getId(), RecommendationLog.Event.SUGGESTED);
            }
        }
        renderLines();
        renderPacks();
        updateTotals();
    }

    private void renderLines() {
        LayoutInflater inflater = getLayoutInflater();
        LinearLayout core = findViewById(R.id.core_container);
        LinearLayout recommended = findViewById(R.id.addons_container);
        LinearLayout suggested = findViewById(R.id.suggested_container);
        LinearLayout optional = findViewById(R.id.optional_container);
        LinearLayout excluded = findViewById(R.id.excluded_container);
        core.removeAllViews();
        recommended.removeAllViews();
        suggested.removeAllViews();
        optional.removeAllViews();
        excluded.removeAllViews();
        int recCount = 0, sugCount = 0, optCount = 0;
        for (Line line : rec.getLines()) {
            LinearLayout target;
            if (line.getRole() != Recommendation.Role.ADD_ON) {
                target = core;
            } else if (line.isRecommended()) {
                target = recommended;   // "You asked for"
                recCount++;
            } else if (line.isSuggested()) {
                target = suggested;     // "Businesses like yours often add"
                sugCount++;
            } else {
                target = optional;      // "You can also add"
                optCount++;
            }
            target.addView(lineView(inflater, target, line));
        }
        findViewById(R.id.addons_header).setVisibility(recCount == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.suggested_header).setVisibility(sugCount == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.optional_header).setVisibility(optCount == 0 ? View.GONE : View.VISIBLE);

        for (Recommendation.Excluded e : rec.getExcluded()) {
            View v = inflater.inflate(R.layout.item_excluded, excluded, false);
            ((TextView) v.findViewById(R.id.excluded_name)).setText(e.getProduct().getName());
            ((TextView) v.findViewById(R.id.excluded_reason)).setText(e.getReason());
            excluded.addView(v);
        }
        findViewById(R.id.excluded_header).setVisibility(rec.getExcluded().isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** "More options": one collapsed row per pack; tap a row to expand it, then choose it. */
    private void renderPacks() {
        TextView header = findViewById(R.id.packs_header);
        LinearLayout container = findViewById(R.id.packs_container);
        header.setVisibility(packs.size() > 1 ? View.VISIBLE : View.GONE);
        header.setText((packsOpen ? "▾  " : "▸  ") + getString(R.string.more_options));
        container.setVisibility(packsOpen ? View.VISIBLE : View.GONE);
        container.removeAllViews();
        if (!packsOpen) return;

        Pack your = packs.get(0);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < packs.size(); i++) {
            Pack pack = packs.get(i);
            View row = inflater.inflate(R.layout.item_pack, container, false);
            ((TextView) row.findViewById(R.id.pack_name)).setText(packName(pack.kind));
            ((TextView) row.findViewById(R.id.pack_total)).setText(
                    getString(R.string.line_monthly, Money.inr(pack.monthly())));

            int diff = pack.monthly() - your.monthly();
            TextView diffView = row.findViewById(R.id.pack_diff);
            if (i == selected) {
                diffView.setText(R.string.pack_selected);
            } else if (pack == your) {
                diffView.setVisibility(View.GONE); // comparing Your plan with itself says nothing
            } else if (diff == 0) {
                diffView.setText(R.string.pack_same);
            } else {
                diffView.setText(getString(R.string.pack_diff, (diff > 0 ? "+" : "−") + Money.inr(Math.abs(diff))));
            }
            if (pack.overBudgetInr > 0) {
                TextView over = row.findViewById(R.id.pack_over);
                over.setText(getString(R.string.pack_over, Money.inr(pack.overBudgetInr)));
                over.setVisibility(View.VISIBLE);
            }

            String extra = whatYouGet(pack, your, 2);
            TextView extraView = row.findViewById(R.id.pack_extra);
            extraView.setText(extra);
            extraView.setVisibility(extra.isEmpty() ? View.GONE : View.VISIBLE);
            ((TextView) row.findViewById(R.id.pack_extra_full)).setText(whatYouGet(pack, your, 99));

            View details = row.findViewById(R.id.pack_details);
            View choose = row.findViewById(R.id.pack_choose);
            choose.setVisibility(i == selected ? View.GONE : View.VISIBLE);
            final int index = i;
            row.setOnClickListener(v -> details.setVisibility(
                    details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            choose.setOnClickListener(v -> showPack(index, true));
            container.addView(row);
        }
    }

    /** One line: what this pack adds vs Your plan (or what it leaves out, for cheaper packs). */
    private String whatYouGet(Pack pack, Pack your, int maxNames) {
        if (pack == your) return "";
        List<Product> adds = pack.adds(your);
        if (!adds.isEmpty()) return getString(R.string.pack_adds, names(adds, maxNames));
        List<Product> leaves = pack.leaves(your);
        if (!leaves.isEmpty()) return getString(R.string.pack_leaves, names(leaves, maxNames));
        return "";
    }

    private static String names(List<Product> products, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < products.size() && i < max; i++) {
            if (i > 0) b.append(", ");
            b.append(products.get(i).getName());
        }
        if (products.size() > max) b.append(" +").append(products.size() - max);
        return b.toString();
    }

    private View lineView(LayoutInflater inflater, LinearLayout parent, Line line) {
        View v = inflater.inflate(R.layout.item_plan_line, parent, false);
        ((TextView) v.findViewById(R.id.line_name)).setText(line.getProduct().getName());

        TextView detailView = v.findViewById(R.id.line_detail);
        TextView priceView = v.findViewById(R.id.line_price);
        Runnable refresh = () -> {
            String detail = getString(R.string.line_detail, line.getQuantity(),
                    Money.inr(line.getUnitPriceInr()), texts.unit(line.getProduct().getUnit()));
            if (line.getSetupInr() > 0) detail += "  " + getString(R.string.line_setup, Money.inr(line.getSetupInr()));
            detailView.setText(detail);
            priceView.setText(getString(R.string.line_monthly, Money.inr(line.getMonthlyInr())));
        };
        refresh.run();
        ((TextView) v.findViewById(R.id.line_why)).setText(getString(R.string.why_prefix, line.getReason()));

        // Quantities are a starting point from real bundles; the owner can change them.
        if (line.getRole() != Recommendation.Role.PRIMARY) {
            v.findViewById(R.id.qty_stepper).setVisibility(View.VISIBLE);
            TextView qty = v.findViewById(R.id.qty_value);
            qty.setText(String.valueOf(line.getQuantity()));
            v.findViewById(R.id.qty_minus).setOnClickListener(b -> {
                line.setQuantity(line.getQuantity() - 1);
                qty.setText(String.valueOf(line.getQuantity()));
                refresh.run();
                updateTotals();
            });
            v.findViewById(R.id.qty_plus).setOnClickListener(b -> {
                line.setQuantity(line.getQuantity() + 1);
                qty.setText(String.valueOf(line.getQuantity()));
                refresh.run();
                updateTotals();
            });
        }

        TextView note = v.findViewById(R.id.line_note);
        if (line.getNote() != null) {
            note.setText(line.getNote());
            note.setVisibility(View.VISIBLE);
        }

        MaterialSwitch sw = v.findViewById(R.id.line_switch);
        if (line.isToggleable()) {
            sw.setVisibility(View.VISIBLE);
            sw.setChecked(line.isSelected());
            sw.setContentDescription(line.getProduct().getName());
            v.setAlpha(line.isSelected() ? 1f : 0.6f);
            sw.setOnCheckedChangeListener((button, checked) -> {
                line.setSelected(checked);
                log(line.getProduct().getId(), checked ? RecommendationLog.Event.TOGGLED_ON
                        : RecommendationLog.Event.TOGGLED_OFF);
                v.setAlpha(checked ? 1f : 0.6f);
                updateTotals();
            });
        }
        return v;
    }

    private void updateTotals() {
        totalMonthly.setText(Money.inr(rec.getMonthlyTotal()));
        int setup = rec.getSetupTotal();
        setupText.setText(setup > 0
                ? getString(R.string.setup_contract, Money.inr(setup), rec.getContractMonths())
                : getString(R.string.setup_none, rec.getContractMonths()));
        int budget = rec.getBudgetInr();
        if (budget <= 0) {
            budgetText.setText(R.string.budget_none);
        } else if (rec.isOverBudget()) {
            budgetText.setText(getString(R.string.budget_over,
                    Money.inr(rec.getMonthlyTotal() - budget), Money.inr(budget)));
        } else {
            budgetText.setText(getString(R.string.budget_ok, Money.inr(budget)));
        }
    }

    private void openPlaceholder(int titleRes, String icon) {
        Intent i = new Intent(this, PlaceholderActivity.class);
        i.putExtra(PlaceholderActivity.EXTRA_TITLE, titleRes);
        i.putExtra(PlaceholderActivity.EXTRA_ICON, icon);
        startActivity(i);
    }
}
