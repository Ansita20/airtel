package com.udyam.digital.voice;

import com.udyam.digital.model.Language;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers in speech, both ways, for all 10 languages. Pure Java.
 *
 * <b>Hearing</b> ({@link #parse}): digits ("3", "5,000", "5k"), Indian-script digits (३, ৩, ௩ ...),
 * number words 1-100 in every language (तीन, तिन्, மூன்று, మూడు, ಮೂರು, മൂന്ന്, তিন, ਤਿੰਨ, ત્રણ),
 * common romanised forms ("teen", "moonu"), and hundred / thousand / lakh ("पाँच हज़ार" = 5000,
 * "ढाई हज़ार" = 2500). Hindi, Marathi, Gujarati, Punjabi and Bengali have a separate word for every
 * number to 100, listed in full; Tamil, Telugu, Kannada and Malayalam build 21-99 from tens +
 * units ("இருபத்தி ஐந்து"), and fused forms ("இருபத்தைந்து") are matched fuzzily.
 *
 * <b>Speaking</b> ({@link #rupees}, {@link #rupeesPerMonth}): prices as words, e.g.
 * "तीन हज़ार एक सौ रुपये महीना". For Tamil, Telugu, Kannada and Malayalam, where hundreds and
 * thousands change form when joined, the digits are given to the phone's voice, which reads
 * them correctly in that language.
 */
public final class NumberWords {
    private NumberWords() {}

    // ------------------------------------------------------------------ 1-100 (Indo-Aryan, irregular)
    private static final Map<Language, String> ONE_TO_HUNDRED = new EnumMap<>(Language.class);
    static {
        ONE_TO_HUNDRED.put(Language.HI,
                "एक दो तीन चार पांच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस बीस "
                + "इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस "
                + "सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास "
                + "इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ "
                + "उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उनासी अस्सी इक्यासी बयासी तिरासी "
                + "चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानवे बानवे तिरानवे चौरानवे पचानवे छियानवे सत्तानवे अट्ठानवे "
                + "निन्यानवे सौ");
        ONE_TO_HUNDRED.put(Language.MR,
                "एक दोन तीन चार पाच सहा सात आठ नऊ दहा अकरा बारा तेरा चौदा पंधरा सोळा सतरा अठरा एकोणीस वीस "
                + "एकवीस बावीस तेवीस चोवीस पंचवीस सव्वीस सत्तावीस अठ्ठावीस एकोणतीस तीस एकतीस बत्तीस तेहेतीस चौतीस पस्तीस छत्तीस "
                + "सदतीस अडतीस एकोणचाळीस चाळीस एक्केचाळीस बेचाळीस त्रेचाळीस चव्वेचाळीस पंचेचाळीस सेहेचाळीस सत्तेचाळीस अठ्ठेचाळीस "
                + "एकोणपन्नास पन्नास एक्कावन्न बावन्न त्रेपन्न चोपन्न पंचावन्न छप्पन्न सत्तावन्न अठ्ठावन्न एकोणसाठ साठ एकसष्ठ बासष्ठ "
                + "त्रेसष्ठ चौसष्ठ पासष्ठ सहासष्ठ सदुसष्ठ अडुसष्ठ एकोणसत्तर सत्तर एक्काहत्तर बाहत्तर त्र्याहत्तर चौऱ्याहत्तर पंच्याहत्तर "
                + "शहात्तर सत्याहत्तर अठ्ठ्याहत्तर एकोणऐंशी ऐंशी एक्क्याऐंशी ब्याऐंशी त्र्याऐंशी चौऱ्याऐंशी पंच्याऐंशी शहाऐंशी "
                + "सत्त्याऐंशी अठ्ठ्याऐंशी एकोणनव्वद नव्वद एक्क्याण्णव ब्याण्णव त्र्याण्णव चौऱ्याण्णव पंच्याण्णव शहाण्णव सत्त्याण्णव "
                + "अठ्ठ्याण्णव नव्व्याण्णव शंभर");
        ONE_TO_HUNDRED.put(Language.GU,
                "એક બે ત્રણ ચાર પાંચ છ સાત આઠ નવ દસ અગિયાર બાર તેર ચૌદ પંદર સોળ સત્તર અઢાર ઓગણીસ વીસ "
                + "એકવીસ બાવીસ તેવીસ ચોવીસ પચ્ચીસ છવ્વીસ સત્તાવીસ અઠ્ઠાવીસ ઓગણત્રીસ ત્રીસ એકત્રીસ બત્રીસ તેત્રીસ ચોત્રીસ પાંત્રીસ "
                + "છત્રીસ સાડત્રીસ આડત્રીસ ઓગણચાલીસ ચાલીસ એકતાલીસ બેતાલીસ તેતાલીસ ચુંમાલીસ પિસ્તાલીસ છેતાલીસ સુડતાલીસ અડતાલીસ "
                + "ઓગણપચાસ પચાસ એકાવન બાવન ત્રેપન ચોપન પંચાવન છપ્પન સત્તાવન અઠ્ઠાવન ઓગણસાઠ સાઠ એકસઠ બાસઠ ત્રેસઠ ચોસઠ પાંસઠ "
                + "છાસઠ સડસઠ અડસઠ અગણોસિત્તેર સિત્તેર એકોતેર બોતેર તોતેર ચુમોતેર પંચોતેર છોતેર સિત્યોતેર ઇઠ્યોતેર ઓગણએંસી એંસી "
                + "એક્યાસી બ્યાસી ત્યાસી ચોર્યાસી પંચાસી છ્યાસી સિત્યાસી ઈઠ્યાસી નેવ્યાસી નેવું એકાણું બાણું ત્રાણું ચોરાણું પંચાણું "
                + "છન્નું સત્તાણું અઠ્ઠાણું નવ્વાણું સો");
        ONE_TO_HUNDRED.put(Language.PA,
                "ਇੱਕ ਦੋ ਤਿੰਨ ਚਾਰ ਪੰਜ ਛੇ ਸੱਤ ਅੱਠ ਨੌਂ ਦਸ ਗਿਆਰਾਂ ਬਾਰਾਂ ਤੇਰਾਂ ਚੌਦਾਂ ਪੰਦਰਾਂ ਸੋਲਾਂ ਸਤਾਰਾਂ ਅਠਾਰਾਂ ਉੱਨੀ ਵੀਹ "
                + "ਇੱਕੀ ਬਾਈ ਤੇਈ ਚੌਵੀ ਪੱਚੀ ਛੱਬੀ ਸਤਾਈ ਅਠਾਈ ਉਨੱਤੀ ਤੀਹ ਇਕੱਤੀ ਬੱਤੀ ਤੇਤੀ ਚੌਂਤੀ ਪੈਂਤੀ ਛੱਤੀ ਸੈਂਤੀ ਅਠੱਤੀ ਉਨਤਾਲੀ ਚਾਲੀ "
                + "ਇਕਤਾਲੀ ਬਤਾਲੀ ਤਿਰਤਾਲੀ ਚੁਤਾਲੀ ਪੰਤਾਲੀ ਛਿਆਲੀ ਸੰਤਾਲੀ ਅਠਤਾਲੀ ਉਣੰਜਾ ਪੰਜਾਹ ਇਕਵੰਜਾ ਬਵੰਜਾ ਤਰਵੰਜਾ ਚਰਵੰਜਾ ਪਚਵੰਜਾ "
                + "ਛਪੰਜਾ ਸਤਵੰਜਾ ਅਠਵੰਜਾ ਉਣਾਹਠ ਸੱਠ ਇਕਾਹਠ ਬਾਹਠ ਤਰੇਹਠ ਚੌਂਹਠ ਪੈਂਹਠ ਛਿਆਹਠ ਸਤਾਹਠ ਅਠਾਹਠ ਉਣੱਤਰ ਸੱਤਰ ਇਕਹੱਤਰ ਬਹੱਤਰ "
                + "ਤਿਹੱਤਰ ਚੌਹੱਤਰ ਪਚੱਤਰ ਛਿਹੱਤਰ ਸਤੱਤਰ ਅਠੱਤਰ ਉਣਾਸੀ ਅੱਸੀ ਇਕਾਸੀ ਬਿਆਸੀ ਤਰਾਸੀ ਚੁਰਾਸੀ ਪਚਾਸੀ ਛਿਆਸੀ ਸਤਾਸੀ ਅਠਾਸੀ "
                + "ਉਣਾਨਵੇਂ ਨੱਬੇ ਇਕਾਨਵੇਂ ਬਾਨਵੇਂ ਤਰਾਨਵੇਂ ਚਰਾਨਵੇਂ ਪਚਾਨਵੇਂ ਛਿਆਨਵੇਂ ਸਤਾਨਵੇਂ ਅਠਾਨਵੇਂ ਨਿੜਿਆਨਵੇਂ ਸੌ");
        ONE_TO_HUNDRED.put(Language.BN,
                "এক দুই তিন চার পাঁচ ছয় সাত আট নয় দশ এগারো বারো তেরো চোদ্দ পনেরো ষোলো সতেরো আঠারো উনিশ কুড়ি "
                + "একুশ বাইশ তেইশ চব্বিশ পঁচিশ ছাব্বিশ সাতাশ আটাশ ঊনত্রিশ ত্রিশ একত্রিশ বত্রিশ তেত্রিশ চৌত্রিশ পঁয়ত্রিশ ছত্রিশ "
                + "সাঁইত্রিশ আটত্রিশ ঊনচল্লিশ চল্লিশ একচল্লিশ বিয়াল্লিশ তেতাল্লিশ চুয়াল্লিশ পঁয়তাল্লিশ ছেচল্লিশ সাতচল্লিশ আটচল্লিশ "
                + "ঊনপঞ্চাশ পঞ্চাশ একান্ন বাহান্ন তিপ্পান্ন চুয়ান্ন পঞ্চান্ন ছাপ্পান্ন সাতান্ন আটান্ন ঊনষাট ষাট একষট্টি বাষট্টি তেষট্টি "
                + "চৌষট্টি পঁয়ষট্টি ছেষট্টি সাতষট্টি আটষট্টি ঊনসত্তর সত্তর একাত্তর বাহাত্তর তিয়াত্তর চুয়াত্তর পঁচাত্তর ছিয়াত্তর "
                + "সাতাত্তর আটাত্তর ঊনআশি আশি একাশি বিরাশি তিরাশি চুরাশি পঁচাশি ছিয়াশি সাতাশি অষ্টাশি ঊননব্বই নব্বই একানব্বই "
                + "বিরানব্বই তিরানব্বই চুরানব্বই পঁচানব্বই ছিয়ানব্বই সাতানব্বই আটানব্বই নিরানব্বই একশো");
    }

    // ------------------------------------------------------------------ Dravidian building blocks
    /** {1-19 (space-separated), tens 20-90, tens as said before a unit, 100}. */
    private static final Map<Language, String[]> DRAVIDIAN = new EnumMap<>(Language.class);
    static {
        DRAVIDIAN.put(Language.TA, new String[] {
                "ஒன்று இரண்டு மூன்று நான்கு ஐந்து ஆறு ஏழு எட்டு ஒன்பது பத்து பதினொன்று பன்னிரண்டு பதின்மூன்று பதினான்கு "
                        + "பதினைந்து பதினாறு பதினேழு பதினெட்டு பத்தொன்பது",
                "இருபது முப்பது நாற்பது ஐம்பது அறுபது எழுபது எண்பது தொண்ணூறு",
                "இருபத்தி முப்பத்தி நாற்பத்தி ஐம்பத்தி அறுபத்தி எழுபத்தி எண்பத்தி தொண்ணூத்தி",
                "நூறு"});
        DRAVIDIAN.put(Language.TE, new String[] {
                "ఒకటి రెండు మూడు నాలుగు ఐదు ఆరు ఏడు ఎనిమిది తొమ్మిది పది పదకొండు పన్నెండు పదమూడు పద్నాలుగు పదిహేను "
                        + "పదహారు పదిహేడు పద్దెనిమిది పందొమ్మిది",
                "ఇరవై ముప్పై నలభై యాభై అరవై డెబ్బై ఎనభై తొంభై",
                "ఇరవై ముప్పై నలభై యాభై అరవై డెబ్బై ఎనభై తొంభై",
                "వంద"});
        DRAVIDIAN.put(Language.KN, new String[] {
                "ಒಂದು ಎರಡು ಮೂರು ನಾಲ್ಕು ಐದು ಆರು ಏಳು ಎಂಟು ಒಂಬತ್ತು ಹತ್ತು ಹನ್ನೊಂದು ಹನ್ನೆರಡು ಹದಿಮೂರು ಹದಿನಾಲ್ಕು ಹದಿನೈದು "
                        + "ಹದಿನಾರು ಹದಿನೇಳು ಹದಿನೆಂಟು ಹತ್ತೊಂಬತ್ತು",
                "ಇಪ್ಪತ್ತು ಮೂವತ್ತು ನಲವತ್ತು ಐವತ್ತು ಅರವತ್ತು ಎಪ್ಪತ್ತು ಎಂಬತ್ತು ತೊಂಬತ್ತು",
                "ಇಪ್ಪತ್ತು ಮೂವತ್ತು ನಲವತ್ತು ಐವತ್ತು ಅರವತ್ತು ಎಪ್ಪತ್ತು ಎಂಬತ್ತು ತೊಂಬತ್ತು",
                "ನೂರು"});
        DRAVIDIAN.put(Language.ML, new String[] {
                "ഒന്ന് രണ്ട് മൂന്ന് നാല് അഞ്ച് ആറ് ഏഴ് എട്ട് ഒമ്പത് പത്ത് പതിനൊന്ന് പന്ത്രണ്ട് പതിമൂന്ന് പതിനാല് പതിനഞ്ച് "
                        + "പതിനാറ് പതിനേഴ് പതിനെട്ട് പത്തൊമ്പത്",
                "ഇരുപത് മുപ്പത് നാൽപ്പത് അമ്പത് അറുപത് എഴുപത് എൺപത് തൊണ്ണൂറ്",
                "ഇരുപത്തി മുപ്പത്തി നാൽപ്പത്തി അമ്പത്തി അറുപത്തി എഴുപത്തി എൺപത്തി തൊണ്ണൂറ്റി",
                "നൂറ്"});
    }

    private static final String EN_UNITS = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
            + "fifteen sixteen seventeen eighteen nineteen";
    private static final String EN_TENS = "twenty thirty forty fifty sixty seventy eighty ninety";

    /** Extra words per language: spoken variants, fused thousands, fractions, romanised 1-10. value as text. */
    private static final Map<Language, String[][]> EXTRA = new EnumMap<>(Language.class);
    static {
        EXTRA.put(Language.EN, new String[][] {{"1", "single"}, {"2", "couple"}, {"0", "zero"}});
        EXTRA.put(Language.HI, new String[][] {{"6", "छः", "छे", "छै", "chhe", "chah", "chhah"}, {"1", "ek"}, {"2", "do", "dono"},
                {"3", "teen", "tin"}, {"4", "char", "chaar"}, {"5", "paanch", "panch"}, {"7", "saat"}, {"8", "aath"},
                {"9", "nau"}, {"10", "das"}, {"1.5", "डेढ़", "dedh", "derh"}, {"2.5", "ढाई", "dhai"}, {"2", "दोनों"}});
        EXTRA.put(Language.MR, new String[][] {{"1", "ek"}, {"2", "don", "दोन्ही"}, {"3", "teen"}, {"4", "char"}, {"5", "paach", "pach"},
                {"6", "saha"}, {"7", "saat"}, {"8", "aath"}, {"9", "nau"}, {"10", "daha"}, {"1.5", "दीड", "deed"}, {"2.5", "अडीच", "adich"}});
        EXTRA.put(Language.GU, new String[][] {{"1", "ek"}, {"2", "be", "બંને"}, {"3", "tran", "tren"}, {"4", "char"}, {"5", "paanch", "panch"},
                {"6", "chha", "chh"}, {"7", "saat"}, {"8", "aath"}, {"9", "nav"}, {"10", "das"}, {"1.5", "દોઢ", "dodh"}, {"2.5", "અઢી", "adhi"}});
        EXTRA.put(Language.PA, new String[][] {{"1", "ikk", "ik"}, {"2", "do", "ਦੋਵੇਂ"}, {"3", "tinn", "tin"}, {"4", "chaar", "char"},
                {"5", "panj"}, {"6", "chhe"}, {"7", "satt", "sat"}, {"8", "atth", "ath"}, {"9", "nau"}, {"10", "das"},
                {"1.5", "ਡੇਢ", "dedh"}, {"2.5", "ਢਾਈ", "dhai"}});
        EXTRA.put(Language.BN, new String[][] {{"20", "বিশ"}, {"14", "চৌদ্দ"}, {"6", "ছ"}, {"1", "ek"}, {"2", "dui", "duto", "দুটো", "দুজন"},
                {"3", "tin", "teen", "তিনজন"}, {"4", "char"}, {"5", "panch"}, {"6", "chhoy", "choy"}, {"7", "saat", "sat"},
                {"8", "aat"}, {"9", "noy"}, {"10", "dosh"}, {"1.5", "দেড়", "der"}, {"2.5", "আড়াই", "arai"}, {"100", "শো", "শ", "একশ"}});
        EXTRA.put(Language.TA, new String[][] {{"1", "ஒண்ணு", "ஒன்னு", "ஒரு", "onnu", "ondru", "oru"}, {"2", "ரெண்டு", "rendu", "irandu"},
                {"3", "மூணு", "moonu", "moondru"}, {"4", "நாலு", "naalu", "naangu"}, {"5", "அஞ்சு", "anju", "ainthu"},
                {"6", "aaru"}, {"7", "ezhu", "yezhu"}, {"8", "ettu"}, {"9", "ஒம்போது", "ombodhu", "onbathu"}, {"10", "pathu"},
                {"2000", "இரண்டாயிரம்", "ரெண்டாயிரம்"}, {"3000", "மூவாயிரம்"}, {"4000", "நாலாயிரம்", "நான்காயிரம்"},
                {"5000", "ஐயாயிரம்", "அஞ்சாயிரம்"}, {"10000", "பத்தாயிரம்"}, {"20000", "இருபதாயிரம்"},
                {"25000", "இருபத்தைந்தாயிரம்", "இருபத்தஞ்சாயிரம்"}, {"50000", "ஐம்பதாயிரம்"}});
        EXTRA.put(Language.TE, new String[][] {{"1", "okati", "oka", "ఒక"}, {"2", "rendu", "ఇద్దరు"}, {"3", "moodu", "ముగ్గురు"},
                {"4", "naalugu", "నలుగురు"}, {"5", "aidu", "అయిదు", "ఐదుగురు"}, {"6", "aaru"}, {"7", "edu"}, {"8", "enimidi"},
                {"9", "tommidi"}, {"10", "padi"}, {"100", "నూరు"}});
        EXTRA.put(Language.KN, new String[][] {{"1", "ondu", "ಒಬ್ಬ", "ಒಬ್ಬರು"}, {"2", "eradu", "ಇಬ್ಬರು"}, {"3", "mooru", "ಮೂವರು"},
                {"4", "naalku", "ನಾಲ್ವರು"}, {"5", "aidu", "ಐವರು"}, {"6", "aaru"}, {"7", "elu"}, {"8", "entu"}, {"9", "ombattu"},
                {"10", "hattu"}});
        EXTRA.put(Language.ML, new String[][] {{"1", "onnu", "ഒരു"}, {"2", "randu", "രണ്ടു"}, {"3", "moonnu", "മൂന്നു"}, {"4", "naalu"},
                {"5", "anchu", "anju"}, {"6", "aaru"}, {"7", "ezhu"}, {"8", "ettu"}, {"9", "onpathu", "ombathu"}, {"10", "pathu"},
                {"2000", "രണ്ടായിരം"}, {"3000", "മൂവായിരം"}, {"4000", "നാലായിരം"}, {"5000", "അയ്യായിരം"},
                {"10000", "പതിനായിരം"}, {"20000", "ഇരുപതിനായിരം"}, {"25000", "ഇരുപത്തിയയ്യായിരം"}, {"50000", "അമ്പതിനായിരം"}});
    }

    /** hundred, thousand, lakh, crore words (native + romanised + English, which people mix in). */
    private static final Map<Language, String[][]> MULTIPLIERS = new EnumMap<>(Language.class);
    static {
        String[] enH = {"hundred"}, enT = {"thousand", "k", "hazaar", "hazar", "hajar"}, enL = {"lakh", "lac", "lakhs"}, enC = {"crore"};
        MULTIPLIERS.put(Language.EN, new String[][] {enH, enT, enL, enC});
        MULTIPLIERS.put(Language.HI, new String[][] {{"सौ", "sau", "sou"}, {"हजार", "हज़ार"}, {"लाख"}, {"करोड़"}});
        MULTIPLIERS.put(Language.MR, new String[][] {{"शे", "शंभर", "shambhar"}, {"हजार"}, {"लाख"}, {"कोटी"}});
        MULTIPLIERS.put(Language.GU, new String[][] {{"સો"}, {"હજાર"}, {"લાખ"}, {"કરોડ"}});
        MULTIPLIERS.put(Language.PA, new String[][] {{"ਸੌ"}, {"ਹਜ਼ਾਰ", "ਹਜਾਰ"}, {"ਲੱਖ"}, {"ਕਰੋੜ"}});
        MULTIPLIERS.put(Language.BN, new String[][] {{"শত"}, {"হাজার"}, {"লাখ", "লক্ষ"}, {"কোটি"}});
        MULTIPLIERS.put(Language.TA, new String[][] {{"நூறு", "nooru"}, {"ஆயிரம்", "ஆயிரத்து", "aayiram"}, {"லட்சம்", "latcham"}, {"கோடி"}});
        MULTIPLIERS.put(Language.TE, new String[][] {{"వంద", "వందలు", "vanda"}, {"వెయ్యి", "వేలు", "వేల", "veyyi", "velu"},
                {"లక్ష", "లక్షలు", "laksha"}, {"కోటి"}});
        MULTIPLIERS.put(Language.KN, new String[][] {{"ನೂರು", "nooru"}, {"ಸಾವಿರ", "saavira", "savira"}, {"ಲಕ್ಷ", "laksha"}, {"ಕೋಟಿ"}});
        MULTIPLIERS.put(Language.ML, new String[][] {{"നൂറ്", "nooru"}, {"ആയിരം", "aayiram"}, {"ലക്ഷം", "laksham"}, {"കോടി"}});
    }
    private static final long[] MULT_VALUE = {100, 1000, 100000, 10000000};

    // ------------------------------------------------------------------ dictionaries
    /** Normalised word -> value. Value >= 100 marks a multiplier (negative = multiplier code). */
    private static final Map<Language, Map<String, Double>> WORDS = new EnumMap<>(Language.class);
    private static final Map<Language, Map<String, Long>> MULTS = new EnumMap<>(Language.class);

    private static synchronized Map<String, Double> words(Language l) {
        Map<String, Double> m = WORDS.get(l);
        if (m != null) return m;
        m = new HashMap<>();
        addEnglish(m);
        String full = ONE_TO_HUNDRED.get(l);
        if (full != null) {
            String[] w = full.split(" ");
            for (int i = 0; i < w.length; i++) m.put(VoiceText.norm(w[i]), (double) (i + 1));
        }
        String[] d = DRAVIDIAN.get(l);
        if (d != null) {
            String[] units = d[0].split(" "), tens = d[1].split(" "), comb = d[2].split(" ");
            for (int i = 0; i < units.length; i++) m.put(VoiceText.norm(units[i]), (double) (i + 1));
            for (int i = 0; i < tens.length; i++) {
                double tv = (i + 2) * 10;
                m.put(VoiceText.norm(tens[i]), tv);
                m.putIfAbsent(VoiceText.norm(comb[i]), tv);
                // Fused forms ("இருபத்தைந்து" = 25) are matched fuzzily against stem + unit.
                for (int u = 0; u < 9; u++) m.putIfAbsent(VoiceText.norm(comb[i] + units[u]), tv + u + 1);
            }
            m.put(VoiceText.norm(d[3]), 100.0);
        }
        String[][] extra = EXTRA.get(l);
        if (extra != null) for (String[] e : extra) for (int i = 1; i < e.length; i++) m.put(VoiceText.norm(e[i]), Double.parseDouble(e[0]));
        WORDS.put(l, m);
        Map<String, Long> mult = new HashMap<>();
        String[][] mm = MULTIPLIERS.get(l);
        for (int k = 0; k < 4; k++) {
            for (String w : MULTIPLIERS.get(Language.EN)[k]) mult.put(VoiceText.norm(w), MULT_VALUE[k]);
            for (String w : mm[k]) mult.put(VoiceText.norm(w), MULT_VALUE[k]);
        }
        // A word that is both a number and a multiplier (Hindi "सौ" = 100) acts as the multiplier.
        for (String k : mult.keySet()) m.remove(k);
        MULTS.put(l, mult);
        return m;
    }

    private static void addEnglish(Map<String, Double> m) {
        String[] u = EN_UNITS.split(" "), t = EN_TENS.split(" ");
        for (int i = 0; i < u.length; i++) m.put(u[i], (double) (i + 1));
        for (int i = 0; i < t.length; i++) m.put(t[i], (double) ((i + 2) * 10));
        for (String[] e : EXTRA.get(Language.EN)) for (int i = 1; i < e.length; i++) m.put(e[i], Double.parseDouble(e[0]));
    }

    /** Value of one normalised token, or null. Exact first; fuzzy (>= 0.8, best wins) for longer Indian-script words. */
    static Double wordValue(String token, Language l) {
        Map<String, Double> m = words(l);
        Double v = m.get(token);
        if (v != null || VoiceText.latin(token) || token.codePointCount(0, token.length()) < 4) return v;
        double best = 0.8 - 1e-9;
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (VoiceText.latin(e.getKey())) continue;
            double s = VoiceText.similarity(token, e.getKey());
            if (s > best) { best = s; v = e.getValue(); }
        }
        return v;
    }

    static Long multiplier(String token, Language l) {
        words(l);
        return MULTS.get(l).get(token);
    }

    /** A number found in speech, and the words it was read from (for "Was it 3 or 13?"). */
    public static final class Found {
        public final double value;
        public final String surface;

        Found(double value, String surface) {
            this.value = value;
            this.surface = surface;
        }
    }

    /** The first number in {@code text}, or null. "मेरे पास 3 employees हैं" -> 3; "पाँच हज़ार" -> 5000. */
    public static Found parse(String text, Language l) {
        List<String> tok = VoiceText.tokens(VoiceText.norm(text));
        double total = 0, cur = 0;
        boolean seen = false;
        StringBuilder surface = new StringBuilder();
        for (String t : tok) {
            Double v = null;
            Long mult = multiplier(t, l);
            if (mult == null) {
                if (t.matches("[0-9]+(\\.[0-9]+)?")) v = Double.parseDouble(t);
                else v = wordValue(t, l);
            }
            if (mult != null && seen) {
                if (mult == 100) cur = (cur == 0 ? 1 : cur) * 100;
                else { total += (cur == 0 ? 1 : cur) * mult; cur = 0; }
            } else if (mult != null) {
                cur = mult; // "सौ" / "hundred" alone
                seen = true;
            } else if (v != null) {
                if (v >= 1000 && (!seen || cur == 0)) { total += v; cur = 0; }               // fused "ஐயாயிரம்" = 5000
                else if (!seen || (cur == 0 && total > 0)) cur = v;                         // first word, or after thousand/lakh
                else if (cur < 100 && cur % 10 == 0 && cur % 1 == 0 && v < 10) cur += v;    // "twenty five", "இருபத்தி ஐந்து"
                else if (cur >= 100 && cur % 100 == 0 && v < 100) cur += v;                 // "एक सौ बीस"
                else break;                                                                 // a second, separate number
                seen = true;
            } else if (seen) {
                break;
            } else {
                continue;
            }
            if (surface.length() > 0) surface.append(' ');
            surface.append(t);
        }
        if (!seen) return null;
        return new Found(total + cur, surface.toString());
    }

    // ------------------------------------------------------------------ speaking
    private static final Map<Language, String> CRORE = new EnumMap<>(Language.class), LAKH = new EnumMap<>(Language.class),
            THOUSAND = new EnumMap<>(Language.class);
    static {
        CRORE.put(Language.HI, "करोड़"); LAKH.put(Language.HI, "लाख"); THOUSAND.put(Language.HI, "हज़ार");
        CRORE.put(Language.MR, "कोटी"); LAKH.put(Language.MR, "लाख"); THOUSAND.put(Language.MR, "हजार");
        CRORE.put(Language.GU, "કરોડ"); LAKH.put(Language.GU, "લાખ"); THOUSAND.put(Language.GU, "હજાર");
        CRORE.put(Language.PA, "ਕਰੋੜ"); LAKH.put(Language.PA, "ਲੱਖ"); THOUSAND.put(Language.PA, "ਹਜ਼ਾਰ");
        CRORE.put(Language.BN, "কোটি"); LAKH.put(Language.BN, "লাখ"); THOUSAND.put(Language.BN, "হাজার");
        CRORE.put(Language.EN, "crore"); LAKH.put(Language.EN, "lakh"); THOUSAND.put(Language.EN, "thousand");
    }

    /** True where {@link #toWords} spells the number out; else digits are left for the phone's voice. */
    public static boolean spellsOut(Language l) { return l == Language.EN || ONE_TO_HUNDRED.containsKey(l); }

    private static String upTo99(int n, Language l) {
        if (l == Language.EN) {
            String[] u = EN_UNITS.split(" "), t = EN_TENS.split(" ");
            if (n < 20) return u[n - 1];
            return t[n / 10 - 2] + (n % 10 == 0 ? "" : " " + u[n % 10 - 1]);
        }
        return ONE_TO_HUNDRED.get(l).split(" ")[n - 1];
    }

    private static String hundreds(int h, boolean alone, Language l) {
        switch (l) {
            case EN: return upTo99(h, l) + " hundred";
            case MR: return alone && h == 1 ? "शंभर" : (h == 1 ? "एक" : upTo99(h, l)) + "शे";
            case GU: return alone && h == 1 ? "સો" : (h == 2 ? "બ" : upTo99(h, l)) + "સો";
            case BN: {
                String[] b = {"এক", "দু", "তিন", "চার", "পাঁচ", "ছ", "সাত", "আট", "ন"};
                return b[h - 1] + "শো";
            }
            case PA: return upTo99(h, l) + " ਸੌ";
            default: return upTo99(h, l) + " सौ";
        }
    }

    /** Number in words (English + Hindi, Marathi, Gujarati, Punjabi, Bengali), Indian grouping. Others: digits. */
    public static String toWords(long n, Language l) {
        if (!spellsOut(l) || n <= 0 || n >= 1_000_000_000L) return Long.toString(n);
        StringBuilder b = new StringBuilder();
        long crore = n / 10_000_000, lakh = n / 100_000 % 100, thousand = n / 1000 % 100;
        int hundred = (int) (n / 100 % 10), rest = (int) (n % 100);
        if (crore > 0) b.append(toWords(crore, l)).append(' ').append(CRORE.get(l)).append(' ');
        if (lakh > 0) b.append(upTo99((int) lakh, l)).append(' ').append(LAKH.get(l)).append(' ');
        if (thousand > 0) b.append(upTo99((int) thousand, l)).append(' ').append(THOUSAND.get(l)).append(' ');
        if (hundred > 0) b.append(hundreds(hundred, n == hundred * 100L, l)).append(' ');
        if (rest > 0) b.append(upTo99(rest, l));
        return b.toString().trim();
    }

    /** "{amount in words} रुपये": template voice_rupees in res/values-<lang>/strings.xml. */
    public static String rupees(long n, Language l) {
        return com.udyam.digital.i18n.I18n.get(l, "voice_rupees", toWords(n, l));
    }

    /** "तीन हज़ार एक सौ रुपये महीना", "3100 rupees a month" in words, "மாதம் 3100 ரூபாய்". */
    public static String rupeesPerMonth(long n, Language l) {
        return com.udyam.digital.i18n.I18n.get(l, "voice_rupees_month", rupees(n, l));
    }
}
