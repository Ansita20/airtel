package com.udyam.digital.help.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads assets/product_catalogue.csv (25 products). */
public final class ProductCatalogue {
    private final Map<String, Product> byId = new LinkedHashMap<>();

    public static ProductCatalogue load(InputStream in) throws IOException {
        ProductCatalogue c = new ProductCatalogue();
        for (Map<String, String> r : Csv.read(in)) {
            Set<Integer> tiers = new LinkedHashSet<>();
            for (String t : r.get("available_city_tiers").split(";")) {
                if (!t.trim().isEmpty()) tiers.add(Integer.parseInt(t.trim()));
            }
            List<String> deps = new ArrayList<>();
            String dep = r.get("requires_one_of");
            if (dep != null && !dep.trim().isEmpty()) deps.addAll(Arrays.asList(dep.trim().split("\\|")));
            Set<String> segs = new LinkedHashSet<>(Arrays.asList(r.get("target_segments").split(";")));
            Product p = new Product(
                    r.get("product_id"), r.get("category"), r.get("product_name"), r.get("description"),
                    r.get("unit"), num(r.get("price_min_inr_per_month")), num(r.get("price_max_inr_per_month")),
                    num(r.get("one_time_setup_inr")), num(r.get("min_contract_months")), tiers,
                    num(r.get("min_employees")), num(r.get("min_sites")), "Y".equalsIgnoreCase(r.get("requires_gstin")),
                    deps, num(r.get("typical_install_days")), segs);
            c.byId.put(p.id, p);
        }
        return c;
    }

    private static int num(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        return (int) Math.round(Double.parseDouble(s.trim()));
    }

    public Product get(String id) {
        Product p = byId.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown product " + id);
        return p;
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public Collection<Product> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Products that cannot be sold without a valid, active GSTIN. */
    public List<Product> gstinOnly() {
        List<Product> out = new ArrayList<>();
        for (Product p : byId.values()) if (p.requiresGstin) out.add(p);
        return out;
    }
}
