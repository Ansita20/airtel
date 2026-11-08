package com.udyam.digital.kyc;

/** What a document is, as decided by {@link DocumentClassifier}. */
public enum DocType {
    GST_CERTIFICATE,
    UDYAM_CERTIFICATE,
    SIGNATORY_ID,
    PAN_CARD,
    VOTER_ID,
    DRIVING_LICENCE,
    PASSPORT,
    AADHAAR,
    UTILITY_BILL,
    TELEPHONE_BILL,
    BANK_STATEMENT,
    RENT_AGREEMENT,
    UNKNOWN;

    /** The type's name as a translatable text ("doctype_pan_card"). */
    public com.udyam.digital.i18n.I18n.Msg text() { return com.udyam.digital.i18n.I18n.msg("doctype_" + name().toLowerCase(java.util.Locale.ROOT)); }

    public String label(com.udyam.digital.model.Language l) { return text().in(l); }
}
