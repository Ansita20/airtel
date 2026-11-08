package com.udyam.digital.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.udyam.digital.R;

import java.util.List;

/** Builds the big, chunky tap tiles (emoji on top, 1–3 words below) and lays them out in a grid. */
final class OptionCards {
    private OptionCards() {}

    static View create(LayoutInflater inflater, ViewGroup parent, String icon, String label, String hint) {
        View tile = inflater.inflate(R.layout.item_tile, parent, false);
        ((TextView) tile.findViewById(R.id.option_icon)).setText(icon);
        TextView labelView = tile.findViewById(R.id.option_label);
        labelView.setText(label);
        tile.setContentDescription(label);
        TextView hintView = tile.findViewById(R.id.option_hint);
        if (hint != null && !hint.isEmpty()) {
            hintView.setText(hint);
            hintView.setVisibility(View.VISIBLE);
        }
        return tile;
    }

    /** Selection colours come from the tile's state-list background and text colours. */
    static void setSelected(View tile, boolean selected) {
        tile.setSelected(selected);
        View check = tile.findViewById(R.id.option_check);
        if (check != null) check.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    /** A small "pop" so a tap feels acknowledged. */
    static void bounce(View v) {
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110).start())
                .start();
    }

    /** Adds tiles to the container in rows of {@code columns}, all tiles in a row the same height. */
    static void grid(ViewGroup container, List<View> tiles, int columns) {
        Context ctx = container.getContext();
        int gap = ctx.getResources().getDimensionPixelSize(R.dimen.tile_gap);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(tiles.get(i), lp);
        }
        int rem = tiles.size() % columns;
        if (rem != 0) {
            for (int i = rem; i < columns; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.setMargins(gap, gap, gap, gap);
                row.addView(new Space(ctx), lp);
            }
        }
    }
}
