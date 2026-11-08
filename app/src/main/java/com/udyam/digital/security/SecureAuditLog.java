package com.udyam.digital.security;

import android.content.Context;

import com.udyam.digital.kyc.CheckResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * The access log, encrypted at rest (EncryptedFile, app-wide Keystore key). Records who/what
 * opened, read, saved or deleted which document slot and when - never document contents
 * (see {@link AccessLog}). Kept when one application's documents are shredded, so the deletion
 * itself is on record. Viewable in the debug screen.
 */
public final class SecureAuditLog {
    private SecureAuditLog() {}

    private static File file(Context ctx) { return new File(ctx.getFilesDir(), "security/access_log.enc"); }

    public static synchronized AccessLog load(Context ctx) {
        File f = file(ctx);
        if (!f.exists()) return new AccessLog();
        try {
            return AccessLog.parse(new String(KeyVault.read(ctx, KeyVault.GLOBAL, f), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new AccessLog();
        }
    }

    public static synchronized void record(Context ctx, AccessLog.Actor actor, AccessLog.Action action,
                                           String appId, String slot, String detail) {
        AccessLog log = load(ctx);
        log.add(new AccessLog.Entry(Instant.now().toString(), actor, action, appId, slot, detail));
        try {
            KeyVault.write(ctx, KeyVault.GLOBAL, file(ctx), log.serialize().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The log must never break the KYC flow.
        }
    }

    /** One entry per run of the checks: codes and results only (e.g. "C13 FAIL, C15 WARN"). */
    public static void checks(Context ctx, String appId, List<CheckResult> results) {
        StringBuilder b = new StringBuilder();
        for (CheckResult r : results) {
            if (r.status == CheckResult.Status.PASS) continue;
            if (b.length() > 0) b.append(", ");
            b.append(r.code).append(' ').append(r.status);
        }
        record(ctx, AccessLog.Actor.RULE_ENGINE, AccessLog.Action.CHECKED, appId, "", b.length() == 0 ? "ALL PASS" : b.toString());
    }
}
