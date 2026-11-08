package com.udyam.digital.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Everything the questionnaire learns about a business. Setters return this for terse tests. */
public final class BusinessProfile {
    private Segment segment = Segment.MICRO;
    /** Business type as named in smb_leads.csv ("Salon", "CA firm"), or null when only the segment is known. */
    private String businessType;
    private String city;
    private int cityTier = 2;
    private int employees = 1;
    private int sites = 1;
    private String gstin;
    private boolean hasValidGstin;
    private InternetType currentInternet = InternetType.MOBILE_HOTSPOT;
    private final Set<Need> needs = EnumSet.noneOf(Need.class);
    /** Monthly budget in rupees. 0 means no limit. */
    private int budgetInr;
    /** The owner tapped "Best plan" on the budget screen. */
    private boolean bestPlan;
    private Language language = Language.EN;

    public Segment getSegment() { return segment; }
    public BusinessProfile setSegment(Segment segment) { this.segment = segment; return this; }

    public String getBusinessType() { return businessType; }
    public BusinessProfile setBusinessType(String t) {
        this.businessType = t == null || t.isEmpty() ? null : t;
        return this;
    }

    public String getCity() { return city; }
    public BusinessProfile setCity(String city) { this.city = city; return this; }

    public int getCityTier() { return cityTier; }
    public BusinessProfile setCityTier(int cityTier) { this.cityTier = cityTier; return this; }

    public int getEmployees() { return employees; }
    public BusinessProfile setEmployees(int employees) { this.employees = Math.max(1, employees); return this; }

    public int getSites() { return sites; }
    public BusinessProfile setSites(int sites) { this.sites = Math.max(1, sites); return this; }

    public String getGstin() { return gstin; }
    public BusinessProfile setGstin(String gstin) { this.gstin = gstin; return this; }

    /** True only for a GSTIN that passed format + checksum and is Active in the registry. */
    public boolean hasValidGstin() { return hasValidGstin; }
    public BusinessProfile setHasValidGstin(boolean v) { this.hasValidGstin = v; return this; }

    public InternetType getCurrentInternet() { return currentInternet; }
    public BusinessProfile setCurrentInternet(InternetType t) { this.currentInternet = t; return this; }

    public Set<Need> getNeeds() { return Collections.unmodifiableSet(needs); }
    public BusinessProfile addNeed(Need... ns) { Collections.addAll(needs, ns); return this; }
    public BusinessProfile removeNeed(Need n) { needs.remove(n); return this; }
    public boolean hasNeed(Need n) { return needs.contains(n); }

    public int getBudgetInr() { return budgetInr; }
    public BusinessProfile setBudgetInr(int budgetInr) { this.budgetInr = Math.max(0, budgetInr); return this; }

    public boolean wantsBestPlan() { return bestPlan; }
    public BusinessProfile setBestPlan(boolean v) { this.bestPlan = v; return this; }

    public Language getLanguage() { return language; }
    public BusinessProfile setLanguage(Language language) { this.language = language; return this; }
}
