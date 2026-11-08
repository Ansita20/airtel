package com.udyam.digital;

import android.app.Application;
import android.util.Log;

import com.udyam.digital.data.CatalogueLoader;
import com.udyam.digital.data.DataRepository;
import com.udyam.digital.data.GstinLookup;
import com.udyam.digital.data.UdyamLookup;
import com.udyam.digital.engine.ProductScorer;
import com.udyam.digital.engine.RecommendationLog;

import java.io.File;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Loads the catalogue and both registries from assets once at startup. */
public class UdyamApp extends Application {
    private static final String TAG = "UdyamApp";

    @Override
    public void onCreate() {
        super.onCreate();
        // All text (also the pure-Java engines') comes from res/values-<lang>/strings*.xml.
        com.udyam.digital.i18n.I18n.install(new com.udyam.digital.i18n.ResSource(this));
        // No screenshots on sensitive screens; 2-minute background lock for documents.
        com.udyam.digital.security.SecureScreens.install(this);
        // Daily retention: documents 30 days after the verdict, events after 90 days.
        com.udyam.digital.security.RetentionWorker.schedule(this);
        long start = System.currentTimeMillis();
        try (Reader catalogue = open("product_catalogue.csv");
             Reader gstin = open("mock_gstin_registry.csv");
             Reader udyam = open("mock_udyam_registry.csv")) {
            DataRepository.init(
                    CatalogueLoader.load(catalogue),
                    GstinLookup.load(gstin),
                    UdyamLookup.load(udyam),
                    loadScores(),
                    openLog());
        } catch (IOException e) {
            throw new IllegalStateException("Could not load bundled data", e);
        }
        DataRepository d = DataRepository.get();
        Log.i(TAG, "Learned scores: " + (d.scorer().isEmpty() ? "none (rules + needs only)"
                : d.scorer().version() + ", " + d.scorer().ranker()));
        // Counts only; never log GSTINs or names.
        Log.i(TAG, "Loaded " + d.catalogue().size() + " products, " + d.gstin().size()
                + " GSTINs, " + d.udyam().size() + " Udyam records in "
                + (System.currentTimeMillis() - start) + " ms");
    }

    /** Missing or broken product_scores.json -> empty scorer; the app still works on rules + needs. */
    private ProductScorer loadScores() {
        try (Reader r = open("product_scores.json")) {
            return ProductScorer.loadOrEmpty(r);
        } catch (IOException e) {
            Log.w(TAG, "product_scores.json not found; ranking by tapped needs only");
            return ProductScorer.empty();
        }
    }

    /** App-private usage log; rows older than 90 days are deleted at every start. */
    private RecommendationLog openLog() {
        RecommendationLog log = new RecommendationLog(new File(getFilesDir(), "recommendation_log.csv"));
        int removed = log.prune(System.currentTimeMillis());
        if (removed > 0) Log.i(TAG, "Deleted " + removed + " log rows older than 90 days");
        return log;
    }

    private Reader open(String asset) throws IOException {
        return new InputStreamReader(getAssets().open(asset), StandardCharsets.UTF_8);
    }
}
