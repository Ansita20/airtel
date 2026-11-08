package com.udyam.digital.help;

import android.content.Context;

import com.udyam.digital.engine.Pack;
import com.udyam.digital.help.core.BusinessProfile.GstinStatus;
import com.udyam.digital.help.core.BusinessProfile.Need;
import com.udyam.digital.help.core.BusinessProfile.Segment;
import com.udyam.digital.help.core.Product;
import com.udyam.digital.help.core.Recommendation;
import com.udyam.digital.help.core.RulesEngine;
import com.udyam.digital.kyc.CheckResult;
import com.udyam.digital.kyc.GstinValidator;
import com.udyam.digital.kyc.KycApplication;
import com.udyam.digital.kyc.KycClock;
import com.udyam.digital.kycui.KycEngines;
import com.udyam.digital.kycui.KycStore;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.ui.AppState;

import java.util.EnumSet;
import java.util.List;

/**
 * Fills the help bot's context from what the app already knows: the questionnaire answers,
 * the plan the owner selected, and the KYC state. Only what the bot needs is copied, and the
 * GSTIN only ever in masked form. KYC contributes check codes, never document contents.
 */
public final class AppHelpContext {
    private AppHelpContext() {}

    /** Manual KYC review adds this to the escalation score. */
    public static final int KYC_MANUAL_REVIEW_POINTS = 30;

    public static HelpContext build(Context app, RulesEngine helpEngine) {
        HelpContext c = new HelpContext();
        try {
            c.appLanguage = Lang.valueOf(AppState.language().name());
        } catch (IllegalArgumentException e) {
            c.appLanguage = Lang.EN;
        }

        BusinessProfile p = AppState.get().profile();
        com.udyam.digital.help.core.BusinessProfile b = new com.udyam.digital.help.core.BusinessProfile();
        b.businessType = p.getBusinessType() == null ? p.getSegment().leadName : p.getBusinessType();
        b.segment = segment(p);
        b.city = p.getCity() == null ? "" : p.getCity();
        b.employees = p.getEmployees();
        b.sites = p.getSites();
        b.monthlyBudget = p.getBudgetInr();
        b.currentInternet = p.getCurrentInternet() == null ? "" : p.getCurrentInternet().name();
        b.needs = needs(p);
        b.gstinStatus = p.hasValidGstin() ? GstinStatus.ACTIVE : GstinStatus.NONE;
        c.profile = b;
        c.stage = JourneyStage.ADVISOR;

        // The plan the owner is looking at: Your plan (first pack), lines that are switched on.
        List<Pack> packs = AppState.get().getPacks();
        if (packs != null && !packs.isEmpty()) {
            Recommendation r = new Recommendation();
            for (com.udyam.digital.engine.Recommendation.Line l : packs.get(0).plan.getLines()) {
                if (!l.isSelected()) continue;
                Product hp = helpEngine.catalogue().get(l.getProduct().getId());
                if (hp != null) r.add(hp, l.getQuantity());
            }
            c.recommendation = r;
            c.stage = JourneyStage.QUOTE;
        }

        // KYC: status, failed check codes and masked GSTIN only.
        KycApplication k = new KycStore(app).load();
        if (k != null && k.kycConsent) {
            c.stage = JourneyStage.KYC;
            if (!k.noGstin && GstinValidator.isValid(GstinValidator.normalize(k.gstin))) c.setGstin(GstinValidator.normalize(k.gstin));
            c.docsDone = k.documents.size();
            c.docsTotal = k.requiredSlots().length;
            List<CheckResult> results = KycEngines.engine(app).run(k, KycClock.TODAY);
            for (CheckResult r : results) {
                if (r.status != CheckResult.Status.FAIL) continue;
                HelpContext.KycCheck kc = map(r);
                if (kc != null && !c.failedChecks.contains(kc)) c.failedChecks.add(kc);
                if (kc == HelpContext.KycCheck.NAME_MISMATCH && c.mismatchField.isEmpty()) c.mismatchField = r.en;
                if (kc == HelpContext.KycCheck.GSTIN_CANCELLED) b.gstinStatus = GstinStatus.CANCELLED;
                if (kc == HelpContext.KycCheck.GSTIN_SUSPENDED) b.gstinStatus = GstinStatus.SUSPENDED;
            }
            switch (k.outcome) {
                case APPROVED:
                    c.kycStatus = HelpContext.KycStatus.VERIFIED;
                    c.orderStatus = HelpContext.OrderStatus.KYC_VERIFIED;
                    c.stage = JourneyStage.INSTALL;
                    c.orderId = "UD-" + k.id;
                    c.orderDate = KycClock.TODAY;
                    if (c.recommendation != null) c.expectedDate = KycClock.TODAY.plusDays(c.recommendation.maxInstallDays());
                    break;
                case MANUAL_REVIEW:
                    c.kycStatus = HelpContext.KycStatus.FAILED;
                    c.appEscalationPoints = KYC_MANUAL_REVIEW_POINTS;
                    c.appEscalationReason = "KYC sent for manual review (ticket " + k.ticketId + ")";
                    break;
                case PLEASE_FIX:
                    c.kycStatus = HelpContext.KycStatus.FAILED;
                    break;
                default:
                    c.kycStatus = HelpContext.KycStatus.IN_PROGRESS;
            }
            if (c.orderStatus == HelpContext.OrderStatus.NONE) {
                c.orderStatus = HelpContext.OrderStatus.PLACED;
                c.orderId = "UD-" + k.id;
                c.orderDate = KycClock.TODAY;
            }
        }
        addOrder(app, c, helpEngine);
        return c;
    }

