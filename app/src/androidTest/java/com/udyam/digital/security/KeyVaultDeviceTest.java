package com.udyam.digital.security;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Runs on a phone (./gradlew connectedDebugAndroidTest): the real Android Keystore and
 * Jetpack Security EncryptedFile, which can't run on the JVM.
 */
@RunWith(AndroidJUnit4.class)
public class KeyVaultDeviceTest {

    private final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final File dir = new File(ctx.getFilesDir(), "kyc_test");
    private static final byte[] DATA = "GSTIN 27ABCXX1234X1Z5 - specimen bytes".getBytes(StandardCharsets.UTF_8);

    @After public void cleanUp() {
        KeyVault.destroy(ctx, "TESTAPP1");
        KeyVault.destroy(ctx, "TESTAPP2");
        DocumentShredder.deleteTree(dir);
    }

    @Test public void encryptDecryptRoundTrip() throws Exception {
        File f = new File(dir, "doc.enc");
        KeyVault.write(ctx, "TESTAPP1", f, DATA);
        assertTrue(KeyVault.hasKey("TESTAPP1"));
        byte[] onDisk = Files.readAllBytes(f.toPath());
        assertFalse("ciphertext on disk", new String(onDisk, StandardCharsets.ISO_8859_1).contains("27ABCXX1234X1Z5"));
        assertArrayEquals(DATA, KeyVault.read(ctx, "TESTAPP1", f));
    }

    @Test public void fileIsUnreadableAfterKeyDeletion() throws Exception {
        File f = new File(dir, "doc.enc");
        File other = new File(dir, "other.enc");
        KeyVault.write(ctx, "TESTAPP1", f, DATA);
        KeyVault.write(ctx, "TESTAPP2", other, DATA);
        byte[] leftoverCopy = Files.readAllBytes(f.toPath());

        KeyVault.destroy(ctx, "TESTAPP1");
        assertFalse(KeyVault.hasKey("TESTAPP1"));
        // Put the old ciphertext back, as if a copy had survived somewhere.
        Files.write(f.toPath(), leftoverCopy);
        try {
            byte[] got = KeyVault.read(ctx, "TESTAPP1", f);
            fail("Leftover copy decrypted after crypto-shredding: " + Arrays.toString(got));
        } catch (IOException | RuntimeException expected) {
            // unreadable, as intended
        }
        assertArrayEquals("other applications keep their own key", DATA, KeyVault.read(ctx, "TESTAPP2", other));
    }

    @Test public void eachApplicationHasItsOwnKey() throws Exception {
        File f = new File(dir, "doc.enc");
        KeyVault.write(ctx, "TESTAPP1", f, DATA);
        try {
            KeyVault.read(ctx, "TESTAPP2", f);
            fail("Read with another application's key");
        } catch (IOException | RuntimeException expected) {
            // expected
        }
    }
}
