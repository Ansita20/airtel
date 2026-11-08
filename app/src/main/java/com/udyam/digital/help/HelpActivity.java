package com.udyam.digital.help;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.udyam.digital.BuildConfig;
import com.udyam.digital.help.core.GstinLookup;
import com.udyam.digital.help.core.ProductCatalogue;
import com.udyam.digital.R;
import com.udyam.digital.help.core.RulesEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Duolingo-style help: one step per screen with big tap cards and a progress bar.
 *   Step 1  pick a topic        (Internet, Plan, Documents, Payment, Installation, Something else)
 *   Step 2  pick the problem    (each card is a message HelpSession understands)
 *   Step 3  the fix             (answer + action cards + "Solved" / "Still a problem")
 *   Then    Solved! screen, or the "a person will help you" screen with callback slots.
 * Typing, the mic and "Talk to a person" work on every step. Long-press the header for the
 * debug panel; "History" shows the full transcript.
 */
public class HelpActivity extends AppCompatActivity {

    private static final int REQ_MIC = 41;
    /** Help-bot statistics on this phone (chats, % solved by the bot, handoffs by reason), for the partner dashboard. */
    public static HelpStats stats(android.content.Context c) {
        return HelpStats.fromJson(c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATS, ""));
    }

    private static final String PREFS = "udyam_help";
    private static final String KEY_STATS = "help_stats";
    private static final String KEY_PERSONA = "demo_persona";
    /** Intent extra: open with a demo persona instead of the app's real state. */
    public static final String EXTRA_DEMO = "demo";
    /** "Persona" meaning the owner's own data from the app. */
    static final int APP_PERSONA = -1;

    private enum Step { TOPICS, PROBLEMS, ANSWER, SOLVED, HANDOFF }

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // logic
    private KnowledgeBase kb;
    private RulesEngine engine;
    private GstinLookup gstin;
    private String systemPrompt = "";
    private LlmClient llm;
    private HelpStats stats;
    private HelpSession session;
    private HelpReply lastReply;
    private int persona;
    private Step step = Step.TOPICS;
    private KnowledgeBase.Topic currentTopic;
    private long problemStartedAt;
    private final List<ChatAdapter.Message> history = new ArrayList<>();

    // views
    private LinearProgressIndicator progress;
    private TextView stepLabel, banner, speechTitle, speech;
    private ImageView mascot;
    private LinearLayout cards, screen;
    private ScrollView scroll;
    private TextInputEditText input;
    private androidx.activity.result.ActivityResultLauncher<String> micPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.help_menu);
        // Back arrow: one step back inside help; from the first screen it returns to the app.
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_history) {
                showHistory();
                return true;
            }
            if (item.getItemId() == R.id.action_close) {
                finish(); // straight back to the app screen help was opened from
                return true;
            }
            return false;
        });
        toolbar.setOnLongClickListener(v -> {
            showDebugPanel();
            return true;
        });

        progress = findViewById(R.id.progress);
        stepLabel = findViewById(R.id.step_label);
        banner = findViewById(R.id.banner);
        speechTitle = findViewById(R.id.speech_title);
        speech = findViewById(R.id.speech);
        mascot = findViewById(R.id.mascot);
        cards = findViewById(R.id.cards);
        screen = findViewById(R.id.screen);
        scroll = findViewById(R.id.scroll);
        input = findViewById(R.id.input);

        findViewById(R.id.send).setOnClickListener(v -> sendTyped());
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });
        ImageButton mic = findViewById(R.id.mic);
        mic.setOnClickListener(v -> startVoice());
        micPermission = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) startVoice(); });
        MaterialButton talk = findViewById(R.id.talk_to_person);
        talk.setOnClickListener(v -> talkToPerson());

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        stats = HelpStats.fromJson(p.getString(KEY_STATS, ""));
        // Opened from the app: use this owner's real details. Demo personas stay in the debug panel.
        persona = getIntent().getBooleanExtra(EXTRA_DEMO, false) ? p.getInt(KEY_PERSONA, 0) : APP_PERSONA;
        llm = new LlmClient(BuildConfig.ANTHROPIC_API_KEY);

        setInputEnabled(false);
        io.execute(() -> {
            try {
                kb = KnowledgeBase.load(open("help_kb.json"));
                engine = new RulesEngine(ProductCatalogue.load(open("product_catalogue.csv")));
                gstin = GstinLookup.load(open("mock_gstin_registry.csv"));
                systemPrompt = KnowledgeBase.readAll(open("help_system_prompt.txt"));
                runOnUiThread(() -> {
                    startSession(persona);
                    setInputEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.help_load_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private InputStream open(String asset) throws IOException {
        return getAssets().open(asset);
    }

    private void setInputEnabled(boolean on) {
        input.setEnabled(on);
        findViewById(R.id.send).setEnabled(on);
        findViewById(R.id.mic).setEnabled(on);
    }

    private Lang ui() {
        return lastReply != null ? lastReply.language : session.uiLanguage();
    }

    private String t(String phraseKey) {
        return kb.phrase(phraseKey, ui());
    }

    // ================================================================== session

    private void startSession(int personaIndex) {
        persona = personaIndex;
        HelpContext ctx;
        if (personaIndex == APP_PERSONA) {
            ctx = AppHelpContext.build(this, engine);
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_PERSONA, personaIndex).apply();
            ctx = DemoPersonas.build(personaIndex, engine);
        }
        ctx.llmAvailable = llm.isConfigured() && isOnline();
        session = new HelpSession(kb, engine, gstin, ctx, stats);
        lastReply = null;
        history.clear();
        showTopics();
        updateBanner();
        saveStats();
    }

    /** Every message, tapped or typed or spoken, goes through here. */
    private void send(String msg) {
        history.add(new ChatAdapter.Message(msg, true));
        session.context().llmAvailable = llm.isConfigured() && isOnline();
        HelpReply reply = session.handle(msg);
        lastReply = reply;
        history.add(new ChatAdapter.Message(reply.text, false));
        saveStats();
        updateBanner();
        if (reply.ticket != null && reply.band() == EscalationScorer.Band.HANDOFF) {
            showHandoff(reply.ticket, reply.text);
        } else {
            showAnswer(reply);
            polishInBackground(reply, msg);
        }
    }

    private void sendTyped() {
        spokenTurn = false;
        CharSequence cs = input.getText();
        String msg = cs == null ? "" : cs.toString().trim();
        if (msg.isEmpty() || session == null) return;
        input.setText("");
        hideKeyboard();
        if (problemStartedAt == 0) problemStartedAt = SystemClock.elapsedRealtime();
        send(msg);
    }

    // ================================================================== screens

    private void showTopics() {
        step = Step.TOPICS;
        currentTopic = null;
        problemStartedAt = 0;
        setStepProgress(1);
        say(null, t("flow_pick_topic"));
        cards.removeAllViews();
        for (KnowledgeBase.Topic topic : kb.topics()) {
            addCard(topic.icon + "   " + topic.label(ui()), false, v -> showProblems(topic));
        }
        enter();
    }

    private void showProblems(KnowledgeBase.Topic topic) {
        step = Step.PROBLEMS;
        currentTopic = topic;
        problemStartedAt = SystemClock.elapsedRealtime();
        setStepProgress(2);
        say(null, t("flow_pick_problem"));
        cards.removeAllViews();
        for (String q : topic.questions(ui())) addCard(q, false, v -> send(q));
        addCard(t("flow_type"), false, v -> focusInput());
        addCard(t("flow_back"), false, v -> showTopics());
        enter();
    }

    private void showAnswer(HelpReply r) {
        step = Step.ANSWER;
        setStepProgress(3);
        say(null, r.text);
        speakReply(r.text, r.language);
        spokenTurn = false;
        cards.removeAllViews();

        String talk = t("chip_talk_person"), chatAgent = t("chip_chat_agent"), callback = t("chip_callback");
        for (int i = 0; i < r.quickReplies.size(); i++) {
            final String label = r.quickReplies.get(i);
            if (label.equals(talk) || label.equals(chatAgent)) continue; // the button below is always there
            if (label.equals(callback)) {
                addCard("📞  " + label, false, v -> showCallbackSlots(ensureTicket()));
                continue;
            }
            final boolean accept = i == 0 && isAcceptAction(r.action);
            addCard(label, false, v -> {
                if (accept) {
                    history.add(new ChatAdapter.Message(label, true));
                    session.acceptAction(r.intentId);
                    openForAction(r.action);
                    String ack = t("accepted_ack");
                    history.add(new ChatAdapter.Message(ack, false));
                    updateBanner();
                    showAck(ack);
                } else {
                    send(label);
                }
            });
        }
        addSolvedOrStill();
        enter();
    }

    /** After the user accepted a suggestion: confirm, then ask if it is solved. */
    private void showAck(String ack) {
        say(null, ack);
        cards.removeAllViews();
        addSolvedOrStill();
        enter();
    }

    private void addSolvedOrStill() {
        addCard(t("flow_solved_btn"), true, v -> {
            send(t("flow_solved_msg"));
            showSolved();
        });
        addCard(t("flow_still_btn"), false, v -> send(t("flow_still_msg")));
    }

    private void showSolved() {
        step = Step.SOLVED;
        setStepProgress(3);
        long secs = problemStartedAt == 0 ? 0 : Math.max(1, (SystemClock.elapsedRealtime() - problemStartedAt) / 1000);
        say(t("flow_solved_title"), t("flow_solved_text").replace("{seconds}", String.valueOf(secs)));
        screen.setBackgroundColor(ContextCompat.getColor(this, R.color.solved_bg));
        cards.removeAllViews();
        addCard(t("flow_ask_more"), true, v -> showTopics());
        celebrate();
    }

    private void showHandoff(HandoffTicket ticket, String botText) {
        step = Step.HANDOFF;
        setStepProgress(3);
        String detail = t("flow_handoff_text")
                .replace("{ticket_id}", ticket.ticketId)
                .replace("{wait_minutes}", String.valueOf(ticket.expectedWaitMinutes()));
        // Keep the KB answer (e.g. the OTP warning) and add the handoff details.
        String first = botText == null ? "" : botText.split("\n\n")[0];
        say(t("flow_handoff_title"), first.isEmpty() ? detail : first + "\n\n" + detail);
        speakReply(first.isEmpty() ? detail : first + ". " + detail, lastReply == null ? null : lastReply.language);
        spokenTurn = false;
        screen.setBackgroundColor(ContextCompat.getColor(this, R.color.handoff_bg));
        cards.removeAllViews();
        Lang l = ui();
        final String[] slots = {kb.phrase("slot_15min", l), kb.phrase("slot_today", l), kb.phrase("slot_tomorrow", l)};
        for (String slot : slots) {
            addCard("📞  " + slot, false, v -> bookCallback(ticket, slot));
        }
        addCard("💬  " + t("chip_chat_agent"), false, v -> {
            String msg = getString(R.string.agent_joining, ticket.expectedWaitMinutes());
            history.add(new ChatAdapter.Message(msg, false));
            say(t("flow_handoff_title"), msg);
            cards.removeAllViews();
            addCard(t("flow_back_topics"), false, x -> showTopics());
        });
        addCard(t("flow_back_topics"), false, v -> showTopics());
        enter();
    }

    private void bookCallback(HandoffTicket ticket, String slot) {
        session.setCallbackSlot(slot);
        String msg = t("callback_booked").replace("{slot}", slot).replace("{ticket_id}", ticket.ticketId);
        history.add(new ChatAdapter.Message(msg, false));
        say("✅", msg);
        cards.removeAllViews();
        addCard(t("flow_back_topics"), false, v -> showTopics());
        enter();
    }

    private void showCallbackSlots(HandoffTicket ticket) {
        showHandoff(ticket, null);
    }

    private HandoffTicket ensureTicket() {
        return session.ticket() != null ? session.ticket() : session.requestHuman().ticket;
    }

    /** Always allowed, never blocked. */
    private void talkToPerson() {
        if (session == null) return;
        HelpReply r = session.requestHuman();
        history.add(new ChatAdapter.Message(r.text, false));
        saveStats();
        showHandoff(r.ticket, null);
    }

    // ================================================================== view helpers

    private void say(String title, String text) {
        screen.setBackgroundColor(ContextCompat.getColor(this, R.color.screen_bg));
        if (title == null) {
            speechTitle.setVisibility(View.GONE);
        } else {
            speechTitle.setVisibility(View.VISIBLE);
            speechTitle.setText(title);
        }
        speech.setText(text);
    }

    private void setStepProgress(int n) {
        progress.setProgressCompat(n, true);
        stepLabel.setText(t("flow_step").replace("{n}", String.valueOf(n)));
    }

    private void addCard(String label, boolean good, View.OnClickListener onTap) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        c.setTextColor(ContextCompat.getColor(this, good ? R.color.good_text : R.color.card_text));
        c.setBackgroundResource(good ? R.drawable.card_good : R.drawable.card_answer);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setMinHeight(dp(60));
        c.setPadding(dp(18), dp(14), dp(18), dp(18)); // extra bottom padding sits on the 3D edge
        c.setClickable(true);
        c.setFocusable(true);
        c.setOnClickListener(onTap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        cards.addView(c, lp);
    }

    /** Cards slide up one after another, like a lesson screen. */
    private void enter() {
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
        for (int i = 0; i < cards.getChildCount(); i++) {
            View v = cards.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(24));
            v.animate().alpha(1f).translationY(0).setStartDelay(40L * i).setDuration(220).start();
        }
        mascot.setScaleX(0.9f);
        mascot.setScaleY(0.9f);
        mascot.animate().scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(new OvershootInterpolator()).start();
    }

    private void celebrate() {
        ObjectAnimator up = ObjectAnimator.ofFloat(mascot, View.TRANSLATION_Y, 0f, -dp(18), 0f);
        ObjectAnimator spin = ObjectAnimator.ofFloat(mascot, View.ROTATION, 0f, -10f, 10f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(up, spin);
        set.setDuration(700);
        set.setInterpolator(new OvershootInterpolator());
        set.start();
        enter();
    }

    private void focusInput() {
        say(null, t("flow_type_hint"));
        input.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** "Open tracker", "Change visit time", "Retry payment"... open the app screen itself. */
    private void openForAction(String action) {
        Class<?> target = null;
        boolean slots = false;
        switch (action) {
            case "SHOW_TRACKER": target = com.udyam.digital.orderui.TrackerActivity.class; break;
            case "RESCHEDULE_VISIT": target = com.udyam.digital.orderui.TrackerActivity.class; slots = true; break;
            case "RETRY_PAYMENT": target = com.udyam.digital.orderui.CheckoutActivity.class; break;
            case "OPEN_KYC_UPLOAD": target = com.udyam.digital.kycui.KycActivity.class; break;
            default: break;
        }
        if (target != null && (target == com.udyam.digital.kycui.KycActivity.class
                || new com.udyam.digital.orderui.OrderRepository(this).hasOrder())) {
            startActivity(new android.content.Intent(this, target)
                    .putExtra(com.udyam.digital.orderui.TrackerActivity.EXTRA_OPEN_SLOTS, slots));
        }
    }

    private static boolean isAcceptAction(String action) {
        switch (action) {
            case "RERUN_ENGINE": case "SWITCH_TO_AIRFIBER": case "SHOW_TRACKER": case "OPEN_KYC_UPLOAD":
            case "SHOW_SIM_ONLY": case "RESCHEDULE_VISIT": case "SEND_SIGNATORY_LINK": case "SET_REMINDER":
            case "RETRY_PAYMENT": case "START_GSTIN_HELP": case "OPEN_QUOTE":
                return true;
            default:
                return false;
        }
    }

    // ================================================================== AI rewrite

    /** Step 3 of AnswerComposer: the AI rewrites the KB answer in the user's language. */
    private void polishInBackground(HelpReply reply, String userMsg) {
        HelpContext ctx = session.context();
        if (!ctx.llmAvailable || reply.entry == null) return;
        final HelpSession s = session;
        final String kbPart = reply.text.split("\n\n")[0];
        final String rest = reply.text.length() > kbPart.length() ? reply.text.substring(kbPart.length()) : "";
        io.execute(() -> {
            String out = s.composer().polish(llm, systemPrompt, reply.entry, ctx, userMsg, kbPart, reply.language);
            if (!out.equals(kbPart)) {
                runOnUiThread(() -> {
                    if (s == session && lastReply == reply && step == Step.ANSWER) {
                        speech.setText(out + rest);
                        history.set(history.size() - 1, new ChatAdapter.Message(out + rest, false));
                    }
                });
            }
        });
    }

    // ================================================================== banner, history, debug

    private void updateBanner() {
        HelpContext c = session.context();
        Lang lang = ui();
        if (c.orderStatus == HelpContext.OrderStatus.NONE) {
            // No plan picked yet (help opened from the first screens): nothing to show.
            boolean hasPlan = c.recommendation != null && c.monthlyBundleValue() > 0;
            banner.setVisibility(hasPlan ? android.view.View.VISIBLE : android.view.View.GONE);
            if (hasPlan) banner.setText(getString(R.string.banner_quote, AnswerComposer.rs(c.monthlyBundleValue())));
            return;
        }
        banner.setVisibility(android.view.View.VISIBLE);
        StringBuilder b = new StringBuilder();
        if (!TextUtils.isEmpty(c.orderId)) b.append(c.orderId).append(" · ");
        b.append(kb.phrase("tracker_" + c.orderStatus.name(), lang));
        if (c.expectedDate != null && c.installPending()) {
            b.append(" · ").append(kb.phrase("tracker_expected", lang).replace("{date}", c.expectedDate.toString()));
            if (c.daysLate() > 0) b.append(" · ").append(kb.phrase("tracker_late", lang).replace("{days}", String.valueOf(c.daysLate())));
        }
        banner.setText(b.toString());
    }

    private void showHistory() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.history_empty);
            empty.setPadding(dp(24), dp(24), dp(24), dp(24));
            sheet.setContentView(empty);
        } else {
            RecyclerView rv = new RecyclerView(this);
            rv.setLayoutManager(new LinearLayoutManager(this));
            ChatAdapter a = new ChatAdapter();
            for (ChatAdapter.Message m : history) a.add(m);
            rv.setAdapter(a);
            rv.setPadding(0, dp(12), 0, dp(24));
            rv.setClipToPadding(false);
            rv.scrollToPosition(a.getItemCount() - 1);
            sheet.setContentView(rv);
        }
        sheet.show();
    }

    private void showDebugPanel() {
        if (session == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        sv.addView(box);

        EscalationScorer.Result s = session.lastScore();
        StringBuilder t = new StringBuilder();
        t.append("ESCALATION SCORE: ").append(s.score).append("  (").append(s.band).append(", priority ").append(s.priority).append(")\n");
        for (String r : s.reasons) t.append("  ").append(r).append('\n');
        if (lastReply != null) {
            t.append("\nLast intent: ").append(lastReply.intentId)
                    .append(String.format(Locale.ROOT, " (confidence %.2f)", lastReply.confidence))
                    .append("\nAnswer language: ").append(lastReply.language.displayName)
                    .append("\nAI rewrite: ").append(session.context().llmAvailable ? "on (" + LlmClient.MODEL + ")" : "off (offline KB)");
        }
        HandoffTicket ticket = session.ticket();
        if (ticket != null) {
            t.append("\n\nHANDOFF TICKET ").append(ticket.ticketId).append(" · ").append(ticket.priority)
                    .append(ticket.callbackSlot.isEmpty() ? "" : " · callback " + ticket.callbackSlot).append('\n');
            for (String line : ticket.summary) t.append("  ").append(line).append('\n');
        }
        t.append("\nHELP STATS\n  Chats: ").append(stats.chats())
                .append("\n  Resolved by bot: ").append(stats.percentResolvedByBot()).append('%')
                .append(String.format(Locale.ROOT, "\n  Average score: %.1f", stats.averageScore()))
                .append("\n  Handoffs: ").append(stats.handoffs()).append('\n');
        for (Map.Entry<String, Integer> e : stats.handoffsByReason().entrySet()) {
            t.append("    ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        tv.setText(t.toString());
        box.addView(tv);

        TextView head = new TextView(this);
        head.setText(R.string.demo_personas);
        head.setPadding(0, pad, 0, pad / 2);
        box.addView(head);
        for (int i = 0; i < DemoPersonas.NAMES.length; i++) {
            final int idx = i;
            MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(DemoPersonas.NAMES[i] + (i == persona ? "  ✓" : ""));
            b.setOnClickListener(v -> {
                sheet.dismiss();
                startSession(idx);
            });
            box.addView(b);
        }
        sheet.setContentView(sv);
        sheet.show();
    }

    // ================================================================== voice

    private com.udyam.digital.voice.VoiceInput voiceIn;
    private final java.util.Map<Lang, com.udyam.digital.ui.Speaker> voices = new java.util.EnumMap<>(Lang.class);
    /** The last question was spoken: reply out loud too. */
    private boolean spokenTurn;

    private Lang voiceLang() { return session.context().appLanguage == null ? Lang.HI : session.context().appLanguage; }

    /** Mic: listens in the app's language (offline when the pack is installed), live text in the box. */
    private void startVoice() {
        if (voiceIn == null) voiceIn = new com.udyam.digital.voice.VoiceInput(this, voiceLang().speechTag);
        com.udyam.digital.voice.VoiceMode.withMic(this, micPermission, () -> voiceIn.checkAvailability(a -> {
            if (a == com.udyam.digital.voice.VoiceInput.Availability.UNAVAILABLE) {
                Toast.makeText(this, getString(R.string.voice_not_available, languageName()), Toast.LENGTH_LONG).show();
                return;
            }
            voiceIn.listen(new com.udyam.digital.voice.VoiceInput.Listener() {
                @Override public void onListening() {
                    Toast.makeText(HelpActivity.this, R.string.voice_listening, Toast.LENGTH_SHORT).show();
                }
                @Override public void onPartial(String text) { input.setText(text); input.setSelection(text.length()); }
                @Override public void onResult(List<String> heard, float[] conf) {
                    input.setText("");
                    if (problemStartedAt == 0) problemStartedAt = SystemClock.elapsedRealtime();
                    spokenTurn = true;
                    send(heard.get(0));
                }
                @Override public void onFailed(boolean unavailable) {
                    Toast.makeText(HelpActivity.this, unavailable ? getString(R.string.voice_not_available, languageName())
                            : getString(R.string.voice_error), Toast.LENGTH_SHORT).show();
                }
            });
        }));
    }

    private String languageName() {
        return com.udyam.digital.model.Language.fromTags(voiceLang().code).nativeName;
    }

    /** Bot replies are read aloud in their own language in voice mode, or when the question was spoken. */
    private void speakReply(String text, Lang lang) {
        if (text == null || !(spokenTurn || com.udyam.digital.voice.VoiceMode.isOn(this))) return;
        if (com.udyam.digital.voice.VoiceMode.screenReader(this)) return; // TalkBack reads the bubble itself
        Lang l = lang == null ? voiceLang() : lang;
        com.udyam.digital.model.Language ml = com.udyam.digital.model.Language.fromTags(l.code);
        com.udyam.digital.ui.Speaker sp = voices.get(l);
        if (sp == null) { sp = new com.udyam.digital.ui.Speaker(this, ml, null); voices.put(l, sp); }
        // No emoji or symbols; prices as words ("₹3,100" -> "तीन हज़ार एक सौ रुपये").
        String clean = com.udyam.digital.voice.VoiceLexicon.spoken(
                text.replaceAll("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}*_#•]", " "), ml).replaceAll("\\s+", " ").trim();
        sp.speak(clean);
    }

    // ================================================================== misc

    @Override
    public void onBackPressed() {
        // Back walks the steps instead of closing help.
        if (step == Step.PROBLEMS || step == Step.ANSWER || step == Step.SOLVED || step == Step.HANDOFF) {
            if (step == Step.ANSWER && currentTopic != null) showProblems(currentTopic);
            else showTopics();
            return;
        }
        super.onBackPressed();
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void saveStats() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STATS, stats.toJson()).apply();
    }

    @Override
    protected void onDestroy() {
        if (voiceIn != null) voiceIn.destroy();
        for (com.udyam.digital.ui.Speaker sp : voices.values()) sp.shutdown();
        io.shutdownNow();
        super.onDestroy();
    }
}
