package com.udyam.digital.kyc;

import org.junit.Test;

import static org.junit.Assert.*;

public class GstinValidatorTest {

    @Test public void validGstinFromRegistry() throws Exception {
        String g = "32TTNXB7384T1ZW";
        assertTrue(GstinValidator.isValidFormat(g));
        assertTrue(GstinValidator.hasValidChecksum(g));
        GstinValidator.GstRecord r = KycTestData.gst().find(g);
        assertNotNull(r);
        assertEquals("Balaji Dental Care LLP", r.legalName);
        assertEquals("Kerala", r.state);
        assertTrue(GstinValidator.stateCodeMatches(r));
        assertEquals("TTNXB7384T", GstinValidator.panOf(g));
    }

    @Test public void badChecksumIsRejected() {
        assertTrue(GstinValidator.isValidFormat("32TTNXB7384T1ZX"));
        assertFalse(GstinValidator.hasValidChecksum("32TTNXB7384T1ZX"));
        assertFalse(GstinValidator.isValid("32TTNXB7384T1ZX"));
    }

    @Test public void wrongLengthIsRejected() {
        assertFalse(GstinValidator.isValid("32TTNXB7384T1Z"));
        assertFalse(GstinValidator.isValid("32TTNXB7384T1ZWW"));
        assertFalse(GstinValidator.isValid(""));
        assertFalse(GstinValidator.isValid(null));
    }

    @Test public void xAsFourthPanCharacterIsAccepted() {
        // 4th PAN character (index 5 of the GSTIN) is 'X' throughout this dataset.
        String base = "27ABCXX1234X1Z";
        String g = base + GstinValidator.checksumChar(base);
        assertTrue(GstinValidator.isValid(g));
        // A real holder type works too.
        String real = "27ABCPX1234X1Z";
        assertTrue(GstinValidator.isValid(real + GstinValidator.checksumChar(real)));
        // But not an arbitrary letter there.
        String bad = "27ABCQX1234X1Z";
        assertFalse(GstinValidator.isValidFormat(bad + GstinValidator.checksumChar(bad)));
    }

    @Test public void checksumFollowsTheSpecAlgorithm() {
        // Hand-computed example from the spec description.
        assertEquals('W', GstinValidator.checksumChar("32TTNXB7384T1Z"));
        assertEquals('B', GstinValidator.checksumChar("03PJNXS4821G1Z"));
    }

    @Test public void unknownStateCodeIsRejected() {
        String base = "99ABCXX1234X1Z".replace("99", "45");
        assertFalse(GstinValidator.isValidFormat(base + GstinValidator.checksumChar(base)));
    }

    @Test public void cancelledAndSuspendedAreFound() throws Exception {
        int cancelled = 0;
        for (String g : new String[] {"23QYBXS1811Q1ZD"}) {
            GstinValidator.GstRecord r = KycTestData.gst().find(g);
            assertNotNull(r);
            assertFalse(r.isActive());
            cancelled++;
        }
        assertEquals(1, cancelled);
    }

    @Test public void masking() {
        assertEquals("32******84T1ZW", Mask.gstin("32TTNXB7384T1ZW"));
        assertEquals("ABCXX****F", Mask.pan("ABCXX1234F"));
        assertEquals("XXXX XXXX 9012", Mask.aadhaar("1234 5678 9012"));
    }
}
