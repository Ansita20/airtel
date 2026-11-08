package com.udyam.digital.voice;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Listening, in the app's language (en-IN, hi-IN, mr-IN, gu-IN, ta-IN, te-IN, kn-IN, ml-IN,
 * bn-IN, pa-IN) with Android's SpeechRecognizer. Offline recognition is used when that
 * language's pack is installed on the phone (EXTRA_PREFER_OFFLINE), online otherwise. On first
 * use per language we check the recogniser supports it; if not, callers show "Voice is not
 * available in ... - you can tap instead" and tapping keeps working. Live partial text while the
 * owner speaks.
 */
public final class VoiceInput {

    public enum Availability { UNKNOWN, OFFLINE, ONLINE, UNAVAILABLE }

    public interface Listener {
        void onListening();
        void onPartial(String text);
        /** Alternatives best first, with the recogniser's confidences (may be null). */
        void onResult(List<String> heard, float[] confidence);
        /** Nothing usable: {@code languageUnavailable} = this phone can't recognise the language. */
        void onFailed(boolean languageUnavailable);
    }

    private static final String PREFS = "voice_support";

    private final Context ctx;
    private final String tag;
    private SpeechRecognizer recognizer;
    private Availability availability = Availability.UNKNOWN;
    private boolean listening;

    /** @param languageTag e.g. "ta-IN" */
    public VoiceInput(Context ctx, String languageTag) {
        this.ctx = ctx;
        this.tag = languageTag;
    }

    public static String tagFor(com.udyam.digital.model.Language l) { return l.tag + "-IN"; }

    private SharedPreferences prefs() { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public Availability availability() { return availability; }

    public boolean isListening() { return listening; }

    /** First use per language: is it supported, and is the offline pack installed? Answer on the main thread. */
    public void checkAvailability(Consumer<Availability> then) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            availability = Availability.UNAVAILABLE;
            then.accept(availability);
            return;
        }
        String cached = prefs().getString(tag, null);
        if (cached != null && !cached.equals(Availability.UNAVAILABLE.name())) {
            availability = Availability.valueOf(cached);
            then.accept(availability);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            SpeechRecognizer probe = SpeechRecognizer.createSpeechRecognizer(ctx);
            probe.checkRecognitionSupport(intent(false), ContextCompat.getMainExecutor(ctx), new RecognitionSupportCallback() {
                @Override public void onSupportResult(RecognitionSupport s) {
                    probe.destroy();
                    finish(fromSupport(s), then);
                }

                @Override public void onError(int error) {
                    probe.destroy();
                    finish(Availability.ONLINE, then); // can't tell: try, and learn from the first attempt
                }
            });
        } else {
            finish(Availability.ONLINE, then); // older phones can't be asked; the first attempt tells us
        }
    }

    private Availability fromSupport(RecognitionSupport s) {
        if (contains(s.getInstalledOnDeviceLanguages())) return Availability.OFFLINE;
        if (contains(s.getOnlineLanguages()) || contains(s.getSupportedOnDeviceLanguages()) || contains(s.getPendingOnDeviceLanguages())) {
            return Availability.ONLINE;
        }
        boolean nothingListed = s.getInstalledOnDeviceLanguages().isEmpty() && s.getOnlineLanguages().isEmpty()
                && s.getSupportedOnDeviceLanguages().isEmpty();
        return nothingListed ? Availability.ONLINE : Availability.UNAVAILABLE;
    }

    private boolean contains(List<String> tags) {
        if (tags == null) return false;
        String lang = tag.substring(0, 2);
        for (String t : tags) {
            String x = t.replace('_', '-').toLowerCase(Locale.ROOT);
            if (x.equals(tag.toLowerCase(Locale.ROOT)) || x.equals(lang) || x.startsWith(lang + "-")) return true;
        }
        return false;
    }

    private void finish(Availability a, Consumer<Availability> then) {
        availability = a;
        prefs().edit().putString(tag, a.name()).apply();
        then.accept(a);
    }

    private Intent intent(boolean preferOffline) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        if (preferOffline) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        return i;
    }

    /** Starts listening (microphone permission must already be granted). */
    public void listen(Listener l) {
        stop();
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { l.onListening(); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int t, Bundle b) { }

            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> p = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (p != null && !p.isEmpty() && !p.get(0).isEmpty()) l.onPartial(p.get(0));
            }

            @Override public void onResults(Bundle b) {
                listening = false;
                ArrayList<String> heard = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] conf = b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                if (heard == null || heard.isEmpty()) l.onFailed(false);
                else l.onResult(heard, conf);
            }

            @Override public void onError(int error) {
                listening = false;
                boolean unsupported = Build.VERSION.SDK_INT >= 31
                        && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE);
                if (unsupported) {
                    availability = Availability.UNAVAILABLE;
                    prefs().edit().putString(tag, availability.name()).apply();
                }
                // Offline pack expected but missing: next time go online.
                if (!unsupported && availability == Availability.OFFLINE
                        && (error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_CLIENT)) {
                    availability = Availability.ONLINE;
                    prefs().edit().putString(tag, availability.name()).apply();
                }
                l.onFailed(unsupported);
            }
        });
        listening = true;
        recognizer.startListening(intent(availability == Availability.OFFLINE));
    }

    public void stop() {
        if (recognizer != null && listening) recognizer.cancel();
        listening = false;
    }

    public void destroy() {
        stop();
        if (recognizer != null) recognizer.destroy();
        recognizer = null;
    }
}
