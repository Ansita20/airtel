package com.udyam.digital.data;

import com.udyam.digital.TestData;
import com.udyam.digital.engine.Money;

import org.junit.Test;

import java.io.Reader;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GstinLookupTest {

    @Test
    public void everyRegistryGstinPassesFormatAndChecksum() throws Exception {
        int n = 0;
        try (Reader r = TestData.reader("mock_gstin_registry.csv")) {
            for (Map<String, String> row : CsvParser.readRecords(r)) {
                String g = row.get("gstin");
                assertTrue("format " + g, GstinLookup.isValidFormat(g));
                assertTrue("checksum " + g, GstinLookup.hasValidChecksum(g));
                n++;
            }
        }
        assertEquals(3534, n);
    }

    @Test
    public void activeLookupReturnsCity() throws Exception {
        GstinLookup.Result r = TestData.gstin().lookup(" 03pjnxs4821g1zb ");
        assertEquals(GstinLookup.Status.ACTIVE, r.status);
        assertEquals("Ludhiana", r.city);
        assertEquals("Saffron Bakers", r.tradeName);
    }

    @Test
    public void wrongCheckDigitIsRejected() throws Exception {
        assertEquals(GstinLookup.Status.INVALID_CHECKSUM, TestData.gstin().lookup("03PJNXS4821G1ZC").status);
    }

    @Test
    public void badFormatIsRejected() throws Exception {
        assertEquals(GstinLookup.Status.INVALID_FORMAT, TestData.gstin().lookup("12345").status);
        assertEquals(GstinLookup.Status.INVALID_FORMAT, TestData.gstin().lookup("03PJNQS4821G1ZB").status); // Q not a holder type
    }

    @Test
    public void validButUnknownGstinIsNotFound() throws Exception {
        String first14 = "27ABCXE1234F1Z";
        String g = first14 + GstinLookup.checksumChar(first14);
        assertEquals(GstinLookup.Status.NOT_FOUND, TestData.gstin().lookup(g).status);
    }

    @Test
    public void registryHasSuspendedAndCancelled() throws Exception {
        GstinLookup l = TestData.gstin();
        boolean suspended = false, cancelled = false;
        try (Reader r = TestData.reader("mock_gstin_registry.csv")) {
            for (Map<String, String> row : CsvParser.readRecords(r)) {
                GstinLookup.Status s = l.lookup(row.get("gstin")).status;
                suspended |= s == GstinLookup.Status.SUSPENDED;
                cancelled |= s == GstinLookup.Status.CANCELLED;
            }
        }
        assertTrue(suspended);
        assertTrue(cancelled);
    }

    @Test
    public void cityTiers() {
        assertEquals(1, CityTiers.tierOf("Mumbai"));
        assertEquals(1, CityTiers.tierOf(" bengaluru "));
        assertEquals(2, CityTiers.tierOf("Indore"));
        assertEquals(2, CityTiers.tierOf("Shimla"));
        assertEquals(2, CityTiers.tierOf(null));
        assertEquals(22, CityTiers.all().size());
    }

    @Test
    public void maskingAndMoney() {
        assertEquals("03PJN******G1ZB", GstinLookup.mask("03PJNXS4821G1ZB"));
        assertFalse(GstinLookup.mask("03PJNXS4821G1ZB").contains("4821"));
        assertEquals("₹1,23,456", Money.inr(123456));
        assertEquals("₹999", Money.inr(999));
    }
}
