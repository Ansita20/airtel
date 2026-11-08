package com.udyam.digital.kyc;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** The four upload slots and which document types each accepts. */
public enum Slot {
    GST_CERT(EnumSet.of(DocType.GST_CERTIFICATE)),
    UDYAM_CERT(EnumSet.of(DocType.UDYAM_CERTIFICATE)),
    SIGNATORY_ID(EnumSet.of(DocType.SIGNATORY_ID, DocType.PAN_CARD,
            DocType.VOTER_ID, DocType.DRIVING_LICENCE, DocType.PASSPORT, DocType.AADHAAR)),
    ADDRESS_PROOF(EnumSet.of(DocType.UTILITY_BILL, DocType.TELEPHONE_BILL,
            DocType.BANK_STATEMENT, DocType.RENT_AGREEMENT));

    public final Set<DocType> accepts;

    Slot(Set<DocType> accepts) { this.accepts = accepts; }

    /** The slot's name as a translatable text ("slot_gst_cert"). */
    public com.udyam.digital.i18n.I18n.Msg text() { return com.udyam.digital.i18n.I18n.msg("slot_" + name().toLowerCase(java.util.Locale.ROOT)); }

    public String label(com.udyam.digital.model.Language l) { return text().in(l); }

    /** File-name suffix used by the specimen pack (K01_gst_certificate.png ...). */
    public String specimenSuffix() {
        switch (this) {
            case GST_CERT: return "gst_certificate";
            case UDYAM_CERT: return "udyam_certificate";
            case SIGNATORY_ID: return "signatory_id";
            default: return "address_proof";
        }
    }

    public static Slot fromSpecimenSuffix(String s) {
        for (Slot sl : values()) if (sl.specimenSuffix().equals(s)) return sl;
        throw new IllegalArgumentException("Unknown specimen document: " + s + " (known: "
                + Arrays.toString(values()) + ")");
    }
}
