package com.udyam.digital.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** City -> tier. Any city not listed is treated as Tier 2. */
public final class CityTiers {
    private CityTiers() {}

    public static final List<String> TIER_1 = Collections.unmodifiableList(Arrays.asList(
            "Ahmedabad", "Bengaluru", "Chennai", "Delhi", "Hyderabad", "Kolkata", "Mumbai", "Pune"));

    public static final List<String> TIER_2 = Collections.unmodifiableList(Arrays.asList(
            "Bhopal", "Coimbatore", "Indore", "Jaipur", "Kochi", "Lucknow", "Ludhiana", "Mysuru",
            "Nagpur", "Nashik", "Patna", "Surat", "Vadodara", "Visakhapatnam"));

    public static int tierOf(String city) {
        if (city == null) return 2;
        String c = city.trim().toLowerCase(Locale.ROOT);
        for (String t1 : TIER_1) {
            if (t1.toLowerCase(Locale.ROOT).equals(c)) return 1;
        }
        return 2;
    }

    /** Known cities, canonical spelling, or null if not in either list. */
    public static String canonical(String city) {
        if (city == null) return null;
        String c = city.trim();
        for (String k : all()) if (k.equalsIgnoreCase(c)) return k;
        return null;
    }

    /** Tier 1 first, then Tier 2, each alphabetical. */
    public static List<String> all() {
        List<String> out = new ArrayList<>(TIER_1);
        out.addAll(TIER_2);
        return out;
    }
}
