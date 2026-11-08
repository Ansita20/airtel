package com.udyam.digital.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.udyam.digital.R;
import com.udyam.digital.data.CityNames;
import com.udyam.digital.data.CityTiers;
import com.udyam.digital.data.DataRepository;
import com.udyam.digital.data.GstinLookup;
import com.udyam.digital.data.QuestionBank;
import com.udyam.digital.engine.NeedMap;
import com.udyam.digital.engine.ProductScorer;
import com.udyam.digital.engine.RulesEngine;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.Language;
import com.udyam.digital.model.Need;
import com.udyam.digital.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Screen 2: one short question per screen, emoji tiles in a grid, progress bar, read-aloud (🔊)
 * and speak-your-answer (🎤). In voice mode ("Talk instead of type") each question is read out,
 * the answer is heard, understood (VoiceAnswerParser), confirmed ("You said: 3 people. Correct?")
 * and the app moves on by itself. Tapping always works and stops the voice flow.
 */
public class QuestionnaireActivity extends AppCompatActivity {
    private static final String KEY_INDEX = "index";
    private static final long ADVANCE_DELAY_MS = 350;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<Question> questions;
    private int index;
    private boolean advancing;

    private LinearProgressIndicator progress;
    private TextView title;
    private View speakButton;
    private Speaker speaker;
    private TextView subtitle;
    private ScrollView scroll;
    private LinearLayout container;
    private Button continueButton;

