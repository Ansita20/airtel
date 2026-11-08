package com.udyam.digital.kyc;

import java.time.LocalDate;

/** The one "today" used by every KYC date check. Never the phone's real date. */
public final class KycClock {
    private KycClock() {}

    public static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
}
