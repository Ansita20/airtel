package com.udyam.digital.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.udyam.digital.R;
import com.udyam.digital.model.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 1: pick one of 10 languages from big letter tiles, or say it (🎤). "Talk instead of
 * type" turns on voice mode for the whole questionnaire.
 */
public class LanguageActivity extends AppCompatActivity {

    private com.udyam.digital.voice.VoiceInput voice;
    private androidx.activity.result.ActivityResultLauncher<String> micPermission;
    private TextView hint;

    /** Set when the owner deliberately comes back here (Start again): don't jump to the tracker. */
    public static final String EXTRA_NO_REDIRECT = "no_redirect";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Start-up screen with the logo. Held briefly on a cold start so it is actually seen.
        androidx.core.splashscreen.SplashScreen splash = androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        if (savedInstanceState == null) {
            long until = android.os.SystemClock.uptimeMillis() + 900;
            splash.setKeepOnScreenCondition(() -> android.os.SystemClock.uptimeMillis() < until);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language);

        LinearLayout container = findViewById(R.id.language_container);
        LayoutInflater inflater = getLayoutInflater();
        Language current = AppState.language();
        boolean hasChosen = !AppCompatDelegate.getApplicationLocales().isEmpty();

        List<View> tiles = new ArrayList<>();
        for (Language l : Language.values()) {
            String hint = l == Language.EN ? null : l.englishName;
            View tile = OptionCards.create(inflater, container, l.glyph, l.nativeName, hint);
            TextView glyph = tile.findViewById(R.id.option_icon);
            glyph.setTextColor(ContextCompat.getColorStateList(this, R.color.glyph_text));
            OptionCards.setSelected(tile, hasChosen && l == current);
            tile.setOnClickListener(v -> {
                for (View t : tiles) OptionCards.setSelected(t, t == v);
                OptionCards.bounce(v);
                v.postDelayed(() -> choose(l), 180);
            });
            tiles.add(tile);
        }
        OptionCards.grid(container, tiles, 2);
        HelpButton.wire(findViewById(R.id.help_button), this);

