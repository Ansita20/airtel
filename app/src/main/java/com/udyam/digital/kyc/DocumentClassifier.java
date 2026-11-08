package com.udyam.digital.kyc;

import java.util.List;
import java.util.Locale;

/** Decides the document type from keywords in the OCR text. Highest weighted score wins. */
public final class DocumentClassifier {
    private DocumentClassifier() {}

    private static final Object[][] RULES = {
            // type, weight, keywords...
            {DocType.GST_CERTIFICATE, 3, "goods and services tax", "form gst reg-06", "gst reg-06", "gst registration certificate"},
            {DocType.GST_CERTIFICATE, 1, "gstin", "date of liability", "constitution", "principal place"},
            {DocType.UDYAM_CERTIFICATE, 4, "udyam registration certificate", "udyam reg", "udyam registration number"},
            {DocType.UDYAM_CERTIFICATE, 1, "enterprise name", "major activity", "msme"},
            {DocType.PAN_CARD, 3, "income tax department", "permanent account number"},
            {DocType.VOTER_ID, 3, "election commission", "elector"},
            {DocType.DRIVING_LICENCE, 3, "driving licence", "driving license", "transport department"},
            {DocType.PASSPORT, 3, "passport no", "republic of india passport", "passport"},
            {DocType.AADHAAR, 3, "aadhaar", "आधार", "unique identification authority"},
            {DocType.SIGNATORY_ID, 3, "authorised signatory", "authorized signatory", "signatory id"},
            {DocType.SIGNATORY_ID, 1, "designation", "valid till", "for business"},
            {DocType.UTILITY_BILL, 3, "electricity bill", "water bill", "gas bill"},
            {DocType.UTILITY_BILL, 1, "electricity", "bill date", "units", "consumer no", "supply address", "amount due"},
            {DocType.TELEPHONE_BILL, 3, "telephone bill", "broadband bill", "landline bill", "postpaid bill"},
            {DocType.BANK_STATEMENT, 3, "bank statement", "account statement", "statement of account"},
            {DocType.RENT_AGREEMENT, 3, "rent agreement", "lease agreement", "lease deed", "rental agreement"},
    };

    public static DocType classify(List<OcrLine> lines) {
        StringBuilder b = new StringBuilder();
        for (OcrLine l : lines) b.append(l.text).append('\n');
        return classify(b.toString());
    }

    public static DocType classify(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        int[] score = new int[DocType.values().length];
        for (Object[] r : RULES) {
            DocType type = (DocType) r[0];
            int w = (Integer) r[1];
            for (int i = 2; i < r.length; i++) if (t.contains((String) r[i])) score[type.ordinal()] += w;
        }
        DocType best = DocType.UNKNOWN;
        int bestScore = 1; // need more than a single weak keyword
        for (DocType d : DocType.values()) {
            if (score[d.ordinal()] > bestScore) {
                best = d;
                bestScore = score[d.ordinal()];
            }
        }
        return best;
    }
}
