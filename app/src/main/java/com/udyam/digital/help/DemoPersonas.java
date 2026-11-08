package com.udyam.digital.help;

import com.udyam.digital.help.core.BusinessProfile;
import com.udyam.digital.help.core.BusinessProfile.GstinStatus;
import com.udyam.digital.help.core.BusinessProfile.Need;
import com.udyam.digital.help.core.BusinessProfile.Segment;
import com.udyam.digital.help.core.RulesEngine;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;

/**
 * Ready-made customers for the demo (switch them from the debug panel). In the real app the
 * HelpContext comes from the advisor, quote, KYC and order screens instead.
 */
public final class DemoPersonas {
    private DemoPersonas() {}

    public static final String[] NAMES = {
            "Kirana, Patna (Hindi, quote)",
            "Dental clinic, Pune (English, KYC)",
            "Restaurant chain, 7 sites (Marathi, quote)",
            "Textile unit, Surat (Gujarati, install late)",
            "Pharmacy, Kochi (Malayalam, active)"
    };

    public static HelpContext build(int index, RulesEngine engine) {
        HelpContext c = new HelpContext();
        BusinessProfile b = new BusinessProfile();
        switch (index) {
            case 1:
                b.segment = Segment.PROFESSIONAL;
                b.businessType = "Dental clinic";
                b.city = "Pune";
                b.employees = 8;
                b.gstinStatus = GstinStatus.ACTIVE;
                b.needs = EnumSet.of(Need.INTERNET, Need.EMAIL, Need.SECURITY, Need.CLOUD_BACKUP, Need.SIMS);
                c.profile = b;
                c.setGstin("27QTBXZ1995X1Z9");
                c.appLanguage = Lang.EN;
                c.stage = JourneyStage.KYC;
                c.kycStatus = HelpContext.KycStatus.IN_PROGRESS;
                c.docsDone = 2;
                c.mismatchField = "Legal name on GST certificate";
                break;
            case 2:
                b.segment = Segment.MULTI_SITE;
                b.businessType = "Restaurant chain";
                b.city = "Mumbai";
                b.employees = 60;
                b.sites = 7;
                b.siteStates = new LinkedHashSet<>(Arrays.asList("Maharashtra", "Gujarat"));
                b.gstinStatus = GstinStatus.ACTIVE;
                b.needs = EnumSet.of(Need.INTERNET, Need.SIMS, Need.POS, Need.CCTV, Need.BACKUP_INTERNET, Need.MULTI_SITE_NETWORK);
                c.profile = b;
                c.setGstin("27QTBXZ1995X1Z9");
                c.appLanguage = Lang.MR;
                c.stage = JourneyStage.QUOTE;
                break;
            case 3:
                b.segment = Segment.MANUFACTURER;
                b.businessType = "Textile unit";
                b.city = "Surat";
                b.employees = 80;
                b.gstinStatus = GstinStatus.ACTIVE;
                b.needs = EnumSet.of(Need.INTERNET, Need.SIMS, Need.EMAIL, Need.MACHINE_IOT);
                c.profile = b;
                c.setGstin("24DTTXB1771D1ZD");
                c.appLanguage = Lang.GU;
                c.stage = JourneyStage.INSTALL;
                c.orderStatus = HelpContext.OrderStatus.VISIT_SCHEDULED;
                c.orderId = "UD-24017";
                c.orderDate = c.today.minusDays(25);
                c.visitSlot = "16 Jul, 10 AM-12 PM";
                break;
            case 4:
                b.segment = Segment.MICRO;
                b.businessType = "Pharmacy";
                b.city = "Kochi";
                b.employees = 4;
                b.gstinStatus = GstinStatus.ACTIVE;
                b.needs = EnumSet.of(Need.INTERNET, Need.SIMS, Need.POS);
                c.profile = b;
                c.setGstin("32TTNXB7384T1ZW");
                c.appLanguage = Lang.ML;
                c.stage = JourneyStage.ACTIVE;
                c.orderStatus = HelpContext.OrderStatus.ACTIVE;
                c.orderId = "UD-31188";
                break;
            default:
                b.segment = Segment.MICRO;
                b.businessType = "Kirana store";
                b.city = "Patna";
                b.employees = 3;
                b.monthlyBudget = 5000;
                b.sims = 2;
                b.currentInternet = "Mobile hotspot";
                b.needs = EnumSet.of(Need.INTERNET, Need.SIMS, Need.POS, Need.CCTV, Need.GUEST_WIFI, Need.WHATSAPP);
                c.profile = b;
                c.appLanguage = Lang.HI;
                c.stage = JourneyStage.QUOTE;
                break;
        }
        c.recommendation = engine.recommend(b);
        if (c.orderDate != null) c.expectedDate = c.orderDate.plusDays(c.recommendation.maxInstallDays());
        return c;
    }
}
