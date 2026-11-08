package com.udyam.digital.model;

import java.util.EnumMap;
import java.util.Map;

/** A short piece of text in every {@link Language}. */
public final class Tr {
    private final Map<Language, String> text = new EnumMap<>(Language.class);

    /** Set for a resource-backed text: looked up in res/values-<lang>/ when shown. */
    private String key, fallback;

    private Tr() {}

    /** The string resource {@code key} in the asked language (English if that language lacks it). */
    public static Tr res(String key) { return res(key, null); }

    /** As {@link #res(String)}, but {@code fallback} if no such resource exists at all. */
    public static Tr res(String key, String fallback) {
        Tr t = new Tr();
        t.key = key;
        t.fallback = fallback;
        return t;
    }

    /** Values in {@link Language} order: en, hi, mr, gu, pa, bn, ta, te, kn, ml. */
    public static Tr of(String... values) {
        Language[] langs = Language.values();
        if (values.length != langs.length) {
            throw new IllegalArgumentException("Need " + langs.length + " translations, got "
                    + values.length + " for \"" + (values.length > 0 ? values[0] : "") + "\"");
        }
        Tr t = new Tr();
        for (int i = 0; i < langs.length; i++) t.text.put(langs[i], values[i]);
        return t;
    }

    /** Same text in every language (numbers, prices, brand names). */
    public static Tr same(String value) {
        Tr t = new Tr();
        for (Language l : Language.values()) t.text.put(l, value);
        return t;
    }

    public String get(Language l) {
        if (key != null) {
            if (fallback != null && !com.udyam.digital.i18n.I18n.has(key)) return fallback;
            return com.udyam.digital.i18n.I18n.get(l == null ? Language.EN : l, key);
        }
        String s = text.get(l);
        return s != null ? s : text.get(Language.EN);
    }
}
