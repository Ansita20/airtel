package com.udyam.digital.kycui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.udyam.digital.kyc.DocType;
import com.udyam.digital.kyc.ImageQuality;
import com.udyam.digital.kyc.KycApplication;
import com.udyam.digital.kyc.KycDocument;
import com.udyam.digital.kyc.Mask;
import com.udyam.digital.kyc.Slot;
import com.udyam.digital.security.AccessLog;
import com.udyam.digital.security.DocumentShredder;
import com.udyam.digital.security.JpegMetadata;
import com.udyam.digital.security.KeyVault;
import com.udyam.digital.security.RetentionPolicy;
import com.udyam.digital.security.SecureAuditLog;
import com.udyam.digital.util.AppClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encrypted KYC storage, ONE KEY PER APPLICATION. Everything about an application - the
 * extracted fields (application.enc) and every document photo (&lt;slot&gt;.enc) - lives in
 * files/kyc/&lt;applicationId&gt;/ (app-private storage), encrypted with AES-256-GCM through
 * Jetpack Security EncryptedFile under that application's own Android Keystore key
 * ({@link KeyVault}). Deleting an application destroys the key first (crypto-shredding).
 *
 * Small app-wide state (which application is current, retained results with masked IDs,
 * tickets) is in EncryptedSharedPreferences.
 *
 * Retention ({@link RetentionPolicy}, run daily by RetentionWorker): documents go 30 days after
 * the verdict; the result + masked IDs stay for the contract period.
 */
public final class KycStore {

    public static final String NOTICE_VERSION = "2026-07-v1";
    public static final int RETENTION_DAYS = RetentionPolicy.DOCUMENT_DAYS;

    private static final String PREFS = "kyc_secure";
    private static final String KEY_CURRENT = "current_application";
    private static final String KEY_RESULTS = "retained_results";
    /** Before per-application keys the whole application was stored here (migrated on first load). */
    private static final String LEGACY_KEY_APP = "application";
    private static final String LEGACY_KEY_SUMMARY = "summary";
    private static final String KEY_TICKETS = "tickets";
    private static final String KEY_ESCALATION = "helpbot_escalation_score";
    private static final String APP_FILE = "application.enc";

    private final Context ctx;
    private final MasterKey prefsKey;
    private final SharedPreferences prefs;

