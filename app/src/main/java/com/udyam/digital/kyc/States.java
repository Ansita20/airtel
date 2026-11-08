package com.udyam.digital.kyc;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** GST state codes and Udyam 2-letter codes -> state names. */
public final class States {
    private States() {}

    private static final Map<String, String> GST = new HashMap<>();
    private static final Map<String, String> UDYAM = new HashMap<>();

    static {
        String[][] gst = {
                {"01", "Jammu and Kashmir"}, {"02", "Himachal Pradesh"}, {"03", "Punjab"}, {"04", "Chandigarh"},
                {"05", "Uttarakhand"}, {"06", "Haryana"}, {"07", "Delhi"}, {"08", "Rajasthan"},
                {"09", "Uttar Pradesh"}, {"10", "Bihar"}, {"11", "Sikkim"}, {"12", "Arunachal Pradesh"},
                {"13", "Nagaland"}, {"14", "Manipur"}, {"15", "Mizoram"}, {"16", "Tripura"}, {"17", "Meghalaya"},
                {"18", "Assam"}, {"19", "West Bengal"}, {"20", "Jharkhand"}, {"21", "Odisha"},
                {"22", "Chhattisgarh"}, {"23", "Madhya Pradesh"}, {"24", "Gujarat"},
                {"26", "Dadra and Nagar Haveli and Daman and Diu"}, {"27", "Maharashtra"}, {"29", "Karnataka"},
                {"30", "Goa"}, {"31", "Lakshadweep"}, {"32", "Kerala"}, {"33", "Tamil Nadu"}, {"34", "Puducherry"},
                {"35", "Andaman and Nicobar Islands"}, {"36", "Telangana"}, {"37", "Andhra Pradesh"}, {"38", "Ladakh"}};
        for (String[] r : gst) GST.put(r[0], r[1]);
        String[][] ud = {
                {"AP", "Andhra Pradesh"}, {"AR", "Arunachal Pradesh"}, {"AS", "Assam"}, {"BR", "Bihar"},
                {"CG", "Chhattisgarh"}, {"CT", "Chhattisgarh"}, {"DL", "Delhi"}, {"GA", "Goa"}, {"GJ", "Gujarat"},
                {"HR", "Haryana"}, {"HP", "Himachal Pradesh"}, {"JK", "Jammu and Kashmir"}, {"JH", "Jharkhand"},
                {"KA", "Karnataka"}, {"KR", "Karnataka"}, {"KL", "Kerala"}, {"MP", "Madhya Pradesh"},
                {"MH", "Maharashtra"}, {"MN", "Manipur"}, {"ML", "Meghalaya"}, {"MZ", "Mizoram"}, {"NL", "Nagaland"},
                {"OD", "Odisha"}, {"OR", "Odisha"}, {"PB", "Punjab"}, {"RJ", "Rajasthan"}, {"SK", "Sikkim"},
                {"TN", "Tamil Nadu"}, {"TS", "Telangana"}, {"TG", "Telangana"}, {"TR", "Tripura"},
                {"UP", "Uttar Pradesh"}, {"UK", "Uttarakhand"}, {"UT", "Uttarakhand"}, {"WB", "West Bengal"}};
        for (String[] r : ud) UDYAM.put(r[0], r[1]);
    }

    public static String fromGstCode(String code) { return GST.get(code); }

    public static String fromUdyamCode(String code) {
        return code == null ? null : UDYAM.get(code.toUpperCase(Locale.ROOT));
    }

    public static boolean same(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
