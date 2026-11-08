package com.udyam.digital.kycui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.udyam.digital.kyc.DocumentProcessor;
import com.udyam.digital.kyc.KycDocument;
import com.udyam.digital.kyc.KycRuleEngine;
import com.udyam.digital.kyc.OcrLine;
import com.udyam.digital.kyc.Slot;
import com.udyam.digital.kyc.SpecimenDump;
import com.udyam.digital.kyc.SpecimenReport;
import com.udyam.digital.kyc.SpecimenRunner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

/**
 * DEBUG BUILDS ONLY. "Run all specimens": every image in assets/kyc_samples goes through the
 * same pipeline as a real upload - quality gate, OCR on the image AND on the enhanced copy,
 * field merge, registry-guided ID repair, all checks C01-C20 - and is compared with the answer
 * key in assets/kyc/expected_results.json.
 *
 * (The ML Kit document scanner is an interactive camera screen, so it can't be run on stored
 * files; the specimens are already flat, cropped scans. Everything after the scanner is the same.)
 *
 * Writes files/kyc_specimen_report.md (with raw OCR text per specimen - never in release builds)
 * and files/kyc_ocr_dump/*.json, which tools/pull_specimen_dumps.sh copies into the JUnit replay.
 */
public class KycSpecimenActivity extends AppCompatActivity {

    private TextView out;
    private final StringBuilder screen = new StringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FLAG_SECURE comes from SecureScreens (app-wide policy).
        setTitle("KYC specimens K01-K12");
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(12);
        out.setPadding(pad, pad, pad, pad);
        out.setTextIsSelectable(true);
        ScrollView v = new ScrollView(this);
        v.addView(out);
        LinearLayout root = new LinearLayout(this);
        root.addView(v, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
        Executors.newSingleThreadExecutor().execute(this::runAll);
    }

    private void say(String line) {
        ui.post(() -> {
            screen.append(line).append('\n');
            out.setText(screen);
        });
    }

