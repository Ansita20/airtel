package com.udyam.digital.data;

import com.udyam.digital.engine.ProductScorer;
import com.udyam.digital.engine.RecommendationLog;
import com.udyam.digital.engine.RulesEngine;
import com.udyam.digital.model.Product;

import java.util.Collections;
import java.util.List;

/** Holds the data loaded at startup. Initialised once by UdyamApp. Pure Java. */
public final class DataRepository {
    private static volatile DataRepository instance;

    private final List<Product> catalogue;
    private final RulesEngine engine;
    private final GstinLookup gstin;
    private final UdyamLookup udyam;
    private final RecommendationLog log;

    private DataRepository(List<Product> catalogue, GstinLookup gstin, UdyamLookup udyam,
                           ProductScorer scorer, RecommendationLog log) {
        this.catalogue = Collections.unmodifiableList(catalogue);
        this.engine = new RulesEngine(catalogue, scorer);
        this.gstin = gstin;
        this.udyam = udyam;
        this.log = log;
    }

    public static void init(List<Product> catalogue, GstinLookup gstin, UdyamLookup udyam,
                            ProductScorer scorer, RecommendationLog log) {
        instance = new DataRepository(catalogue, gstin, udyam, scorer, log);
    }

    public static DataRepository get() {
        if (instance == null) throw new IllegalStateException("DataRepository not initialised");
        return instance;
    }

    public List<Product> catalogue() { return catalogue; }
    public RulesEngine engine() { return engine; }
    public GstinLookup gstin() { return gstin; }
    public UdyamLookup udyam() { return udyam; }
    public ProductScorer scorer() { return engine.scorer(); }
    /** May be null in tests. */
    public RecommendationLog log() { return log; }
}
