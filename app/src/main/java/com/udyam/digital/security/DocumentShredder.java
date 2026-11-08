package com.udyam.digital.security;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deletes one application's documents: destroys its key first (crypto-shredding: any copy left
 * on flash or in a backup is now unreadable), then deletes the files. Also runs the document
 * part of {@link RetentionPolicy} over files/kyc/&lt;applicationId&gt;/. Pure Java (tested with
 * temporary folders); on the phone the key destroyer deletes the Android Keystore key.
 */
public final class DocumentShredder {
    private DocumentShredder() {}

    public interface KeyDestroyer { void destroy(String appId); }

    /** When each application was decided and last changed. */
    public static final class Dates {
        public final LocalDate decidedOn, lastChanged;

        public Dates(LocalDate decidedOn, LocalDate lastChanged) {
            this.decidedOn = decidedOn;
            this.lastChanged = lastChanged;
        }
    }

    /** Destroys the key, then deletes files/kyc/&lt;appId&gt;. Returns files deleted. */
    public static int shred(File kycRoot, String appId, KeyDestroyer keys) {
        keys.destroy(appId);
        return deleteTree(new File(kycRoot, appId));
    }

    /**
     * Shreds every application whose documents are past retention on {@code today}.
     * @return ids shredded
     */
    public static List<String> runRetention(File kycRoot, Map<String, Dates> apps, LocalDate today, KeyDestroyer keys) {
        List<String> done = new ArrayList<>();
        for (Map.Entry<String, Dates> e : apps.entrySet()) {
            Dates d = e.getValue();
            if (RetentionPolicy.deleteDocuments(d.decidedOn, d.lastChanged, today)) {
                shred(kycRoot, e.getKey(), keys);
                done.add(e.getKey());
            }
        }
        return done;
    }

    public static int deleteTree(File f) {
        int n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += deleteTree(k);
        if (f.exists() && f.delete()) n++;
        return n;
    }
}
