package com.udyam.digital.voice;

import com.udyam.digital.data.QuestionBank;
import com.udyam.digital.model.Language;
import com.udyam.digital.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.udyam.digital.model.Language.*;
import static org.junit.Assert.*;

/**
 * At least 10 spoken answers per language: numbers, yes/no, business types, cities, and mixed
 * speech. Expected: an option value, "A+B" for a multi-select, YES / NO, or NOTHING.
 */
public class VoiceAnswerParserTest {

    /** {language, question id ("yesno" for confirmations), what the recogniser heard, expected}. */
    private static final Object[][] CASES = {
            // English
            {EN, "segment", "I run a small salon", "MICRO:Salon"},
            {EN, "segment", "we have a kirana store", "MICRO:Kirana store"},
            {EN, "employees", "three people", "3"},
            {EN, "employees", "we are 12 of us", "10"},
            {EN, "sites", "just one shop", "1"},
            {EN, "budget", "around five thousand rupees", "5000"},
            {EN, "budget", "two thousand five hundred", "2000"},
            {EN, "city", "Bangalore", "Bengaluru"},
            {EN, "yesno", "yes that's right", "YES"},
            {EN, "yesno", "no that's wrong", "NO"},
            {EN, "services", "SIM and broadband", "MOBILE_SIM+BROADBAND"},
            {EN, "needs", "nothing else", "NOTHING"},
            {EN, "internet", "we use mobile data", "MOBILE_HOTSPOT"},
            // Hindi
            {HI, "employees", "मेरे पास 3 employees हैं", "3"},
            {HI, "employees", "तीन लोग", "3"},
            {HI, "employees", "हम पच्चीस लोग हैं", "25"},
            {HI, "segment", "मेरी ब्यूटी पार्लर की दुकान है", "MICRO:Salon"},
            {HI, "segment", "किराने की दुकान", "MICRO:Kirana store"},
            {HI, "segment", "salon chalata hoon", "MICRO:Salon"},
            {HI, "segment", "दवाई की दुकान है", "MICRO:Pharmacy"},
            {HI, "budget", "पाँच हज़ार", "5000"},
            {HI, "budget", "ढाई हज़ार तक", "2000"},
            {HI, "city", "लखनऊ", "Lucknow"},
            {HI, "yesno", "हाँ जी", "YES"},
            {HI, "yesno", "नहीं, गलत है", "NO"},
            {HI, "services", "सिम और इंटरनेट दोनों", "MOBILE_SIM+BROADBAND"},
            {HI, "needs", "कुछ नहीं चाहिए", "NOTHING"},
            {HI, "internet", "नहीं", "NONE"},
            // Marathi
            {MR, "segment", "आमचे सलून आहे", "MICRO:Salon"},
            {MR, "segment", "किराणा दुकान", "MICRO:Kirana store"},
            {MR, "segment", "मेडिकल", "MICRO:Pharmacy"},
            {MR, "employees", "चार लोक", "3"},
            {MR, "employees", "पंधरा", "10"},
            {MR, "sites", "दोन शाखा", "2"},
            {MR, "budget", "पाच हजार", "5000"},
            {MR, "city", "पुणे", "Pune"},
            {MR, "city", "नागपूर", "Nagpur"},
            {MR, "yesno", "होय", "YES"},
            {MR, "yesno", "नाही", "NO"},
            {MR, "needs", "काही नाही", "NOTHING"},
            // Gujarati
            {GU, "segment", "અમારી કરિયાણાની દુકાન છે", "MICRO:Kirana store"},
            {GU, "segment", "બ્યુટી પાર્લર", "MICRO:Salon"},
            {GU, "segment", "મેડિકલ સ્ટોર", "MICRO:Pharmacy"},
            {GU, "employees", "ત્રણ લોકો", "3"},
            {GU, "sites", "બે શાખા", "2"},
            {GU, "budget", "પાંચ હજાર", "5000"},
            {GU, "budget", "દસ હજાર", "10000"},
            {GU, "city", "સુરત", "Surat"},
            {GU, "city", "અમદાવાદ", "Ahmedabad"},
            {GU, "yesno", "હા", "YES"},
            {GU, "yesno", "ના", "NO"},
            // Punjabi
            {PA, "employees", "ਤਿੰਨ ਬੰਦੇ", "3"},
            {PA, "employees", "ਵੀਹ", "10"},
            {PA, "segment", "ਸੈਲੂਨ", "MICRO:Salon"},
            {PA, "segment", "ਕਰਿਆਨੇ ਦੀ ਦੁਕਾਨ", "MICRO:Kirana store"},
            {PA, "segment", "ਮੈਡੀਕਲ ਸਟੋਰ", "MICRO:Pharmacy"},
            {PA, "sites", "ਇੱਕ ਦੁਕਾਨ", "1"},
            {PA, "budget", "ਪੰਜ ਹਜ਼ਾਰ", "5000"},
            {PA, "city", "ਲੁਧਿਆਣਾ", "Ludhiana"},
            {PA, "yesno", "ਹਾਂ ਜੀ", "YES"},
            {PA, "yesno", "ਨਹੀਂ", "NO"},
            {PA, "needs", "ਕੁਝ ਨਹੀਂ", "NOTHING"},
            // Bengali
            {BN, "segment", "আমার মুদির দোকান", "MICRO:Kirana store"},
            {BN, "segment", "পার্লার", "MICRO:Salon"},
            {BN, "segment", "ওষুধের দোকান", "MICRO:Pharmacy"},
            {BN, "employees", "তিনজন", "3"},
            {BN, "employees", "পঁচিশ", "25"},
            {BN, "sites", "দুই", "2"},
            {BN, "budget", "পাঁচ হাজার", "5000"},
            {BN, "city", "কলকাতা", "Kolkata"},
            {BN, "city", "Calcutta", "Kolkata"},
            {BN, "yesno", "হ্যাঁ", "YES"},
            {BN, "yesno", "না", "NO"},
            {BN, "needs", "কিছু না", "NOTHING"},
            // Tamil
            {TA, "segment", "naan oru salon vachirukken", "MICRO:Salon"},
            {TA, "segment", "சலூன் வச்சிருக்கேன்", "MICRO:Salon"},
            {TA, "segment", "மளிகை கடை", "MICRO:Kirana store"},
            {TA, "employees", "மூணு பேர்", "3"},
            {TA, "employees", "மூன்று", "3"},
            {TA, "employees", "இருபத்தி ஐந்து பேர்", "25"},
            {TA, "budget", "ஐயாயிரம்", "5000"},
            {TA, "city", "சென்னை", "Chennai"},
            {TA, "city", "madras", "Chennai"},
            {TA, "city", "கோவை", "Coimbatore"},
            {TA, "yesno", "ஆமாம்", "YES"},
            {TA, "yesno", "இல்லை", "NO"},
            {TA, "needs", "எதுவும் வேண்டாம்", "NOTHING"},
            // Telugu
            {TE, "employees", "మూడు", "3"},
            {TE, "employees", "ఇరవై ఐదు", "25"},
            {TE, "segment", "సెలూన్ ఉంది", "MICRO:Salon"},
            {TE, "segment", "కిరాణా కొట్టు", "MICRO:Kirana store"},
            {TE, "segment", "మెడికల్ షాప్", "MICRO:Pharmacy"},
            {TE, "sites", "రెండు", "2"},
            {TE, "budget", "ఐదు వేలు", "5000"},
            {TE, "city", "హైదరాబాద్", "Hyderabad"},
            {TE, "city", "vizag", "Visakhapatnam"},
            {TE, "yesno", "అవును", "YES"},
            {TE, "yesno", "కాదు", "NO"},
            {TE, "needs", "ఏమీ వద్దు", "NOTHING"},
            // Kannada
            {KN, "employees", "ಮೂರು", "3"},
            {KN, "employees", "ಇಪ್ಪತ್ತೈದು", "25"},
            {KN, "segment", "ಸಲೂನ್", "MICRO:Salon"},
            {KN, "segment", "ದಿನಸಿ ಅಂಗಡಿ", "MICRO:Kirana store"},
            {KN, "segment", "ಮೆಡಿಕಲ್ ಸ್ಟೋರ್", "MICRO:Pharmacy"},
            {KN, "sites", "ಎರಡು", "2"},
            {KN, "budget", "ಐದು ಸಾವಿರ", "5000"},
            {KN, "city", "ಬೆಂಗಳೂರು", "Bengaluru"},
            {KN, "city", "Bangalore", "Bengaluru"},
            {KN, "city", "ಮೈಸೂರು", "Mysuru"},
            {KN, "yesno", "ಹೌದು", "YES"},
            {KN, "yesno", "ಇಲ್ಲ", "NO"},
            {KN, "needs", "ಏನೂ ಬೇಡ", "NOTHING"},
            // Malayalam
            {ML, "employees", "മൂന്ന്", "3"},
            {ML, "employees", "ഇരുപത്തിയഞ്ച്", "25"},
            {ML, "segment", "സലൂൺ", "MICRO:Salon"},
            {ML, "segment", "പലചരക്ക് കട", "MICRO:Kirana store"},
            {ML, "segment", "മെഡിക്കൽ ഷോപ്പ്", "MICRO:Pharmacy"},
            {ML, "sites", "രണ്ട്", "2"},
            {ML, "budget", "അയ്യായിരം", "5000"},
            {ML, "city", "കൊച്ചി", "Kochi"},
            {ML, "city", "Cochin", "Kochi"},
            {ML, "yesno", "അതെ", "YES"},
            {ML, "yesno", "അല്ല", "NO"},
            {ML, "needs", "ഒന്നും വേണ്ട", "NOTHING"},
    };

