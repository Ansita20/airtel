package com.udyam.digital.security;

import java.io.ByteArrayOutputStream;

/**
 * Removes every metadata block from a JPEG before it is stored: EXIF (GPS location, phone model,
 * date and time, thumbnail), XMP, ICC/maker notes (APP1-APP15) and comments (COM). Keeps APP0
 * (JFIF) and everything needed to show the picture. Pure Java, so it is unit-tested.
 *
 * (Android's Bitmap.compress writes no EXIF anyway; this guarantees it for every path.)
 */
public final class JpegMetadata {
    private JpegMetadata() {}

    private static final int SOI = 0xD8, EOI = 0xD9, SOS = 0xDA, APP0 = 0xE0, APP15 = 0xEF, COM = 0xFE;

    /** True if the bytes look like a JPEG. */
    public static boolean isJpeg(byte[] b) {
        return b != null && b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == SOI;
    }

    /** JPEG without metadata segments. Non-JPEG input is returned unchanged. */
    public static byte[] strip(byte[] in) {
        if (!isJpeg(in)) return in;
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(SOI);
        int i = 2;
        while (i + 3 < in.length) {
            if ((in[i] & 0xFF) != 0xFF) return in; // not a marker where one must be: leave the file alone
            int marker = in[i + 1] & 0xFF;
            if (marker == 0xFF) { i++; continue; } // fill byte
            if (marker == EOI) break;
            if (marker >= 0xD0 && marker <= 0xD7) { i += 2; continue; } // stray restart marker
            int len = ((in[i + 2] & 0xFF) << 8) | (in[i + 3] & 0xFF);
            if (len < 2 || i + 2 + len > in.length) return in;
            boolean metadata = (marker > APP0 && marker <= APP15) || marker == COM;
            if (marker == SOS) {
                // Scan data runs to the end: copy the rest unchanged.
                out.write(in, i, in.length - i);
                return out.toByteArray();
            }
            if (!metadata) out.write(in, i, 2 + len);
            i += 2 + len;
        }
        out.write(0xFF);
        out.write(EOI);
        return out.toByteArray();
    }

    /** True if any APP1 block (EXIF/XMP) is still present before the image data. */
    public static boolean hasExif(byte[] in) {
        if (!isJpeg(in)) return false;
        int i = 2;
        while (i + 3 < in.length) {
            if ((in[i] & 0xFF) != 0xFF) return false;
            int marker = in[i + 1] & 0xFF;
            if (marker == SOS || marker == EOI) return false;
            if (marker == 0xE1) return true;
            int len = ((in[i + 2] & 0xFF) << 8) | (in[i + 3] & 0xFF);
            if (len < 2) return false;
            i += 2 + len;
        }
        return false;
    }
}
