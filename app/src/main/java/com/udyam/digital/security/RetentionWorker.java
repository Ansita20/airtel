package com.udyam.digital.security;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.udyam.digital.data.DataRepository;
import com.udyam.digital.kycui.KycStore;
import com.udyam.digital.kycui.TempFiles;
import com.udyam.digital.util.AppClock;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * Daily WorkManager job applying {@link RetentionPolicy}: KYC documents 30 days after the
 * verdict, results + masked IDs after the contract period, recommendation events after 90 days,
 * and any stray scanner/camera temp file.
 */
public final class RetentionWorker extends Worker {

    static final String NAME = "kyc_retention_daily";

    public RetentionWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    public static void schedule(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(RetentionWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @NonNull @Override
    public Result doWork() {
        runNow(getApplicationContext(), AppClock.today(), System.currentTimeMillis());
        return Result.success();
    }

    /** The whole retention pass; also behind the debug button "Run retention now". */
    public static String runNow(Context ctx, LocalDate today, long nowMs) {
        KycStore.RetentionResult kyc = new KycStore(ctx).runRetention(today);
        int events = 0;
        try {
            events = DataRepository.get().log().prune(nowMs);
        } catch (RuntimeException ignored) {
            // Repository not ready (process started only for this job): the app prunes at start.
        }
        int temp = TempFiles.purge(ctx);
        return "As of " + today + ": " + kyc + "; " + events + " recommendation event(s) older than 90 days deleted; "
                + temp + " temp image(s) removed.";
    }
}
