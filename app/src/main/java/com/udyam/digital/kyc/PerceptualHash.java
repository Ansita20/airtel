package com.udyam.digital.kyc;

/** 64-bit difference hash (dHash) to spot the same picture uploaded in two slots. */
public final class PerceptualHash {
    private PerceptualHash() {}

    public static final int SAME_IMAGE_MAX_DISTANCE = 6;

    /** dHash of a greyscale image: shrink to 9x8 by area averaging, compare neighbours. */
    public static long dHash(int[] gray, int w, int h) {
        double[] small = new double[9 * 8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 9; x++) {
                int x0 = x * w / 9, x1 = Math.max(x0 + 1, (x + 1) * w / 9);
                int y0 = y * h / 8, y1 = Math.max(y0 + 1, (y + 1) * h / 8);
                long sum = 0;
                int n = 0;
                for (int yy = y0; yy < y1 && yy < h; yy++) {
                    for (int xx = x0; xx < x1 && xx < w; xx++) {
                        sum += gray[yy * w + xx];
                        n++;
                    }
                }
                small[y * 9 + x] = n == 0 ? 0 : (double) sum / n;
            }
        }
        long hash = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash <<= 1;
                if (small[y * 9 + x] > small[y * 9 + x + 1]) hash |= 1;
            }
        }
        return hash;
    }

    public static int distance(long a, long b) { return Long.bitCount(a ^ b); }
}
