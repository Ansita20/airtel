package com.udyam.digital.kycui;

import android.os.Build;
import android.os.SystemClock;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.udyam.digital.R;
import com.udyam.digital.security.AccessLog;
import com.udyam.digital.security.SecureAuditLog;
import com.udyam.digital.security.SecureScreens;

/**
 * Viewing a stored document needs the phone owner: fingerprint / face, or the device PIN,
 * pattern or password. Once passed, documents stay viewable until the app has been in the
 * background for 2 minutes. Phones without any screen lock can't view stored documents
 * (uploading still works).
 */
public final class DocumentGate {
    private DocumentGate() {}

    private static int authenticators() {
        // Strong biometrics + device credential is supported from Android 11; before that, weak + credential.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                : BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }

    /** Runs {@code onUnlocked} after a successful check (or straight away if still unlocked). */
    public static void unlockThen(FragmentActivity a, String appId, String slot, Runnable onUnlocked) {
        if (SecureScreens.SESSION.isUnlocked(SystemClock.elapsedRealtime())) {
            onUnlocked.run();
            return;
        }
        int can = BiometricManager.from(a).canAuthenticate(authenticators());
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            SecureAuditLog.record(a, AccessLog.Actor.OWNER, AccessLog.Action.VIEW_DENIED, appId, slot, "NO SCREEN LOCK");
            new MaterialAlertDialogBuilder(a).setMessage(R.string.gate_no_lock).setPositiveButton(R.string.upload_ok, null).show();
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(a, ContextCompat.getMainExecutor(a), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                SecureScreens.SESSION.unlock();
                onUnlocked.run();
            }

            @Override public void onAuthenticationError(int code, CharSequence msg) {
                SecureAuditLog.record(a, AccessLog.Actor.OWNER, AccessLog.Action.VIEW_DENIED, appId, slot, "CANCELLED " + code);
            }
        });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(a.getString(R.string.gate_title))
                .setSubtitle(a.getString(R.string.gate_subtitle))
                .setAllowedAuthenticators(authenticators())
                .build());
    }
}
