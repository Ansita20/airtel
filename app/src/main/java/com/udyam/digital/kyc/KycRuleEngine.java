package com.udyam.digital.kyc;

import com.udyam.digital.data.UdyamLookup;
import com.udyam.digital.i18n.I18n;
import com.udyam.digital.model.Language;
import com.udyam.digital.kyc.CheckResult.FixAt;
import com.udyam.digital.kyc.CheckResult.Status;
import com.udyam.digital.kyc.GstinValidator.GstRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.udyam.digital.kyc.FieldExtractor.*;

/**
 * Cross-checks C01-C20 over the entered numbers, the registries and the uploaded documents.
 * Each check gives PASS / WARN / FAIL plus a confidence, with a plain-language reason in
 * English and Hindi.
 *
 * Avoiding wrong verdicts: a FAIL that rests on a document field OCR read with confidence
 * below 0.80, which the owner didn't confirm, becomes "We couldn't read this clearly - please
 * rescan" (fixable) instead. Better to ask for a rescan than to reject a good applicant.
 *
 * Decision: anything the owner can't fix (cancelled GSTIN, PAN mismatch, name far off,
 * not in registry, under-age, C20 possible tampering) -> manual review; else any fixable FAIL
 * or unconfirmed WARN -> "Please fix"; else approved. Pure Java; dates from {@link KycClock#TODAY}.
 */
public final class KycRuleEngine {

    private final GstinValidator gstValidator;
    private final UdyamValidator udyamValidator;

    public KycRuleEngine(GstinValidator gst, UdyamValidator udyam) {
        this.gstValidator = gst;
        this.udyamValidator = udyam;
    }

    /** Registry lookups for {@link IdCorrector}: a repaired identifier must exist here. */
    public IdCorrector.Registry registry() {
        return new IdCorrector.Registry() {
            @Override public boolean hasGstin(String g) { return gstValidator.find(g) != null; }
            @Override public boolean hasUdyam(String u) { return udyamValidator.find(u) != null; }
            @Override public boolean hasPan(String p) { return gstValidator.hasPan(p); }
        };
    }

    /** Overall result of a run. */
    public static final class Decision {
        public final KycApplication.Outcome outcome;
        /** Plain-language reason (manual review), translatable; English and Hindi text for logs/tests. */
        public final I18n.Msg reason;
        public final String reasonEn, reasonHi;
        /** Items the owner must fix or confirm. */
        public final int toFix;

        Decision(KycApplication.Outcome o, I18n.Msg reason, int toFix) {
            this.outcome = o;
            this.reason = reason;
            this.reasonEn = reason == null ? "" : reason.in(Language.EN);
            this.reasonHi = reason == null ? "" : reason.in(Language.HI);
            this.toFix = toFix;
        }
    }

    // ------------------------------------------------------------------ helpers
    private final List<CheckResult> out = new ArrayList<>();

    private static I18n.Msg m(String key, Object... args) { return I18n.msg(key, args); }

    private void pass(String code, I18n.Msg t) { out.add(new CheckResult(code, Status.PASS, true, FixAt.NONE, null, t)); }
    private void warn(String code, Slot slot, I18n.Msg t) { out.add(new CheckResult(code, Status.WARN, true, FixAt.CONFIRM, slot, t)); }
    private void fix(String code, FixAt at, Slot slot, I18n.Msg t) { out.add(new CheckResult(code, Status.FAIL, true, at, slot, t)); }
    private void block(String code, Slot slot, I18n.Msg t) { out.add(new CheckResult(code, Status.FAIL, false, FixAt.NONE, slot, t)); }

    private static boolean usable(KycDocument d) {
        return d != null && d.slot.accepts.contains(d.type) && (d.quality == null || d.quality.ok()) && !d.unmaskedAadhaar;
    }

    /** A field this result rests on that OCR was unsure of (or a date read two ways), not confirmed by the owner. */
    private static String unsureField(KycDocument d, String... fields) {
        if (d == null) return null;
        for (String f : fields) if (d.ambiguous(f) || d.unsure(f)) return f;
        return null;
    }

