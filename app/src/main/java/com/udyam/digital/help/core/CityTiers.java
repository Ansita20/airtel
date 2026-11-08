package com.udyam.digital.help.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** City -> tier and state, taken from the 22 cities in smb_leads.csv. Unknown cities count as tier 2. */
public final class CityTiers {
    private CityTiers() {}

    private static final Map<String, Integer> TIER = new HashMap<>();
    private static final Map<String, String> STATE = new HashMap<>();

    private static void put(String city, int tier, String state) {
        String k = city.toLowerCase(Locale.ROOT);
        TIER.put(k, tier);
        STATE.put(k, state);
    }

    static {
        put("Ahmedabad", 1, "Gujarat");
        put("Bengaluru", 1, "Karnataka");
        put("Chennai", 1, "Tamil Nadu");
        put("Delhi", 1, "Delhi");
        put("Hyderabad", 1, "Telangana");
        put("Kolkata", 1, "West Bengal");
        put("Mumbai", 1, "Maharashtra");
        put("Pune", 1, "Maharashtra");
        put("Bhopal", 2, "Madhya Pradesh");
        put("Coimbatore", 2, "Tamil Nadu");
        put("Indore", 2, "Madhya Pradesh");
        put("Jaipur", 2, "Rajasthan");
        put("Kochi", 2, "Kerala");
        put("Lucknow", 2, "Uttar Pradesh");
        put("Ludhiana", 2, "Punjab");
        put("Mysuru", 2, "Karnataka");
        put("Nagpur", 2, "Maharashtra");
        put("Nashik", 2, "Maharashtra");
        put("Patna", 2, "Bihar");
        put("Surat", 2, "Gujarat");
        put("Vadodara", 2, "Gujarat");
        put("Visakhapatnam", 2, "Andhra Pradesh");
    }

    public static int tierOf(String city) {
        if (city == null) return 2;
        Integer t = TIER.get(city.trim().toLowerCase(Locale.ROOT));
        return t == null ? 2 : t;
    }

    public static String stateOf(String city) {
        if (city == null) return null;
        return STATE.get(city.trim().toLowerCase(Locale.ROOT));
    }
}
