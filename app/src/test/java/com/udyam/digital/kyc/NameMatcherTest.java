package com.udyam.digital.kyc;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameMatcherTest {

    private static NameMatcher.Verdict v(String a, String b) { return NameMatcher.verdict(NameMatcher.score(a, b)); }

    @Test public void pvtLtdAndPrivateLimitedAreTheSame() {
        assertEquals(NameMatcher.Verdict.PASS, v("Star Tech Pvt Ltd", "Star Tech Private Limited"));
        assertEquals(NameMatcher.Verdict.PASS, v("Star Tech Pvt. Ltd.", "STAR TECH PRIVATE LIMITED"));
        assertEquals(NameMatcher.Verdict.PASS, v("Gupta & Co", "Gupta and Company"));
    }

    @Test public void msPrefixIsIgnored() {
        assertEquals(NameMatcher.Verdict.PASS, v("M/s Saffron Bakers", "Saffron Bakers"));
        assertEquals(NameMatcher.Verdict.PASS, v("M/S. Saffron Bakers", "saffron bakers"));
    }

    @Test public void proprietorFormatIsSplit() {
        String[] p = NameMatcher.splitProprietor("Sneha Rao (Prop. Saffron Bakers)");
        assertArrayEquals(new String[] {"Sneha Rao", "Saffron Bakers"}, p);
        assertArrayEquals(new String[] {"Neha Kaur", "Vindhya Tours & Travels"},
                NameMatcher.splitProprietor("Neha Kaur (Prop. Vindhya Tours & Travels)"));
        assertNull(NameMatcher.splitProprietor("Balaji Dental Care LLP"));
        assertEquals(NameMatcher.Verdict.PASS,
                NameMatcher.verdict(NameMatcher.best("Saffron Bakers", NameMatcher.businessNames("Sneha Rao (Prop. Saffron Bakers)", null))));
    }

    @Test public void smallTyposStillMatchOrNeedConfirmation() {
        assertNotEquals(NameMatcher.Verdict.FAIL, v("Safron Bakers", "Saffron Bakers"));
        assertNotEquals(NameMatcher.Verdict.FAIL, v("Saffron Bakery", "Saffron Bakers"));
        assertEquals(NameMatcher.Verdict.PASS, v("Neha Kaur", "Neha  Kaur"));
    }

    @Test public void differentBusinessesFail() {
        assertEquals(NameMatcher.Verdict.FAIL, v("Saffron Bakers", "Star Electronics"));
        assertEquals(NameMatcher.Verdict.FAIL, v("Neha Kaur", "Rahul Verma"));
    }

    @Test public void jaroWinklerKnownValues() {
        assertEquals(0.961, NameMatcher.jaroWinkler("MARTHA", "MARHTA"), 0.001);
        assertEquals(0.840, NameMatcher.jaroWinkler("DWAYNE", "DUANE"), 0.001);
    }
}