    private static float conf(KycDocument d, String... fields) {
        float c = 1f;
        if (d != null) for (String f : fields) if (d.get(f) != null && !d.vouched(f)) c = Math.min(c, d.confidence(f));
        return c;
    }

    /**
     * A FAIL based on what OCR read from document {@code d}. If any of those fields is unsure,
     * the owner is asked to rescan (or pick the date) instead: fixable, never a rejection.
     */
    private void failOn(String code, boolean fixable, FixAt at, Slot slot, I18n.Msg t, KycDocument d, String... fields) {
        String f = unsureField(d, fields);
        if (f != null) {
            rescan(code, d, f);
            return;
        }
        out.add(new CheckResult(code, Status.FAIL, fixable, fixable ? at : FixAt.NONE, slot, t, conf(d, fields), false));
    }

    private void rescan(String code, KycDocument d, String field) {
        LocalDate[] two = d.ambiguous(field) ? KycDates.readings(d.get(field)) : null;
        I18n.Msg t = two != null
                ? m("kyc_rescan_two", FieldLabels.text(field), d.slot.text(), two[0], two[1])
                : m("kyc_rescan", FieldLabels.text(field), d.slot.text());
        out.add(new CheckResult(code, Status.FAIL, true, FixAt.UPLOAD, d.slot, t, d.confidence(field), true));
    }

    /** WARN that a person must look at (not fixable by the owner): manual review. */
    private void review(String code, Slot slot, I18n.Msg t) {
        out.add(new CheckResult(code, Status.WARN, false, FixAt.NONE, slot, t));
    }

