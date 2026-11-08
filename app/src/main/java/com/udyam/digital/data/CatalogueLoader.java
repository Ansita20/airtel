package com.udyam.digital.data;

import com.udyam.digital.model.Product;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses product_catalogue.csv into {@link Product}s. */
public final class CatalogueLoader {
    private CatalogueLoader() {}

    public static List<Product> load(Reader in) throws IOException {
        List<Product> out = new ArrayList<>();
        for (Map<String, String> r : CsvParser.readRecords(in)) {
            Set<Integer> tiers = new LinkedHashSet<>();
            for (String t : split(r.get("available_city_tiers"), ";")) tiers.add(Integer.parseInt(t));
            out.add(new Product(
                    r.get("product_id"),
                    r.get("category"),
                    r.get("product_name"),
                    r.get("description"),
                    r.get("unit"),
                    intOf(r.get("price_min_inr_per_month")),
                    intOf(r.get("price_max_inr_per_month")),
                    intOf(r.get("one_time_setup_inr")),
                    intOf(r.get("min_contract_months")),
                    tiers,
                    intOf(r.get("min_employees")),
                    intOf(r.get("min_sites")),
                    "Y".equalsIgnoreCase(r.get("requires_gstin")),
                    split(r.get("requires_one_of"), "\\|"),
                    intOf(r.get("typical_install_days")),
                    new LinkedHashSet<>(split(r.get("target_segments"), ";"))));
        }
        return out;
    }

    private static int intOf(String s) {
        return (s == null || s.isEmpty()) ? 0 : Integer.parseInt(s.trim());
    }

    private static List<String> split(String s, String regex) {
        List<String> out = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return out;
        for (String part : s.split(regex)) {
            if (!part.trim().isEmpty()) out.add(part.trim());
        }
        return out;
    }
}
