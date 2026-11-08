package com.udyam.digital.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.udyam.digital.BuildConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Warns (never blocks) when documents on this phone are less safe: a rooted phone, a
 * test-keys system build, or a release build of this app that has been made debuggable.
 * Each warning is written to the access log.
 */
public final class DeviceIntegrity {
    private DeviceIntegrity() {}

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/su/bin/su",
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su", "/system/app/Superuser.apk",
            "/data/adb/magisk", "/system/bin/.ext/su", "/vendor/bin/su"};

    private static boolean warnedThisRun;

    /** Reasons the phone looks less safe; empty if none. Codes only (for the access log). */
    public static List<String> problems(Context ctx) {
        List<String> p = new ArrayList<>();
        for (String s : SU_PATHS) {
            if (new File(s).exists()) {
                p.add("ROOTED");
                break;
            }
        }
        String tags = Build.TAGS;
        if (tags != null && tags.contains("test-keys")) p.add("TEST-KEYS BUILD");
        boolean debuggable = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable && !BuildConfig.DEBUG) p.add("DEBUGGABLE RELEASE");
        return p;
    }

    /** Once per app run: the problems found (logged), or an empty list. */
    public static List<String> checkOnce(Context ctx) {
        if (warnedThisRun) return new ArrayList<>();
        warnedThisRun = true;
        List<String> p = problems(ctx);
        if (!p.isEmpty()) {
            SecureAuditLog.record(ctx, AccessLog.Actor.SECURITY_CHECK, AccessLog.Action.INTEGRITY_WARNING, "", "", String.join(", ", p));
        }
        return p;
    }
}
