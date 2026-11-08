package com.udyam.digital.kyc;

import com.udyam.digital.engine.MiniJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One specimen's on-device OCR output, as written by the debug "Run all specimens" screen
 * (files/kyc_ocr_dump/*.json). Replaying it in JUnit runs the exact same document processing
 * and checks without a phone. Pure Java.
 *
 * Line format: [text, left, top, right, bottom, angle, confidence, [words], [word heights]];
 * older dumps stop after angle. "lines" = pass 1 (scan), "lines_enhanced" = pass 2 (optional).
 */
public final class SpecimenDump {
    public final String file, applicant;
    public final Slot slot;
    public final ImageQuality quality;
    public final long hash;
    public final List<OcrLine> pass1, pass2;

    private SpecimenDump(String file, ImageQuality q, long hash, List<OcrLine> p1, List<OcrLine> p2) {
        this.file = file;
        this.applicant = applicantOf(file);
        this.slot = slotOf(file);
        this.quality = q;
        this.hash = hash;
        this.pass1 = p1;
        this.pass2 = p2;
    }

    /** "K07_gst_certificate.png" -> "K07". */
    public static String applicantOf(String file) { return file.substring(0, 3); }

    /** "K07_gst_certificate.png" -> GST_CERT. */
    public static Slot slotOf(String file) {
        String base = file.replaceAll("\\.(png|jpg|jpeg|json)$", "");
        return Slot.fromSpecimenSuffix(base.substring(4));
    }

    public KycDocument process(IdCorrector.Registry registry) {
        return DocumentProcessor.process(slot, pass1, pass2, quality, hash, registry);
    }

    @SuppressWarnings("unchecked")
    public static SpecimenDump parse(String json) {
        Map<String, Object> o = (Map<String, Object>) MiniJson.parse(json);
        String file = (String) o.get("file");
        int w = num(o.get("width")).intValue(), h = num(o.get("height")).intValue();
        double br = num(o.get("brightness")).doubleValue(), sh = num(o.get("sharpness")).doubleValue();
        ImageQuality q = o.containsKey("glare")
                ? new ImageQuality(w, h, br, sh, num(o.get("glare")).doubleValue(), num(o.get("ink")).intValue(),
                        Boolean.TRUE.equals(o.get("idCard")))
                : new ImageQuality(w, h, br, sh);
        long hash = num(o.get("hash")).longValue();
        return new SpecimenDump(file, q, hash, lines(o.get("lines")), lines(o.get("lines_enhanced")));
    }

    @SuppressWarnings("unchecked")
    static List<OcrLine> lines(Object arr) {
        List<OcrLine> out = new ArrayList<>();
        if (!(arr instanceof List)) return out;
        for (Object lo : (List<Object>) arr) {
            List<Object> l = (List<Object>) lo;
            float angle = l.size() > 5 ? num(l.get(5)).floatValue() : 0f;
            float conf = l.size() > 6 ? num(l.get(6)).floatValue() : 1f;
            String[] words = new String[0];
            int[] heights = new int[0];
            if (l.size() > 8) {
                List<Object> ws = (List<Object>) l.get(7), hs = (List<Object>) l.get(8);
                words = new String[ws.size()];
                heights = new int[hs.size()];
                for (int i = 0; i < ws.size(); i++) words[i] = String.valueOf(ws.get(i));
                for (int i = 0; i < hs.size(); i++) heights[i] = num(hs.get(i)).intValue();
            }
            out.add(new OcrLine((String) l.get(0), num(l.get(1)).intValue(), num(l.get(2)).intValue(),
                    num(l.get(3)).intValue(), num(l.get(4)).intValue(), angle, conf, words, heights));
        }
        return out;
    }

    private static Number num(Object o) { return o instanceof Number ? (Number) o : 0; }
}
