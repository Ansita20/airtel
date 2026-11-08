package com.udyam.digital.help.core;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** One row of product_catalogue.csv. Prices are Rs per unit per month. */
public final class Product {
    public final String id;
    public final String category;
    public final String name;
    public final String description;
    public final String unit;
    public final int priceMin;
    public final int priceMax;
    public final int setupFee;
    public final int minContractMonths;
    public final Set<Integer> cityTiers;
    public final int minEmployees;
    public final int minSites;
    public final boolean requiresGstin;
    /** Product needs at least one of these in the same bundle (empty = no dependency). */
    public final List<String> requiresOneOf;
    public final int installDays;
    public final Set<String> segments;

    public Product(String id, String category, String name, String description, String unit,
                   int priceMin, int priceMax, int setupFee, int minContractMonths,
                   Set<Integer> cityTiers, int minEmployees, int minSites, boolean requiresGstin,
                   List<String> requiresOneOf, int installDays, Set<String> segments) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        this.setupFee = setupFee;
        this.minContractMonths = minContractMonths;
        this.cityTiers = Collections.unmodifiableSet(cityTiers);
        this.minEmployees = minEmployees;
        this.minSites = minSites;
        this.requiresGstin = requiresGstin;
        this.requiresOneOf = Collections.unmodifiableList(requiresOneOf);
        this.installDays = installDays;
        this.segments = Collections.unmodifiableSet(segments);
    }

    /** The price we quote: the catalogue's list minimum. */
    public int monthlyPrice() {
        return priceMin;
    }

    public boolean isConnectivityBase() {
        switch (id) {
            case "P01": case "P02": case "P03": case "P04": case "P05": case "P06":
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return id + " " + name;
    }
}
