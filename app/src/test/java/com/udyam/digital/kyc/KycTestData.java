package com.udyam.digital.kyc;

import com.udyam.digital.TestData;
import com.udyam.digital.data.UdyamLookup;

import java.io.IOException;
import java.io.Reader;

final class KycTestData {
    private KycTestData() {}

    private static GstinValidator gst;
    private static UdyamValidator udyam;

    static synchronized GstinValidator gst() throws IOException {
        if (gst == null) try (Reader r = TestData.reader("mock_gstin_registry.csv")) { gst = GstinValidator.load(r); }
        return gst;
    }

    static synchronized UdyamValidator udyam() throws IOException {
        if (udyam == null) try (Reader r = TestData.reader("mock_udyam_registry.csv")) { udyam = new UdyamValidator(UdyamLookup.load(r)); }
        return udyam;
    }

    static KycRuleEngine engine() throws IOException { return new KycRuleEngine(gst(), udyam()); }
}
