package com.udyam.digital.kyc;

/** One recognised line of text with its bounding box (pixels). Produced by ML Kit on device. */
public final class OcrLine {
    public final String text;
    public final int left, top, right, bottom;
    /** Text direction in degrees from ML Kit (0 = horizontal). Used to straighten tilted scans. */
    public final float angle;
    /** ML Kit's confidence for this line, 0-1. 1 when unknown (older replay files). */
    public final float confidence;
    /** Height in pixels of each word (ML Kit element) in the line; empty when unknown. Used by C20. */
    public final int[] wordHeights;
    /** The words matching {@link #wordHeights}; empty when unknown. */
    public final String[] words;

    public OcrLine(String text, int left, int top, int right, int bottom) {
        this(text, left, top, right, bottom, 0f);
    }

    public OcrLine(String text, int left, int top, int right, int bottom, float angle) {
        this(text, left, top, right, bottom, angle, 1f, new String[0], new int[0]);
    }

    public OcrLine(String text, int left, int top, int right, int bottom, float angle, float confidence,
                   String[] words, int[] wordHeights) {
        this.angle = angle;
        this.text = text == null ? "" : text.trim();
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.confidence = Float.isNaN(confidence) ? 1f : Math.max(0f, Math.min(1f, confidence));
        this.words = words == null ? new String[0] : words;
        this.wordHeights = wordHeights == null ? new int[0] : wordHeights;
    }

    /** Same line moved to a new box (used when straightening a tilted scan). */
    public OcrLine withBox(int l, int t, int r, int b) {
        return new OcrLine(text, l, t, r, b, 0f, confidence, words, wordHeights);
    }

    public int height() { return Math.max(1, bottom - top); }
    public int centerY() { return (top + bottom) / 2; }

    @Override public String toString() { return text + " [" + left + "," + top + "," + right + "," + bottom + "] c=" + confidence; }
}
