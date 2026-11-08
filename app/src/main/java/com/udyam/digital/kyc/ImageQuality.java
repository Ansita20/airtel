package com.udyam.digital.kyc;

/**
 * Photo quality gate, run BEFORE any OCR: size, brightness range, blur and glare. Pure Java so
 * it is unit-testable; Android passes a greyscale copy of the bitmap (0-255 per pixel).
 * A photo that fails never reaches the checks; the owner is told exactly what to change.
 */
public final class ImageQuality {

    /** Short side of a page photo, in pixels. Below this, small print (GSTIN, dates) is unreliable. */
    public static final int MIN_SHORT_SIDE = 1000;
    /**
     * ID cards (85.6 x 54 mm) are a quarter the width of an A4 page, so the same print
     * resolution needs far fewer pixels: for card-shaped photos the LONG side must reach 1000.
     * (The specimen IDs are 1012 x 638 and read perfectly.)
     */
    public static final int MIN_CARD_LONG_SIDE = 1000;
    /** Long/short ratio of an ID-1 card is 1.586; A4 is 1.414. */
    static final double CARD_MIN_RATIO = 1.45, CARD_MAX_RATIO = 1.75;
    /** Mean grey level below this = too dark. */
    public static final double MIN_BRIGHTNESS = 60;
    /** The darkest 0.5% of pixels lighter than this = washed out (no dark ink left). */
    public static final int MAX_INK_LEVEL = 160;
    /**
     * Calibrated on the specimen pack (ML Kit pipeline, long side scaled to 1000 px): sharp
     * scans measure 4,000-16,000, the blurred certificate 26. 500 leaves a wide margin.
     */
    public static final double MIN_SHARPNESS = 500;
    /** Share of blown-out pixels (>= 250) on paper that is not itself pure white. */
    public static final double MAX_GLARE = 0.03;
    /** Paper (median grey) brighter than this is already clean white (a scan); glare can't be told apart. */
    static final int CLEAN_PAPER_LEVEL = 235;

    public enum Problem { NONE, TOO_SMALL, TOO_DARK, TOO_BRIGHT, BLURRY, GLARE }

    public final int width, height;
    public final double brightness, sharpness;
    /** Share (0-1) of blown-out pixels; 0 when not measured. */
    public final double glare;
    /** Grey level of the darkest 0.5% of pixels; 0 when not measured. */
    public final int inkLevel;
    /** Judged as an ID card (card-shaped photo in the signatory-ID slot): size rule uses the long side. */
    public final boolean idCard;
    public final Problem problem;

    /** For values stored before glare and ink level were measured. Card-shaped = ID card. */
    public ImageQuality(int width, int height, double brightness, double sharpness) {
        this(width, height, brightness, sharpness, 0, 0, cardShaped(width, height));
    }

    public ImageQuality(int width, int height, double brightness, double sharpness, double glare, int inkLevel, boolean idCard) {
        this.width = width;
        this.height = height;
        this.brightness = brightness;
        this.sharpness = sharpness;
        this.glare = glare;
        this.inkLevel = inkLevel;
        this.idCard = idCard;
        boolean bigEnough = idCard ? Math.max(width, height) >= MIN_CARD_LONG_SIDE : Math.min(width, height) >= MIN_SHORT_SIDE;
        if (!bigEnough) problem = Problem.TOO_SMALL;
        else if (brightness < MIN_BRIGHTNESS) problem = Problem.TOO_DARK;
        // Blur before "faded": a blurred photo also has washed-out ink, but the fix is to hold still.
        else if (sharpness < MIN_SHARPNESS) problem = Problem.BLURRY;
        else if (glare > MAX_GLARE) problem = Problem.GLARE;
        else if (inkLevel > MAX_INK_LEVEL) problem = Problem.TOO_BRIGHT;
        else problem = Problem.NONE;
    }

    public boolean ok() { return problem == Problem.NONE; }

    /** Plain reason for the owner, English. The app shows the translated string for {@link #problem}. */
    public String reasonEn() {
        switch (problem) {
            case TOO_SMALL: return "Too small - move closer so the document fills the frame";
            case TOO_DARK: return "Too dark - move to a brighter spot";
            case TOO_BRIGHT: return "Too bright or faded - move out of direct light";
            case GLARE: return "Glare on the document - tilt it slightly or turn off the flash";
            case BLURRY: return "Blurry - hold the phone still and tap to focus";
            default: return "Clear";
        }
    }

    /**
     * @param gray        greyscale pixels of a (possibly downscaled) copy, row-major
     * @param w,h         size of {@code gray}
     * @param origW,origH size of the original photo
     */
    public static boolean cardShaped(int w, int h) {
        if (w <= 0 || h <= 0) return false;
        double r = (double) Math.max(w, h) / Math.min(w, h);
        return r >= CARD_MIN_RATIO && r <= CARD_MAX_RATIO;
    }

    /** As {@link #measure(int[], int, int, int, int, boolean)} for a page document. */
    public static ImageQuality measure(int[] gray, int w, int h, int origW, int origH) {
        return measure(gray, w, h, origW, origH, false);
    }

    /** @param idSlot the photo is for the signatory-ID slot (card-shaped photos then use the card size rule) */
    public static ImageQuality measure(int[] gray, int w, int h, int origW, int origH, boolean idSlot) {
        int[] hist = new int[256];
        long sum = 0;
        for (int v : gray) {
            int c = Math.max(0, Math.min(255, v));
            hist[c]++;
            sum += c;
        }
        int n = gray.length;
        double mean = n == 0 ? 0 : (double) sum / n;
        int ink = percentile(hist, n, 0.005);
        int paper = percentile(hist, n, 0.5);
        long blown = 0;
        for (int i = 250; i < 256; i++) blown += hist[i];
        double glare = n == 0 || paper > CLEAN_PAPER_LEVEL ? 0 : (double) blown / n;

        // Laplacian (4-neighbour) variance, only over pixels near ink, so large white margins
        // don't make a sharp document look blurry.
        double s = 0, s2 = 0;
        long cnt = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int c = gray[y * w + x];
                int lap = gray[(y - 1) * w + x] + gray[(y + 1) * w + x] + gray[y * w + x - 1] + gray[y * w + x + 1] - 4 * c;
                if (c > 235 && Math.abs(lap) < 4) continue; // flat paper
                s += lap;
                s2 += (double) lap * lap;
                cnt++;
            }
        }
        double var = cnt == 0 ? 0 : s2 / cnt - (s / cnt) * (s / cnt);
        return new ImageQuality(origW, origH, mean, var, glare, ink, idSlot && cardShaped(origW, origH));
    }

    /** Grey level below which {@code q} of the pixels fall. */
    static int percentile(int[] hist, int n, double q) {
        long target = Math.max(1, Math.round(n * q));
        long acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= target) return i;
        }
        return 255;
    }
}
