package com.udyam.digital.kyc;

/**
 * Builds the "enhanced copy" for the second OCR pass: greyscale -> contrast stretch ->
 * adaptive threshold (Bradley-Roth, local mean over an integral image). Uneven light and
 * shadows across a phone photo are handled because each pixel is compared with its own
 * neighbourhood, not with one global level. Pure Java on greyscale pixels (0-255).
 */
public final class ImageEnhancer {
    private ImageEnhancer() {}

    /** A pixel darker than (100 - T)% of its local mean becomes ink. */
    static final int THRESHOLD_PERCENT = 15;

    /**
     * Stretches grey levels so the 0.5th percentile maps to 0 and the 99.5th to 255. Narrow
     * percentiles on purpose: text is often only 1-3% of a page, and clipping more would merge
     * ink in a shadow with the shadowed paper around it.
     */
    public static int[] contrastStretch(int[] gray) {
        int[] hist = new int[256];
        for (int v : gray) hist[clamp(v)]++;
        int lo = ImageQuality.percentile(hist, gray.length, 0.005);
        int hi = ImageQuality.percentile(hist, gray.length, 0.995);
        int[] out = new int[gray.length];
        if (hi - lo < 8) { // flat image: nothing to stretch
            System.arraycopy(gray, 0, out, 0, gray.length);
            return out;
        }
        for (int i = 0; i < gray.length; i++) out[i] = clamp((clamp(gray[i]) - lo) * 255 / (hi - lo));
        return out;
    }

    /** Black (0) ink on white (255) paper. Window is 1/16 of the longer side, at least 15 px. */
    public static int[] adaptiveThreshold(int[] gray, int w, int h) {
        long[] integral = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long row = 0;
            for (int x = 0; x < w; x++) {
                row += gray[y * w + x];
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row;
            }
        }
        int half = Math.max(7, Math.max(w, h) / 32);
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - half), y1 = Math.min(h - 1, y + half);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - half), x1 = Math.min(w - 1, x + half);
                long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
                long s = integral[(y1 + 1) * (w + 1) + x1 + 1] - integral[y0 * (w + 1) + x1 + 1]
                        - integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0];
                out[y * w + x] = (long) gray[y * w + x] * count * 100 < s * (100 - THRESHOLD_PERCENT) ? 0 : 255;
            }
        }
        return out;
    }

    /** The full enhancement: contrast stretch, then adaptive threshold. */
    public static int[] enhance(int[] gray, int w, int h) {
        return adaptiveThreshold(contrastStretch(gray), w, h);
    }

    private static int clamp(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
}
