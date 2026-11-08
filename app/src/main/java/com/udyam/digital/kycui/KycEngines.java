package com.udyam.digital.kycui;

import android.content.Context;

import com.udyam.digital.data.DataRepository;
import com.udyam.digital.kyc.GstinValidator;
import com.udyam.digital.kyc.KycRuleEngine;
import com.udyam.digital.kyc.UdyamValidator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Lazily loads the KYC validators from the registry CSVs in assets. */
public final class KycEngines {
    private KycEngines() {}

    private static GstinValidator gst;
    private static KycRuleEngine engine;

    public static synchronized GstinValidator gst(Context ctx) {
        if (gst == null) {
            try (Reader r = new InputStreamReader(ctx.getAssets().open("mock_gstin_registry.csv"), StandardCharsets.UTF_8)) {
                gst = GstinValidator.load(r);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return gst;
    }

    public static UdyamValidator udyam() { return new UdyamValidator(DataRepository.get().udyam()); }

    public static synchronized KycRuleEngine engine(Context ctx) {
        if (engine == null) engine = new KycRuleEngine(gst(ctx), udyam());
        return engine;
    }
}
