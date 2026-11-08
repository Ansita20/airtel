package com.udyam.digital.data;

import com.udyam.digital.model.Language;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CityNamesTest {

    @Test
    public void everyKnownCityHasANativeNameInEveryIndianLanguage() {
        for (String c : CityTiers.all()) {
            assertEquals(c, CityNames.of(c).get(Language.EN));
            for (Language l : Language.values()) {
                if (l == Language.EN) continue;
                String n = CityNames.of(c).get(l);
                assertFalse(c + " in " + l + " is still in English letters", n.matches(".*[A-Za-z].*"));
            }
        }
    }

    @Test
    public void registryCitiesAreLocalisedAndUnknownOnesKept() {
        assertEquals("लखनऊ", CityNames.name("lucknow", Language.HI));
        assertEquals("சென்னை", CityNames.name("Chennai", Language.TA));
        assertEquals("Gangtok", CityNames.name("Gangtok", Language.HI));
    }
}
