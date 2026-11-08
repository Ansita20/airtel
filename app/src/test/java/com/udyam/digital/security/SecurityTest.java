package com.udyam.digital.security;

import com.udyam.digital.kyc.Mask;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** EXIF removal, retention at day 29/30 on real files, masking, outbound redaction, the 2-minute lock, access log. */
public class SecurityTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // ------------------------------------------------------------------ EXIF
    /** sample.jpg (made by macOS sips: has real EXIF + Photoshop blocks) plus GPS/phone-model EXIF and a comment. */
    private static byte[] jpegWithExif() throws Exception {
        byte[] clean = Files.readAllBytes(resource("security/sample.jpg").toPath());
        byte[] exifBody = ("Exif\0\0GPSLatitude=19.0760N;GPSLongitude=72.8777E;Model=Pixel 8;DateTime=2026:07:15 10:42:00")
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] comment = "taken at home".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(clean, 0, 2); // SOI
        b.write(0xFF); b.write(0xE1); b.write((exifBody.length + 2) >> 8); b.write((exifBody.length + 2) & 0xFF); b.write(exifBody);
        b.write(0xFF); b.write(0xFE); b.write((comment.length + 2) >> 8); b.write((comment.length + 2) & 0xFF); b.write(comment);
        b.write(clean, 2, clean.length - 2);
        return b.toByteArray();
    }

    static File resource(String path) {
        for (String base : new String[] {"src/test/resources/", "app/src/test/resources/"}) {
            File f = new File(base + path);
            if (f.exists()) return f;
        }
        throw new IllegalStateException("Missing " + path);
    }

    /** Markers of the header segments, in order, up to and including SOS. */
    private static List<Integer> markers(byte[] b) {
        List<Integer> m = new ArrayList<>();
        int i = 2;
        while (i + 3 < b.length) {
            int mk = b[i + 1] & 0xFF;
            m.add(mk);
            if (mk == 0xDA) break;
            i += 2 + (((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF));
        }
        return m;
    }

    private static byte[] fromSos(byte[] b) {
        int i = 2;
        while (i + 3 < b.length) {
            if ((b[i + 1] & 0xFF) == 0xDA) return java.util.Arrays.copyOfRange(b, i, b.length);
            i += 2 + (((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF));
        }
        throw new AssertionError("no SOS");
    }

    @Test public void exifIsRemovedAndTheImageIsUnchanged() throws Exception {
        byte[] in = jpegWithExif();
        assertTrue(JpegMetadata.hasExif(in));
        byte[] out = JpegMetadata.strip(in);

        assertFalse(JpegMetadata.hasExif(out));
        String raw = new String(out, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("GPSLatitude"));
        assertFalse(raw.contains("Pixel 8"));
        assertFalse(raw.contains("taken at home"));
        assertFalse("sips' own EXIF block is gone too", raw.contains("Exif"));
        for (int m : markers(out)) assertFalse("metadata segment 0x" + Integer.toHexString(m), (m > 0xE0 && m <= 0xEF) || m == 0xFE);
        List<Integer> kept = new ArrayList<>();
        for (int m : markers(in)) if (!((m > 0xE0 && m <= 0xEF) || m == 0xFE)) kept.add(m);
        assertEquals("every picture segment kept, in order", kept, markers(out));
        assertArrayEquals("compressed picture data identical", fromSos(in), fromSos(out));
        assertEquals(0xD8, out[1] & 0xFF);
    }

    @Test public void nonJpegIsLeftAlone() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        assertSame(png, JpegMetadata.strip(png));
    }

    // ------------------------------------------------------------------ retention
    @Test public void documentRetentionBoundaries() {
        LocalDate decided = LocalDate.of(2026, 7, 15);
        assertFalse("day 29", RetentionPolicy.deleteDocuments(decided, null, decided.plusDays(29)));
        assertTrue("day 30", RetentionPolicy.deleteDocuments(decided, null, decided.plusDays(30)));
        assertFalse("never submitted, day 89", RetentionPolicy.deleteDocuments(null, decided, decided.plusDays(89)));
        assertTrue("never submitted, day 90", RetentionPolicy.deleteDocuments(null, decided, decided.plusDays(90)));
        assertEquals(LocalDate.of(2029, 7, 15), RetentionPolicy.keepResultUntil(decided, 36));
        assertEquals(LocalDate.of(2027, 7, 15), RetentionPolicy.keepResultUntil(decided, 0));
        assertFalse(RetentionPolicy.deleteResult(LocalDate.of(2027, 7, 15), LocalDate.of(2027, 7, 15)));
        assertTrue(RetentionPolicy.deleteResult(LocalDate.of(2027, 7, 15), LocalDate.of(2027, 7, 16)));
    }

    private File app(File root, String id) throws Exception {
        File d = new File(root, id);
        d.mkdirs();
        Files.write(new File(d, "application.enc").toPath(), new byte[] {1, 2, 3});
        Files.write(new File(d, "gst_cert.enc").toPath(), new byte[] {4, 5, 6});
        return d;
    }

    @Test public void retentionDeletesTheRightFilesAtDay30NotDay29() throws Exception {
        File root = tmp.newFolder("kyc");
        LocalDate decided = LocalDate.of(2026, 7, 15);
        File approved = app(root, "AAAA1111");
        File recent = app(root, "BBBB2222");
        File draft = app(root, "CCCC3333");
        Map<String, DocumentShredder.Dates> dates = new LinkedHashMap<>();
        dates.put("AAAA1111", new DocumentShredder.Dates(decided, decided));
        dates.put("BBBB2222", new DocumentShredder.Dates(decided.plusDays(10), decided.plusDays(10)));
        dates.put("CCCC3333", new DocumentShredder.Dates(null, decided));
        List<String> keysDestroyed = new ArrayList<>();

        List<String> day29 = DocumentShredder.runRetention(root, dates, decided.plusDays(29), keysDestroyed::add);
        assertTrue(day29.isEmpty());
        assertTrue(new File(approved, "gst_cert.enc").exists());
        assertTrue(keysDestroyed.isEmpty());

        List<String> day30 = DocumentShredder.runRetention(root, dates, decided.plusDays(30), keysDestroyed::add);
        assertEquals(List.of("AAAA1111"), day30);
        assertFalse("documents gone", approved.exists());
        assertEquals("key destroyed before the files", List.of("AAAA1111"), keysDestroyed);
        assertTrue("decided 10 days later: kept", new File(recent, "gst_cert.enc").exists());
        assertTrue("draft only 30 days old: kept", new File(draft, "application.enc").exists());
    }

    // ------------------------------------------------------------------ masking
    @Test public void maskingFunctions() {
        assertEquals("27******34X1Z5", Mask.gstin("27ABCXX1234X1Z5"));
        assertEquals("ABCXX****F", Mask.pan("ABCXX1234F"));
        assertEquals("XXXX XXXX 9012", Mask.aadhaar("2345 6789 9012"));
        assertEquals("UDYAM-MH-00-***4567", Mask.udyam("UDYAM-MH-00-1234567"));
        assertEquals("", Mask.gstin(null));
        assertEquals("***", Mask.pan("SHORT"));
    }

    @Test public void outboundGuardMasksEveryIdButKeepsPrices() {
        String body = "{\"content\":\"GSTIN 27ABCXX1234X1Z5, PAN ABCXX1234F, UDYAM-MH-00-1234567, "
                + "Aadhaar 2345 6789 9012, call +91 9876543210. Plan ₹3,100 a month, 12 months, 100 Mbps.\"}";
        String out = OutboundGuard.redact(body);
        assertFalse(out.contains("27ABCXX1234X1Z5"));
        assertFalse(out.contains("ABCXX1234F"));
        assertFalse(out.contains("1234567"));
        assertFalse(out.contains("2345 6789"));
        assertFalse(out.contains("9876543210"));
        assertTrue(out.contains("27******34X1Z5"));
        assertTrue(out.contains("₹3,100") && out.contains("12 months") && out.contains("100 Mbps"));
        assertTrue(OutboundGuard.isClean(out));
        assertFalse(OutboundGuard.isClean(body));
    }

    @Test public void llmRequestNeverCarriesFullIds() throws Exception {
        java.lang.reflect.Method m = Class.forName("com.udyam.digital.help.LlmClient").getDeclaredMethod("requestBody",
                String.class, String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        String body = (String) m.invoke(null, "system", "kb", "{\"gstin\":\"27ABCXX1234X1Z5\"}", "my PAN is ABCXX1234F", "draft");
        assertTrue(OutboundGuard.isClean(body));
        assertFalse(body.contains("ABCXX1234F"));
    }

    // ------------------------------------------------------------------ 2-minute lock
    @Test public void documentsLockAfterTwoMinutesInTheBackground() {
        AccessSession s = new AccessSession();
        assertFalse(s.isUnlocked(0));
        s.unlock();
        assertTrue(s.isUnlocked(1000));
        s.onBackground(10_000);
        s.onForeground(10_000 + AccessSession.LOCK_AFTER_MS - 1);
        assertTrue("1:59 away: still unlocked", s.isUnlocked(200_000));
        s.onBackground(300_000);
        s.onForeground(300_000 + AccessSession.LOCK_AFTER_MS);
        assertFalse("2:00 away: ask again", s.isUnlocked(500_000));
    }

    // ------------------------------------------------------------------ access log
    @Test public void accessLogCannotHoldContents() {
        AccessLog.Entry e = new AccessLog.Entry("2026-07-15T10:00:00Z", AccessLog.Actor.OWNER, AccessLog.Action.VIEWED,
                "ab12cd34", "SIGNATORY_ID", "Neha Kaur 27ABCXX1234X1Z5 C13 FAIL");
        assertEquals("AB12CD34", e.appId);
        assertEquals("? ? ? C13 FAIL", e.detail);
        AccessLog log = new AccessLog();
        log.add(e);
        AccessLog back = AccessLog.parse(log.serialize());
        assertEquals(1, back.entries().size());
        assertEquals(AccessLog.Action.VIEWED, back.entries().get(0).action);
        assertFalse(log.serialize().contains("Neha"));
        for (int i = 0; i < AccessLog.MAX_ENTRIES + 5; i++) log.add(e);
        assertEquals(AccessLog.MAX_ENTRIES, log.entries().size());
    }
}
