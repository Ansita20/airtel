package com.udyam.digital.data;

import com.udyam.digital.model.Language;
import com.udyam.digital.model.Question;
import com.udyam.digital.model.Question.Field;
import com.udyam.digital.model.Question.Option;
import com.udyam.digital.model.Question.Type;
import com.udyam.digital.model.Tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The 9 onboarding questions, in order.
 *
 * Text is kept to 1–3 words so shop owners who read slowly can follow it; the
 * emoji on each tile carries most of the meaning, and the app can read the
 * question aloud. Translation order everywhere: en, hi, mr, gu, pa, bn, ta, te, kn, ml.
 */
public final class QuestionBank {
    private QuestionBank() {}

    /** Cities to show first for each language (where most of its speakers in smb_leads.csv are). */
    private static final Map<Language, List<String>> HOME_CITIES = new EnumMap<>(Language.class);
    static {
        HOME_CITIES.put(Language.HI, Arrays.asList("Delhi", "Lucknow", "Patna", "Jaipur", "Bhopal", "Indore"));
        HOME_CITIES.put(Language.MR, Arrays.asList("Mumbai", "Pune", "Nagpur", "Nashik"));
        HOME_CITIES.put(Language.GU, Arrays.asList("Ahmedabad", "Surat", "Vadodara"));
        HOME_CITIES.put(Language.PA, Arrays.asList("Ludhiana"));
        HOME_CITIES.put(Language.BN, Arrays.asList("Kolkata"));
        HOME_CITIES.put(Language.TA, Arrays.asList("Chennai", "Coimbatore"));
        HOME_CITIES.put(Language.TE, Arrays.asList("Hyderabad", "Visakhapatnam"));
        HOME_CITIES.put(Language.KN, Arrays.asList("Bengaluru", "Mysuru"));
        HOME_CITIES.put(Language.ML, Arrays.asList("Kochi"));
    }

    /** An answer tile whose label is the string resource {@code key} (strings_questions.xml). */
    private static Option opt(String value, String icon, String key) {
        return new Option(value, icon, Tr.res(key));
    }

    private static Option same(String value, String icon, String label) {
        return new Option(value, icon, Tr.same(label));
    }

