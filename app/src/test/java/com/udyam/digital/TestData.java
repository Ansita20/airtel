package com.udyam.digital;

import com.udyam.digital.data.CatalogueLoader;
import com.udyam.digital.data.GstinLookup;
import com.udyam.digital.engine.ProductScorer;
import com.udyam.digital.model.Product;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loads the real CSVs from app/src/main/assets so tests run against the shipped data. */
public final class TestData {
    private TestData() {}

    private static List<Product> catalogue;
    private static GstinLookup gstin;
    private static ProductScorer scorer;

    public static File asset(String name) {
        String[] candidates = {"src/main/assets/" + name, "app/src/main/assets/" + name};
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) return f;
        }
        throw new IllegalStateException("Asset not found: " + name + " (cwd=" + new File(".").getAbsolutePath() + ")");
    }

    public static Reader reader(String name) throws IOException {
        return new InputStreamReader(new FileInputStream(asset(name)), StandardCharsets.UTF_8);
    }

    public static synchronized List<Product> catalogue() throws IOException {
        if (catalogue == null) {
            try (Reader r = reader("product_catalogue.csv")) { catalogue = CatalogueLoader.load(r); }
        }
        return catalogue;
    }

    /** The shipped learned scores (app/src/main/assets/product_scores.json). */
    public static synchronized ProductScorer scorer() throws IOException {
        if (scorer == null) {
            try (Reader r = reader("product_scores.json")) { scorer = ProductScorer.load(r); }
        }
        return scorer;
    }

    public static synchronized GstinLookup gstin() throws IOException {
        if (gstin == null) {
            try (Reader r = reader("mock_gstin_registry.csv")) { gstin = GstinLookup.load(r); }
        }
        return gstin;
    }
}
