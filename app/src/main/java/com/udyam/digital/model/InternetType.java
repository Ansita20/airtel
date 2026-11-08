package com.udyam.digital.model;

/** Values of current_internet in smb_leads.csv. */
public enum InternetType {
    NONE,
    MOBILE_HOTSPOT,
    HOME_BROADBAND,
    BUSINESS_BROADBAND,
    LEASED_LINE,
    MIXED;

    /**
     * Whether the site already has a fixed line, which we use as a proxy for
     * "fibre can reach this site". Mobile-hotspot-only or no internet means we
     * recommend 5G AirFiber (P04) instead of fibre.
     */
    public boolean hasFixedLine() {
        return this == HOME_BROADBAND || this == BUSINESS_BROADBAND
                || this == LEASED_LINE || this == MIXED;
    }
}
