package com.udyam.digital.voice;

import com.udyam.digital.model.Language;

import org.junit.Test;

import static org.junit.Assert.*;

public class NumberWordsTest {

    private static double n(String s, Language l) {
        NumberWords.Found f = NumberWords.parse(s, l);
        assertNotNull("no number in '" + s + "'", f);
        return f.value;
    }

    @Test public void oneToHundredRoundTripsInEveryFullyListedLanguage() {
        for (Language l : new Language[] {Language.EN, Language.HI, Language.MR, Language.GU, Language.PA, Language.BN}) {
            for (int i = 1; i <= 100; i++) assertEquals(l + " " + i + " = " + NumberWords.toWords(i, l), i, n(NumberWords.toWords(i, l), l), 0);
        }
    }

    @Test public void promptExamplesForThree() {
        assertEquals(3, n("तीन", Language.HI), 0);
        assertEquals(3, n("மூன்று", Language.TA), 0);
        assertEquals(3, n("మూడు", Language.TE), 0);
        assertEquals(3, n("ಮೂರು", Language.KN), 0);
        assertEquals(3, n("മൂന്ന്", Language.ML), 0);
        assertEquals(3, n("তিন", Language.BN), 0);
        assertEquals(3, n("ਤਿੰਨ", Language.PA), 0);
        assertEquals(3, n("ત્રણ", Language.GU), 0);
        assertEquals(3, n("३", Language.HI), 0);
        assertEquals(3, n("৩", Language.BN), 0);
    }

    @Test public void dravidianTensAndUnits() {
        assertEquals(25, n("இருபத்தி ஐந்து", Language.TA), 0);
        assertEquals(25, n("ఇరవై ఐదు", Language.TE), 0);
        assertEquals(25, n("ಇಪ್ಪತ್ತೈದು", Language.KN), 0);
        assertEquals(25, n("ഇരുപത്തിയഞ്ച്", Language.ML), 0);
        assertEquals(19, n("பத்தொன்பது", Language.TA), 0);
        assertEquals(90, n("తొంభై", Language.TE), 0);
    }

    @Test public void thousandsLakhsAndMixedSpeech() {
        assertEquals(5000, n("पाँच हज़ार", Language.HI), 0);
        assertEquals(5000, n("5 हजार", Language.HI), 0);
        assertEquals(5000, n("5k", Language.EN), 0);
        assertEquals(5000, n("₹5,000", Language.EN), 0);
        assertEquals(2500, n("ढाई हज़ार", Language.HI), 0);
        assertEquals(2500, n("two thousand five hundred", Language.EN), 0);
        assertEquals(150000, n("डेढ़ लाख", Language.HI), 0);
        assertEquals(120, n("एक सौ बीस", Language.HI), 0);
        assertEquals(5000, n("ஐயாயிரம்", Language.TA), 0);
        assertEquals(5000, n("ಐದು ಸಾವಿರ", Language.KN), 0);
        assertEquals(3, n("मेरे पास 3 employees हैं", Language.HI), 0);
        assertEquals(3, n("teen log", Language.HI), 0);
        assertEquals("first number only", 3, n("3 या 13", Language.HI), 0);
    }

    @Test public void pricesAsWords() {
        assertEquals("तीन हज़ार एक सौ रुपये महीना", NumberWords.rupeesPerMonth(3100, Language.HI));
        assertEquals("three thousand one hundred rupees a month", NumberWords.rupeesPerMonth(3100, Language.EN));
        assertEquals("दरमहा तीन हजार एकशे रुपये", NumberWords.rupeesPerMonth(3100, Language.MR));
        assertEquals("મહિને ત્રણ હજાર એકસો રૂપિયા", NumberWords.rupeesPerMonth(3100, Language.GU));
        assertEquals("ਤਿੰਨ ਹਜ਼ਾਰ ਇੱਕ ਸੌ ਰੁਪਏ ਮਹੀਨਾ", NumberWords.rupeesPerMonth(3100, Language.PA));
        assertEquals("মাসে তিন হাজার একশো টাকা", NumberWords.rupeesPerMonth(3100, Language.BN));
        assertEquals("एक लाख पच्चीस हज़ार रुपये", NumberWords.rupees(125000, Language.HI));
        // Tamil, Telugu, Kannada, Malayalam: digits for the phone's voice, which reads them in the language.
        assertEquals("மாதம் 3100 ரூபாய்", NumberWords.rupeesPerMonth(3100, Language.TA));
        assertEquals("నెలకు 3100 రూపాయలు", NumberWords.rupeesPerMonth(3100, Language.TE));
    }

    @Test public void labelsReadAloud() {
        assertEquals("पांच हज़ार रुपये", VoiceLexicon.spoken("₹5,000", Language.HI));
        assertEquals("3 से 4", VoiceLexicon.spoken("3–4", Language.HI));
        assertEquals("50 or more", VoiceLexicon.spoken("50+", Language.EN));
        assertEquals("SIM, Mobile", VoiceLexicon.spoken("SIM / Mobile", Language.EN));
    }
}
