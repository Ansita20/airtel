package com.udyam.digital.kycui;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.udyam.digital.R;
import com.udyam.digital.data.DataRepository;
import com.udyam.digital.kyc.CheckResult;
import com.udyam.digital.kyc.DocType;
import com.udyam.digital.kyc.FieldExtractor;
import com.udyam.digital.kyc.FieldLabels;
import com.udyam.digital.kyc.KycDates;
import com.udyam.digital.kyc.GstinValidator;
import com.udyam.digital.kyc.ImageQuality;
import com.udyam.digital.kyc.KycApplication;
import com.udyam.digital.kyc.KycClock;
import com.udyam.digital.kyc.KycDocument;
import com.udyam.digital.kyc.KycRuleEngine;
import com.udyam.digital.kyc.Mask;
import com.udyam.digital.kyc.Slot;
import com.udyam.digital.kyc.UdyamValidator;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.Language;
import com.udyam.digital.security.AccessLog;
import com.udyam.digital.security.SafeClipboard;
import com.udyam.digital.security.SecureAuditLog;
import com.udyam.digital.security.SecureScreens;
import com.udyam.digital.ui.AppState;
import com.udyam.digital.util.AppClock;
import com.udyam.digital.ui.PlanActivity;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Smart KYC, one step per screen: 1 consent, 2 business numbers, 3 documents, 4 review,
 * 5 result. Saved (encrypted) after every change, so it can be resumed any time.
 * FLAG_SECURE (SecureScreens) blocks screenshots and screen recording; documents are stored
 * watermarked, EXIF-free and encrypted under this application's own key; viewing one needs
 * fingerprint / face / PIN.
 */
public class KycActivity extends AppCompatActivity {

    private static final int STEPS = 5;

    private KycStore store;
    private KycApplication app;
    private boolean hindi;
    /** The owner's language: every message, label and date is shown in it. */
    private Language lang;
    private List<CheckResult> results;
    private Slot pendingSlot;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout content;
    private MaterialButton next, back;

