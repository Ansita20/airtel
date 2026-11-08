package com.udyam.digital.security;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Window;
import android.view.WindowManager;

import com.udyam.digital.BuildConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * App-wide screen protection, registered once in UdyamApp:
 * <ul>
 *   <li>FLAG_SECURE on every screen that shows documents, IDs, the plan with prices, or the help
 *       chat (which can contain KYC data): no screenshots, no screen recording, blank in the
 *       recent-apps preview. Dialogs that show IDs call {@link #secure(Dialog)}.</li>
 *   <li>Tracks when the whole app goes to the background, so document viewing is locked
 *       again after 2 minutes away ({@link AccessSession}).</li>
 * </ul>
 * Debug builds only: a switch in the debug screen can allow screenshots for recording the demo.
 */
public final class SecureScreens implements Application.ActivityLifecycleCallbacks {

    /** Unlock state for viewing stored documents. */
    public static final AccessSession SESSION = new AccessSession();

    private static final Set<String> SECURE = new HashSet<>(Arrays.asList(
            "com.udyam.digital.kycui.KycActivity",
            "com.udyam.digital.kycui.KycSettingsActivity",
            "com.udyam.digital.kycui.CameraCaptureActivity",
            "com.udyam.digital.kycui.KycSpecimenActivity",
            "com.udyam.digital.ui.PlanActivity",
            "com.udyam.digital.orderui.CheckoutActivity",
            "com.udyam.digital.orderui.TrackerActivity",
            "com.udyam.digital.help.HelpActivity",
            "com.udyam.digital.ui.DebugActivity",
            "com.udyam.digital.partner.PartnerActivity"));

    private static final String PREFS = "debug_screens";
    private static final String KEY_ALLOW = "allow_screenshots";

    private static Context app;
    private int started;

    public static void install(Application a) {
        app = a.getApplicationContext();
        a.registerActivityLifecycleCallbacks(new SecureScreens());
    }

    public static boolean isSecureScreen(String className) { return SECURE.contains(className); }

    /** Debug builds only: allow screenshots on secure screens (to record the demo). Always false in release. */
    public static boolean screenshotsAllowed() {
        return BuildConfig.DEBUG && app != null && app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ALLOW, false);
    }

    public static void setScreenshotsAllowed(boolean allow) {
        if (BuildConfig.DEBUG && app != null) app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALLOW, allow).apply();
    }

    private static void apply(Window w) {
        if (w == null) return;
        if (screenshotsAllowed()) w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    /** For dialogs that show IDs or documents: dialogs have their own window. Returns the dialog. */
    public static <T extends Dialog> T secure(T d) {
        apply(d.getWindow());
        return d;
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {
        if (SECURE.contains(a.getClass().getName())) apply(a.getWindow());
    }

    @Override public void onActivityStarted(Activity a) {
        if (started++ == 0) SESSION.onForeground(SystemClock.elapsedRealtime());
    }

    @Override public void onActivityStopped(Activity a) {
        if (--started == 0) SESSION.onBackground(SystemClock.elapsedRealtime());
    }

    @Override public void onActivityResumed(Activity a) { }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