    private void runAll() {
        try {
            String[] files = getAssets().list("kyc_samples");
            Arrays.sort(files);
            // 1. Mapping first, so a wrongly named file is obvious before anything runs.
            List<String> images = new ArrayList<>(), mapping = new ArrayList<>();
            say("FILE MAPPING");
            for (String f : files) {
                if (!f.endsWith(".png") && !f.endsWith(".jpg")) continue;
                String m = f + " -> " + SpecimenDump.applicantOf(f) + " " + SpecimenDump.slotOf(f);
                images.add(f);
                mapping.add(m);
                say("  " + m);
            }
            say(images.size() + " images\n");

            KycRuleEngine engine = KycEngines.engine(this);
            Map<String, Map<Slot, KycDocument>> byApplicant = new TreeMap<>();
            File dump = new File(getFilesDir(), "kyc_ocr_dump");
            dump.mkdirs();
            StringBuilder raw = new StringBuilder("## Raw OCR text (debug build only)\n\n");
            for (String f : images) {
                Slot slot = SpecimenDump.slotOf(f);
                Bitmap b;
                try (InputStream in = getAssets().open("kyc_samples/" + f)) {
                    b = KycImaging.decode(readAll(in), 2000);
                }
                KycImaging.Read r = KycImaging.read(slot, b, true, engine.registry());
                byApplicant.computeIfAbsent(SpecimenDump.applicantOf(f), k -> new EnumMap<>(Slot.class)).put(slot, r.doc);
                writeDump(new File(dump, f.replaceAll("\\.(png|jpg)$", ".json")), f, r);
                raw.append("### ").append(f).append("\n\n")
                        .append("type ").append(r.doc.type).append(", quality ").append(r.doc.quality.problem)
                        .append(", fields ").append(r.doc.fields.keySet()).append("\n\n")
                        .append("confidence: ").append(DocumentProcessor.describeConfidence(r.doc)).append("\n\n");
                if (!r.doc.autoCorrected.isEmpty()) raw.append("auto-corrected: ").append(r.doc.autoCorrected).append("\n\n");
                if (r.doc.looksTampered()) raw.append("C20 signals: ").append(r.doc.passDisagreement).append(' ').append(r.doc.fontMismatch).append("\n\n");
                raw.append("Pass 1 (scan):\n```\n");
                for (OcrLine l : r.pass1) raw.append(String.format(java.util.Locale.ROOT, "%.2f  %s%n", l.confidence, l.text));
                raw.append("```\nPass 2 (enhanced):\n```\n");
                for (OcrLine l : r.pass2) raw.append(String.format(java.util.Locale.ROOT, "%.2f  %s%n", l.confidence, l.text));
                raw.append("```\n\n");
                b.recycle();
                say("read " + f);
            }

            List<SpecimenRunner.Row> rows = new ArrayList<>();
            for (Map.Entry<String, Map<Slot, KycDocument>> e : byApplicant.entrySet()) {
                rows.add(SpecimenRunner.run(engine, e.getKey(), e.getValue()));
            }
            String key;
            try (InputStream in = getAssets().open("kyc/expected_results.json")) {
                key = new String(readAll(in), StandardCharsets.UTF_8);
            }
            SpecimenReport report = SpecimenReport.compare(rows, SpecimenReport.parseExpected(key));
            String md = report.markdown("on this phone (" + android.os.Build.MODEL + "), full pipeline", mapping, rows, raw.toString());
            File mdFile = new File(getFilesDir(), "kyc_specimen_report.md");
            try (FileWriter w = new FileWriter(mdFile)) { w.write(md); }

            say("\nRESULTS (today = 2026-07-15)");
            if (report.compared() == 0) {
                say("Answer key is empty - fill in kyc/expected_results.json.");
            } else {
                say(String.format(java.util.Locale.ROOT, "Matched %d of %d (%.0f%%)  false rejects %d  false accepts %d",
                        report.matched(), report.compared(), report.accuracy() * 100, report.falseRejects(), report.falseAccepts()));
            }
            for (SpecimenReport.Line l : report.lines) {
                String res = !l.compared() ? "    " : l.match() ? "PASS" : "FAIL";
                String exp = l.compared() ? l.expected.overall.name() : "-";
                say(String.format(java.util.Locale.ROOT, "%s %s  exp %-13s got %-13s %s", res, l.applicant, exp, l.actual.name(),
                        String.join(",", l.flagged)));
            }
            say("\nReport: " + mdFile.getAbsolutePath());
            say("OCR dumps: " + dump.getAbsolutePath() + "  (tools/pull_specimen_dumps.sh)");
        } catch (Exception ex) {
            say("Failed: " + ex);
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private static JSONArray lines(List<OcrLine> ls) throws Exception {
        JSONArray a = new JSONArray();
        for (OcrLine l : ls) {
            JSONArray words = new JSONArray(), heights = new JSONArray();
            for (String w : l.words) words.put(w);
            for (int h : l.wordHeights) heights.put(h);
            a.put(new JSONArray().put(l.text).put(l.left).put(l.top).put(l.right).put(l.bottom)
                    .put((double) l.angle).put((double) l.confidence).put(words).put(heights));
        }
        return a;
    }

    /** Same format SpecimenDump.parse reads in JUnit. */
    private static void writeDump(File f, String name, KycImaging.Read r) throws Exception {
        JSONObject o = new JSONObject();
        o.put("file", name).put("width", r.doc.quality.width).put("height", r.doc.quality.height)
                .put("brightness", r.doc.quality.brightness).put("sharpness", r.doc.quality.sharpness)
                .put("glare", r.doc.quality.glare).put("ink", r.doc.quality.inkLevel).put("idCard", r.doc.quality.idCard)
                .put("hash", r.doc.hash).put("lines", lines(r.pass1)).put("lines_enhanced", lines(r.pass2));
        try (FileWriter w = new FileWriter(f)) { w.write(o.toString(1)); }
    }
}
