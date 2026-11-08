package com.udyam.digital.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.udyam.digital.model.Language;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads text aloud in the app's chosen language (all 10: en, hi, mr, gu, pa, bn, ta, te, kn, ml).
 *
 * Many phones ship a default speech engine (Samsung, Xiaomi ...) that has no voice for most
 * Indian languages, so we use Google's engine when it is installed: it speaks all ten. Among its
 * voices we pick one for the exact language that is already on the phone, preferring offline.
 * A tap made while the engine is still starting is spoken as soon as it is ready. If the voice
 * isn't downloaded yet, {@link #state()} says so and {@link #installVoiceIntent()} opens the
 * download screen; nothing is hidden or silently dropped.
 */
public final class Speaker implements TextToSpeech.OnInitListener {

    public enum State { LOADING, READY, MISSING_VOICE, NOT_SUPPORTED, NO_ENGINE }

    public interface Listener { void onState(State state); }

    static final String GOOGLE_TTS = "com.google.android.tts";

    private final Context context;
    private final Locale locale;
    private final Listener listener;
    private TextToSpeech tts;
    private String engine;
    private State state = State.LOADING;
    private String pending;
    private Runnable pendingDone;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> whenDone = new HashMap<>();
    private int next;

    public Speaker(Context context, Language language, Listener listener) {
        this.context = context.getApplicationContext();
        this.locale = new Locale(language.tag, "IN");
        this.listener = listener;
        start(installed(GOOGLE_TTS) ? GOOGLE_TTS : null);
    }

    private void start(String enginePackage) {
        engine = enginePackage;
        state = State.LOADING;
        tts = enginePackage == null ? new TextToSpeech(context, this) : new TextToSpeech(context, this, enginePackage);
    }

    private boolean installed(String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            if (engine != null) { // Google engine failed to start: try the phone's default one
                tts.shutdown();
                start(null);
                return;
            }
            set(State.NO_ENGINE);
            return;
        }
        int r = tts.setLanguage(locale);
        if (r == TextToSpeech.LANG_MISSING_DATA) {
            set(State.MISSING_VOICE);
        } else if (r == TextToSpeech.LANG_NOT_SUPPORTED) {
            set(engine == null && installed(GOOGLE_TTS) ? State.MISSING_VOICE : State.NOT_SUPPORTED);
        } else {
            Voice v = bestVoice();
            if (v != null) tts.setVoice(v);
            tts.setSpeechRate(0.9f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { finished(id); }
                @Override public void onError(String id) { finished(id); }
                @Override public void onStop(String id, boolean interrupted) { finished(id); }
            });
            set(State.READY);
            if (pending != null) {
                String p = pending;
                Runnable d = pendingDone;
                pending = null;
                pendingDone = null;
                speak(p, d);
            }
        }
    }

    /** An installed voice for exactly this language, offline first. Null = keep the engine's choice. */
    private Voice bestVoice() {
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (RuntimeException e) {
            return null;
        }
        if (voices == null) return null;
        Voice best = null;
        for (Voice v : voices) {
            if (v.getLocale() == null || !locale.getLanguage().equals(v.getLocale().getLanguage())) continue;
            if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
            if (best == null
                    || best.isNetworkConnectionRequired() && !v.isNetworkConnectionRequired()
                    || best.isNetworkConnectionRequired() == v.isNetworkConnectionRequired()
                        && "IN".equals(v.getLocale().getCountry()) && !"IN".equals(best.getLocale().getCountry())) {
                best = v;
            }
        }
        return best;
    }

    private void set(State s) {
        state = s;
        if (listener != null) listener.onState(s);
        if (s != State.READY && s != State.LOADING && pendingDone != null) { // can't speak: carry on without voice
            Runnable d = pendingDone;
            pendingDone = null;
            pending = null;
            main.post(d);
        }
    }

    private void finished(String id) {
        Runnable r;
        synchronized (whenDone) { r = whenDone.remove(id); }
        if (r != null) main.post(r);
    }

    public State state() { return state; }

    /**
     * Speaks now, or as soon as the engine is ready. Returns the state so the caller can explain
     * a missing voice.
     */
    public State speak(String text) { return speak(text, null); }

    /**
     * Speaks, then runs {@code onDone} on the main thread (also straight away if this phone can't
     * speak the language, so a voice-mode flow never gets stuck). Short sentences: the text is
     * split at full stops into separate utterances.
     */
    public State speak(String text, Runnable onDone) {
        if (text == null || text.trim().isEmpty()) {
            if (onDone != null) main.post(onDone);
            return state;
        }
        if (state == State.READY) {
            // QUEUE_FLUSH stops what was being said; its "when done" must not fire (the flow moved on).
            synchronized (whenDone) { whenDone.clear(); }
            String[] parts = text.split("(?<=[.?!।])\\s+");
            for (int i = 0; i < parts.length; i++) {
                String id = "udyam-" + (next++);
                if (i == parts.length - 1 && onDone != null) synchronized (whenDone) { whenDone.put(id, onDone); }
                tts.speak(parts[i], i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, id);
            }
        } else if (state == State.LOADING) {
            pending = text;
            pendingDone = onDone;
        } else if (onDone != null) {
            main.post(onDone);
        }
        return state;
    }

    public boolean isSpeaking() { return state == State.READY && tts.isSpeaking(); }

    /** Opens the engine's "download voice data" screen. */
    public Intent installVoiceIntent() {
        Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        String pkg = engine != null ? engine : installed(GOOGLE_TTS) ? GOOGLE_TTS : null;
        if (pkg != null) i.setPackage(pkg);
        return i;
    }

    /** Play Store page of Google's speech engine, which speaks all ten languages. */
    public static Intent getEngineIntent() {
        return new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + GOOGLE_TTS));
    }

    /** Re-checks after the owner came back from downloading a voice. */
    public void retry() {
        if (state == State.READY || state == State.LOADING) return;
        tts.shutdown();
        start(installed(GOOGLE_TTS) ? GOOGLE_TTS : null);
    }

    /** Stops speaking; pending "when done" callbacks are dropped (the caller moved on). */
    public void stop() {
        pending = null;
        pendingDone = null;
        synchronized (whenDone) { whenDone.clear(); }
        if (state == State.READY) tts.stop();
    }

    public void shutdown() {
        pending = null;
        tts.stop();
        tts.shutdown();
    }
}
