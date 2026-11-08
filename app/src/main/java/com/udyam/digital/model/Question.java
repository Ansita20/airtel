package com.udyam.digital.model;

import com.udyam.digital.data.CityTiers;

import java.util.Collections;
import java.util.List;

/** One questionnaire screen: short text in each language, answer options, and the profile field it sets. */
public final class Question {

    public enum Type { SINGLE, MULTI, GSTIN }

    public enum Field { SEGMENT, CITY, EMPLOYEES, SITES, GSTIN, CURRENT_INTERNET, NEEDS, BUDGET }

    public static final class Option {
        private final String value;
        private final String icon;
        private final Tr label;

        public Option(String value, String icon, Tr label) {
            this.value = value;
            this.icon = icon;
            this.label = label;
        }

        public String getValue() { return value; }
        public String getIcon() { return icon; }
        public String label(Language l) { return label.get(l); }
    }

    private final String id;
    private final Field field;
    private final Type type;
    private final Tr text;
    private final Tr subtitle;
    private final List<Option> options;
    private int minPicks;

    public Question(String id, Field field, Type type, Tr text, Tr subtitle, List<Option> options) {
        this.id = id;
        this.field = field;
        this.type = type;
        this.text = text;
        this.subtitle = subtitle;
        this.options = Collections.unmodifiableList(options);
    }

    public String getId() { return id; }
    public Field getField() { return field; }
    public Type getType() { return type; }
    public String text(Language l) { return text.get(l); }
    /** May be null. */
    public String subtitle(Language l) { return subtitle == null ? null : subtitle.get(l); }
    public List<Option> getOptions() { return options; }

    /** For MULTI questions: how many options must be picked before Next is enabled. */
    public int getMinPicks() { return minPicks; }
    public Question withMinPicks(int n) { minPicks = n; return this; }

    /** Writes an answer into the profile. For NEEDS, the value toggles that need on or off. */
    public void apply(BusinessProfile p, String value) {
        switch (field) {
            case SEGMENT:
                // Values look like "MICRO:Kirana store": segment, then the business type as named in
                // smb_leads.csv. A type starting with '*' ("PROF:*clinic") is a tile that covers several
                // types, so only the segment is known and learned scores use segment level.
                int colon = value.indexOf(':');
                p.setSegment(Segment.valueOf(colon < 0 ? value : value.substring(0, colon)));
                String type = colon < 0 ? null : value.substring(colon + 1);
                p.setBusinessType(type == null || type.startsWith("*") ? null : type);
                break;
            case CITY:
                p.setCity(value);
                p.setCityTier(CityTiers.tierOf(value));
                break;
            case EMPLOYEES:
                p.setEmployees(Integer.parseInt(value));
                break;
            case SITES:
                p.setSites(Integer.parseInt(value));
                break;
            case GSTIN:
                // Validity is set by the GSTIN lookup; an empty value means "I don't have one".
                if (value == null || value.isEmpty()) {
                    p.setGstin(null);
                    p.setHasValidGstin(false);
                } else {
                    p.setGstin(value);
                }
                break;
            case CURRENT_INTERNET:
                p.setCurrentInternet(InternetType.valueOf(value));
                break;
            case NEEDS:
                Need n = Need.valueOf(value);
                if (p.hasNeed(n)) p.removeNeed(n); else p.addNeed(n);
                break;
            case BUDGET:
                // "0" is the "Best plan" tile: no limit, and the owner wants the fullest plan.
                p.setBudgetInr(Integer.parseInt(value));
                p.setBestPlan("0".equals(value));
                break;
        }
    }
}
