package com.udyam.digital.kyc;

import org.junit.Test;

import static org.junit.Assert.*;

public class UdyamValidatorTest {

    @Test public void validNumberWithDistrict00IsFound() throws Exception {
        String u = "UDYAM-KL-00-3078801";
        assertTrue(UdyamValidator.isValidFormat(u));
        assertEquals("Kerala", UdyamValidator.stateOf(u));
        assertEquals("Balaji Dental Care LLP", KycTestData.udyam().find(u).enterpriseName);
    }

    @Test public void badFormats() {
        assertFalse(UdyamValidator.isValidFormat("UDYAM-KL-0-3078801"));
        assertFalse(UdyamValidator.isValidFormat("UDYAM-KL-00-307880"));
        assertFalse(UdyamValidator.isValidFormat("UDYOG-KL-00-3078801"));
        assertFalse(UdyamValidator.isValidFormat("UDYAM-ZZ-00-3078801")); // unknown state code
    }

    @Test public void normalisesSpacesAndCase() {
        assertEquals("UDYAM-KL-00-3078801", UdyamValidator.normalize(" udyam-kl-00-3078801 "));
    }

    @Test public void karnatakaUsesKrInThisDataset() {
        assertEquals("Karnataka", UdyamValidator.stateOf("UDYAM-KR-00-1234567"));
    }

    @Test public void ocrFixesInNumbers() {
        assertEquals("UDYAM-KL-00-3078801", FieldExtractor.findUdyam("Udyam reg. number UDYAM - KL - O0 - 3078801"));
    }
}
