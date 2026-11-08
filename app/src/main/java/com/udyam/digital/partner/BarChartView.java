package com.udyam.digital.partner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.udyam.digital.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple horizontal bar chart (one series): category on the left, a thin bar anchored at the
 * baseline with a rounded data end, and the value (+ optional note, e.g. conversion %) as a
 * direct label. Bar colour is the validated chart_bar (its own step in dark mode); all text uses
 * the theme's text colours, never the bar colour. Tap a bar to see its detail; TalkBack reads
 * every value (content description), so nothing depends on colour or on seeing the bars.
 */
public final class BarChartView extends View {

    public static final class Bar {
        final String label, value, detail;
        final double amount;

        public Bar(String label, double amount, String value, String detail) {
            this.label = label;
            this.amount = amount;
            this.value = value;
            this.detail = detail;
        }
    }

    private final List<Bar> bars = new ArrayList<>();
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            muted = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private int selected = -1;
    private Consumer<String> onSelect;

    public BarChartView(Context c) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        bar.setColor(ContextCompat.getColor(c, R.color.chart_bar));
        int ink = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
        int inkMuted = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF555555);
        text.setColor(ink);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics()));
        muted.setColor(inkMuted);
        muted.setTextSize(text.getTextSize());
        track.setColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0x14000000));
    }

    public void setBars(List<Bar> data, Consumer<String> onSelect) {
        bars.clear();
        bars.addAll(data);
        this.onSelect = onSelect;
        selected = -1;
        StringBuilder d = new StringBuilder();
        for (Bar b : data) d.append(b.label).append(": ").append(b.value).append(b.detail == null ? "" : ", " + b.detail).append(". ");
        setContentDescription(d.toString());
        requestLayout();
        invalidate();
    }

    private float rowH() { return 44 * dp; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (bars.size() * rowH() + 4 * dp));
    }

    @Override protected void onDraw(Canvas c) {
        double max = 0;
        for (Bar b : bars) max = Math.max(max, b.amount);
        float w = getWidth();
        float barH = 10 * dp, r = 4 * dp;
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            float top = i * rowH();
            // Category name above the bar, value at the right end of the row.
            c.drawText(b.label, 0, top + 16 * dp, i == selected ? text : muted);
            float vw = text.measureText(b.value);
            c.drawText(b.value, w - vw, top + 16 * dp, text);
            float y = top + 24 * dp, full = w;
            c.drawRoundRect(new RectF(0, y, full, y + barH), r, r, track);
            float len = max <= 0 ? 0 : (float) (b.amount / max) * full;
            if (len > 0) {
                // Rounded data end, square-ish start: draw a rounded rect and square off its left side.
                c.drawRoundRect(new RectF(0, y, Math.max(len, 2 * r), y + barH), r, r, bar);
                c.drawRect(0, y, Math.min(r, len), y + barH, bar);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int i = (int) (e.getY() / rowH());
            if (i >= 0 && i < bars.size()) {
                selected = i;
                invalidate();
                Bar b = bars.get(i);
                if (onSelect != null) onSelect.accept(b.label + ": " + b.value + (b.detail == null ? "" : " - " + b.detail));
                performClick();
            }
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }
}
