package com.udyam.digital.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.udyam.digital.R;

/**
 * "Talk instead of type": the app reads each question, listens, confirms and moves on by itself.
 * Also: read-aloud on/off (on by default in voice mode), TalkBack detection, and the microphone
 * permission with a one-line reason.
 */
public final class VoiceMode {
    private VoiceMode() {}

    private static final String PREFS = "voice_mode";
    private static final String KEY_ON = "on", KEY_READ = "read_aloud", KEY_ASKED = "mic_reason_shown";

    public static boolean isOn(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false); }

    public static void set(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).remove(KEY_READ).apply();
    }

    /** Read questions aloud automatically: ON in voice mode, OFF otherwise, unless the owner switched it. */
    public static boolean readAloud(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_READ, isOn(c));
    }

    public static void setReadAloud(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_READ, on).apply();
    }

    /** TalkBack (or another screen reader) is on: it does the reading, we must not talk over it. */
    public static boolean screenReader(Context c) {
        AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    public static boolean hasMic(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Runs {@code then} if the microphone may be used; otherwise explains why in one line, then
     * asks Android ({@code launcher}'s callback should call back here when granted).
     */
    public static void withMic(AppCompatActivity a, ActivityResultLauncher<String> launcher, Runnable then) {
        if (hasMic(a)) {
            then.run();
            return;
        }
        android.content.SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_ASKED, false)) {
            launcher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        p.edit().putBoolean(KEY_ASKED, true).apply();
        new MaterialAlertDialogBuilder(a)
                .setMessage(R.string.voice_mic_reason)
                .setPositiveButton(R.string.voice_ok, (d, w) -> launcher.launch(Manifest.permission.RECORD_AUDIO))
                .setNegativeButton(R.string.voice_not_now, null)
                .show();
    }
}
