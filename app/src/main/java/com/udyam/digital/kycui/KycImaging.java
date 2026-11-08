package com.udyam.digital.kycui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.udyam.digital.kyc.DocumentProcessor;
import com.udyam.digital.kyc.IdCorrector;
import com.udyam.digital.kyc.ImageEnhancer;
import com.udyam.digital.kyc.ImageQuality;
import com.udyam.digital.kyc.KycDocument;
import com.udyam.digital.kyc.OcrLine;
import com.udyam.digital.kyc.PerceptualHash;
import com.udyam.digital.kyc.Slot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Photo -> quality gate -> on-device OCR, twice (ML Kit Text Recognition v2, Latin + Devanagari,
 * bundled models, so images never leave the phone) -> classified document. Pass 1 reads the
 * scan, pass 2 an enhanced copy (contrast stretch + adaptive threshold). Background thread only.
 *
 * ML Kit reads Latin and Devanagari scripts only (not Tamil, Bengali, Telugu ...). KYC documents
 * are in English/Hindi, so this covers them.
 */
public final class KycImaging {

    private static TextRecognizer latin, devanagari;

    private KycImaging() {}

    private static synchronized TextRecognizer latin() {
        if (latin == null) latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        return latin;
    }

    private static synchronized TextRecognizer devanagari() {
        if (devanagari == null) devanagari = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        return devanagari;
    }

    /** Decodes with down-sampling so the long side is at most {@code maxSide}. */
    public static Bitmap decode(byte[] data, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    public static byte[] jpeg(Bitmap b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        return out.toByteArray();
    }

    /** Greyscale copy with the long side scaled to at most {@code side}. Returns {pixels, w, h}. */
    private static Object[] gray(Bitmap src, int side) {
        float scale = Math.min(1f, side / (float) Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, Math.round(src.getWidth() * scale)), h = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap s = scale < 1f ? Bitmap.createScaledBitmap(src, w, h, true) : src;
        int[] px = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            px[i] = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
        }
        if (s != src) s.recycle();
        return new Object[] {px, w, h};
    }

    /** Quality gate. Run BEFORE OCR; a failing photo is never read. */
    public static ImageQuality quality(Bitmap b, Slot slot) {
        Object[] g = gray(b, 1000);
        return ImageQuality.measure((int[]) g[0], (Integer) g[1], (Integer) g[2], b.getWidth(), b.getHeight(),
                slot == Slot.SIGNATORY_ID);
    }

    public static long hash(Bitmap b) {
        Object[] g = gray(b, 256);
        return PerceptualHash.dHash((int[]) g[0], (Integer) g[1], (Integer) g[2]);
    }

    /** The enhanced copy for OCR pass 2: greyscale, contrast stretch, adaptive threshold. */
    public static Bitmap enhanced(Bitmap b) {
        Object[] g = gray(b, 2000);
        int w = (Integer) g[1], h = (Integer) g[2];
        int[] bw = ImageEnhancer.enhance((int[]) g[0], w, h);
        int[] argb = new int[bw.length];
        for (int i = 0; i < bw.length; i++) argb[i] = 0xFF000000 | (bw[i] << 16) | (bw[i] << 8) | bw[i];
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Runs both recognisers; keeps Latin lines plus any line containing Devanagari script. */
    public static List<OcrLine> ocr(Bitmap b) throws ExecutionException, InterruptedException {
        InputImage img = InputImage.fromBitmap(b, 0);
        List<OcrLine> out = new ArrayList<>();
        addLines(Tasks.await(latin().process(img)), out, false);
        addLines(Tasks.await(devanagari().process(img)), out, true);
        return out;
    }

    private static void addLines(Text t, List<OcrLine> out, boolean devanagariOnly) {
        for (Text.TextBlock block : t.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String s = line.getText();
                if (devanagariOnly && !s.matches(".*[\\u0900-\\u097F].*")) continue;
                Rect r = line.getBoundingBox();
                if (r == null) continue;
                List<Text.Element> els = line.getElements();
                String[] words = new String[els.size()];
                int[] heights = new int[els.size()];
                for (int i = 0; i < els.size(); i++) {
                    words[i] = els.get(i).getText();
                    Rect er = els.get(i).getBoundingBox();
                    heights[i] = er == null ? 0 : er.height();
                }
                out.add(new OcrLine(s, r.left, r.top, r.right, r.bottom, line.getAngle(), line.getConfidence(), words, heights));
            }
        }
    }

    /** Result of reading one photo. */
    public static final class Read {
        public final KycDocument doc;
        /** OCR of the scan, and of the enhanced copy. Empty if the photo failed the quality gate. */
        public final List<OcrLine> pass1, pass2;

        Read(KycDocument doc, List<OcrLine> pass1, List<OcrLine> pass2) {
            this.doc = doc;
            this.pass1 = pass1;
            this.pass2 = pass2;
        }
    }

    /**
     * Quality gate first; both OCR passes only if the photo is usable (unless {@code ocrAnyway},
     * used by the specimen harness so C19 can be reported alongside the other checks).
     */
    public static Read read(Slot slot, Bitmap b, boolean ocrAnyway, IdCorrector.Registry registry)
            throws ExecutionException, InterruptedException {
        ImageQuality q = quality(b, slot);
        long h = hash(b);
        List<OcrLine> p1 = new ArrayList<>(), p2 = new ArrayList<>();
        if (q.ok() || ocrAnyway) {
            p1 = ocr(b);
            Bitmap e = enhanced(b);
            try {
                p2 = ocr(e);
            } finally {
                e.recycle();
            }
        }
        return new Read(DocumentProcessor.process(slot, p1, p2, q, h, registry), p1, p2);
    }
}
