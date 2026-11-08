package com.udyam.digital.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** One row of product_catalogue.csv. */
public final class Product {
    private final String id;
    private final String category;
    private final String name;
    private final String description;
    private final String unit;
    private final int priceMinInr;
    private final int priceMaxInr;
    private final int setupInr;
    private final int minContractMonths;
    private final Set<Integer> cityTiers;
    private final int minEmployees;
    private final int minSites;
    private final boolean requiresGstin;
    private final List<String> requiresOneOf;
    private final int installDays;
    private final Set<String> targetSegments;

    public Product(String id, String category, String name, String description, String unit,
                   int priceMinInr, int priceMaxInr, int setupInr, int minContractMonths,
                   Set<Integer> cityTiers, int minEmployees, int minSites, boolean requiresGstin,
                   List<String> requiresOneOf, int installDays, Set<String> targetSegments) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.priceMinInr = priceMinInr;
        this.priceMaxInr = priceMaxInr;
        this.setupInr = setupInr;
        this.minContractMonths = minContractMonths;
        this.cityTiers = Collections.unmodifiableSet(cityTiers);
        this.minEmployees = minEmployees;
        this.minSites = minSites;
        this.requiresGstin = requiresGstin;
        this.requiresOneOf = Collections.unmodifiableList(requiresOneOf);
        this.installDays = installDays;
        this.targetSegments = Collections.unmodifiableSet(targetSegments);
    }

    public String getId() { return id; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public int getPriceMinInr() { return priceMinInr; }
    public int getPriceMaxInr() { return priceMaxInr; }
    public int getSetupInr() { return setupInr; }
    public int getMinContractMonths() { return minContractMonths; }
    public Set<Integer> getCityTiers() { return cityTiers; }
    public int getMinEmployees() { return minEmployees; }
    public int getMinSites() { return minSites; }
    public boolean isRequiresGstin() { return requiresGstin; }
    /** Product IDs, at least one of which must be in the same bundle. Empty = no dependency. */
    public List<String> getRequiresOneOf() { return requiresOneOf; }
    public int getInstallDays() { return installDays; }
    public Set<String> getTargetSegments() { return targetSegments; }

    public boolean isAvailableInTier(int tier) { return cityTiers.contains(tier); }

    @Override public String toString() { return id + " " + name; }
}
