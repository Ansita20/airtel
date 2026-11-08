package com.udyam.digital.partner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The partner's lead list: tabs, the city rule, filters and sorting. Pure Java.
 *
 * City rule: a channel partner only ever sees leads in their own city (chosen at login); Accel
 * field sales (partnerCity == null) see every city and may filter by one.
 */
public final class LeadBook {

    public enum Sort { SCORE, PLAN_VALUE, WAITING }

    private final List<PartnerLead> all;
    /** Null = Accel field sales (all cities). */
    private final String partnerCity;

    public LeadBook(List<PartnerLead> leads, String partnerCity) {
        this.all = leads;
        this.partnerCity = partnerCity;
    }

    public String partnerCity() { return partnerCity; }

    /** Every lead this user may see at all (the city rule), whatever the tab or filter. */
    public List<PartnerLead> visible() {
        List<PartnerLead> out = new ArrayList<>();
        for (PartnerLead l : all) if (partnerCity == null || partnerCity.equalsIgnoreCase(l.city)) out.add(l);
        return out;
    }

    /**
     * @param city     filter (ignored for a partner: they are locked to their city); null = all
     * @param language filter; null = all
     */
    public List<PartnerLead> list(PartnerLead.Tab tab, String city, String language, Sort sort, LocalDate today) {
        List<PartnerLead> out = new ArrayList<>();
        for (PartnerLead l : visible()) {
            if (l.tab != tab || l.status == PartnerLead.Status.DONE) continue;
            if (partnerCity == null && city != null && !city.equalsIgnoreCase(l.city)) continue;
            if (language != null && !language.equalsIgnoreCase(l.language)) continue;
            out.add(l);
        }
        Comparator<PartnerLead> byScore = Comparator.comparingInt(l -> l.score == null ? -1 : l.score.score);
        Comparator<PartnerLead> c;
        switch (sort) {
            case PLAN_VALUE: c = Comparator.<PartnerLead>comparingInt(l -> l.planValueInr).reversed().thenComparing(byScore.reversed()); break;
            case WAITING: c = Comparator.<PartnerLead>comparingInt(l -> l.waitingDays(today)).reversed().thenComparing(byScore.reversed()); break;
            default: c = byScore.reversed().thenComparing(Comparator.<PartnerLead>comparingInt(l -> l.planValueInr).reversed());
        }
        // Live leads from this phone always come first: they are happening now.
        out.sort(Comparator.<PartnerLead, Boolean>comparing(l -> !l.live).thenComparing(c));
        return out;
    }

    public int count(PartnerLead.Tab tab) {
        int n = 0;
        for (PartnerLead l : visible()) if (l.tab == tab && l.status != PartnerLead.Status.DONE) n++;
        return n;
    }

    public List<String> cities() {
        TreeSet<String> s = new TreeSet<>();
        for (PartnerLead l : visible()) s.add(l.city);
        return new ArrayList<>(s);
    }

    public List<String> languages() {
        TreeSet<String> s = new TreeSet<>();
        for (PartnerLead l : visible()) s.add(l.language);
        return new ArrayList<>(s);
    }

    public static List<String> allCities(List<PartnerLead> leads) {
        TreeSet<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PartnerLead l : leads) if (l.city != null && !l.city.isEmpty()) s.add(l.city);
        return new ArrayList<>(s);
    }

    /** Demo partner firms per city, for "Assign to partner in that city". */
    public static List<String> partnersIn(String city) {
        String c = city == null || city.isEmpty() ? "Local" : city;
        List<String> p = new ArrayList<>();
        p.add(c + " Telecom Partners");
        p.add("Shree " + c + " Digital Services");
        p.add(c.toUpperCase(Locale.ROOT).charAt(0) + "K Business Connect");
        return p;
    }
}
