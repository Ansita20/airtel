package com.udyam.digital.engine;

import com.udyam.digital.i18n.I18n;
import com.udyam.digital.model.Language;

/**
 * Customer-facing reason strings produced by the engine, in the customer's language. The text
 * lives in res/values-&lt;lang&gt;/strings_plan.xml (keys "plan_..."); this class only maps the
 * engine's keys ("why.P07", "unit.per site") onto them.
 */
public final class Texts {

    private final Language lang;

    private Texts(Language lang) { this.lang = lang; }

    public static Texts of(Language lang) { return new Texts(lang == null ? Language.EN : lang); }

    static String res(String key) { return "plan_" + key.replaceAll("[^A-Za-z0-9]+", "_"); }

    public String get(String key) { return has(key) ? I18n.get(lang, res(key)) : key; }

    public String fmt(String key, Object... args) { return I18n.get(lang, res(key), args); }

    public boolean has(String key) { return I18n.has(res(key)); }

    /** Why line for an add-on or mobile product; quantity is substituted when the text uses it. */
    public String why(String productId, int quantity) {
        String k = "why." + productId;
        return I18n.pattern(res(k), lang).contains("$d") ? fmt(k, quantity) : get(k);
    }

    public String unit(String unit) {
        String key = "unit." + unit;
        return has(key) ? get(key) : unit;
    }
}
