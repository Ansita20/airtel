package com.udyam.digital.help.core;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the advisor questionnaire learned about the business. Public fields keep the demo
 * simple; replace with your existing BusinessProfile when merging.
 */
public final class BusinessProfile {

    public enum Segment { MICRO, PROFESSIONAL, MULTI_SITE, MANUFACTURER }

    public enum GstinStatus { NONE, ACTIVE, CANCELLED, SUSPENDED }

    public enum Need {
        INTERNET, FAST_INTERNET, LEASED_LINE, BACKUP_INTERNET, STATIC_IP, MULTI_SITE_NETWORK,
        SIMS, MOBILE_ONLY, POS, CCTV, GUEST_WIFI, WHATSAPP, SMS, TOLL_FREE, TELEPHONY,
        EMAIL, CLOUD_BACKUP, SECURITY, FIREWALL, WEBSITE, MACHINE_IOT, FLEET
    }

    public String businessType = "";
    public Segment segment = Segment.MICRO;
    public String city = "";
    /** States the sites are in. GST is registered per state. */
    public Set<String> siteStates = new LinkedHashSet<>();
    public int employees = 1;
    public int sites = 1;
    public String gstin;
    public GstinStatus gstinStatus = GstinStatus.NONE;
    /** 0 = no budget given. */
    public int monthlyBudget = 0;
    public Set<Need> needs = EnumSet.of(Need.INTERNET);
    public boolean fibreAvailable = true;
    public String currentInternet = "";
    /** Quantities. 0 = let the engine pick a default. */
    public int sims, users, cameras, counters, devices, vehicles;
    /**
     * Contact name. Never used by the rules engine, the help answers or the escalation
     * score (fairness rule); kept only so tests can prove that.
     */
    public String contactName = "";

    public int cityTier() {
        return CityTiers.tierOf(city);
    }

    public boolean hasActiveGstin() {
        return gstin != null && !gstin.isEmpty() && gstinStatus == GstinStatus.ACTIVE;
    }

    public String primaryState() {
        if (!siteStates.isEmpty()) return siteStates.iterator().next();
        return CityTiers.stateOf(city);
    }

    public BusinessProfile copy() {
        BusinessProfile b = new BusinessProfile();
        b.businessType = businessType;
        b.segment = segment;
        b.city = city;
        b.siteStates = new LinkedHashSet<>(siteStates);
        b.employees = employees;
        b.sites = sites;
        b.gstin = gstin;
        b.gstinStatus = gstinStatus;
        b.monthlyBudget = monthlyBudget;
        b.needs = needs.isEmpty() ? EnumSet.noneOf(Need.class) : EnumSet.copyOf(needs);
        b.fibreAvailable = fibreAvailable;
        b.currentInternet = currentInternet;
        b.sims = sims;
        b.users = users;
        b.cameras = cameras;
        b.counters = counters;
        b.devices = devices;
        b.vehicles = vehicles;
        b.contactName = contactName;
        return b;
    }
}
