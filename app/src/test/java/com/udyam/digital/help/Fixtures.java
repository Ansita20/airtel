package com.udyam.digital.help;

import com.udyam.digital.help.core.BusinessProfile;
import com.udyam.digital.help.core.BusinessProfile.Need;
import com.udyam.digital.help.core.GstinLookup;
import com.udyam.digital.help.core.ProductCatalogue;
import com.udyam.digital.help.core.RulesEngine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

/** Loads the real assets (KB, catalogue, GSTIN registry) and builds persona profiles. */
final class Fixtures {
    private Fixtures() {}

    private static KnowledgeBase kb;
    private static ProductCatalogue catalogue;
    private static GstinLookup gstin;

    static File assets() {
        String p = System.getProperty("udyam.assets");
        if (p != null) return new File(p);
        for (String c : new String[]{"src/main/assets", "app/src/main/assets"}) {
            File f = new File(c);
            if (f.isDirectory()) return f;
        }
        throw new IllegalStateException("assets folder not found; set -Dudyam.assets");
    }

    static InputStream asset(String name) throws IOException {
        return new FileInputStream(new File(assets(), name));
    }

    static synchronized KnowledgeBase kb() {
        if (kb == null) {
            try (InputStream in = asset("help_kb.json")) {
                kb = KnowledgeBase.load(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return kb;
    }

    static synchronized ProductCatalogue catalogue() {
        if (catalogue == null) {
            try (InputStream in = asset("product_catalogue.csv")) {
                catalogue = ProductCatalogue.load(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return catalogue;
    }

    static synchronized GstinLookup gstin() {
        if (gstin == null) {
            try (InputStream in = asset("mock_gstin_registry.csv")) {
                gstin = GstinLookup.load(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return gstin;
    }

    static RulesEngine engine() {
        return new RulesEngine(catalogue());
    }

    static String systemPrompt() {
        try (InputStream in = asset("help_system_prompt.txt")) {
            return KnowledgeBase.readAll(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Context with the bundle already recommended by the engine. */
    static HelpContext context(BusinessProfile p, Lang appLang, JourneyStage stage, String rawGstin) {
        HelpContext c = new HelpContext();
        c.profile = p;
        c.appLanguage = appLang;
        c.stage = stage;
        c.setGstin(rawGstin);
        c.recommendation = engine().recommend(p);
        return c;
    }

    static HelpSession session(HelpContext c) {
        return new HelpSession(kb(), engine(), gstin(), c, new HelpStats());
    }

    static BusinessProfile profile(BusinessProfile.Segment seg, String type, String city, int staff, int sites,
                                   BusinessProfile.GstinStatus gst, Need... needs) {
        BusinessProfile b = new BusinessProfile();
        b.segment = seg;
        b.businessType = type;
        b.city = city;
        b.employees = staff;
        b.sites = sites;
        b.gstinStatus = gst;
        b.needs = needs.length == 0 ? EnumSet.of(Need.INTERNET) : EnumSet.of(needs[0], needs);
        return b;
    }

    /** A real Active GSTIN from the mock registry for a state (by state code). */
    static String activeGstin(String stateCode) {
        // Picked from mock_gstin_registry.csv; checked in GstinAndMaskingTest.
        switch (stateCode) {
            case "27": return "27QTBXZ1995X1Z9"; // Maharashtra
            case "24": return "24DTTXB1771D1ZD"; // Gujarat
            case "33": return "33MFTXS9954Q1Z9"; // Tamil Nadu
            case "09": return "09YHJXG6633U1ZC"; // Uttar Pradesh
            default: return "32TTNXB7384T1ZW";  // Kerala
        }
    }
}