    private ActivityResultLauncher<String> pickImage;
    private ActivityResultLauncher<Intent> takePhoto;
    private ActivityResultLauncher<String> cameraPermission;
    private ActivityResultLauncher<androidx.activity.result.IntentSenderRequest> scan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FLAG_SECURE comes from SecureScreens (app-wide policy).
        setContentView(R.layout.activity_kyc);
        lang = AppState.language();
        hindi = lang == Language.HI;
        store = new KycStore(this);
        store.runRetention(AppClock.today());
        // A scan or camera photo left behind (app closed before it was read) never stays unencrypted.
        TempFiles.purge(this);
        app = store.loadOrCreate();
        // Rooted / test-keys / debuggable release: warn (once per run), never block.
        java.util.List<String> unsafe = com.udyam.digital.security.DeviceIntegrity.checkOnce(this);
        if (!unsafe.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.integrity_title)
                    .setMessage(R.string.integrity_msg).setPositiveButton(R.string.upload_ok, null).show();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(R.string.kyc_settings).setIcon(android.R.drawable.ic_menu_manage)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        com.udyam.digital.ui.HelpButton.addTo(toolbar, this);
        toolbar.setOnMenuItemClickListener(i -> {
            startActivity(new Intent(this, KycSettingsActivity.class));
            return true;
        });
        content = findViewById(R.id.kyc_content);
        next = findViewById(R.id.kyc_next);
        back = findViewById(R.id.kyc_back);
        back.setOnClickListener(v -> {
            if (app.step > 1 && app.step < 5) go(app.step - 1); else finish();
        });

        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            Slot s = pendingSlot;
            if (uri != null && s != null) readUri(s, uri, false, () -> gallery(s));
        });
        takePhoto = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            Slot s = pendingSlot;
            if (r.getResultCode() == RESULT_OK && r.getData() != null && s != null) {
                readUri(s, r.getData().getData(), true, () -> cameraFallback(s)); // camera temp file: delete once read
            }
        });
        scan = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), r -> {
            Slot s = pendingSlot;
            Uri page = DocScanner.page(r);
            if (page != null && s != null) readUri(s, page, true, () -> camera(s)); // scanner's cache file: delete once read
        });
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && pendingSlot != null) takePhoto.launch(cameraIntent(pendingSlot));
            else Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_LONG).show();
        });
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Settings may have withdrawn consent and deleted everything.
        KycApplication stored = store.load();
        if (stored == null && app.kycConsent) {
            app = store.loadOrCreate();
            render();
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void go(int step) {
        app.step = step;
        store.save(app);
        render();
        ((android.widget.ScrollView) findViewById(R.id.kyc_scroll)).scrollTo(0, 0);
    }

    private String t(int res, Object... args) { return getString(res, args); }

    // ------------------------------------------------------------------ view helpers
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView text(CharSequence s, int appearance, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextAppearance(appearance);
        if (bold) v.setTypeface(v.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        v.setLayoutParams(lp);
        content.addView(v);
        return v;
    }

    private TextView title(int res) {
        return text(getString(res), com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall, true);
    }

    private TextView para(CharSequence s) {
        return text(s, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge, false);
    }

    private MaterialButton button(ViewGroup parent, String label, boolean outlined, View.OnClickListener l) {
        MaterialButton b = outlined
                ? new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                : new MaterialButton(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        parent.addView(b);
        return b;
    }

    private MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        c.setRadius(dp(16));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(12), dp(16), dp(12));
        c.addView(inner);
        content.addView(c);
        return c;
    }

    private static LinearLayout inner(MaterialCardView c) { return (LinearLayout) c.getChildAt(0); }

    private TextInputEditText field(int hint, String value, int inputType) {
        TextInputLayout l = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        l.setHint(getString(hint));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        l.setLayoutParams(lp);
        TextInputEditText e = new TextInputEditText(l.getContext());
        e.setInputType(inputType);
        e.setText(value);
        l.addView(e);
        content.addView(l);
        return e;
    }

    // ------------------------------------------------------------------ steps
    private void render() {
        content.removeAllViews();
        int step = Math.max(1, Math.min(STEPS, app.step));
        ((LinearProgressIndicator) findViewById(R.id.kyc_progress)).setProgressCompat(step * 100 / STEPS, true);
        ((TextView) findViewById(R.id.kyc_step)).setText(t(R.string.kyc_step, step, STEPS));
        next.setVisibility(View.VISIBLE);
        next.setEnabled(true);
        next.setText(R.string.kyc_continue);
        back.setVisibility(step == 1 || step == 5 ? View.GONE : View.VISIBLE);
        switch (step) {
            case 1: consent(); break;
            case 2: lookup(); break;
            case 3: upload(); break;
            case 4: review(); break;
            default: result(); break;
        }
    }

    // 1. Consent (DPDP Act, 2023)
    private void consent() {
        title(R.string.consent_title);
        para(getString(R.string.consent_intro));
        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        table.setLayoutParams(lp);
        tableRow(table, true, t(R.string.consent_col_doc), t(R.string.consent_col_purpose), t(R.string.consent_col_kept));
        tableRow(table, false, Slot.GST_CERT.label(lang), t(R.string.purpose_gst), t(R.string.kept_30));
        tableRow(table, false, Slot.UDYAM_CERT.label(lang), t(R.string.purpose_udyam), t(R.string.kept_30));
        tableRow(table, false, Slot.SIGNATORY_ID.label(lang), t(R.string.purpose_id), t(R.string.kept_30));
        tableRow(table, false, Slot.ADDRESS_PROOF.label(lang), t(R.string.purpose_address), t(R.string.kept_30));
        content.addView(table);
        para(t(R.string.consent_retention));
        para(t(R.string.consent_who));
        para(t(R.string.consent_withdraw));

        CheckBox kyc = new CheckBox(this);
        kyc.setText(R.string.consent_kyc);
        kyc.setChecked(app.kycConsent);
        content.addView(kyc);
        CheckBox marketing = new CheckBox(this);
        marketing.setText(R.string.consent_marketing);
        marketing.setChecked(app.marketingConsent); // never pre-ticked: false unless the owner chose it
        content.addView(marketing);

        next.setEnabled(kyc.isChecked());
        kyc.setOnCheckedChangeListener((b, c) -> next.setEnabled(c));
        next.setOnClickListener(v -> {
            app.kycConsent = true;
            app.marketingConsent = marketing.isChecked();
            app.consentTimestamp = Instant.now().toString();
            app.noticeVersion = KycStore.NOTICE_VERSION;
            go(2);
        });
    }

    private void tableRow(TableLayout table, boolean header, String... cells) {
        TableRow r = new TableRow(this);
        for (String c : cells) {
            TextView tv = new TextView(this);
            tv.setText(c);
            tv.setPadding(dp(6), dp(6), dp(6), dp(6));
            tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            if (header) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            r.addView(tv);
        }
        table.addView(r);
    }

    // 2. Business lookup
    private void lookup() {
        title(R.string.lookup_title);
        if (app.gstin.isEmpty()) {
            BusinessProfile p = AppState.get().profile();
            if (p.getGstin() != null) app.gstin = p.getGstin();
        }
        TextInputEditText gstin = field(R.string.lookup_gstin, app.gstin,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        TextView gstinResult = para("");
        CheckBox none = new CheckBox(this);
        none.setText(R.string.lookup_no_gstin);
        none.setChecked(app.noGstin);
        content.addView(none);
        TextInputEditText udyam = field(R.string.lookup_udyam, app.udyam,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        SafeClipboard.blockCopy(gstin);
        SafeClipboard.blockCopy(udyam);
        TextView udyamResult = para("");
        TextInputEditText install = field(R.string.lookup_install, app.installationAddress,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        Runnable check = () -> {
            app.gstin = GstinValidator.normalize(String.valueOf(gstin.getText()));
            app.udyam = UdyamValidator.normalize(String.valueOf(udyam.getText()));
            app.noGstin = none.isChecked();
            app.installationAddress = String.valueOf(install.getText()).trim();
            gstin.setEnabled(!app.noGstin);
            boolean gstOk = app.noGstin;
            if (!app.noGstin && !app.gstin.isEmpty()) {
                if (!GstinValidator.isValidFormat(app.gstin)) {
                    gstinResult.setText(R.string.lookup_bad_format);
                } else if (!GstinValidator.hasValidChecksum(app.gstin)) {
                    gstinResult.setText(R.string.lookup_bad_checksum);
                } else {
                    gstOk = true;
                    GstinValidator.GstRecord r = KycEngines.gst(this).find(app.gstin);
                    gstinResult.setText(r == null ? t(R.string.lookup_not_found)
                            : t(R.string.lookup_found, r.tradeName, r.state, r.status) + "\n" + Mask.gstin(app.gstin));
                }
            } else {
                gstinResult.setText("");
            }
            boolean udyamOk = UdyamValidator.isValidFormat(app.udyam);
            if (app.udyam.isEmpty()) {
                udyamResult.setText("");
            } else if (!udyamOk) {
                udyamResult.setText(R.string.lookup_bad_format);
            } else {
                com.udyam.digital.data.UdyamLookup.Record u = KycEngines.udyam().find(app.udyam);
                udyamResult.setText(u == null ? t(R.string.lookup_not_found)
                        : t(R.string.lookup_found, u.enterpriseName, u.state, u.enterpriseType));
            }
            next.setEnabled(gstOk && udyamOk);
        };
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { check.run(); }
        };
        gstin.addTextChangedListener(w);
        udyam.addTextChangedListener(w);
        install.addTextChangedListener(w);
        none.setOnCheckedChangeListener((b, c) -> check.run());
        check.run();
        next.setOnClickListener(v -> { check.run(); go(3); });
    }

    // 3. Documents
    private void upload() {
        title(R.string.upload_title);
        para(t(R.string.upload_hint));
        boolean all = true;
        for (Slot s : app.requiredSlots()) {
            MaterialCardView c = card();
            LinearLayout in = inner(c);
            TextView name = new TextView(this);
            name.setText(s.label(lang));
            name.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            name.setTypeface(name.getTypeface(), Typeface.BOLD);
            in.addView(name);
            KycDocument d = app.doc(s);
            TextView status = new TextView(this);
            status.setText(d == null ? t(R.string.upload_missing) : t(R.string.upload_done, summary(d)));
            in.addView(status);
            if (d == null) all = false;
            com.google.android.material.chip.ChipGroup buttons = new com.google.android.material.chip.ChipGroup(this);
            in.addView(buttons);
            button(buttons, t(R.string.upload_scan), false, v -> camera(s));
            button(buttons, t(R.string.upload_gallery), true, v -> gallery(s));
            button(buttons, t(R.string.upload_digilocker), true, v -> digiLocker(s));
            if (d != null && !d.fields.isEmpty()) button(buttons, t(R.string.upload_check_details), true, v -> confirmFields(s, d, null, () -> camera(s)));
            if (d != null && d.imageFile != null) button(buttons, t(R.string.upload_view), true, v -> viewDocument(s, d));
        }
        if (!all) para(t(R.string.upload_need_all));
        next.setEnabled(all);
        next.setOnClickListener(v -> go(4));
    }

    /** One line about an uploaded document, IDs masked. */
    private String summary(KycDocument d) {
        List<String> parts = new ArrayList<>();
        parts.add(d.type.label(lang));
        for (Map.Entry<String, String> e : d.fields.entrySet()) {
            String k = e.getKey();
            if (k.equals(FieldExtractor.GSTIN)) parts.add(Mask.gstin(e.getValue()));
            else if (k.equals(FieldExtractor.PAN)) parts.add(Mask.pan(e.getValue()));
            else if (k.equals(FieldExtractor.AADHAAR)) parts.add(Mask.aadhaar(e.getValue()));
            else if (k.equals(FieldExtractor.UDYAM)) parts.add(Mask.udyam(e.getValue()));
            else if (k.equals(FieldExtractor.LEGAL_NAME) || k.equals(FieldExtractor.NAME) || k.equals(FieldExtractor.ENTERPRISE)
                    || k.equals(FieldExtractor.BILL_DATE)) parts.add(e.getValue());
        }
        return String.join(" · ", parts);
    }

    /** Scan with the ML Kit document scanner; CameraX with a guide frame if Play services are missing. */
    private void camera(Slot s) {
        pendingSlot = s;
        if (DocScanner.available(this)) {
            DocScanner.start(this, req -> scan.launch(req), () -> cameraFallback(s));
        } else {
            cameraFallback(s);
        }
    }

    private void cameraFallback(Slot s) {
        pendingSlot = s;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhoto.launch(cameraIntent(s));
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private Intent cameraIntent(Slot s) {
        return new Intent(this, CameraCaptureActivity.class).putExtra(CameraCaptureActivity.EXTRA_CARD, s == Slot.SIGNATORY_ID);
    }

    private void gallery(Slot s) {
        pendingSlot = s;
        pickImage.launch("image/*");
    }

    /** Demo: sample documents bundled in debug builds only. */
    private void digiLocker(Slot s) {
        String[] files;
        try {
            files = getAssets().list("kyc_samples");
        } catch (java.io.IOException e) {
            files = null;
        }
        if (files == null || files.length == 0) {
            Toast.makeText(this, R.string.digilocker_none, Toast.LENGTH_LONG).show();
            return;
        }
        List<String> ids = new ArrayList<>();
        Arrays.sort(files);
        for (String f : files) if (f.endsWith(s.specimenSuffix() + ".png")) ids.add(f.substring(0, 3));
        String[] items = ids.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.digilocker_title)
                .setItems(items, (dlg, i) -> readAsset(s, "kyc_samples/" + items[i] + "_" + s.specimenSuffix() + ".png"))
                .setNegativeButton(R.string.cancel, null)
                .show();
        Toast.makeText(this, R.string.digilocker_pick, Toast.LENGTH_SHORT).show();
    }

    /**
     * @param deleteAfter true for the camera's / scanner's temporary file in our cache: it is
     *                    removed as soon as it has been read, so only the encrypted copy remains.
     */
    private void readUri(Slot s, Uri uri, boolean deleteAfter, Runnable again) {
        process(s, () -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return readAll(in);
            } finally {
                if (deleteAfter && "file".equals(uri.getScheme()) && uri.getPath() != null) {
                    java.io.File f = new java.io.File(uri.getPath());
                    if (f.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath())) f.delete();
                }
            }
        }, again);
    }

    private void readAsset(Slot s, String path) {
        process(s, () -> {
            try (InputStream in = getAssets().open(path)) { return readAll(in); }
        }, () -> digiLocker(s));
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    private interface Source { byte[] get() throws Exception; }

    /** 1. Quality gate (no OCR on a bad photo). 2. Preview: "Looks good / Rescan". 3. OCR. */
    @SuppressWarnings("deprecation")
    private void process(Slot s, Source source, Runnable again) {
        ProgressDialog pd = ProgressDialog.show(this, null, t(R.string.upload_checking), true, false);
        worker.execute(() -> {
            try {
                Bitmap bmp = KycImaging.decode(source.get(), 2000);
                if (bmp == null) throw new java.io.IOException("Not an image");
                ImageQuality q = KycImaging.quality(bmp, s);
                ui.post(() -> {
                    pd.dismiss();
                    if (!q.ok()) qualityProblem(q, again); else preview(s, bmp, again);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pd.dismiss();
                    Toast.makeText(this, t(R.string.upload_unclear), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** The exact reason, and a button to try again. Checks never run on a bad photo. */
    private void qualityProblem(ImageQuality q, Runnable again) {
        int msg;
        switch (q.problem) {
            case TOO_SMALL: msg = R.string.upload_too_small; break;
            case TOO_DARK: msg = R.string.upload_too_dark; break;
            case TOO_BRIGHT: msg = R.string.upload_too_bright; break;
            case GLARE: msg = R.string.upload_glare; break;
            default: msg = R.string.upload_blurry; break;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.upload_quality_title)
                .setMessage(msg)
                .setPositiveButton(R.string.upload_rescan, (dl, w) -> again.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void preview(Slot s, Bitmap bmp, Runnable again) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setMaxHeight(dp(440));
        iv.setPadding(dp(20), dp(8), dp(20), 0);
        iv.setContentDescription(t(R.string.upload_preview_title));
        SecureScreens.secure(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.upload_preview_title)
                .setMessage(R.string.upload_preview_hint)
                .setView(iv)
                .setCancelable(false)
                .setPositiveButton(R.string.upload_looks_good, (dl, w) -> ocr(s, bmp, again))
                .setNegativeButton(R.string.upload_rescan, (dl, w) -> again.run())
                .create()).show();
    }

    @SuppressWarnings("deprecation")
    private void ocr(Slot s, Bitmap bmp, Runnable again) {
        ProgressDialog pd = ProgressDialog.show(this, null, t(R.string.upload_reading), true, false);
        worker.execute(() -> {
            try {
                KycImaging.Read r = KycImaging.read(s, bmp, false, KycEngines.engine(this).registry());
                SecureAuditLog.record(this, AccessLog.Actor.APP_OCR, AccessLog.Action.READ_FOR_CHECKS, app.id, s.name(), "");
                // The stored copy is watermarked; OCR above ran on the clean scan. EXIF is stripped on save.
                Bitmap marked = Watermarker.apply(bmp, AppClock.today());
                byte[] jpeg = KycImaging.jpeg(marked);
                marked.recycle();
                ui.post(() -> {
                    pd.dismiss();
                    afterRead(s, r.doc, jpeg, again);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pd.dismiss();
                    Toast.makeText(this, t(R.string.upload_unclear), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void afterRead(Slot s, KycDocument d, byte[] jpeg, Runnable again) {
        // Aadhaar with the full number: never stored.
        if (d.unmaskedAadhaar) {
            message(t(R.string.upload_aadhaar), again);
            return;
        }
        // Wrong document in this slot.
        if (!s.accepts.contains(d.type)) {
            DocType expected = s.accepts.iterator().next();
            String need = s == Slot.SIGNATORY_ID ? s.label(lang) : s == Slot.ADDRESS_PROOF ? s.label(lang) : expected.label(lang);
            message(t(R.string.upload_wrong, d.type.label(lang), need), again);
            return;
        }
        confirmFields(s, d, jpeg, again);
    }

    private void message(String m, Runnable again) {
        new MaterialAlertDialogBuilder(this).setMessage(m)
                .setPositiveButton(R.string.upload_rescan, (dl, w) -> again.run())
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Fingerprint / face / PIN, then the stored (watermarked) copy, in a screenshot-proof dialog. */
    private void viewDocument(Slot s, KycDocument d) {
        DocumentGate.unlockThen(this, app.id, s.name(), () -> worker.execute(() -> {
            try {
                byte[] jpeg = store.readImage(app.id, d.imageFile);
                Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                SecureAuditLog.record(this, AccessLog.Actor.OWNER, AccessLog.Action.VIEWED, app.id, s.name(), "");
                ui.post(() -> {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setImageBitmap(bmp);
                    iv.setAdjustViewBounds(true);
                    iv.setPadding(dp(12), dp(12), dp(12), dp(12));
                    iv.setContentDescription(s.label(lang));
                    SecureScreens.secure(new MaterialAlertDialogBuilder(this).setTitle(s.label(lang)).setView(iv)
                            .setPositiveButton(R.string.upload_ok, null).create()).show();
                });
            } catch (java.io.IOException e) {
                ui.post(() -> Toast.makeText(this, R.string.upload_view_gone, Toast.LENGTH_LONG).show());
            }
        }));
    }

    /**
     * Every field we read, for the owner to confirm or correct. Fields OCR was unsure of
     * (confidence < 0.80) and identifiers we auto-corrected need an explicit tick or an edit;
     * dates that read two ways need a choice. Edited fields become "user-corrected" and are
     * re-checked against the registry. {@code jpeg} null = re-checking a stored document.
     */
    private void confirmFields(Slot s, KycDocument d, byte[] jpeg, Runnable again) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView hint = new TextView(this);
        hint.setText(R.string.upload_confirm_hint);
        box.addView(hint);
        // {field, EditText or null, original text, CheckBox or null, RadioGroup or null, LocalDate[] or null}
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : new ArrayList<>(d.fields.entrySet())) {
            String k = e.getKey();
            if (k.equals(FieldExtractor.PIN)) continue; // derived from the address
            boolean locked = k.equals(FieldExtractor.AADHAAR); // only the masked last 4 digits exist
            TextInputLayout l = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
            l.setHint(FieldLabels.of(k, lang));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(10);
            l.setLayoutParams(lp);
            EditText et = new TextInputEditText(l.getContext());
            et.setText(e.getValue());
            et.setEnabled(!locked);
            boolean id = k.equals(FieldExtractor.GSTIN) || k.equals(FieldExtractor.PAN) || k.equals(FieldExtractor.UDYAM);
            if (id) {
                SafeClipboard.blockCopy(et);
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            }
            l.addView(et);
            box.addView(l);
            LocalDate[] two = d.ambiguous(k) ? KycDates.readings(e.getValue()) : null;
            boolean needTick = !locked && two == null && (d.unsure(k) || (d.autoCorrected.containsKey(k) && !d.vouched(k)));
            CheckBox tick = null;
            android.widget.RadioGroup choice = null;
            if (two != null) {
                l.setHelperText(t(R.string.upload_date_two_ways));
                choice = new android.widget.RadioGroup(this);
                java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",
                        hindi ? new java.util.Locale("hi", "IN") : java.util.Locale.ENGLISH);
                for (int i = 0; i < 2; i++) {
                    android.widget.RadioButton rb = new android.widget.RadioButton(this);
                    rb.setId(View.generateViewId());
                    rb.setText(two[i].format(f));
                    rb.setTag(i);
                    choice.addView(rb);
                }
                box.addView(choice);
                et.setEnabled(false);
            } else if (needTick) {
                String why = d.autoCorrected.containsKey(k) ? t(R.string.upload_autocorrected, d.autoCorrected.get(k)) : t(R.string.upload_unsure);
                l.setHelperText(why);
                l.setBoxStrokeColor(0xFFE08A00);
                tick = new CheckBox(this);
                tick.setText(R.string.upload_tick);
                box.addView(tick);
            }
            rows.add(new Object[] {k, locked ? null : et, e.getValue(), tick, choice, two});
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        // Shows full IDs: its own window must be screenshot-proof too.
        androidx.appcompat.app.AlertDialog dlg = SecureScreens.secure(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.upload_confirm_title) + " · " + d.type.label(lang))
                .setView(sv)
                .setPositiveButton(R.string.upload_ok, null)
                .setNegativeButton(R.string.upload_rescan, (dl, w) -> again.run())
                .create());
        dlg.show();
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Everything that needs the owner's word must have it before we save.
            for (Object[] r : rows) {
                EditText et = (EditText) r[1];
                boolean edited = et != null && !String.valueOf(et.getText()).trim().equals(r[2]);
                if (r[4] != null && ((android.widget.RadioGroup) r[4]).getCheckedRadioButtonId() == -1
                        || r[3] != null && !((CheckBox) r[3]).isChecked() && !edited) {
                    Toast.makeText(this, R.string.upload_need_ticks, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            for (Object[] r : rows) {
                String k = (String) r[0];
                if (r[4] != null) {
                    android.widget.RadioGroup g = (android.widget.RadioGroup) r[4];
                    int idx = (Integer) g.findViewById(g.getCheckedRadioButtonId()).getTag();
                    d.fields.put(k, ((LocalDate[]) r[5])[idx].toString());
                    d.confirm(k);
                    continue;
                }
                EditText et = (EditText) r[1];
                if (et != null) {
                    String v2 = String.valueOf(et.getText()).trim();
                    if (k.equals(FieldExtractor.GSTIN)) v2 = GstinValidator.normalize(v2);
                    if (k.equals(FieldExtractor.UDYAM)) v2 = UdyamValidator.normalize(v2);
                    if (k.equals(FieldExtractor.PAN)) v2 = v2.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
                    d.edit(k, v2);
                }
                if (r[3] != null && ((CheckBox) r[3]).isChecked()) d.confirm(k);
            }
            if (jpeg != null) {
                try {
                    d.imageFile = store.saveImage(app.id, s, jpeg);
                } catch (java.io.IOException ex) {
                    d.imageFile = null;
                }
            }
            app.documents.put(s, d);
            app.confirmedWarnings.clear(); // new evidence: re-confirm warnings
            store.save(app);
            dlg.dismiss();
            render();
        });
    }

    // 4. Review
    private void review() {
        title(R.string.review_title);
        para(t(R.string.review_intro));
        results = KycEngines.engine(this).run(app, KycClock.TODAY);
        List<CheckResult> ordered = new ArrayList<>();
        for (CheckResult r : results) if (r.status == CheckResult.Status.FAIL) ordered.add(r);
        for (CheckResult r : results) if (r.status == CheckResult.Status.WARN) ordered.add(r);
        for (CheckResult r : results) if (r.status == CheckResult.Status.PASS) ordered.add(r);
        for (CheckResult r : ordered) {
            MaterialCardView c = card();
            LinearLayout in = inner(c);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            TextView icon = new TextView(this);
            boolean confirmed = r.status == CheckResult.Status.WARN && app.confirmedWarnings.contains(r.key());
            icon.setText(r.status == CheckResult.Status.PASS || confirmed ? "✓" : r.status == CheckResult.Status.WARN ? "⚠" : "✗");
            icon.setTextSize(22);
            icon.setTextColor(r.status == CheckResult.Status.PASS || confirmed ? 0xFF21A75A
                    : r.status == CheckResult.Status.WARN ? 0xFFE08A00 : 0xFFD32F2F);
            icon.setPadding(0, 0, dp(12), 0);
            row.addView(icon);
            TextView msg = new TextView(this);
            msg.setText(r.message(lang) + "  (" + r.code + ")");
            msg.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            row.addView(msg, new LinearLayout.LayoutParams(0, -2, 1));
            in.addView(row);
            LinearLayout actions = new LinearLayout(this);
            in.addView(actions);
            if (r.status == CheckResult.Status.WARN && !r.fixable) {
                TextView person = new TextView(this);
                person.setText(R.string.review_person);
                actions.addView(person);
            } else if (r.status == CheckResult.Status.WARN && !confirmed) {
                button(actions, t(R.string.review_confirm), false, v -> {
                    app.confirmedWarnings.add(r.key());
                    store.save(app);
                    render();
                });
            } else if (confirmed) {
                TextView done = new TextView(this);
                done.setText(R.string.review_confirmed);
                actions.addView(done);
            }
            if (r.status == CheckResult.Status.FAIL && r.fixable) {
                button(actions, t(r.rescan ? R.string.review_rescan : R.string.review_fix), true,
                        v -> go(r.fixAt == CheckResult.FixAt.LOOKUP ? 2 : 3));
            }
            if (r.code.equals("C02") && r.status == CheckResult.Status.FAIL && KycRuleEngine.inactiveGstin(results)) {
                button(actions, t(R.string.result_plan_without_gst), true, v -> planWithoutGst());
            }
        }
        next.setText(R.string.review_submit);
        next.setOnClickListener(v -> submit());
    }

    private void submit() {
        if (results == null) results = KycEngines.engine(this).run(app, KycClock.TODAY);
        KycRuleEngine.Decision d = KycRuleEngine.decide(app, results);
        app.outcome = d.outcome;
        app.outcomeReason = d.reason == null ? "" : d.reason.in(lang);
        if (d.outcome == KycApplication.Outcome.MANUAL_REVIEW && app.ticketId == null) {
            app.ticketId = store.createTicket(app.id); // also +30 on the help bot's escalation score
        }
        if (d.outcome != KycApplication.Outcome.PLEASE_FIX) app.decidedOn = KycClock.TODAY.toString();
        // Keep the order in step: approved -> KYC_COMPLETE today; manual review -> dates paused.
        com.udyam.digital.orderui.OrderManager om = new com.udyam.digital.orderui.OrderManager(this);
        if (d.outcome == KycApplication.Outcome.APPROVED) om.onKycApproved();
        else if (d.outcome == KycApplication.Outcome.MANUAL_REVIEW) om.onKycReview(app.outcomeReason);
        SecureAuditLog.checks(this, app.id, results);
        go(5);
    }

    private void planWithoutGst() {
        BusinessProfile p = AppState.get().profile();
        p.setHasValidGstin(false);
        AppState.get().setPacks(DataRepository.get().engine().packs(p));
        startActivity(new Intent(this, PlanActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    // 5. Result
    private void result() {
        next.setVisibility(View.GONE);
        MaterialCardView c = card();
        LinearLayout in = inner(c);
        TextView head = new TextView(this);
        head.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        TextView body = new TextView(this);
        body.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        in.addView(head);
        in.addView(body);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        in.addView(actions);
        switch (app.outcome) {
            case APPROVED:
                head.setText("✅ " + t(R.string.result_approved));
                body.setText(R.string.result_approved_msg);
                if (new com.udyam.digital.orderui.OrderRepository(this).hasOrder()) {
                    button(actions, t(R.string.result_continue_pay), false, v ->
                            startActivity(new Intent(this, com.udyam.digital.orderui.CheckoutActivity.class)));
                }
                button(actions, t(R.string.result_esign), false, v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.result_esign).setMessage(R.string.result_esign_msg)
                        .setPositiveButton(R.string.upload_ok, (dl, w) ->
                                Toast.makeText(this, R.string.result_esigned, Toast.LENGTH_LONG).show())
                        .show());
                break;
            case MANUAL_REVIEW:
                head.setText("🧑‍💼 " + t(R.string.result_manual));
                body.setText(t(R.string.result_manual_msg, app.outcomeReason, app.ticketId));
                // For support calls: the reference with IDs masked, never the full numbers.
                button(actions, t(R.string.result_copy_ref), true, v -> {
                    SafeClipboard.copyMasked(this, "KYC reference", app.ticketId + " · GSTIN " + app.gstin + " · " + app.udyam);
                    Toast.makeText(this, R.string.result_copied, Toast.LENGTH_SHORT).show();
                });
                button(actions, t(R.string.cta_chat), false,
                        v -> startActivity(new Intent(this, com.udyam.digital.help.HelpActivity.class)));
                if (results == null) results = KycEngines.engine(this).run(app, KycClock.TODAY);
                if (KycRuleEngine.inactiveGstin(results)) {
                    button(actions, t(R.string.result_plan_without_gst), true, v -> planWithoutGst());
                }
                break;
            default:
                if (results == null) results = KycEngines.engine(this).run(app, KycClock.TODAY);
                int n = KycRuleEngine.decide(app, results).toFix;
                head.setText("✏️ " + t(R.string.result_fix, n));
                body.setText(R.string.result_fix_msg);
                button(actions, t(R.string.result_go_fix), false, v -> go(4));
                break;
        }
        button(actions, t(R.string.result_tracker), true, v -> startActivity(new Intent(this, com.udyam.digital.orderui.TrackerActivity.class)));
    }
}