    public KycStore(Context context) {
        this.ctx = context.getApplicationContext();
        try {
            prefsKey = new MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .setRequestStrongBoxBacked(true).build();
            prefs = EncryptedSharedPreferences.create(ctx, PREFS, prefsKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Secure storage unavailable", e);
        }
        migrateLegacy();
    }

    private File root() { return new File(ctx.getFilesDir(), "kyc"); }

    private File dir(String appId) { return new File(root(), appId); }

    // ------------------------------------------------------------------ application
    public KycApplication loadOrCreate() {
        KycApplication a = load();
        return a != null ? a : new KycApplication(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /** The current application, or null. */
    public KycApplication load() {
        String id = prefs.getString(KEY_CURRENT, null);
        return id == null ? null : load(id);
    }

    private KycApplication load(String appId) {
        File f = new File(dir(appId), APP_FILE);
        if (!f.exists()) return null;
        try {
            return fromJson(new JSONObject(new String(KeyVault.read(ctx, appId, f), StandardCharsets.UTF_8)));
        } catch (IOException | JSONException e) {
            return null; // key destroyed or file damaged: unreadable by design
        }
    }

    public void save(KycApplication a) {
        a.updatedOn = AppClock.today().toString();
        try {
            KeyVault.write(ctx, a.id, new File(dir(a.id), APP_FILE), toJson(a).toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException | IOException e) {
            throw new IllegalStateException("Could not save the application securely", e);
        }
        prefs.edit().putString(KEY_CURRENT, a.id).apply();
    }

    // ------------------------------------------------------------------ images
    /**
     * Stores a document photo encrypted with the application's key. EXIF/metadata is stripped
     * again here whatever the caller did, and scanner/camera temp files are purged afterwards.
     * Returns the file name to keep in the document.
     */
    public String saveImage(String appId, Slot slot, byte[] jpeg) throws IOException {
        String name = slot.name().toLowerCase(java.util.Locale.ROOT) + ".enc";
        KeyVault.write(ctx, appId, new File(dir(appId), name), JpegMetadata.strip(jpeg));
        TempFiles.purge(ctx);
        SecureAuditLog.record(ctx, AccessLog.Actor.APP_STORE, AccessLog.Action.SAVED, appId, slot.name(), "");
        return name;
    }

    /** Decrypts a stored photo. Callers must have passed {@link DocumentGate} and log the access. */
    public byte[] readImage(String appId, String name) throws IOException {
        return KeyVault.read(ctx, appId, new File(dir(appId), name));
    }

    // ------------------------------------------------------------------ withdraw / retention
    private List<String> applicationIds() {
        List<String> ids = new ArrayList<>();
        File[] kids = root().listFiles();
        if (kids != null) for (File k : kids) if (k.isDirectory()) ids.add(k.getName());
        return ids;
    }

    private DocumentShredder.KeyDestroyer destroyer(AccessLog.Actor actor) {
        return id -> {
            KeyVault.destroy(ctx, id);
            SecureAuditLog.record(ctx, actor, AccessLog.Action.KEY_DESTROYED, id, "", "");
        };
    }

    /**
     * "Withdraw consent and delete my documents": destroys every application's key, then
     * deletes its files, results and tickets. The access log keeps a record that it happened.
     */
    public int withdrawAndDeleteAll() {
        int n = 0;
        for (String id : applicationIds()) {
            n += DocumentShredder.shred(root(), id, destroyer(AccessLog.Actor.OWNER_WITHDRAW));
            SecureAuditLog.record(ctx, AccessLog.Actor.OWNER_WITHDRAW, AccessLog.Action.DELETED, id, "ALL", "");
        }
        DocumentShredder.deleteTree(root());
        prefs.edit().remove(KEY_CURRENT).remove(KEY_RESULTS).remove(KEY_TICKETS).remove(KEY_ESCALATION).apply();
        TempFiles.purge(ctx);
        return n;
    }

    /** What one retention run did. */
    public static final class RetentionResult {
        public final List<String> shredded = new ArrayList<>();
        public int resultsDeleted;

        @Override public String toString() {
            return "documents deleted for " + shredded.size() + " application(s)" + (shredded.isEmpty() ? "" : " " + shredded)
                    + ", " + resultsDeleted + " old result(s) removed";
        }
    }

    /**
     * Applies the retention policy as of {@code today}: shreds documents past 30 days after the
     * verdict (or 90 days after the last change if never submitted), keeping the result and
     * masked IDs until the end of the contract; drops results whose contract period is over.
     */
    public RetentionResult runRetention(LocalDate today) {
        RetentionResult r = new RetentionResult();
        Map<String, DocumentShredder.Dates> dates = new LinkedHashMap<>();
        Map<String, KycApplication> apps = new LinkedHashMap<>();
        for (String id : applicationIds()) {
            KycApplication a = load(id);
            if (a == null) { // unreadable (key gone): nothing worth keeping
                dates.put(id, new DocumentShredder.Dates(today.minusDays(RetentionPolicy.DOCUMENT_DAYS), null));
                continue;
            }
            apps.put(id, a);
            dates.put(id, new DocumentShredder.Dates(a.decidedOn == null ? null : LocalDate.parse(a.decidedOn),
                    a.updatedOn == null ? today : LocalDate.parse(a.updatedOn)));
        }
        r.shredded.addAll(DocumentShredder.runRetention(root(), dates, today, destroyer(AccessLog.Actor.RETENTION_JOB)));
        JSONArray results = results();
        for (String id : r.shredded) {
            KycApplication a = apps.get(id);
            SecureAuditLog.record(ctx, AccessLog.Actor.RETENTION_JOB, AccessLog.Action.DELETED, id, "ALL", "");
            if (a != null && a.decidedOn != null) results.put(summary(a));
            if (id.equals(prefs.getString(KEY_CURRENT, null))) prefs.edit().remove(KEY_CURRENT).apply();
        }
        JSONArray kept = new JSONArray();
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) continue;
            if (RetentionPolicy.deleteResult(LocalDate.parse(o.optString("keepUntil", today.toString())), today)) r.resultsDeleted++;
            else kept.put(o);
        }
        prefs.edit().putString(KEY_RESULTS, kept.toString()).apply();
        SecureAuditLog.record(ctx, AccessLog.Actor.RETENTION_JOB, AccessLog.Action.RETENTION_RUN, "", "",
                "AS OF " + today + ": " + r.shredded.size() + " APPS, " + r.resultsDeleted + " RESULTS");
        return r;
    }

    private JSONObject summary(KycApplication a) {
        JSONObject s = new JSONObject();
        try {
            int months = 0;
            try {
                com.udyam.digital.order.Order o = new com.udyam.digital.orderui.OrderRepository(ctx).load();
                if (o != null) months = o.contractMonths;
            } catch (RuntimeException ignored) {
            }
            LocalDate decided = LocalDate.parse(a.decidedOn);
            s.put("id", a.id).put("outcome", a.outcome.name()).put("decidedOn", a.decidedOn)
                    .put("gstin", Mask.gstin(a.gstin)).put("udyam", Mask.udyam(a.udyam))
                    .put("keepUntil", RetentionPolicy.keepResultUntil(decided, months).toString());
        } catch (JSONException ignored) {
        }
        return s;
    }

    private JSONArray results() {
        try {
            return new JSONArray(prefs.getString(KEY_RESULTS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    /** What remains after retention: outcome + masked IDs + keep-until date, or null. */
    public String retainedSummary() {
        JSONArray r = results();
        if (r.length() == 0) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.optJSONObject(i);
            if (o == null) continue;
            if (b.length() > 0) b.append('\n');
            b.append(o.optString("id")).append(": ").append(o.optString("outcome")).append(", ").append(o.optString("decidedOn"))
                    .append(", GSTIN ").append(o.optString("gstin")).append(", Udyam ").append(o.optString("udyam"))
                    .append(" (kept until ").append(o.optString("keepUntil")).append(')');
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ legacy migration
    /** Moves an application stored by the previous version (one shared key) to its own key. */
    @SuppressWarnings("deprecation")
    private void migrateLegacy() {
        // The old check log was a plaintext CSV; the access log is now encrypted (SecureAuditLog).
        new File(ctx.getFilesDir(), "kyc_audit.csv").delete();
        String legacy = prefs.getString(LEGACY_KEY_APP, null);
        if (legacy == null) return;
        try {
            KycApplication a = fromJson(new JSONObject(legacy));
            for (KycDocument d : a.documents.values()) {
                if (d.imageFile == null) continue;
                File f = new File(dir(a.id), d.imageFile);
                try {
                    EncryptedFile old = new EncryptedFile.Builder(ctx, f, prefsKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
                    byte[] jpeg;
                    try (InputStream in = old.openFileInput(); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                        jpeg = b.toByteArray();
                    }
                    KeyVault.write(ctx, a.id, f, JpegMetadata.strip(jpeg));
                } catch (Exception e) {
                    f.delete(); // can't be moved safely: ask for the photo again
                    d.imageFile = null;
                }
            }
            save(a);
        } catch (JSONException | RuntimeException ignored) {
        }
        String oldSummary = prefs.getString(LEGACY_KEY_SUMMARY, null);
        if (oldSummary != null) {
            JSONArray r = results();
            try {
                JSONObject o = new JSONObject(oldSummary);
                o.put("keepUntil", RetentionPolicy.keepResultUntil(LocalDate.parse(o.getString("decidedOn")), 0).toString());
                r.put(o);
            } catch (JSONException | RuntimeException ignored) {
            }
            prefs.edit().putString(KEY_RESULTS, r.toString()).apply();
        }
        prefs.edit().remove(LEGACY_KEY_APP).remove(LEGACY_KEY_SUMMARY).apply();
    }

    // ------------------------------------------------------------------ tickets / help bot
    public String createTicket(String appId) {
        String id = "KYC-" + appId;
        prefs.edit().putString(KEY_TICKETS, id).putInt(KEY_ESCALATION, escalationScore() + 30).apply();
        return id;
    }

    public int escalationScore() { return prefs.getInt(KEY_ESCALATION, 0); }

    // ------------------------------------------------------------------ JSON
    private static JSONObject toJson(KycApplication a) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", a.id).put("step", a.step).put("kycConsent", a.kycConsent).put("marketing", a.marketingConsent)
                .put("consentTs", a.consentTimestamp).put("notice", a.noticeVersion)
                .put("gstin", a.gstin).put("noGstin", a.noGstin).put("udyam", a.udyam).put("install", a.installationAddress)
                .put("outcome", a.outcome.name()).put("reason", a.outcomeReason).put("decidedOn", a.decidedOn)
                .put("updatedOn", a.updatedOn)
                .put("ticket", a.ticketId).put("confirmed", new JSONArray(a.confirmedWarnings));
        JSONObject docs = new JSONObject();
        for (KycDocument d : a.documents.values()) {
            JSONObject j = new JSONObject();
            j.put("type", d.type.name()).put("fields", new JSONObject(d.fields)).put("corrected", new JSONArray(d.userCorrected))
                    .put("hash", d.hash).put("aadhaar", d.unmaskedAadhaar).put("image", d.imageFile)
                    .put("confirmedFields", new JSONArray(d.confirmed)).put("autoCorrected", new JSONObject(d.autoCorrected))
                    .put("ambiguous", new JSONArray(d.ambiguousDates)).put("disagree", new JSONObject(d.passDisagreement))
                    .put("fontMismatch", new JSONArray(d.fontMismatch));
            JSONObject conf = new JSONObject();
            for (java.util.Map.Entry<String, Float> e : d.confidence.entrySet()) conf.put(e.getKey(), (double) e.getValue());
            j.put("conf", conf);
            if (d.quality != null) {
                j.put("q", new JSONArray().put(d.quality.width).put(d.quality.height).put(d.quality.brightness).put(d.quality.sharpness)
                        .put(d.quality.glare).put(d.quality.inkLevel).put(d.quality.idCard));
            }
            docs.put(d.slot.name(), j);
        }
        o.put("docs", docs);
        return o;
    }

    private static KycApplication fromJson(JSONObject o) throws JSONException {
        KycApplication a = new KycApplication(o.getString("id"));
        a.step = o.optInt("step", 1);
        a.kycConsent = o.optBoolean("kycConsent");
        a.marketingConsent = o.optBoolean("marketing");
        a.consentTimestamp = o.optString("consentTs", null);
        a.noticeVersion = o.optString("notice", null);
        a.gstin = o.optString("gstin", "");
        a.noGstin = o.optBoolean("noGstin");
        a.udyam = o.optString("udyam", "");
        a.installationAddress = o.optString("install", "");
        a.outcome = KycApplication.Outcome.valueOf(o.optString("outcome", "IN_PROGRESS"));
        a.outcomeReason = o.optString("reason", "");
        a.decidedOn = o.isNull("decidedOn") ? null : o.optString("decidedOn", null);
        a.updatedOn = o.isNull("updatedOn") ? null : o.optString("updatedOn", null);
        a.ticketId = o.isNull("ticket") ? null : o.optString("ticket", null);
        JSONArray c = o.optJSONArray("confirmed");
        if (c != null) for (int i = 0; i < c.length(); i++) a.confirmedWarnings.add(c.getString(i));
        JSONObject docs = o.optJSONObject("docs");
        if (docs != null) {
            for (Iterator<String> it = docs.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject j = docs.getJSONObject(k);
                KycDocument d = new KycDocument(Slot.valueOf(k));
                d.type = DocType.valueOf(j.optString("type", "UNKNOWN"));
                JSONObject f = j.optJSONObject("fields");
                if (f != null) for (Iterator<String> fi = f.keys(); fi.hasNext(); ) {
                    String fk = fi.next();
                    d.fields.put(fk, f.getString(fk));
                }
                JSONArray cr = j.optJSONArray("corrected");
                if (cr != null) for (int i = 0; i < cr.length(); i++) d.userCorrected.add(cr.getString(i));
                d.hash = j.optLong("hash");
                d.unmaskedAadhaar = j.optBoolean("aadhaar");
                d.imageFile = j.isNull("image") ? null : j.optString("image", null);
                addAll(j.optJSONArray("confirmedFields"), d.confirmed);
                addAll(j.optJSONArray("ambiguous"), d.ambiguousDates);
                addAll(j.optJSONArray("fontMismatch"), d.fontMismatch);
                putAll(j.optJSONObject("autoCorrected"), d.autoCorrected);
                putAll(j.optJSONObject("disagree"), d.passDisagreement);
                JSONObject conf = j.optJSONObject("conf");
                if (conf != null) for (Iterator<String> ci = conf.keys(); ci.hasNext(); ) {
                    String ck = ci.next();
                    d.confidence.put(ck, (float) conf.getDouble(ck));
                }
                JSONArray q = j.optJSONArray("q");
                if (q != null) d.quality = q.length() >= 7
                        ? new ImageQuality(q.getInt(0), q.getInt(1), q.getDouble(2), q.getDouble(3), q.getDouble(4), q.getInt(5), q.getBoolean(6))
                        : new ImageQuality(q.getInt(0), q.getInt(1), q.getDouble(2), q.getDouble(3));
                a.documents.put(d.slot, d);
            }
        }
        return a;
    }

    private static void addAll(JSONArray arr, java.util.Set<String> into) throws JSONException {
        if (arr != null) for (int i = 0; i < arr.length(); i++) into.add(arr.getString(i));
    }

    private static void putAll(JSONObject o, java.util.Map<String, String> into) throws JSONException {
        if (o != null) for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            into.put(k, o.getString(k));
        }
    }
}