        // Talk instead of type.
        com.google.android.material.materialswitch.MaterialSwitch voiceMode = findViewById(R.id.voice_mode);
        hint = findViewById(R.id.voice_mode_hint);
        voiceMode.setChecked(com.udyam.digital.voice.VoiceMode.isOn(this));
        voiceMode.setOnCheckedChangeListener((b, on) -> com.udyam.digital.voice.VoiceMode.set(this, on));
        // Say the language. The recogniser listens in the phone's own language; names are
        // matched in English and every script ("Tamil", "தமிழ்", "तमिल").
        voice = new com.udyam.digital.voice.VoiceInput(this, phoneTag());
        micPermission = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) listenForLanguage(tiles); });
        findViewById(R.id.language_mic).setOnClickListener(v ->
                com.udyam.digital.voice.VoiceMode.withMic(this, micPermission, () -> listenForLanguage(tiles)));

        // An order already exists on this phone: open its tracker straight away.
        com.udyam.digital.orderui.OrderRepository orders = new com.udyam.digital.orderui.OrderRepository(this);
        findViewById(R.id.have_order).setOnClickListener(v -> {
            if (orders.hasOrder()) startActivity(new Intent(this, com.udyam.digital.orderui.TrackerActivity.class));
            else android.widget.Toast.makeText(this, R.string.no_order, android.widget.Toast.LENGTH_LONG).show();
        });
        if (savedInstanceState == null && orders.hasOrder() && !getIntent().getBooleanExtra(EXTRA_NO_REDIRECT, false)) {
            startActivity(new Intent(this, com.udyam.digital.orderui.TrackerActivity.class));
        }

        // Partner mode (back office): long-press the logo, then the demo PIN.
        findViewById(R.id.logo).setOnLongClickListener(v -> {
            partnerLogin();
            return true;
        });

        // Hidden debug screen for demos: long-press the app title.
        findViewById(R.id.app_title).setOnLongClickListener(v -> {
            startActivity(new Intent(this, DebugActivity.class));
            return true;
        });
    }

    /** PIN, then "who are you": Accel field sales (all cities) or a channel partner in one city. */
    private void partnerLogin() {
        com.google.android.material.textfield.TextInputLayout layout = new com.google.android.material.textfield.TextInputLayout(this);
        layout.setHint(getString(R.string.partner_pin_hint));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        com.google.android.material.textfield.TextInputEditText pin = new com.google.android.material.textfield.TextInputEditText(layout.getContext());
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(pin);
        com.udyam.digital.security.SecureScreens.secure(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.partner_pin_title)
                .setView(layout)
                .setPositiveButton(R.string.voice_ok, (d, w) -> {
                    if (com.udyam.digital.partner.PartnerActivity.DEMO_PIN.equals(String.valueOf(pin.getText()))) {
                        pickPartner();
                    } else {
                        com.udyam.digital.security.SecureAuditLog.record(this, com.udyam.digital.security.AccessLog.Actor.PARTNER,
                                com.udyam.digital.security.AccessLog.Action.PARTNER_LOGIN_FAILED, "", "", "");
                        android.widget.Toast.makeText(this, R.string.partner_pin_wrong, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.voice_not_now, null)
                .create()).show();
    }

    private void pickPartner() {
        List<String> cities = new ArrayList<>(com.udyam.digital.data.CityTiers.all());
        java.util.Collections.sort(cities);
        String[] items = new String[cities.size() + 1];
        items[0] = getString(R.string.partner_role_field);
        for (int i = 0; i < cities.size(); i++) items[i + 1] = getString(R.string.partner_role_city, cities.get(i));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.partner_who)
                .setItems(items, (d, i) -> {
                    String city = i == 0 ? null : cities.get(i - 1);
                    com.udyam.digital.security.SecureAuditLog.record(this,
                            city == null ? com.udyam.digital.security.AccessLog.Actor.FIELD_SALES : com.udyam.digital.security.AccessLog.Actor.PARTNER,
                            com.udyam.digital.security.AccessLog.Action.PARTNER_LOGIN, "", city == null ? "ALL" : city, "");
                    com.udyam.digital.partner.PartnerActivity.open(this, city);
                })
                .show();
    }

    /** The phone's language as an Indian locale tag (en-IN when it isn't one of ours). */
    private static String phoneTag() {
        String l = java.util.Locale.getDefault().getLanguage();
        for (Language x : Language.values()) if (x.tag.equals(l)) return x.tag + "-IN";
        return "en-IN";
    }

    private void listenForLanguage(List<View> tiles) {
        voice.checkAvailability(a -> {
            if (a == com.udyam.digital.voice.VoiceInput.Availability.UNAVAILABLE) {
                hint.setText(getString(R.string.voice_not_available, Language.fromTags(phoneTag()).nativeName));
                return;
            }
            hint.setText("🎤 " + getString(R.string.voice_hearing));
            voice.listen(new com.udyam.digital.voice.VoiceInput.Listener() {
                @Override public void onListening() { }
                @Override public void onPartial(String text) { hint.setText("🎤 " + text); }
                @Override public void onResult(List<String> heard, float[] conf) {
                    List<com.udyam.digital.voice.VoiceAnswerParser.Choice> cs = new ArrayList<>();
                    for (Language l : Language.values()) {
                        List<String> names = new ArrayList<>(com.udyam.digital.voice.VoiceLexicon.languageNames(l));
                        names.add(l.nativeName);
                        names.add(l.englishName);
                        cs.add(new com.udyam.digital.voice.VoiceAnswerParser.Choice(l.name(), l.nativeName, names));
                    }
                    com.udyam.digital.voice.VoiceAnswerParser.Result r = com.udyam.digital.voice.VoiceAnswerParser
                            .parseChoices(cs, heard, conf, Language.fromTags(phoneTag()));
                    if (!r.understood()) {
                        hint.setText(R.string.voice_not_caught);
                        return;
                    }
                    Language l = Language.valueOf(r.values.get(0));
                    hint.setText(l.nativeName);
                    View tile = tiles.get(l.ordinal());
                    tile.performClick();
                }
                @Override public void onFailed(boolean unavailable) {
                    hint.setText(unavailable ? getString(R.string.voice_not_available, Language.fromTags(phoneTag()).nativeName)
                            : getString(R.string.voice_not_caught));
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (voice != null) voice.destroy();
        super.onDestroy();
    }

    private void choose(Language language) {
        if (isFinishing()) return;
        AppState.get().reset(language);
        if (AppState.language() != language
                || AppCompatDelegate.getApplicationLocales().isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag));
        }
        startActivity(new Intent(this, QuestionnaireActivity.class));
    }
}
