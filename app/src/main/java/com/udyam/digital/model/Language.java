package com.udyam.digital.model;

/** UI languages, covering every preferred_language in smb_leads.csv. Order = order on the language screen. */
public enum Language {
    EN("en", "English", "English", "A"),
    HI("hi", "हिन्दी", "Hindi", "अ"),
    MR("mr", "मराठी", "Marathi", "म"),
    GU("gu", "ગુજરાતી", "Gujarati", "ગ"),
    PA("pa", "ਪੰਜਾਬੀ", "Punjabi", "ਪ"),
    BN("bn", "বাংলা", "Bengali", "বা"),
    TA("ta", "தமிழ்", "Tamil", "த"),
    TE("te", "తెలుగు", "Telugu", "తె"),
    KN("kn", "ಕನ್ನಡ", "Kannada", "ಕ"),
    ML("ml", "മലയാളം", "Malayalam", "മ");

    public final String tag;
    /** Name in its own script. */
    public final String nativeName;
    public final String englishName;
    /** One letter of the script, shown big on the language tile. */
    public final String glyph;

    Language(String tag, String nativeName, String englishName, String glyph) {
        this.tag = tag;
        this.nativeName = nativeName;
        this.englishName = englishName;
        this.glyph = glyph;
    }

    /** Language for a BCP-47 tag list such as "ta" or "hi-IN"; English when unknown or empty. */
    public static Language fromTags(String tags) {
        if (tags != null) {
            for (Language l : values()) {
                if (tags.startsWith(l.tag)) return l;
            }
        }
        return EN;
    }
}