    private static final Map<Language, List<Question>> BANK = new EnumMap<>(Language.class);

    static Question q(Language l, String id) {
        List<Question> qs = BANK.computeIfAbsent(l, QuestionBank::build);
        for (Question q : qs) if (q.getId().equals(id)) return q;
        throw new IllegalArgumentException(id);
    }

    private static String outcome(Language l, String qid, String heard) {
        List<String> alts = Collections.singletonList(heard);
        if (qid.equals("yesno")) return VoiceAnswerParser.yesNo(alts, l).name();
        VoiceAnswerParser.Result r = VoiceAnswerParser.parse(q(l, qid), alts, null, l);
        if (r.nothing) return "NOTHING";
        if (q(l, qid).getType() == Question.Type.MULTI) {
            List<String> v = new ArrayList<>(r.values);
            Collections.sort(v, (a, b) -> Integer.compare(q(l, qid).getOptions().indexOf(opt(l, qid, a)), q(l, qid).getOptions().indexOf(opt(l, qid, b))));
            return String.join("+", v);
        }
        return r.values.isEmpty() ? "(nothing understood)" : r.values.get(0) + (r.confident() ? "" : " (not confident: " + r + ")");
    }

    private static Question.Option opt(Language l, String qid, String value) {
        for (Question.Option o : q(l, qid).getOptions()) if (o.getValue().equals(value)) return o;
        return null;
    }