    /** Questions with the city list ordered for this language. */
    public static List<Question> build(Language lang) {
        List<Question> q = new ArrayList<>();

        // 1. What kind of business. Values are "SEGMENT:business type" with the type spelled as in
        // smb_leads.csv, so learned scores can use it; "*" tiles cover several types (segment only).
        q.add(new Question("segment", Field.SEGMENT, Type.SINGLE,
                Tr.res("q_segment_title"),
                null,
                Arrays.asList(
                        opt("MICRO:Kirana store", "🛒", "opt_MICRO_Kirana_store"),
                        opt("MICRO:Pharmacy", "💊", "opt_MICRO_Pharmacy"),
                        opt("MICRO:Salon", "💇", "opt_MICRO_Salon"),
                        opt("MICRO:Bakery", "🍞", "opt_MICRO_Bakery"),
                        opt("MICRO:Sweet shop", "🍬", "opt_MICRO_Sweet_shop"),
                        opt("MICRO:Mobile repair shop", "📱", "opt_MICRO_Mobile_repair_shop"),
                        opt("MICRO:Boutique", "👗", "opt_MICRO_Boutique"),
                        opt("MICRO:Hardware store", "🔧", "opt_MICRO_Hardware_store"),
                        opt("MICRO:Stationery shop", "✏️", "opt_MICRO_Stationery_shop"),
                        opt("MICRO:Optician", "👓", "opt_MICRO_Optician"),
                        opt("PROF:*clinic", "🩺", "opt_PROF_clinic"),
                        opt("PROF:*office", "💼", "opt_PROF_office"),
                        opt("PROF:Coaching centre", "📚", "opt_PROF_Coaching_centre"),
                        opt("MULTI:*chain", "🏬", "opt_MULTI_chain"),
                        opt("MFG:*factory", "🏭", "opt_MFG_factory"),
                        opt("MICRO:*other", "🏪", "opt_MICRO_other"))));

        // 2. Core services. Many businesses need more than one (e.g. SIMs for staff AND
        // broadband at the shop), so this is multi-select and needs at least one pick.
        q.add(new Question("services", Field.NEEDS, Type.MULTI,
                Tr.res("q_services_title"),
                Tr.res("q_services_sub"),
                Arrays.asList(
                        opt("MOBILE_SIM", "📱", "opt_MOBILE_SIM"),
                        opt("BROADBAND", "🌐", "opt_BROADBAND")))
                .withMinPicks(1));

        // 3. City — the user's own region first.
        List<String> order = new ArrayList<>();
        List<String> home = HOME_CITIES.get(lang);
        if (home != null) order.addAll(home);
        for (String c : CityTiers.all()) if (!order.contains(c)) order.add(c);
        List<Option> cities = new ArrayList<>();
        for (String c : order) cities.add(new Option(c, "📍", CityNames.of(c)));
        cities.add(opt("Other", "🗺️", "opt_Other"));
        q.add(new Question("city", Field.CITY, Type.SINGLE,
                Tr.res("q_city_title"),
                null, cities));

        // 4. Employees. Values are the lower bound of each range, aligned with the catalogue's
        // min_employees thresholds (3, 5, 10, 25) so eligibility is exact.
        q.add(new Question("employees", Field.EMPLOYEES, Type.SINGLE,
                Tr.res("q_employees_title"),
                Tr.res("q_employees_sub"),
                Arrays.asList(
                        same("2", "🙋", "1–2"),
                        same("3", "👥", "3–4"),
                        same("5", "👨‍👩‍👧‍👦", "5–9"),
                        same("10", "🏢", "10–24"),
                        same("25", "🏬", "25–49"),
                        same("50", "🏙️", "50+"))));

        // 5. Sites.
        q.add(new Question("sites", Field.SITES, Type.SINGLE,
                Tr.res("q_sites_title"),
                null,
                Arrays.asList(
                        same("1", "🏪", "1"),
                        same("2", "🏪🏪", "2"),
                        same("3", "🏬", "3–4"),
                        same("5", "🏙️", "5–9"),
                        same("10", "🌐", "10+"))));

        // 6. GSTIN.
        q.add(new Question("gstin", Field.GSTIN, Type.GSTIN,
                Tr.res("q_gstin_title"),
                Tr.res("q_gstin_sub"),
                new ArrayList<>()));

        // 7. Current internet (skipped when the business only wants SIMs).
        q.add(new Question("internet", Field.CURRENT_INTERNET, Type.SINGLE,
                Tr.res("q_internet_title"),
                null,
                Arrays.asList(
                        opt("MOBILE_HOTSPOT", "📶", "opt_MOBILE_HOTSPOT"),
                        opt("HOME_BROADBAND", "🏠", "opt_HOME_BROADBAND"),
                        opt("BUSINESS_BROADBAND", "🏢", "opt_BUSINESS_BROADBAND"),
                        opt("LEASED_LINE", "🔗", "opt_LEASED_LINE"),
                        opt("MIXED", "🔀", "opt_MIXED"),
                        opt("NONE", "🚫", "opt_NONE"))));

        // 8. Extras (multi-select, optional).
        q.add(new Question("needs", Field.NEEDS, Type.MULTI,
                Tr.res("q_needs_title"),
                Tr.res("q_needs_sub"),
                Arrays.asList(
                        opt("BILLING_POS", "🧾", "opt_BILLING_POS"),
                        opt("WHATSAPP", "💬", "opt_WHATSAPP"),
                        opt("CCTV", "📹", "opt_CCTV"),
                        opt("BACKUP_INTERNET", "🔁", "opt_BACKUP_INTERNET"),
                        opt("FAST_INTERNET", "🚀", "opt_FAST_INTERNET"),
                        opt("DEDICATED_LINE", "🔒", "opt_DEDICATED_LINE"),
                        opt("GUEST_WIFI", "📡", "opt_GUEST_WIFI"),
                        opt("WEBSITE", "🌍", "opt_WEBSITE"),
                        opt("BULK_SMS", "✉️", "opt_BULK_SMS"),
                        opt("EMAIL_OFFICE", "📧", "opt_EMAIL_OFFICE"),
                        opt("CLOUD_PHONE", "☎️", "opt_CLOUD_PHONE"),
                        opt("TOLL_FREE", "📞", "opt_TOLL_FREE"),
                        opt("CYBER_SECURITY", "🛡️", "opt_CYBER_SECURITY"),
                        opt("DATA_BACKUP", "💾", "opt_DATA_BACKUP"),
                        opt("REMOTE_ACCESS", "🌐", "opt_REMOTE_ACCESS"),
                        opt("MULTI_SITE_NETWORK", "🕸️", "opt_MULTI_SITE_NETWORK"),
                        opt("MACHINE_MONITORING", "⚙️", "opt_MACHINE_MONITORING"),
                        opt("FLEET_TRACKING", "🚚", "opt_FLEET_TRACKING"))));

        // 9. Monthly budget.
        q.add(new Question("budget", Field.BUDGET, Type.SINGLE,
                Tr.res("q_budget_title"),
                null,
                Arrays.asList(
                        same("2000", "💰", "₹2,000"),
                        same("5000", "💰", "₹5,000"),
                        same("10000", "💰", "₹10,000"),
                        same("25000", "💰", "₹25,000"),
                        same("50000", "💰", "₹50,000"),
                        opt("0", "✨", "opt_0"))));
        return q;
    }
}