    /**
     * The order, if there is one: current step, expected date, days late, products, visit.
     * More than 7 days late adds +40 ("installation delayed") to the escalation score.
     */
    private static void addOrder(Context app, HelpContext c, RulesEngine helpEngine) {
        com.udyam.digital.orderui.OrderManager om = new com.udyam.digital.orderui.OrderManager(app);
        com.udyam.digital.order.Order o = om.load();
        if (o == null) return;
        java.time.LocalDate today = com.udyam.digital.util.AppClock.today();
        com.udyam.digital.model.Language lang = com.udyam.digital.model.Language.fromTags(c.appLanguage == null ? "en" : c.appLanguage.code);
        c.today = today;
        c.orderId = o.maskedId();
        c.orderDate = o.created;
        c.expectedDate = o.promisedActive != null ? o.promisedActive : com.udyam.digital.order.OrderTimeline.activeDate(o, today);
        c.connectionReady = com.udyam.digital.order.OrderTimeline.connectionReady(o, today);
        c.visitSlot = o.visitDate == null ? "" : com.udyam.digital.orderui.OrderManager.visitText(o, lang);
        com.udyam.digital.order.OrderStep cur = o.currentStep();
        c.currentStepLabel = om.stepName(cur == null ? com.udyam.digital.order.OrderStep.ACTIVE : cur);
        Recommendation r = new Recommendation();
        for (com.udyam.digital.order.OrderLine l : o.lines) {
            Product hp = helpEngine.catalogue().get(l.productId);
            if (hp != null) r.add(hp, l.quantity);
        }
        c.recommendation = r;
        if (cur == null) {
            c.orderStatus = HelpContext.OrderStatus.ACTIVE;
            c.stage = JourneyStage.ACTIVE;
        } else {
            switch (cur) {
                case PLAN_CHOSEN: case KYC_COMPLETE:
                    c.orderStatus = HelpContext.OrderStatus.PLACED;
                    c.stage = JourneyStage.KYC;
                    break;
                case PAYMENT_DONE:
                    c.orderStatus = HelpContext.OrderStatus.KYC_VERIFIED;
                    c.stage = JourneyStage.PAYMENT;
                    break;
                case INSTALL_BOOKED:
                    c.orderStatus = HelpContext.OrderStatus.KYC_VERIFIED;
                    c.stage = JourneyStage.INSTALL;
                    break;
                case TECHNICIAN_VISIT:
                    c.orderStatus = HelpContext.OrderStatus.VISIT_SCHEDULED;
                    c.stage = JourneyStage.INSTALL;
                    break;
                default:
                    c.orderStatus = HelpContext.OrderStatus.INSTALLING;
                    c.stage = JourneyStage.INSTALL;
            }
        }
        com.udyam.digital.order.Delay d = com.udyam.digital.order.Delay.of(o, today);
        if (d.escalationPoints() > 0) {
            c.appEscalationPoints += d.escalationPoints();
            c.appEscalationReason = c.appEscalationReason.isEmpty() ? com.udyam.digital.order.Delay.ESCALATION_REASON
                    : c.appEscalationReason + "; " + com.udyam.digital.order.Delay.ESCALATION_REASON;
        }
    }

