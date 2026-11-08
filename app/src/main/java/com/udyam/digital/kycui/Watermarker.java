package com.udyam.digital.kycui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.time.LocalDate;

/**
 * Stamps the STORED copy of a document with "For Accel Business KYC only - {date}": a solid
 * band at the bottom plus faint diagonal repeats across the page, so a leaked image can't be
 * passed off for another purpose. OCR runs on the clean photo before this; the owner's paper
 * is untouched.
 */
public final class Watermarker {
    private Watermarker() {}

    public static String text(LocalDate date) { return "For Accel Business KYC only - " + date; }

    public static Bitmap apply(Bitmap src, LocalDate date) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        int w = b.getWidth(), h = b.getHeight();
        String t = text(date);

        // Faint diagonal repeats across the whole document.
        Paint faint = new Paint(Paint.ANTI_ALIAS_FLAG);
        faint.setColor(Color.argb(60, 200, 30, 30));
        faint.setTypeface(Typeface.DEFAULT_BOLD);
        faint.setTextSize(Math.max(18f, Math.min(w, h) / 22f));
        float step = faint.getTextSize() * 5;
        c.save();
        c.rotate(-30, w / 2f, h / 2f);
        for (float y = -h; y < 2 * h; y += step) {
            for (float x = -w; x < 2 * w; x += faint.measureText(t) + step) c.drawText(t, x, y, faint);
        }
        c.restore();

        // Solid band at the bottom, always readable.
        Paint band = new Paint();
        band.setColor(Color.argb(210, 120, 0, 0));
        float bandH = Math.max(28f, h / 24f);
        c.drawRect(0, h - bandH, w, h, band);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(bandH * 0.55f);
        float tw = label.measureText(t);
        if (tw > w * 0.94f) label.setTextSize(label.getTextSize() * w * 0.94f / tw);
        c.drawText(t, (w - label.measureText(t)) / 2f, h - bandH * 0.3f, label);
        return b;
    }
}
