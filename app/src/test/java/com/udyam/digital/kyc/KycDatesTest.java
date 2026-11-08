package com.udyam.digital.kyc;

import com.udyam.digital.kyc.CheckResult.Status;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

public class KycDatesTest {

    private static final LocalDate TODAY = KycClock.TODAY;

    @Test public void todayIsFixed() { assertEquals(LocalDate.of(2026, 7, 15), TODAY); }

    @Test public void parsesAllDocumentFormats() {
        assertEquals(LocalDate.of(2026, 6, 26), KycDates.parse("2026-06-26"));
        assertEquals(LocalDate.of(2026, 6, 26), KycDates.parse("26/06/2026"));
        assertEquals(LocalDate.of(2026, 6, 26), KycDates.parse("26-06-2026"));
        assertEquals(LocalDate.of(2026, 6, 26), KycDates.parse("26 Jun 2026"));
        assertEquals(LocalDate.of(2026, 6, 26), KycDates.parse("Bill date: 26 June 2026"));
        assertEquals(LocalDate.of(2026, 6, 1), KycDates.parse("Jun 2026"));
        assertNull(KycDates.parse("no date here"));
    }

    @Test public void addressProofWindow() {
        assertTrue("today", KycDates.isRecentAddressProof(TODAY, TODAY));
        assertTrue("exactly 90 days", KycDates.isRecentAddressProof(TODAY.minusDays(90), TODAY));
        assertFalse("91 days", KycDates.isRecentAddressProof(TODAY.minusDays(91), TODAY));
        assertFalse("future", KycDates.isRecentAddressProof(TODAY.plusDays(1), TODAY));
    }

    @Test public void idExpiry() {
        assertTrue(KycDates.notExpired(TODAY, TODAY));
        assertFalse(KycDates.notExpired(TODAY.minusDays(1), TODAY));
    }

    @Test public void adultOnTheEighteenthBirthday() {
        assertTrue(KycDates.isAdult(TODAY.minusYears(18), TODAY));
        assertFalse(KycDates.isAdult(TODAY.minusYears(18).plusDays(1), TODAY));
    }

    // The same rules through the full engine (C13).
    private static Status c13(LocalDate billDate) throws Exception {
        KycApplication app = new KycApplication("t");
        app.noGstin = true;
        KycDocument bill = new KycDocument(Slot.ADDRESS_PROOF);
        bill.type = DocType.UTILITY_BILL;
        bill.fields.put(FieldExtractor.BILL_DATE, billDate.toString());
        bill.fields.put(FieldExtractor.ADDRESS, "Shop 1, Main Road, Kochi, Kerala - 682001");
        app.documents.put(Slot.ADDRESS_PROOF, bill);
        List<CheckResult> r = KycTestData.engine().run(app, TODAY);
        for (CheckResult c : r) if (c.code.equals("C13")) return c.status;
        throw new AssertionError("C13 not run");
    }

    @Test public void engineAddressProofDates() throws Exception {
        assertEquals(Status.PASS, c13(TODAY.minusDays(90)));
        assertEquals(Status.FAIL, c13(TODAY.minusDays(91)));
        assertEquals(Status.FAIL, c13(TODAY.plusDays(1)));
        assertEquals(Status.PASS, c13(TODAY));
    }
}
