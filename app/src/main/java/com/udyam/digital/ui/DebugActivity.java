package com.udyam.digital.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.udyam.digital.R;
import com.udyam.digital.data.DataRepository;
import com.udyam.digital.engine.ProductScorer;
import com.udyam.digital.engine.RecommendationLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/** Debug screen (long-press the app title): offline evaluation numbers + export of the usage log. */
public class DebugActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ProductScorer scorer = DataRepository.get().scorer();
        StringBuilder b = new StringBuilder();
        if (scorer.isEmpty()) {
            b.append("No product_scores.json loaded.\nRanking uses tapped needs only.");
        } else {
            Map<String, Object> e = scorer.eval();
            b.append("Scores version : ").append(scorer.version()).append('\n');
            b.append("Ranker         : ").append(scorer.ranker()).append('\n');
            b.append("Winner         : ").append(str(e.get("winner"))).append('\n');
            b.append("Recall@3       : ").append(num(e.get("recall_at_3")))
                    .append("  (baseline ").append(num(e.get("baseline_recall_at_3"))).append(")\n");
            b.append("Recall@5       : ").append(num(e.get("recall_at_5"))).append('\n');
            b.append("Rule violations: ").append(str(e.get("violations"))).append('\n');
            b.append("Test customers : ").append(str(e.get("test_customers")))
                    .append(" (Jun 2026; trained on ").append(str(e.get("train_customers"))).append(")\n");
            b.append("App-log sessions used: ").append(str(e.get("app_event_sessions"))).append("\n\n");
            b.append("Suggestions (OFF), June\n");
            b.append("  cutoff (Apr-May): ").append(num(e.get("suggest_cutoff"))).append('\n');
            b.append("  kept / shown    : ").append(pct(e.get("suggest_precision"))).append('\n');
            b.append("  kept found      : ").append(pct(e.get("suggest_recall"))).append('\n');
            b.append("  >=1 kept        : ").append(pct(e.get("suggest_any_useful"))).append("\n\n");
            b.append("Unwanted extras ON / customer\n");
            b.append("  before: ").append(num(e.get("unwanted_on_before"))).append('\n');
            b.append("  now   : ").append(num(e.get("unwanted_on_now"))).append(" (taps nothing)\n");
            b.append("Stated interest as taps:\n  ").append(pct(e.get("interest_on_precision")))
                    .append(" of ON kept\n\n");
            b.append("Chains Recall@3: ").append(num(e.get("chain_recall_at_3_before")))
                    .append(" -> ").append(num(e.get("chain_recall_at_3_after")));
        }
        ((TextView) findViewById(R.id.debug_eval)).setText(b.toString());

        RecommendationLog log = DataRepository.get().log();
        File f = log == null ? null : log.file();
        long rows = 0;
        if (f != null && f.exists()) {
            try {
                rows = Math.max(0, Files.lines(f.toPath()).count() - 1);
            } catch (IOException ignored) {
            }
        }
        ((TextView) findViewById(R.id.debug_log)).setText(
                String.format(Locale.ENGLISH, "Usage log: %d events (kept 90 days)", rows));

        findViewById(R.id.debug_export).setOnClickListener(v -> export(f));

        // KYC tools. The specimen harness exists only in debug builds.
        android.widget.LinearLayout box = (android.widget.LinearLayout) findViewById(R.id.debug_export).getParent();
        addButton(box, "Run all KYC specimens (K01–K12)", v -> {
            try {
                startActivity(new Intent(this, Class.forName("com.udyam.digital.kycui.KycSpecimenActivity")));
            } catch (ClassNotFoundException e) {
                Toast.makeText(this, "Specimens are only in debug builds", Toast.LENGTH_SHORT).show();
            }
        });
        addButton(box, "KYC privacy settings (withdraw consent)", v ->
                startActivity(new Intent(this, com.udyam.digital.kycui.KycSettingsActivity.class)));
        addButton(box, "Run retention now (as of " + com.udyam.digital.util.AppClock.today() + ")",
                v -> retention(com.udyam.digital.util.AppClock.today()));
        addButton(box, "Run retention as if 30 days have passed (demo)",
                v -> retention(com.udyam.digital.util.AppClock.today().plusDays(30)));
        addButton(box, "View KYC access log (encrypted on disk)", v -> showAccessLog());
        if (com.udyam.digital.BuildConfig.DEBUG) {
            com.google.android.material.materialswitch.MaterialSwitch shots = new com.google.android.material.materialswitch.MaterialSwitch(this);
            shots.setText("Allow screenshots on secure screens (debug builds only, for recording the demo; reopen screens)");
            shots.setChecked(com.udyam.digital.security.SecureScreens.screenshotsAllowed());
            shots.setOnCheckedChangeListener((sw, on) -> com.udyam.digital.security.SecureScreens.setScreenshotsAllowed(on));
            box.addView(shots);
        }
    }

    /** Who/what touched which document slot and when. Codes only: no document contents, names or IDs. */
    private void showAccessLog() {
        java.util.List<com.udyam.digital.security.AccessLog.Entry> e =
                com.udyam.digital.security.SecureAuditLog.load(this).entries();
        StringBuilder b = new StringBuilder();
        for (int i = e.size() - 1; i >= 0 && i >= e.size() - 200; i--) b.append(e.get(i)).append('\n');
        TextView t = new TextView(this);
        t.setText(b.length() == 0 ? "No entries yet." : b);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextSize(11);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(t);
        com.udyam.digital.security.SecureScreens.secure(new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Access log (" + e.size() + " entries, newest first)").setView(sv)
                .setPositiveButton(android.R.string.ok, null).create()).show();
    }

    private void addButton(android.widget.LinearLayout box, String text, android.view.View.OnClickListener l) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setOnClickListener(l);
        box.addView(b);
    }

    /** Same pass as the daily WorkManager job. */
    private void retention(java.time.LocalDate asOf) {
        long now = System.currentTimeMillis() + java.time.temporal.ChronoUnit.DAYS.between(
                com.udyam.digital.util.AppClock.today(), asOf) * 24L * 60 * 60 * 1000;
        String r = com.udyam.digital.security.RetentionWorker.runNow(this, asOf, now);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Retention")
                .setMessage(r).setPositiveButton(android.R.string.ok, null).show();
    }

    /** Shares a copy named app_events.csv, for tools/train_recommender.py step 6. */
    private void export(File log) {
        if (log == null || !log.exists()) {
            Toast.makeText(this, "No events logged yet", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getFilesDir(), "export");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir failed");
            File out = new File(dir, "app_events.csv");
            Files.copy(log.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", out);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.debug_export)));
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String str(Object o) {
        if (o instanceof Double && ((Double) o) == Math.floor((Double) o)) return String.valueOf(((Double) o).intValue());
        return String.valueOf(o);
    }

    private static String pct(Object o) {
        return o instanceof Double ? String.format(Locale.ENGLISH, "%.0f%%", (Double) o * 100) : String.valueOf(o);
    }

    private static String num(Object o) {
        return o instanceof Double ? String.format(Locale.ENGLISH, "%.3f", (Double) o) : String.valueOf(o);
    }
}
