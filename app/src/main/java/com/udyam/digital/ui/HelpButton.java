package com.udyam.digital.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.MenuItem;
import android.widget.ImageButton;

import com.google.android.material.appbar.MaterialToolbar;
import com.udyam.digital.R;
import com.udyam.digital.help.HelpActivity;

/** The Help button in the header of every screen: a "?" icon that opens the help bot. */
public final class HelpButton {
    private HelpButton() {}

    public static void open(Activity a) {
        a.startActivity(new Intent(a, HelpActivity.class));
    }

    /** Adds Help as the right-most toolbar action. Returns the item so callers can tell it apart. */
    public static MenuItem addTo(MaterialToolbar toolbar, Activity a) {
        MenuItem item = toolbar.getMenu().add(R.string.chat_title);
        item.setIcon(R.drawable.ic_help);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item.setOnMenuItemClickListener(i -> {
            open(a);
            return true;
        });
        return item;
    }

    /** For screens without a toolbar: wires an existing header ImageButton. */
    public static void wire(ImageButton button, Activity a) {
        button.setContentDescription(a.getString(R.string.chat_title));
        button.setOnClickListener(v -> open(a));
    }
}
