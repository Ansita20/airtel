package com.udyam.digital.kyc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a KYC application for a specimen applicant (K01-K12) from its processed documents
 * and runs every check. The specimen pack has no typed-in numbers, so the GSTIN and Udyam
 * "entered" by the applicant are the ones read from their certificates; the business address
 * from the GST registry stands in for the installation address. Pure Java.
 */
public final class SpecimenRunner {
    private SpecimenRunner() {}

    public static final class Row {
        public final String applicant;
        public final KycApplication.Outcome overall;
        public final List<String> codes;       // e.g. "C13 FAIL"
        public final String message;
        public final List<CheckResult> results;

        Row(String applicant, KycApplication.Outcome overall, List<String> codes, String message, List<CheckResult> results) {
            this.applicant = applicant;
            this.overall = overall;
            this.codes = codes;
            this.message = message;
            this.results = results;
        }
    }

    public static KycApplication build(String applicant, Map<Slot, KycDocument> docs) {
        KycApplication app = new KycApplication(applicant);
        app.kycConsent = true;
        KycDocument gst = docs.get(Slot.GST_CERT);
        KycDocument ud = docs.get(Slot.UDYAM_CERT);
        app.noGstin = gst == null;
        app.gstin = gst != null && gst.get(FieldExtractor.GSTIN) != null ? gst.get(FieldExtractor.GSTIN) : "";
        app.udyam = ud != null && ud.get(FieldExtractor.UDYAM) != null ? ud.get(FieldExtractor.UDYAM) : "";
        app.documents.putAll(docs);
        return app;
    }

    public static Row run(KycRuleEngine engine, String applicant, Map<Slot, KycDocument> docs) {
        KycApplication app = build(applicant, docs);
        List<CheckResult> results = engine.run(app, KycClock.TODAY);
        KycRuleEngine.Decision d = KycRuleEngine.decide(app, results);
        List<String> codes = new ArrayList<>();
        String message = "";
        for (CheckResult r : results) {
            if (r.status == CheckResult.Status.PASS) continue;
            codes.add(r.code + " " + r.status);
            if (message.isEmpty() && r.status == CheckResult.Status.FAIL) message = r.en;
        }
        if (message.isEmpty()) {
            for (CheckResult r : results) if (r.status == CheckResult.Status.WARN) { message = r.en; break; }
        }
        if (d.outcome == KycApplication.Outcome.MANUAL_REVIEW) message = d.reasonEn;
        return new Row(applicant, d.outcome, codes, message, results);
    }
}
