package com.udyam.digital.engine;

import com.udyam.digital.model.Need;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Need -> add-on products, and how much each add-on matters when the plan
 * is over budget (higher priority = kept longer).
 *
 * BROADBAND, MOBILE_SIM, FAST_INTERNET and DEDICATED_LINE map to no add-on: they decide
 * whether the plan has a primary link / mobile lines, and which ones.
 */
public final class NeedMap {
    private NeedMap() {}

    private static final Map<Need, List<String>> MAP = new EnumMap<>(Need.class);
    private static final Map<String, Integer> PRIORITY = new HashMap<>();

    static {
        MAP.put(Need.BROADBAND, Collections.<String>emptyList());
        MAP.put(Need.MOBILE_SIM, Collections.<String>emptyList());
        MAP.put(Need.FAST_INTERNET, Collections.<String>emptyList());
        MAP.put(Need.DEDICATED_LINE, Collections.<String>emptyList());
        MAP.put(Need.BACKUP_INTERNET, list("P09"));
        MAP.put(Need.MULTI_SITE_NETWORK, list("P10"));
        MAP.put(Need.REMOTE_ACCESS, list("P11"));
        MAP.put(Need.CLOUD_PHONE, list("P12"));
        MAP.put(Need.TOLL_FREE, list("P13"));
        MAP.put(Need.BULK_SMS, list("P14"));
        MAP.put(Need.WHATSAPP, list("P15"));
        MAP.put(Need.EMAIL_OFFICE, list("P16"));
        MAP.put(Need.DATA_BACKUP, list("P17"));
        MAP.put(Need.WEBSITE, list("P18"));
        MAP.put(Need.BILLING_POS, list("P19"));
        MAP.put(Need.CYBER_SECURITY, list("P20", "P21"));
        MAP.put(Need.GUEST_WIFI, list("P22"));
        MAP.put(Need.CCTV, list("P23"));
        MAP.put(Need.MACHINE_MONITORING, list("P24"));
        MAP.put(Need.FLEET_TRACKING, list("P25"));

        // Business-critical first; nice-to-haves are trimmed first when over budget.
        PRIORITY.put("P19", 100); // billing / payments
        PRIORITY.put("P10", 95);  // SD-WAN for chains
        PRIORITY.put("P09", 90);  // failover
        PRIORITY.put("P21", 85);  // firewall
        PRIORITY.put("P20", 80);  // endpoint security
        PRIORITY.put("P23", 75);  // CCTV
        PRIORITY.put("P24", 70);  // machine monitoring
        PRIORITY.put("P12", 65);  // cloud telephony
        PRIORITY.put("P15", 60);  // WhatsApp API
        PRIORITY.put("P16", 55);  // email & office
        PRIORITY.put("P25", 50);  // fleet
        PRIORITY.put("P11", 48);  // static IP
        PRIORITY.put("P17", 45);  // cloud backup
        PRIORITY.put("P22", 40);  // guest Wi-Fi
        PRIORITY.put("P18", 35);  // website
        PRIORITY.put("P14", 30);  // SMS
        PRIORITY.put("P13", 25);  // toll-free
    }

    private static List<String> list(String... ids) {
        return Collections.unmodifiableList(Arrays.asList(ids));
    }

    public static List<String> productsFor(Need need) {
        List<String> l = MAP.get(need);
        return l == null ? Collections.<String>emptyList() : l;
    }

    /**
     * Products whose learned score represents this need when ordering the needs screen.
     * Needs that only steer the main link have no learned score and return an empty list.
     */
    public static List<String> rankingProductsFor(Need need) {
        return productsFor(need);
    }

    public static int priority(String productId) {
        Integer p = PRIORITY.get(productId);
        return p == null ? 0 : p;
    }
}
