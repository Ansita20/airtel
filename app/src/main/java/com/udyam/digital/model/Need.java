package com.udyam.digital.model;

/** What the business owner says they need. Mapped to products in engine/NeedMap. */
public enum Need {
    // Core services. A business can want one or both. If neither is chosen the engine offers both.
    BROADBAND,           // fixed / wireless broadband at the shop -> primary link
    MOBILE_SIM,          // business mobile SIM lines

    FAST_INTERNET,       // wants 1 Gbps -> primary link choice
    DEDICATED_LINE,      // guaranteed uptime -> leased line as primary
    BACKUP_INTERNET,
    MULTI_SITE_NETWORK,
    REMOTE_ACCESS,
    CLOUD_PHONE,
    TOLL_FREE,
    BULK_SMS,
    WHATSAPP,
    EMAIL_OFFICE,
    DATA_BACKUP,
    WEBSITE,
    BILLING_POS,
    CYBER_SECURITY,
    GUEST_WIFI,
    CCTV,
    MACHINE_MONITORING,
    FLEET_TRACKING
}