    private static Segment segment(BusinessProfile p) {
        switch (p.getSegment()) {
            case PROF: return Segment.PROFESSIONAL;
            case MULTI: return Segment.MULTI_SITE;
            case MFG: return Segment.MANUFACTURER;
            default: return Segment.MICRO;
        }
    }

    private static EnumSet<Need> needs(BusinessProfile p) {
        EnumSet<Need> n = EnumSet.noneOf(Need.class);
        boolean broadband = com.udyam.digital.engine.RulesEngine.wantsBroadband(p);
        boolean sims = com.udyam.digital.engine.RulesEngine.wantsMobile(p);
        if (broadband) n.add(Need.INTERNET);
        if (sims) n.add(Need.SIMS);
        if (sims && !broadband) n.add(Need.MOBILE_ONLY);
        for (com.udyam.digital.model.Need need : p.getNeeds()) {
            switch (need) {
                case FAST_INTERNET: n.add(Need.FAST_INTERNET); break;
                case DEDICATED_LINE: n.add(Need.LEASED_LINE); break;
                case BACKUP_INTERNET: n.add(Need.BACKUP_INTERNET); break;
                case REMOTE_ACCESS: n.add(Need.STATIC_IP); break;
                case MULTI_SITE_NETWORK: n.add(Need.MULTI_SITE_NETWORK); break;
                case BILLING_POS: n.add(Need.POS); break;
                case CCTV: n.add(Need.CCTV); break;
                case GUEST_WIFI: n.add(Need.GUEST_WIFI); break;
                case WHATSAPP: n.add(Need.WHATSAPP); break;
                case BULK_SMS: n.add(Need.SMS); break;
                case TOLL_FREE: n.add(Need.TOLL_FREE); break;
                case CLOUD_PHONE: n.add(Need.TELEPHONY); break;
                case EMAIL_OFFICE: n.add(Need.EMAIL); break;
                case DATA_BACKUP: n.add(Need.CLOUD_BACKUP); break;
                case CYBER_SECURITY: n.add(Need.SECURITY); break;
                case WEBSITE: n.add(Need.WEBSITE); break;
                case MACHINE_MONITORING: n.add(Need.MACHINE_IOT); break;
                case FLEET_TRACKING: n.add(Need.FLEET); break;
                default: break;
            }
        }
        return n;
    }

    /** KYC check code -> the help bot's KYC issue. Codes only; no document data. */
    static HelpContext.KycCheck map(CheckResult r) {
        switch (r.code) {
            case "C02":
                // By message key, not by text: works whatever language the owner uses.
                if ("kyc_c02_cancelled".equals(r.msg.key)) return HelpContext.KycCheck.GSTIN_CANCELLED;
                if ("kyc_c02_suspended".equals(r.msg.key)) return HelpContext.KycCheck.GSTIN_SUSPENDED;
                return HelpContext.KycCheck.GSTIN_NOT_FOUND;
            case "C04": case "C07": case "C09": case "C15": return HelpContext.KycCheck.NAME_MISMATCH;
            case "C13": return HelpContext.KycCheck.ADDRESS_PROOF_OLD;
            case "C05": case "C06": return HelpContext.KycCheck.UDYAM_MISSING;
            case "C11": case "C19": return HelpContext.KycCheck.SIGNATORY_ID_UNREADABLE;
            default: return null;
        }
    }
}
