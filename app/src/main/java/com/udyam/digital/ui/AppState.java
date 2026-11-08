package com.udyam.digital.ui;

import androidx.appcompat.app.AppCompatDelegate;

import com.udyam.digital.data.GstinLookup;
import com.udyam.digital.engine.Pack;

import java.util.List;
import com.udyam.digital.model.BusinessProfile;
import com.udyam.digital.model.Language;

import java.util.HashMap;
import java.util.Map;

/** In-memory session state shared by the screens. */
public final class AppState {
    private static final AppState INSTANCE = new AppState();

    private BusinessProfile profile = new BusinessProfile();
    /** question id -> chosen option value, used to highlight previous answers when going back. */
    private final Map<String, String> answers = new HashMap<>();
    private GstinLookup.Result gstinResult;
    /** "Your plan" first, then the other packs. */
    private List<Pack> packs;

    private AppState() {}

    public static AppState get() { return INSTANCE; }

    public void reset(Language language) {
        profile = new BusinessProfile().setLanguage(language);
        answers.clear();
        gstinResult = null;
        packs = null;
    }

    public BusinessProfile profile() { return profile; }
    public Map<String, String> answers() { return answers; }

    public GstinLookup.Result getGstinResult() { return gstinResult; }
    public void setGstinResult(GstinLookup.Result r) { gstinResult = r; }

    public List<Pack> getPacks() { return packs; }

    /** Index of the pack the owner selected on the plan screen (0 = Your plan). */
    private int selectedPack;
    public int selectedPack() { return selectedPack; }
    public void setSelectedPack(int i) { selectedPack = i; }
    public void setPacks(List<Pack> p) { packs = p; }

    /** Current UI language, from the per-app locale (survives process death). */
    public static Language language() {
        return Language.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags());
    }
}
