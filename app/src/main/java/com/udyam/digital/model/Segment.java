package com.udyam.digital.model;

/** The four segments used in the catalogue's target_segments column. */
public enum Segment {
    MICRO("micro", "Micro retail & services"),
    PROF("prof", "Professional offices"),
    MULTI("multi", "Small multi-site businesses"),
    MFG("mfg", "Small manufacturers");

    public final String code;
    /** Segment name as written in smb_leads.csv and product_scores.json. */
    public final String leadName;

    Segment(String code, String leadName) { this.code = code; this.leadName = leadName; }
}
