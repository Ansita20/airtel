package com.udyam.digital.kycui;

import android.content.Context;

import java.io.File;
import java.util.Locale;

/**
 * Scanner and camera temp files live in the app cache only until the encrypted copy is saved.
 * {@link #purge} removes every image/PDF left in the cache (including the ML Kit document
 * scanner's output folder). Called after each save and whenever the KYC screen opens.
 */
public final class TempFiles {
    private TempFiles() {}

    public static int purge(Context ctx) {
        return purge(ctx.getCacheDir());
    }

    static int purge(File dir) {
        int n = 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        for (File f : kids) {
            if (f.isDirectory()) {
                n += purge(f);
                String[] left = f.list();
                if (left != null && left.length == 0) f.delete();
            } else {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".pdf")
                        || name.endsWith(".webp") || name.endsWith(".heic")) {
                    if (f.delete()) n++;
                }
            }
        }
        return n;
    }
}