    // ------------------------------------------------------------------ run
    public synchronized List<CheckResult> run(KycApplication app, LocalDate today) {
        out.clear();
        String gstin = GstinValidator.normalize(app.gstin);
        String udyamNo = UdyamValidator.normalize(app.udyam);
        GstRecord g = null;
        UdyamLookup.Record u = null;
        KycDocument gd = app.doc(Slot.GST_CERT), ud = app.doc(Slot.UDYAM_CERT),
                idd = app.doc(Slot.SIGNATORY_ID), ad = app.doc(Slot.ADDRESS_PROOF);

        // C19 photo quality, C17 document type (per required slot)
        for (Slot s : app.requiredSlots()) {
            KycDocument d = app.doc(s);
            if (d == null) {
                fix("C17", FixAt.UPLOAD, s, m("kyc_c17_missing", s.text()));
                continue;
            }
            if (d.quality != null && !d.quality.ok()) {
                fix("C19", FixAt.UPLOAD, s, m("kyc_c19_unclear", s.text()));
            } else {
                pass("C19", m("kyc_c19_clear", s.text()));
            }
            if (!s.accepts.contains(d.type)) {
                DocType expected = s.accepts.iterator().next();
                I18n.Msg need = s == Slot.SIGNATORY_ID ? m("kyc_need_signatory") : s == Slot.ADDRESS_PROOF ? m("kyc_need_address")
                        : m("kyc_need_doc", expected.text());
                fix("C17", FixAt.UPLOAD, s, m("kyc_c17_wrong", d.type.text(), need));
            } else if (d.unmaskedAadhaar) {
                fix("C17", FixAt.UPLOAD, s, m("kyc_c17_aadhaar"));
            } else {
                pass("C17", m("kyc_c17_ok", s.text()));
            }
        }

        // C20 possible tampering: the two OCR passes read a field differently (both sure), or the
        // words of one field are printed in clearly different sizes.
        boolean anyDoc = false, tampered = false;
        for (Slot s : app.requiredSlots()) {
            KycDocument d = app.doc(s);
            if (d == null || !s.accepts.contains(d.type)) continue;
            anyDoc = true;
            if (!d.looksTampered()) continue;
            tampered = true;
            java.util.Set<String> fs = new java.util.LinkedHashSet<>(d.passDisagreement.keySet());
            fs.addAll(d.fontMismatch);
            review("C20", s, m("kyc_c20_review", s.text(), new FieldList(fs)));
        }
        if (anyDoc && !tampered) pass("C20", m("kyc_c20_ok"));

        // C16 same image in two slots
        List<Slot> slots = new ArrayList<>(app.documents.keySet());
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                KycDocument a = app.doc(slots.get(i)), b = app.doc(slots.get(j));
                // Same picture: near-identical image fingerprint AND the same text read from it
                // (blank templates alone look alike, so the fingerprint is not enough).
                boolean sameText = !a.fields.isEmpty() && a.fields.equals(b.fields) && a.type == b.type;
                if (a.hash != 0 && sameText && PerceptualHash.distance(a.hash, b.hash) <= PerceptualHash.SAME_IMAGE_MAX_DISTANCE) {
                    fix("C16", FixAt.UPLOAD, b.slot, m("kyc_c16_same", a.slot.text(), b.slot.text()));
                }
            }
        }

        // C01-C02 GSTIN
        if (!app.noGstin) {
            if (gstin.isEmpty()) {
                fix("C01", FixAt.LOOKUP, null, m("kyc_c01_empty"));
            } else if (!GstinValidator.isValidFormat(gstin)) {
                fix("C01", FixAt.LOOKUP, null, m("kyc_c01_format"));
            } else if (!GstinValidator.hasValidChecksum(gstin)) {
                fix("C01", FixAt.LOOKUP, null, m("kyc_c01_checksum"));
            } else {
                pass("C01", m("kyc_c01_ok", Mask.gstin(gstin)));
                g = gstValidator.find(gstin);
                // When the number came from reading the certificate, an unsure reading means rescan, not "not found".
                KycDocument src = gd != null && gstin.equals(gd.get(GSTIN)) ? gd : null;
                if (g == null) {
                    // Valid format + checksum but not in the registry: the owner confirms it (no hard reject).
                    String uf = unsureField(src, GSTIN);
                    if (uf != null) rescan("C02", src, uf);
                    else warn("C02", null, m("kyc_c02_unverified", Mask.gstin(gstin)));
                } else if (!GstinValidator.stateCodeMatches(g)) {
                    failOn("C02", false, FixAt.NONE, null, m("kyc_c02_state", gstin.substring(0, 2), g.state), src, GSTIN);
                } else if (!g.isActive()) {
                    String st = g.status.toLowerCase(Locale.ROOT);
                    block("C02", null, m("cancelled".equals(st) ? "kyc_c02_cancelled" : "kyc_c02_suspended"));
                } else {
                    pass("C02", m("kyc_c02_ok"));
                }
            }
        }

        // C03-C04 GST certificate
        if (!app.noGstin && usable(gd)) {
            String certGstin = gd.get(GSTIN);
            if (certGstin == null) {
                fix("C03", FixAt.UPLOAD, Slot.GST_CERT, m("kyc_c03_unread"));
            } else if (gd.corrected(GSTIN) && gstValidator.find(certGstin) == null) {
                // A value the owner typed is re-checked against the registry.
                fix("C03", FixAt.UPLOAD, Slot.GST_CERT, m("kyc_c03_typed_notfound", Mask.gstin(certGstin)));
            } else if (!certGstin.equals(gstin)) {
                failOn("C03", true, FixAt.LOOKUP, Slot.GST_CERT, m("kyc_c03_mismatch", Mask.gstin(certGstin), Mask.gstin(gstin)), gd, GSTIN);
            } else if (gd.corrected(GSTIN)) {
                warn("C03", Slot.GST_CERT, m("kyc_c03_edited"));
            } else {
                pass("C03", m("kyc_c03_ok"));
            }
            if (g != null) {
                String certName = gd.get(LEGAL_NAME);
                if (certName == null) {
                    fix("C04", FixAt.UPLOAD, Slot.GST_CERT, m("kyc_c04_unread"));
                } else {
                    nameCheck("C04", Slot.GST_CERT, legalNameScore(certName, g.legalName), gd, LEGAL_NAME, false, "kyc_c04", certName, g.legalName);
                }
            }
        }

        // C05-C06 Udyam
        if (udyamNo.isEmpty()) {
            fix("C05", FixAt.LOOKUP, null, m("kyc_c05_empty"));
        } else if (!UdyamValidator.isValidFormat(udyamNo)) {
            fix("C05", FixAt.LOOKUP, null, m("kyc_c05_format"));
        } else {
            u = udyamValidator.find(udyamNo);
            KycDocument src = ud != null && udyamNo.equals(ud.get(UDYAM)) ? ud : null;
            if (u == null) {
                String uf = unsureField(src, UDYAM);
                if (uf != null) rescan("C05", src, uf);
                else warn("C05", null, m("kyc_c05_unverified", Mask.udyam(udyamNo)));
            } else if (!States.same(UdyamValidator.stateOf(udyamNo), u.state)) {
                failOn("C05", false, FixAt.NONE, null, m("kyc_c05_state", u.state), src, UDYAM);
            } else {
                pass("C05", m("kyc_c05_ok"));
            }
        }
        if (usable(ud)) {
            String certUdyam = ud.get(UDYAM);
            if (certUdyam == null) {
                fix("C06", FixAt.UPLOAD, Slot.UDYAM_CERT, m("kyc_c06_unread"));
            } else if (ud.corrected(UDYAM) && udyamValidator.find(certUdyam) == null) {
                fix("C06", FixAt.UPLOAD, Slot.UDYAM_CERT, m("kyc_c06_typed_notfound", Mask.udyam(certUdyam)));
            } else if (!certUdyam.equals(udyamNo)) {
                failOn("C06", true, FixAt.LOOKUP, Slot.UDYAM_CERT, m("kyc_c06_mismatch", Mask.udyam(certUdyam)), ud, UDYAM);
            } else if (ud.corrected(UDYAM)) {
                warn("C06", Slot.UDYAM_CERT, m("kyc_c06_edited"));
            } else {
                pass("C06", m("kyc_c06_ok"));
            }
        }

        // C07-C08 Udyam vs GST
        if (g != null && (u != null || usable(ud))) {
            List<String> names = NameMatcher.businessNames(g.legalName, g.tradeName);
            List<String> udyamNames = new ArrayList<>();
            if (u != null) udyamNames.add(u.enterpriseName);
            if (usable(ud) && ud.get(ENTERPRISE) != null) udyamNames.add(ud.get(ENTERPRISE));
            if (!udyamNames.isEmpty()) {
                double worst = 1;
                String worstName = null;
                for (String n : udyamNames) {
                    double sc = NameMatcher.best(n, names);
                    if (sc < worst) { worst = sc; worstName = n; }
                }
                boolean fromCert = usable(ud) && worstName != null && worstName.equals(ud.get(ENTERPRISE))
                        && (u == null || !worstName.equals(u.enterpriseName));
                nameCheck("C07", Slot.UDYAM_CERT, worst, fromCert ? ud : null, ENTERPRISE, false, "kyc_c07", worstName, g.tradeName);
            }
            String uState = u != null ? u.state : ud.get(STATE);
            String certState = usable(ud) ? ud.get(STATE) : null;
            boolean stateOk = States.same(uState, g.state) && (certState == null || States.same(certState, g.state));
            if (uState != null) {
                if (stateOk) {
                    pass("C08", m("kyc_c08_ok"));
                } else {
                    boolean certIsOff = certState != null && !States.same(certState, g.state);
                    String shown = certIsOff ? certState : uState;
                    failOn("C08", false, FixAt.NONE, Slot.UDYAM_CERT, m("kyc_c08_fail", shown, g.state),
                            certIsOff && States.same(uState, g.state) ? ud : null, STATE);
                }
            }
        }

        // C09-C12 signatory ID
        if (usable(idd)) {
            String idName = idd.get(NAME);
            String[] prop = g == null ? null : NameMatcher.splitProprietor(g.legalName);
            boolean proprietorship = g != null && (g.isProprietorship() || prop != null);
            if (idName == null) {
                fix("C09", FixAt.UPLOAD, Slot.SIGNATORY_ID, m("kyc_c09_unread"));
            } else if (proprietorship && prop != null) {
                nameCheck("C09", Slot.SIGNATORY_ID, NameMatcher.score(idName, prop[0]), idd, NAME, false, "kyc_c09", idName, prop[0]);
            } else {
                String forBiz = idd.get(FOR_BUSINESS);
                List<String> names = g != null ? NameMatcher.businessNames(g.legalName, g.tradeName)
                        : u != null ? Collections.singletonList(u.enterpriseName) : Collections.<String>emptyList();
                if (forBiz != null && !names.isEmpty() && NameMatcher.best(forBiz, names) < NameMatcher.WARN) {
                    failOn("C09", false, FixAt.NONE, Slot.SIGNATORY_ID, m("kyc_c09_other_business", forBiz), idd, FOR_BUSINESS);
                } else {
                    warn("C09", Slot.SIGNATORY_ID, m("kyc_c09_confirm", idName));
                }
            }
            String pan = idd.get(PAN);
            if (proprietorship && pan != null && g != null) {
                String gstPan = GstinValidator.panOf(g.gstin);
                if (pan.equals(gstPan)) {
                    pass("C10", m("kyc_c10_ok", Mask.pan(pan)));
                } else {
                    failOn("C10", false, FixAt.NONE, Slot.SIGNATORY_ID, m("kyc_c10_fail", Mask.pan(pan), Mask.pan(gstPan)), idd, PAN);
                }
            }
            String expiryText = idd.get(EXPIRY);
            if (expiryText != null) {
                LocalDate exp = KycDates.parse(expiryText);
                if (idd.ambiguous(EXPIRY)) {
                    rescan("C11", idd, EXPIRY);
                } else if (exp == null) {
                    warn("C11", Slot.SIGNATORY_ID, m("kyc_c11_unread"));
                } else if (!KycDates.notExpired(exp, today)) {
                    failOn("C11", true, FixAt.UPLOAD, Slot.SIGNATORY_ID, m("kyc_c11_expired", exp), idd, EXPIRY);
                } else {
                    pass("C11", m("kyc_c11_ok", exp));
                }
            }
            LocalDate dob = KycDates.parse(idd.get(DOB));
            if (dob != null && idd.ambiguous(DOB)) {
                rescan("C12", idd, DOB);
            } else if (dob != null) {
                if (KycDates.isAdult(dob, today)) {
                    pass("C12", m("kyc_c12_ok"));
                } else {
                    failOn("C12", false, FixAt.NONE, Slot.SIGNATORY_ID, m("kyc_c12_minor", dob), idd, DOB);
                }
            }
        }

        // C13-C15 address proof
        if (usable(ad)) {
            LocalDate billDate = KycDates.parse(ad.get(BILL_DATE));
            if (billDate != null && ad.ambiguous(BILL_DATE)) {
                rescan("C13", ad, BILL_DATE);
            } else if (billDate == null) {
                fix("C13", FixAt.UPLOAD, Slot.ADDRESS_PROOF, m("kyc_c13_missing"));
            } else if (billDate.isAfter(today)) {
                failOn("C13", true, FixAt.UPLOAD, Slot.ADDRESS_PROOF, m("kyc_c13_future", billDate, today), ad, BILL_DATE);
            } else if (!KycDates.isRecentAddressProof(billDate, today)) {
                failOn("C13", true, FixAt.UPLOAD, Slot.ADDRESS_PROOF, m("kyc_c13_old", billDate, (int) KycDates.daysBefore(billDate, today)), ad, BILL_DATE);
            } else {
                pass("C13", m("kyc_c13_ok", billDate));
            }

            String addr = ad.get(ADDRESS);
            String target = g != null ? g.address : null;
            String install = app.installationAddress == null || app.installationAddress.trim().isEmpty() ? null : app.installationAddress;
            if (addr == null) {
                fix("C14", FixAt.UPLOAD, Slot.ADDRESS_PROOF, m("kyc_c14_unread"));
            } else if (target == null && install == null) {
                warn("C14", Slot.ADDRESS_PROOF, m("kyc_c14_confirm", addr));
            } else {
                int m = Math.max(addressMatch(addr, target), addressMatch(addr, install));
                if (m == 2) {
                    pass("C14", m("kyc_c14_ok"));
                } else if (m == 1) {
                    warn("C14", Slot.ADDRESS_PROOF, m("kyc_c14_pin"));
                } else {
                    String[] a = cityPin(addr), b = cityPin(target != null ? target : install);
                    failOn("C14", true, FixAt.UPLOAD, Slot.ADDRESS_PROOF, m("kyc_c14_fail", a[0], a[1], b[0], b[1]), ad, ADDRESS);
                }
            }

            String billName = ad.get(NAME);
            if (billName != null) {
                List<String> names = new ArrayList<>();
                if (g != null) names.addAll(NameMatcher.businessNames(g.legalName, g.tradeName));
                if (u != null) names.add(u.enterpriseName);
                if (usable(idd) && idd.get(NAME) != null) names.add(idd.get(NAME));
                if (g != null) {
                    String[] p = NameMatcher.splitProprietor(g.legalName);
                    if (p != null) names.add(p[0]);
                }
                if (!names.isEmpty()) {
                    double sc = NameMatcher.best(billName, names);
                    if (sc >= NameMatcher.PASS && !ad.corrected(NAME)) {
                        pass("C15", m("kyc_c15_ok"));
                    } else if (sc >= NameMatcher.WARN) {
                        warn("C15", Slot.ADDRESS_PROOF, m("kyc_c15_close", billName));
                    } else {
                        failOn("C15", true, FixAt.UPLOAD, Slot.ADDRESS_PROOF, m("kyc_c15_fail", billName), ad, NAME);
                    }
                }
            }
        }

        // C18 registration dates
        // {label, value, document, field} - document and field are null for registry dates
        List<Object[]> dates = new ArrayList<>();
        if (g != null) dates.add(new Object[] {m("kyc_date_gst_reg"), g.registrationDate, null, null});
        if (usable(gd)) dates.add(new Object[] {m("kyc_date_gst_liability"), gd.get(LIABILITY_DATE), gd, LIABILITY_DATE});
        if (u != null) dates.add(new Object[] {m("kyc_date_udyam_reg"), u.registrationDate, null, null});
        if (usable(ud)) dates.add(new Object[] {m("kyc_date_udyam_cert"), ud.get(REG_DATE), ud, REG_DATE});
        boolean anyDate = false, bad = false;
        for (Object[] d : dates) {
            LocalDate x = KycDates.parse((String) d[1]);
            if (x == null) continue;
            anyDate = true;
            if (x.isAfter(today)) {
                bad = true;
                failOn("C18", false, FixAt.NONE, null, m("kyc_c18_future", d[0], x, today),
                        (KycDocument) d[2], d[3] == null ? "-" : (String) d[3]);
            }
        }
        if (anyDate && !bad) pass("C18", m("kyc_c18_ok"));

        List<CheckResult> res = new ArrayList<>(out);
        Collections.sort(res, (a, b) -> a.code.compareTo(b.code));
        return res;
    }

    /**
     * "Person (Prop. Business)" names are compared part by part, so a changed business name
     * isn't hidden by an identical proprietor name.
     */
    static double legalNameScore(String a, String b) {
        String[] pa = NameMatcher.splitProprietor(a), pb = NameMatcher.splitProprietor(b);
        if (pa != null && pb != null) return Math.min(NameMatcher.score(pa[0], pb[0]), NameMatcher.score(pa[1], pb[1]));
        return NameMatcher.score(a, b);
    }

    /** @param doc the document the compared name was read from (null = registry only) */
    /** Messages {@code base}_ok, _close (warn) and _fail, with the two names compared. */
    private void nameCheck(String code, Slot slot, double score, KycDocument doc, String field, boolean fixableFail,
                           String base, String seen, String expected) {
        boolean corrected = doc != null && doc.corrected(field);
        NameMatcher.Verdict v = NameMatcher.verdict(score);
        if (v == NameMatcher.Verdict.PASS && !corrected) {
            pass(code, m(base + "_ok"));
        } else if (v != NameMatcher.Verdict.FAIL) {
            warn(code, slot, m(base + "_close", seen, expected));
        } else {
            failOn(code, fixableFail, FixAt.UPLOAD, slot, m(base + "_fail", seen, expected), doc, field);
        }
    }

    /** "bill date, name": field names joined, translated in the reader's language. */
    private static final class FieldList extends I18n.Msg {
        FieldList(java.util.Collection<String> fields) { super("", fields.toArray()); }

        @Override public String in(Language l) {
            List<String> names = new ArrayList<>();
            for (Object f : args) names.add(FieldLabels.of((String) f, l));
            return String.join(", ", names);
        }
    }

    private static final Pattern PIN = Pattern.compile("\\b[1-9][0-9]{5}\\b");

    /** {city, pin} from "Shop 1, Road, City, State - 123456". */
    static String[] cityPin(String address) {
        if (address == null) return new String[] {"", ""};
        Matcher m = PIN.matcher(address);
        String pin = "";
        while (m.find()) pin = m.group();
        String[] parts = address.replaceAll("-\\s*[0-9]{6}", "").split(",");
        String city = parts.length >= 2 ? parts[parts.length - 2].trim() : "";
        return new String[] {city, pin};
    }

    /** 2 = same PIN (or same city when a PIN is missing), 1 = same city only, 0 = different. */
    static int addressMatch(String a, String b) {
        if (a == null || b == null) return 0;
        String[] x = cityPin(a), y = cityPin(b);
        boolean pinSame = !x[1].isEmpty() && x[1].equals(y[1]);
        boolean citySame = !x[0].isEmpty() && x[0].equalsIgnoreCase(y[0]);
        if (pinSame) return 2;
        if (citySame) return (x[1].isEmpty() || y[1].isEmpty()) ? 2 : 1;
        return 0;
    }

    /** Overall result. */
    public static Decision decide(KycApplication app, List<CheckResult> results) {
        int toFix = 0;
        for (CheckResult r : results) {
            if (r.status == Status.FAIL && !r.fixable) {
                return new Decision(KycApplication.Outcome.MANUAL_REVIEW, r.msg, 0);
            }
        }
        for (CheckResult r : results) { // C20: a person must look, whatever the owner does
            if (r.status == Status.WARN && !r.fixable) {
                return new Decision(KycApplication.Outcome.MANUAL_REVIEW, r.msg, 0);
            }
        }
        for (CheckResult r : results) {
            if (r.status == Status.FAIL || (r.status == Status.WARN && !app.confirmedWarnings.contains(r.key()))) toFix++;
        }
        return toFix > 0
                ? new Decision(KycApplication.Outcome.PLEASE_FIX, null, toFix)
                : new Decision(KycApplication.Outcome.APPROVED, null, 0);
    }

    /** True when the only blocker is an inactive (cancelled / suspended) GSTIN. */
    public static boolean inactiveGstin(List<CheckResult> results) {
        for (CheckResult r : results) {
            if (r.code.equals("C02") && (r.msg.key.equals("kyc_c02_cancelled") || r.msg.key.equals("kyc_c02_suspended"))) return true;
        }
        return false;
    }

    /** Check codes with WARN or FAIL, e.g. "C13 FAIL, C15 WARN". For the help bot: codes only. */
    public static String summary(List<CheckResult> results) {
        StringBuilder b = new StringBuilder();
        for (CheckResult r : results) {
            if (r.status == Status.PASS) continue;
            if (b.length() > 0) b.append(", ");
            b.append(r.code).append(' ').append(r.status);
        }
        return b.toString();
    }

    static final List<String> ALL_CODES = Arrays.asList("C01", "C02", "C03", "C04", "C05", "C06", "C07", "C08", "C09",
            "C10", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20");
}
