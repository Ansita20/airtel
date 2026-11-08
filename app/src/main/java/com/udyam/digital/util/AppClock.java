package com.udyam.digital.util;

import com.udyam.digital.kyc.KycClock;

import java.time.LocalDate;

/**
 * "Today" for orders and the tracker: 2026-07-15 plus a debug offset (the tracker's debug
 * panel advances days to demo delays). Never the phone's date. KYC checks keep using the
 * fixed {@link KycClock#TODAY}.
 */
public final class AppClock {
    private AppClock() {}

    public static final LocalDate BASE = KycClock.TODAY;
    private static volatile int offsetDays;

    public static LocalDate today() { return BASE.plusDays(offsetDays); }

    public static int offsetDays() { return offsetDays; }

    public static void setOffsetDays(int days) { offsetDays = Math.max(0, days); }
}