    // Voice
    private static final Object VOICE = new Object();
    private com.udyam.digital.voice.VoiceInput voice;
    private androidx.activity.result.ActivityResultLauncher<String> micPermission;
    private TextView voiceStatus, speakToggle, micButton;
    private androidx.appcompat.app.AlertDialog voiceDialog;
    private java.util.Set<String> onlyValues;
    private int misses;
    private boolean permissionPending;
    // GSTIN screen widgets, for answers by voice.
    private TextInputEditText gstinInput;
    private View gstinCheck, gstinNone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questionnaire);

        questions = QuestionBank.build(lang());
        index = savedInstanceState != null ? savedInstanceState.getInt(KEY_INDEX, 0) : 0;

        progress = findViewById(R.id.progress);
        speakButton = findViewById(R.id.speak_button);
        speakToggle = (TextView) speakButton;
        // Always visible: if this phone has no voice for the language yet, a tap explains how to get it.
        speakButton.setVisibility(View.VISIBLE);
        speakButton.setOnClickListener(v -> toggleReadAloud());
        speaker = new Speaker(this, lang(), state -> speakButton.setAlpha(state == Speaker.State.READY ? 1f : 0.6f));
        showReadAloud();
        voiceStatus = findViewById(R.id.voice_status);
        micButton = findViewById(R.id.mic_button);
        micButton.setOnClickListener(v -> { cancelVoice(); misses = 0; listen(); });
        voice = new com.udyam.digital.voice.VoiceInput(this, com.udyam.digital.voice.VoiceInput.tagFor(lang()));
        micPermission = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
            permissionPending = false;
            if (granted) listen();
            else status(getString(R.string.voice_not_available, lang().nativeName));
        });
        title = findViewById(R.id.question_title);
        subtitle = findViewById(R.id.question_subtitle);
        scroll = findViewById(R.id.options_scroll);
        container = findViewById(R.id.options_container);
        continueButton = findViewById(R.id.continue_button);

        findViewById(R.id.back_button).setOnClickListener(v -> goBack());
        HelpButton.wire(findViewById(R.id.help_button), this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBack(); }
        });
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_INDEX, index);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        speaker.shutdown();
        voice.destroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speaker.retry(); // the owner may have just downloaded the voice
        // Back from another screen in voice mode: carry on with this question.
        if (resumed && !permissionPending && com.udyam.digital.voice.VoiceMode.isOn(this) && voiceDialog == null) startTurn();
        resumed = true;
    }

    private boolean resumed;

    @Override
    protected void onPause() {
        if (!permissionPending) cancelVoice();
        super.onPause();
    }

    private Language lang() { return AppState.language(); }

    /** Max answer choices read aloud (the city list can be long). */
    private static final int SPOKEN_OPTIONS = 8;

    /** Question, hint and up to 8 answer choices, as short sentences in the chosen language. */
    private String prompt(Question q) {
        Language l = lang();
        StringBuilder say = new StringBuilder(q.text(l));
        String sub = q.subtitle(l);
        if (sub != null) say.append(". ").append(sub);
        if (q.getType() == Question.Type.GSTIN) say.append(". ").append(getString(R.string.voice_gstin_prompt));
        List<Question.Option> opts = q.getOptions();
        if (opts != null && !opts.isEmpty()) {
            for (int i = 0; i < opts.size() && i < SPOKEN_OPTIONS; i++) {
                say.append(". ").append(com.udyam.digital.voice.VoiceLexicon.spoken(opts.get(i).label(l), l));
            }
        }
        return say.append('.').toString().replace("?.", "?");
    }

    private void showReadAloud() {
        boolean on = com.udyam.digital.voice.VoiceMode.readAloud(this);
        speakToggle.setText(on ? "🔊" : "🔈");
        speakToggle.setContentDescription(getString(on ? R.string.voice_read_on : R.string.voice_read_off));
    }

    /** 🔊 turns reading aloud on (and reads now) or off. */
    private void toggleReadAloud() {
        boolean on = !com.udyam.digital.voice.VoiceMode.readAloud(this);
        com.udyam.digital.voice.VoiceMode.setReadAloud(this, on);
        showReadAloud();
        if (!on) {
            speaker.stop();
            return;
        }
        speakQuestion();
    }

    /** Reads the question now; explains a missing voice for this language. */
    private void speakQuestion() {
        Language l = lang();
        Speaker.State st = speaker.speak(prompt(questions.get(index)));
        if (st == Speaker.State.MISSING_VOICE) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.tts_missing_voice, l.nativeName))
                    .setPositiveButton(R.string.tts_download, (d, w) -> open(speaker.installVoiceIntent()))
                    .setNegativeButton(R.string.tts_not_now, null)
                    .show();
        } else if (st == Speaker.State.NOT_SUPPORTED || st == Speaker.State.NO_ENGINE) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.tts_not_supported, l.nativeName))
                    .setPositiveButton(R.string.tts_get_engine, (d, w) -> open(Speaker.getEngineIntent()))
                    .setNegativeButton(R.string.tts_not_now, null)
                    .show();
        }
    }

    // ================================================================== voice flow

    private void later(Runnable r, long ms) { androidx.core.os.HandlerCompat.postDelayed(handler, r, VOICE, ms); }

    /**
     * Says {@code text} - with our own voice if reading aloud is on, through TalkBack if it is
     * running (we never talk over it) - then runs {@code then}.
     */
    private void say(String text, Runnable then) {
        if (com.udyam.digital.voice.VoiceMode.screenReader(this)) {
            title.announceForAccessibility(text);
            if (then != null) later(then, 1200 + 75L * text.length());
        } else if (com.udyam.digital.voice.VoiceMode.readAloud(this)) {
            speaker.speak(text, then);
        } else if (then != null) {
            later(then, 150);
        }
    }

    /** A new question is on screen. Voice mode: read it, then listen. Else: just read it if read-aloud is on. */
    private void startTurn() {
        cancelVoice();
        misses = 0;
        Question q = questions.get(index);
        if (com.udyam.digital.voice.VoiceMode.isOn(this)) later(() -> say(prompt(q), this::listen), 400);
        else if (com.udyam.digital.voice.VoiceMode.readAloud(this)) later(() -> say(prompt(q), null), 400);
    }

    private void status(String text) {
        voiceStatus.setVisibility(text == null ? View.GONE : View.VISIBLE);
        voiceStatus.setText(text);
    }

    /** Stops listening, speaking, pending steps and voice dialogs. Any tap calls this. */
    private void cancelVoice() {
        handler.removeCallbacksAndMessages(VOICE);
        voice.stop();
        speaker.stop();
        onlyValues = null;
        if (voiceDialog != null) {
            androidx.appcompat.app.AlertDialog d = voiceDialog;
            voiceDialog = null;
            d.dismiss();
        }
        micButton.setSelected(false);
        status(null);
    }

    /** Microphone (asks permission with a one-line reason), language support check, then listen. */
    private void listen() {
        if (!com.udyam.digital.voice.VoiceMode.hasMic(this)) permissionPending = true;
        com.udyam.digital.voice.VoiceMode.withMic(this, micPermission, () -> voice.checkAvailability(a -> {
            if (a == com.udyam.digital.voice.VoiceInput.Availability.UNAVAILABLE) {
                unavailable();
                return;
            }
            micButton.setSelected(true);
            status("🎤 " + getString(R.string.voice_hearing));
            voice.listen(new com.udyam.digital.voice.VoiceInput.Listener() {
                @Override public void onListening() { status("🎤 " + getString(R.string.voice_hearing)); }
                @Override public void onPartial(String text) { status("🎤 " + text); }
                @Override public void onResult(List<String> heard, float[] conf) {
                    micButton.setSelected(false);
                    status("“" + heard.get(0) + "”");
                    if (voiceDialog != null) return; // a dialog listens for itself
                    understand(heard, conf);
                }
                @Override public void onFailed(boolean languageUnavailable) {
                    micButton.setSelected(false);
                    if (languageUnavailable) unavailable(); else notCaught();
                }
            });
        }));
    }

    /** "Voice is not available in Tamil on this phone - you can tap instead." Taps keep working. */
    private void unavailable() {
        String msg = getString(R.string.voice_not_available, lang().nativeName);
        status(msg);
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    private void notCaught() {
        if (++misses > 2) { // stop asking; the tiles are right there
            status(getString(R.string.voice_not_caught));
            return;
        }
        String msg = getString(R.string.voice_not_caught);
        status(msg);
        say(msg, this::listen);
    }

    private void understand(List<String> heard, float[] conf) {
        Question q = questions.get(index);
        if (q.getType() == Question.Type.GSTIN) {
            gstinByVoice(heard);
            return;
        }
        com.udyam.digital.voice.VoiceAnswerParser.Result r =
                com.udyam.digital.voice.VoiceAnswerParser.parse(q, heard, conf, lang(), onlyValues);
        onlyValues = null;
        if (r.confident() || (r.understood() && r.second() == null)) confirm(q, r);
        else if (r.second() != null) reask(q, r);
        else notCaught();
    }

    /** What we understood, as it should be shown and spoken. */
    private String answerText(Question q, com.udyam.digital.voice.VoiceAnswerParser.Result r) {
        Language l = lang();
        if (r.nothing) return getString(R.string.voice_nothing);
        com.udyam.digital.voice.VoiceAnswerParser.Candidate c = r.best();
        if (q.getType() == Question.Type.MULTI) {
            List<String> labels = new ArrayList<>();
            for (Question.Option o : q.getOptions()) if (r.values.contains(o.getValue())) {
                labels.add(com.udyam.digital.voice.VoiceLexicon.spoken(o.label(l), l));
            }
            return String.join(", ", labels);
        }
        if (c.number != null && "employees".equals(q.getId())) return getString(R.string.voice_people, c.heard);
        if (c.number != null && "sites".equals(q.getId())) return getString(R.string.voice_branches, c.heard);
        return com.udyam.digital.voice.VoiceLexicon.spoken(c.label, l);
    }

    private androidx.appcompat.app.AlertDialog voiceDialog(com.google.android.material.dialog.MaterialAlertDialogBuilder b) {
        androidx.appcompat.app.AlertDialog d = b.create();
        d.setOnDismissListener(x -> { if (voiceDialog == d) { voiceDialog = null; voice.stop(); speaker.stop(); } });
        voiceDialog = d;
        d.show();
        return d;
    }

    /** "You said: 3 people. Correct?" - yes / no by voice or tap. Always asked before moving on. */
    private void confirm(Question q, com.udyam.digital.voice.VoiceAnswerParser.Result r) {
        String msg = getString(R.string.voice_you_said, answerText(q, r));
        voiceDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(msg)
                .setPositiveButton(R.string.voice_yes, (d, w) -> accept(q, r))
                .setNegativeButton(R.string.voice_no, (d, w) -> again()));
        say(msg, () -> hearYesNo(q, r, msg, 0));
    }

    private void hearYesNo(Question q, com.udyam.digital.voice.VoiceAnswerParser.Result r, String msg, int tries) {
        if (voiceDialog == null) return;
        com.udyam.digital.voice.VoiceMode.withMic(this, micPermission, () -> voice.listen(new com.udyam.digital.voice.VoiceInput.Listener() {
            @Override public void onListening() { status("🎤 " + getString(R.string.voice_hearing)); }
            @Override public void onPartial(String text) { status("🎤 " + text); }
            @Override public void onResult(List<String> heard, float[] conf) {
                switch (com.udyam.digital.voice.VoiceAnswerParser.yesNo(heard, lang())) {
                    case YES: dismissVoiceDialog(); accept(q, r); break;
                    case NO: dismissVoiceDialog(); again(); break;
                    default: if (tries < 1) say(msg, () -> hearYesNo(q, r, msg, tries + 1)); else status(msg);
                }
            }
            @Override public void onFailed(boolean unavailable) {
                if (tries < 1 && !unavailable) say(msg, () -> hearYesNo(q, r, msg, tries + 1)); else status(msg);
            }
        }));
    }

    private void dismissVoiceDialog() {
        if (voiceDialog != null) {
            androidx.appcompat.app.AlertDialog d = voiceDialog;
            voiceDialog = null;
            d.dismiss();
        }
    }

    /** "Sorry, I didn't catch that. Was it 3 or 13?" with both as buttons. */
    private void reask(Question q, com.udyam.digital.voice.VoiceAnswerParser.Result r) {
        com.udyam.digital.voice.VoiceAnswerParser.Candidate a = r.best(), b = r.second();
        String sa = a.number != null ? a.heard : com.udyam.digital.voice.VoiceLexicon.spoken(a.label, lang());
        String sb = b.number != null ? b.heard : com.udyam.digital.voice.VoiceLexicon.spoken(b.label, lang());
        String msg = getString(R.string.voice_reask, sa, sb);
        voiceDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(msg)
                .setPositiveButton(sa, (d, w) -> accept(q, only(a.value)))
                .setNegativeButton(sb, (d, w) -> accept(q, only(b.value)))
                .setNeutralButton(R.string.voice_say_again, (d, w) -> again()));
        java.util.Set<String> two = new java.util.HashSet<>(java.util.Arrays.asList(a.value, b.value));
        say(msg, () -> hearChoice(q, two, msg));
    }

    /** Listens for the answer to "Was it A or B?" (only those two count), with the buttons still on screen. */
    private void hearChoice(Question q, java.util.Set<String> two, String msg) {
        if (voiceDialog == null) return;
        voice.listen(new com.udyam.digital.voice.VoiceInput.Listener() {
            @Override public void onListening() { status("🎤 " + getString(R.string.voice_hearing)); }
            @Override public void onPartial(String text) { status("🎤 " + text); }
            @Override public void onResult(List<String> heard, float[] conf) {
                com.udyam.digital.voice.VoiceAnswerParser.Result r =
                        com.udyam.digital.voice.VoiceAnswerParser.parse(q, heard, conf, lang(), two);
                if (!r.understood()) { status(msg); return; }
                dismissVoiceDialog();
                confirm(q, r);
            }
            @Override public void onFailed(boolean unavailable) { status(msg); }
        });
    }

    /** A tapped choice from the "Was it A or B?" dialog: explicit, no further confirmation needed. */
    private static com.udyam.digital.voice.VoiceAnswerParser.Result only(String value) {
        return com.udyam.digital.voice.VoiceAnswerParser.chosen(value);
    }

    /** "No": ask the question again. */
    private void again() {
        cancelVoice();
        misses = 0;
        say(prompt(questions.get(index)), this::listen);
    }

    /** Applies the confirmed answer exactly as a tap would, then moves on. */
    private void accept(Question q, com.udyam.digital.voice.VoiceAnswerParser.Result r) {
        cancelVoice();
        if (q.getType() == Question.Type.MULTI) {
            for (Question.Option o : q.getOptions()) {
                Need need = Need.valueOf(o.getValue());
                boolean want = !r.nothing && r.values.contains(o.getValue());
                if (profile().hasNeed(need) != want) q.apply(profile(), o.getValue()); // toggles
            }
            if (pickedCount(q) < q.getMinPicks()) { render(); return; }
        } else {
            String v = r.values.get(0);
            q.apply(profile(), v);
            AppState.get().answers().put(q.getId(), v);
        }
        advancing = true;
        handler.postDelayed(this::next, ADVANCE_DELAY_MS);
    }

    /** GST number by voice: "no" -> no GSTIN; a spoken GSTIN -> filled in and checked; else please type it. */
    private void gstinByVoice(List<String> heard) {
        com.udyam.digital.voice.VoiceAnswerParser.YesNo yn = com.udyam.digital.voice.VoiceAnswerParser.yesNo(heard, lang());
        if (yn == com.udyam.digital.voice.VoiceAnswerParser.YesNo.NO) {
            String msg = getString(R.string.voice_you_said, getString(R.string.gstin_none));
            voiceDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setMessage(msg)
                    .setPositiveButton(R.string.voice_yes, (d, w) -> { cancelVoice(); if (gstinNone != null) gstinNone.performClick(); })
                    .setNegativeButton(R.string.voice_no, (d, w) -> again()));
            say(msg, null);
            return;
        }
        String g = com.udyam.digital.voice.VoiceAnswerParser.gstin(heard);
        if (g != null && gstinInput != null) {
            gstinInput.setText(g);
            gstinCheck.performClick();
            say(getString(R.string.voice_gstin_check), null);
            return;
        }
        String msg = getString(R.string.voice_gstin_type);
        status(msg);
        say(msg, null);
        if (gstinInput != null) gstinInput.requestFocus();
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, getString(R.string.tts_not_supported, lang().nativeName),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private BusinessProfile profile() { return AppState.get().profile(); }

    /** Questions that don't apply to this business are skipped in both directions. */
    private boolean isRelevant(Question q) {
        if ("internet".equals(q.getId())) return RulesEngine.wantsBroadband(profile());
        return true;
    }

    private void goBack() {
        handler.removeCallbacksAndMessages(null);
        int i = index - 1;
        while (i >= 0 && !isRelevant(questions.get(i))) i--;
        if (i >= 0) {
            index = i;
            render();
        } else {
            finish();
        }
    }

    private void next() {
        int i = index + 1;
        while (i < questions.size() && !isRelevant(questions.get(i))) i++;
        if (i < questions.size()) {
            index = i;
            render();
        } else {
            finishQuestionnaire();
        }
    }

    private int pickedCount(Question q) {
        int n = 0;
        for (Question.Option o : q.getOptions()) if (profile().hasNeed(Need.valueOf(o.getValue()))) n++;
        return n;
    }

    private void finishQuestionnaire() {
        BusinessProfile p = profile();
        p.setLanguage(lang());
        AppState.get().setPacks(DataRepository.get().engine().packs(p));
        advancing = false;
        startActivity(new Intent(this, PlanActivity.class));
    }

    private void render() {
        advancing = false;
        if (voice != null) cancelVoice();
        Question q = questions.get(index);
        Language l = lang();

        speaker.stop();
        progress.setProgressCompat((index + 1) * 100 / questions.size(), true);
        progress.setContentDescription(getString(R.string.step_of, index + 1, questions.size()));
        title.setText(q.text(l));
        String sub = q.subtitle(l);
        subtitle.setText(sub);
        subtitle.setVisibility(sub == null ? View.GONE : View.VISIBLE);

        container.removeAllViews();
        scroll.scrollTo(0, 0);
        continueButton.setVisibility(View.GONE);
        continueButton.setEnabled(true);
        continueButton.setText(index == questions.size() - 1 ? R.string.see_my_plan : R.string.continue_btn);

        switch (q.getType()) {
            case SINGLE: renderSingle(q, l); break;
            case MULTI: renderMulti(q, l); break;
            case GSTIN: renderGstin(q, l); break;
        }

        // Slide the new question in.
        View content = findViewById(R.id.question_content);
        content.setAlpha(0f);
        content.setTranslationX(getResources().getDisplayMetrics().density * 32);
        content.animate().alpha(1f).translationX(0f).setDuration(220).start();
        startTurn();
    }

    private View tile(Question.Option opt, Language l) {
        View tile = OptionCards.create(getLayoutInflater(), container, opt.getIcon(), opt.label(l), null);
        tile.setOnLongClickListener(v -> { speaker.speak(opt.label(l)); return true; });
        return tile;
    }

    private void renderSingle(Question q, Language l) {
        String current = AppState.get().answers().get(q.getId());
        List<View> tiles = new ArrayList<>();
        for (Question.Option opt : q.getOptions()) {
            View tile = tile(opt, l);
            OptionCards.setSelected(tile, opt.getValue().equals(current));
            tile.setOnClickListener(v -> {
                if (advancing) return;
                cancelVoice();
                advancing = true;
                for (View t : tiles) OptionCards.setSelected(t, t == v);
                OptionCards.bounce(v);
                q.apply(profile(), opt.getValue());
                AppState.get().answers().put(q.getId(), opt.getValue());
                handler.postDelayed(this::next, ADVANCE_DELAY_MS);
            });
            tiles.add(tile);
        }
        OptionCards.grid(container, tiles, 2);
    }

    /**
     * "Anything else?" lists needs in the order similar businesses kept the matching products,
     * so a salon sees UPI billing, customer Wi-Fi and CCTV first, an office sees email first.
     * Needs without a learned product (fast internet, always-on line) go right after the top 3.
     */
    private List<Question.Option> ordered(Question q) {
        if (!"needs".equals(q.getId())) return q.getOptions();
        ProductScorer scorer = DataRepository.get().scorer();
        if (scorer.isEmpty()) return q.getOptions();
        List<Question.Option> scored = new ArrayList<>();
        List<Question.Option> unscored = new ArrayList<>();
        Map<Question.Option, Double> score = new HashMap<>();
        for (Question.Option o : q.getOptions()) {
            List<String> pids = NeedMap.rankingProductsFor(Need.valueOf(o.getValue()));
            if (pids.isEmpty()) { unscored.add(o); continue; }
            double best = 0;
            for (String pid : pids) best = Math.max(best, scorer.score(profile(), pid));
            score.put(o, best);
            scored.add(o);
        }
        Collections.sort(scored, (a, b) -> Double.compare(score.get(b), score.get(a))); // stable
        scored.addAll(Math.min(3, scored.size()), unscored);
        return scored;
    }

    private void renderMulti(Question q, Language l) {
        List<View> tiles = new ArrayList<>();
        for (Question.Option opt : ordered(q)) {
            View tile = tile(opt, l);
            Need need = Need.valueOf(opt.getValue());
            OptionCards.setSelected(tile, profile().hasNeed(need));
            tile.setOnClickListener(v -> {
                cancelVoice();
                q.apply(profile(), opt.getValue()); // toggles
                boolean on = profile().hasNeed(need);
                OptionCards.setSelected(tile, on);
                if (on) OptionCards.bounce(tile);
                continueButton.setEnabled(pickedCount(q) >= q.getMinPicks());
            });
            tiles.add(tile);
        }
        OptionCards.grid(container, tiles, 2);
        continueButton.setVisibility(View.VISIBLE);
        continueButton.setEnabled(pickedCount(q) >= q.getMinPicks());
        continueButton.setOnClickListener(v -> next());
    }

    private void renderGstin(Question q, Language l) {
        View view = getLayoutInflater().inflate(R.layout.view_gstin, container, false);
        container.addView(view);

        TextInputLayout layout = view.findViewById(R.id.gstin_layout);
        TextInputEditText input = view.findViewById(R.id.gstin_input);
        TextView result = view.findViewById(R.id.gstin_result);
        FrameLayout noneHolder = view.findViewById(R.id.gstin_none_holder);

        View none = OptionCards.create(getLayoutInflater(), noneHolder, "🙅",
                getString(R.string.gstin_none), getString(R.string.gstin_none_hint));
        noneHolder.addView(none);
        gstinInput = input;
        gstinCheck = view.findViewById(R.id.gstin_check);
        gstinNone = none;

        GstinLookup.Result previous = AppState.get().getGstinResult();
        if (previous != null && previous.isFound()) {
            input.setText(previous.gstin);
            showGstinResult(previous, result);
        } else if ("".equals(AppState.get().answers().get(q.getId()))) {
            OptionCards.setSelected(none, true);
        }

        continueButton.setVisibility(View.VISIBLE);
        continueButton.setEnabled(previous != null && previous.isFound());
        continueButton.setOnClickListener(v -> next());

        View.OnClickListener check = v -> {
            hideKeyboard(input);
            layout.setError(null);
            String raw = input.getText() == null ? "" : input.getText().toString();
            GstinLookup.Result r = DataRepository.get().gstin().lookup(raw);
            switch (r.status) {
                case INVALID_FORMAT:
                    layout.setError(getString(R.string.gstin_bad_format));
                    result.setVisibility(View.GONE);
                    continueButton.setEnabled(false);
                    return;
                case INVALID_CHECKSUM:
                    layout.setError(getString(R.string.gstin_bad_checksum));
                    result.setVisibility(View.GONE);
                    continueButton.setEnabled(false);
                    return;
                case NOT_FOUND:
                    result.setText(R.string.gstin_not_found);
                    result.setVisibility(View.VISIBLE);
                    continueButton.setEnabled(false);
                    return;
                default:
                    break;
            }
            // Found in registry (active, cancelled or suspended).
            OptionCards.setSelected(none, false);
            BusinessProfile p = profile();
            q.apply(p, r.gstin);
            p.setHasValidGstin(r.isActive());
            AppState.get().setGstinResult(r);
            AppState.get().answers().put(q.getId(), r.gstin);
            if (r.city != null) {
                // Auto-fill city from the registry address.
                p.setCity(r.city);
                p.setCityTier(CityTiers.tierOf(r.city));
                AppState.get().answers().put("city", CityTiers.canonical(r.city) != null ? r.city : "Other");
            }
            showGstinResult(r, result);
            continueButton.setEnabled(true);
        };
        view.findViewById(R.id.gstin_check).setOnClickListener(check);
        input.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { check.onClick(tv); return true; }
            return false;
        });

        none.setOnClickListener(v -> {
            if (advancing) return;
            advancing = true;
            hideKeyboard(input);
            OptionCards.setSelected(none, true);
            OptionCards.bounce(none);
            q.apply(profile(), "");
            AppState.get().setGstinResult(null);
            AppState.get().answers().put(q.getId(), "");
            handler.postDelayed(this::next, ADVANCE_DELAY_MS);
        });
    }

    private void showGstinResult(GstinLookup.Result r, TextView result) {
        String name = r.tradeName != null ? r.tradeName : r.legalName;
        if (r.isActive()) {
            result.setText(r.city != null
                    ? getString(R.string.gstin_active, name, CityNames.name(r.city, lang()))
                    : getString(R.string.gstin_active_no_city, name));
        } else {
            String status = getString(r.status == GstinLookup.Status.CANCELLED
                    ? R.string.status_cancelled : R.string.status_suspended);
            result.setText(getString(R.string.gstin_inactive, status));
        }
        result.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
