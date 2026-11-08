package com.udyam.digital.security;

import android.content.Context;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * One key per KYC application. Each application's files are encrypted with AES-256-GCM
 * (Jetpack Security EncryptedFile, AES256_GCM_HKDF_4KB). The file keyset is itself wrapped by
 * an AES-256-GCM key that lives in the Android Keystore under "kyc_app_&lt;id&gt;": generated
 * inside the Keystore (StrongBox chip when the phone has one), never exportable.
 *
 * {@link #destroy} deletes that Keystore key and the wrapped keyset: every file of that
 * application - and any copy of it anywhere - can no longer be decrypted (crypto-shredding).
 */
public final class KeyVault {
    private KeyVault() {}

    static final String ALIAS_PREFIX = "kyc_app_";
    static final String KEYSET_PREFIX = "kyc_keyset_";
    /** App-wide key for data that outlives one application (access log, retained results). */
    public static final String GLOBAL = "global";

    private static MasterKey masterKey(Context ctx, String appId) throws GeneralSecurityException, IOException {
        return new MasterKey.Builder(ctx.getApplicationContext(), ALIAS_PREFIX + appId)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true) // used only if the phone has StrongBox
                .build();
    }

    private static EncryptedFile file(Context ctx, String appId, File f) throws GeneralSecurityException, IOException {
        return new EncryptedFile.Builder(ctx.getApplicationContext(), f, masterKey(ctx, appId),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB)
                .setKeysetPrefName(KEYSET_PREFIX + appId)
                .build();
    }

    /** Writes {@code data} encrypted with the application's key (replacing any old file). */
    public static void write(Context ctx, String appId, File f, byte[] data) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        if (tmp.exists()) tmp.delete();
        try {
            try (OutputStream out = file(ctx, appId, tmp).openFileOutput()) { out.write(data); }
        } catch (GeneralSecurityException e) {
            tmp.delete();
            throw new IOException(e);
        }
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) throw new IOException("Could not save " + f.getName());
    }

    public static byte[] read(Context ctx, String appId, File f) throws IOException {
        try (InputStream in = file(ctx, appId, f).openFileInput(); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** Crypto-shredding: deletes the Keystore key and the wrapped keyset of this application. */
    public static void destroy(Context ctx, String appId) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS_PREFIX + appId);
        } catch (Exception ignored) {
            // No key (never written): nothing to destroy.
        }
        ctx.getApplicationContext().deleteSharedPreferences(KEYSET_PREFIX + appId);
    }

    public static boolean hasKey(String appId) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            return ks.containsAlias(ALIAS_PREFIX + appId);
        } catch (Exception e) {
            return false;
        }
    }

    /** True if this application's key sits in a StrongBox security chip. */
    public static boolean isStrongBox(Context ctx, String appId) {
        try {
            return masterKey(ctx, appId).isStrongBoxBacked();
        } catch (Exception e) {
            return false;
        }
    }
}
