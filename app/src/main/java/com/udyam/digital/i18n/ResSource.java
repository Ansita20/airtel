package com.udyam.digital.i18n;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.udyam.digital.model.Language;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Android: strings from res/values-&lt;lang&gt;, for any language regardless of the app's current locale. */
public final class ResSource implements I18n.Source {

    private final Context app;
    private final Map<Language, Resources> res = new EnumMap<>(Language.class);
    private final Map<String, Integer> ids = new HashMap<>();

    public ResSource(Context c) { this.app = c.getApplicationContext(); }

    private synchronized Resources resources(Language l) {
        Resources r = res.get(l);
        if (r == null) {
            Configuration conf = new Configuration(app.getResources().getConfiguration());
            conf.setLocale(I18n.locale(l));
            r = app.createConfigurationContext(conf).getResources();
            res.put(l, r);
        }
        return r;
    }

    @Override public String pattern(String key, Language l) {
        int id;
        synchronized (ids) {
            Integer cached = ids.get(key);
            if (cached == null) {
                cached = app.getResources().getIdentifier(key, "string", app.getPackageName());
                ids.put(key, cached);
            }
            id = cached;
        }
        if (id == 0) return null;
        // getText keeps "%%" as written, like the XML source; I18n.get formats it.
        return resources(l).getString(id);
    }
}
