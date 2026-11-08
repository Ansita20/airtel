package com.udyam.digital.orderui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** Small view builders shared by checkout and tracker. Colours always come from the theme. */
final class OrderUi {
    private final Activity a;
    final LinearLayout root;

    OrderUi(Activity a, LinearLayout root) {
        this.a = a;
        this.root = root;
    }

    int dp(int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    int color(int attr) { return MaterialColors.getColor(root, attr); }

    TextView text(ViewGroup parent, CharSequence s, int appearance, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextAppearance(appearance);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        t.setLayoutParams(lp);
        parent.addView(t);
        return t;
    }

    TextView title(ViewGroup p, CharSequence s) {
        return text(p, s, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge, true);
    }

    TextView body(ViewGroup p, CharSequence s) {
        return text(p, s, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge, false);
    }

    TextView small(ViewGroup p, CharSequence s) {
        TextView t = text(p, s, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium, false);
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        return t;
    }

    /** Card with an optional container colour attribute (0 = surface + outline). */
    LinearLayout card(int bgAttr, int fgAttr) {
        MaterialCardView c = bgAttr == 0
                ? new MaterialCardView(a, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle)
                : new MaterialCardView(a);
        if (bgAttr != 0) {
            c.setCardBackgroundColor(color(bgAttr));
            c.setStrokeWidth(0);
        }
        c.setRadius(dp(18));
        c.setCardElevation(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        LinearLayout in = new LinearLayout(a);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setPadding(dp(16), dp(12), dp(16), dp(14));
        c.addView(in);
        root.addView(c);
        in.setTag(fgAttr);
        return in;
    }

    MaterialButton button(ViewGroup parent, CharSequence label, boolean outlined, android.view.View.OnClickListener l) {
        MaterialButton b = outlined
                ? new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                : new MaterialButton(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        parent.addView(b);
        return b;
    }
}
