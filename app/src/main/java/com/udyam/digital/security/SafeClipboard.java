package com.udyam.digital.security;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * Clipboard rules: full IDs are never copied. Fields that show or take a GSTIN, PAN or Udyam
 * number have copy/cut/paste menus switched off ({@link #blockCopy}); anything the app itself
 * copies goes through {@link #copyMasked}, which masks every ID and marks the clip as sensitive
 * (Android 13+ then hides it from the clipboard preview).
 */
public final class SafeClipboard {
    private SafeClipboard() {}

    public static void blockCopy(EditText e) {
        ActionMode.Callback none = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem i) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) { }
        };
        e.setCustomSelectionActionModeCallback(none);
        e.setCustomInsertionActionModeCallback(none);
        e.setLongClickable(false);
        e.setTextIsSelectable(false);
    }

    /** Copies {@code text} with every ID masked. Returns what was copied. */
    public static String copyMasked(Context ctx, String label, String text) {
        String safe = OutboundGuard.redact(text);
        ClipData clip = ClipData.newPlainText(label, safe);
        if (Build.VERSION.SDK_INT >= 33) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(clip);
        return safe;
    }
}