    @Test
    public void everyLanguageUnderstandsItsSamples() {
        List<String> failures = new ArrayList<>();
        Map<Language, Integer> perLanguage = new EnumMap<>(Language.class);
        for (Object[] c : CASES) {
            Language l = (Language) c[0];
            perLanguage.merge(l, 1, Integer::sum);
            String got = outcome(l, (String) c[1], (String) c[2]);
            if (!got.equals(c[3])) failures.add(l + " " + c[1] + " \"" + c[2] + "\": expected " + c[3] + ", got " + got);
        }
        for (Language l : Language.values()) {
            assertTrue(l + " needs at least 10 samples, has " + perLanguage.get(l), perLanguage.getOrDefault(l, 0) >= 10);
        }
        assertTrue(failures.size() + " of " + CASES.length + " failed:\n" + String.join("\n", failures), failures.isEmpty());
    }

    // ------------------------------------------------------------------ re-ask path
    @Test
    public void lowConfidenceAsksAgainWithTheTwoBest() {
        // The recogniser wasn't sure: "3" (0.55) or "13" (0.40).
        VoiceAnswerParser.Result r = VoiceAnswerParser.parse(q(EN, "employees"), Arrays.asList("3", "13"),
                new float[] {0.55f, 0.40f}, EN);
        assertFalse("must not move on", r.confident());
        assertTrue(r.confidence < VoiceAnswerParser.CONFIDENT);
        assertEquals("3", r.best().value);
        assertEquals("10", r.second().value);
        assertEquals("3", r.best().heard);
        assertEquals("13", r.second().heard); // "Sorry, I didn't catch that. Was it 3 or 13?"

        // The answer to the follow-up is matched against those two only.
        VoiceAnswerParser.Result again = VoiceAnswerParser.parse(q(EN, "employees"), Collections.singletonList("thirteen"),
                null, EN, new HashSet<>(Arrays.asList("3", "10")));
        assertTrue(again.confident());
        assertEquals("10", again.values.get(0));
    }

    @Test
    public void agreeingAlternativesStayConfident() {
        // "3" and "4" are both the 3–4 tile: no doubt about the answer.
        VoiceAnswerParser.Result r = VoiceAnswerParser.parse(q(HI, "employees"), Arrays.asList("तीन", "चार"),
                new float[] {0.6f, 0.5f}, HI);
        assertEquals("3", r.values.get(0));
        assertEquals(1, r.ranked.size());
    }

    @Test
    public void mumblingIsNotUnderstood() {
        VoiceAnswerParser.Result r = VoiceAnswerParser.parse(q(HI, "segment"), Collections.singletonList("हम्म अं"), null, HI);
        assertFalse(r.understood());
        assertFalse(r.confident());
        assertEquals(VoiceAnswerParser.YesNo.UNKNOWN, VoiceAnswerParser.yesNo(Collections.singletonList("शायद"), HI));
    }

    @Test
    public void negationWins() {
        assertEquals(VoiceAnswerParser.YesNo.NO, VoiceAnswerParser.yesNo(Collections.singletonList("सही नहीं है"), HI));
        assertEquals(VoiceAnswerParser.YesNo.NO, VoiceAnswerParser.yesNo(Collections.singletonList("not right"), EN));
    }

    @Test
    public void spokenGstin() {
        assertEquals("27QHJXK0658U1ZL", VoiceAnswerParser.gstin(Collections.singletonList("27 Q H J X K 0 6 5 8 U 1 Z L")));
        assertNull(VoiceAnswerParser.gstin(Collections.singletonList("मेरे पास नहीं है")));
    }
}
