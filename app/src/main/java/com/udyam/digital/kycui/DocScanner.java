package com.udyam.digital.kycui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.util.function.Consumer;

/**
 * Google ML Kit Document Scanner: automatic edge detection, auto-capture, crop, perspective
 * fix and shadow/stain cleanup (FULL mode), JPEG output, one page per slot, gallery import
 * allowed. Runs inside Google Play services; if they are missing the caller falls back to the
 * CameraX screen.
 */
final class DocScanner {
    private DocScanner() {}

    static boolean available(Context c) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS;
    }

    private static GmsDocumentScanner client() {
        GmsDocumentScannerOptions o = new GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build();
        return GmsDocumentScanning.getClient(o);
    }

    /** Starts the scanner; {@code onUnavailable} runs if it can't start (old Play services, low RAM ...). */
    static void start(Activity a, Consumer<IntentSenderRequest> launch, Runnable onUnavailable) {
        client().getStartScanIntent(a)
                .addOnSuccessListener(sender -> launch.accept(new IntentSenderRequest.Builder(sender).build()))
                .addOnFailureListener(e -> onUnavailable.run());
    }

    /** The scanned page's JPEG, or null if the owner cancelled. */
    static Uri page(ActivityResult r) {
        if (r.getResultCode() != Activity.RESULT_OK) return null;
        Intent data = r.getData();
        GmsDocumentScanningResult res = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (res == null || res.getPages() == null || res.getPages().isEmpty()) return null;
        return res.getPages().get(0).getImageUri();
    }
}
