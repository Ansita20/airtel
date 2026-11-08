package com.udyam.digital.help;

import java.util.Locale;

/** Languages seen in smb_leads.csv. Only EN and HI have full offline KB text. */
public enum Lang {
    EN("en", "English", "en-IN", true),
    HI("hi", "Hindi", "hi-IN", true),
    MR("mr", "Marathi", "mr-IN", false),
    KN("kn", "Kannada", "kn-IN", false),
    GU("gu", "Gujarati", "gu-IN", false),
    TE("te", "Telugu", "te-IN", false),
    TA("ta", "Tamil", "ta-IN", false),
    BN("bn", "Bengali", "bn-IN", false),
    PA("pa", "Punjabi", "pa-IN", false),
    ML("ml", "Malayalam", "ml-IN", false);

    public final String code;
    public final String displayName;
    public final String speechTag;
    public final boolean offlineKb;

    Lang(String code, String displayName, String speechTag, boolean offlineKb) {
        this.code = code;
        this.displayName = displayName;
        this.speechTag = speechTag;
        this.offlineKb = offlineKb;
    }

    /** Offline fallback when there is no KB text and no LLM: Hindi for north/west, English otherwise. */
    public Lang offlineFallback() {
        if (offlineKb) return this;
        switch (this) {
            case MR: case GU: case PA: return HI;
            default: return EN;
        }
    }

    public static Lang fromCode(String code) {
        if (code == null) return null;
        String c = code.trim().toLowerCase(Locale.ROOT);
        for (Lang l : values()) {
            if (l.code.equals(c) || l.displayName.toLowerCase(Locale.ROOT).equals(c)) return l;
        }
        return null;
    }
}
