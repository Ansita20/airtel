package com.udyam.digital.data;

import com.udyam.digital.model.Language;
import com.udyam.digital.model.Tr;

import java.util.HashMap;
import java.util.Map;

/** City names in each language's own script (res/values-<lang>/strings_questions.xml). */
public final class CityNames {
    private CityNames() {}

    private static final Map<String, Tr> NAMES = new HashMap<>();

    static {
        // Names in every script live in res/values-<lang>/strings_questions.xml ("city_mumbai").
        for (String c : CityTiers.all()) NAMES.put(c, Tr.res("city_" + c.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_"), c));
    }

    /** The city as a Tr, or the English name in every language if it is not in the table. */
    public static Tr of(String city) {
        Tr t = NAMES.get(city);
        return t != null ? t : Tr.same(city);
    }

    /** Name in the given language; unknown cities (e.g. from the GST registry) stay as written. */
    public static String name(String city, Language l) {
        if (city == null) return null;
        String canon = CityTiers.canonical(city);
        return canon == null ? city : of(canon).get(l);
    }
}
