package com.udyam.digital.kyc;

import java.util.List;

/** The only KYC information the AI help bot may see: check codes and masked IDs. */
public final class HelpBotContext {
    private HelpBotContext() {}

    public static String of(KycApplication app, List<CheckResult> results) {
        StringBuilder b = new StringBuilder();
        b.append("kyc_application=").append(app.id);
        if (!app.noGstin && app.gstin != null && !app.gstin.isEmpty()) b.append("; gstin=").append(Mask.gstin(GstinValidator.normalize(app.gstin)));
        if (app.udyam != null && !app.udyam.isEmpty()) b.append("; udyam=").append(Mask.udyam(UdyamValidator.normalize(app.udyam)));
        b.append("; outcome=").append(app.outcome);
        if (results != null) {
            String s = KycRuleEngine.summary(results);
            if (!s.isEmpty()) b.append("; checks=").append(s);
        }
        return b.toString();
    }
}
