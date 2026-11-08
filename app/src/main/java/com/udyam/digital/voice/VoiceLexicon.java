package com.udyam.digital.voice;

import com.udyam.digital.model.Language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Words people actually say, per language: yes / no, "nothing" / "both", everyday synonyms
 * for each answer (including the English words shop owners use: "salon", "parlour",
 * "पार्लर", "medical", "WhatsApp"), and city names in English, old English names and every
 * script. Mixed speech works because English words are always accepted too. Pure Java.
 */
public final class VoiceLexicon {
    private VoiceLexicon() {}

    // ------------------------------------------------------------------ yes / no
    private static final String[] EN_YES = {"yes", "yeah", "yep", "yup", "correct", "right", "ok", "okay", "sure", "true", "haan", "ha", "ji"};
    private static final String[] EN_NO = {"no", "nope", "not", "wrong", "incorrect", "nahi", "nahin", "na"};

    private static final Map<Language, String[][]> YES_NO = new EnumMap<>(Language.class);
    static {
        YES_NO.put(Language.EN, new String[][] {{}, {}});
        YES_NO.put(Language.HI, new String[][] {{"हां", "हा", "जी", "जी हां", "हांजी", "ठीक", "ठीक है", "सही", "सही है", "बिल्कुल", "हम्म"},
                {"नहीं", "नही", "ना", "गलत", "जी नहीं"}});
        YES_NO.put(Language.MR, new String[][] {{"होय", "हो", "हा", "बरोबर", "बरोबर आहे", "चालेल", "hoy", "ho"},
                {"नाही", "नको", "चूक", "चुकीचे", "nako"}});
        YES_NO.put(Language.GU, new String[][] {{"હા", "હાં", "હાજી", "બરાબર", "સાચું", "સાચું છે", "haa"},
                {"ના", "નહીં", "નથી", "ખોટું"}});
        YES_NO.put(Language.PA, new String[][] {{"ਹਾਂ", "ਹਾਂਜੀ", "ਜੀ", "ਠੀਕ", "ਠੀਕ ਹੈ", "ਸਹੀ", "ਸਹੀ ਹੈ"},
                {"ਨਹੀਂ", "ਨਾ", "ਗਲਤ", "ਜੀ ਨਹੀਂ"}});
        YES_NO.put(Language.BN, new String[][] {{"হ্যাঁ", "হাঁ", "হুঁ", "ঠিক", "ঠিক আছে", "সঠিক", "হ্যা"},
                {"না", "নয়", "ভুল", "না না"}});
        YES_NO.put(Language.TA, new String[][] {{"ஆம்", "ஆமாம்", "ஆமா", "சரி", "ஓகே", "சரிதான்", "aam", "aamam", "aama", "sari"},
                {"இல்லை", "இல்ல", "வேண்டாம்", "தப்பு", "தவறு", "illai", "illa", "vendam", "thappu"}});
        YES_NO.put(Language.TE, new String[][] {{"అవును", "ఔను", "సరే", "అవునండి", "కరెక్ట్", "avunu", "sare"},
                {"కాదు", "లేదు", "వద్దు", "తప్పు", "kaadu", "ledu", "vaddu"}});
        YES_NO.put(Language.KN, new String[][] {{"ಹೌದು", "ಸರಿ", "ಹೂಂ", "ಹೌದ್", "haudu", "sari"},
                {"ಇಲ್ಲ", "ಬೇಡ", "ಅಲ್ಲ", "ತಪ್ಪು", "illa", "beda", "alla"}});
        YES_NO.put(Language.ML, new String[][] {{"അതെ", "ശരി", "ഉവ്വ്", "അതേ", "athe", "shari", "uvvu"},
                {"അല്ല", "ഇല്ല", "വേണ്ട", "തെറ്റ്", "alla", "illa", "venda"}});
    }

    /** Normalised yes words (this language + English). */
    public static List<String> yes(Language l) { return words(YES_NO.get(l)[0], EN_YES); }

    public static List<String> no(Language l) { return words(YES_NO.get(l)[1], EN_NO); }

    // ------------------------------------------------------------------ nothing / both
    private static final Map<Language, String[]> NOTHING = new EnumMap<>(Language.class), ALL = new EnumMap<>(Language.class);
    static {
        NOTHING.put(Language.EN, new String[] {});
        NOTHING.put(Language.HI, new String[] {"कुछ नहीं", "कुछ नहीं चाहिए", "कुछ भी नहीं", "बस", "बस इतना", "रहने दो", "kuch nahi"});
        NOTHING.put(Language.MR, new String[] {"काही नाही", "काहीच नाही", "नको", "बस", "kahi nahi"});
        NOTHING.put(Language.GU, new String[] {"કંઈ નહીં", "કશું નહીં", "બસ", "kai nahi"});
        NOTHING.put(Language.PA, new String[] {"ਕੁਝ ਨਹੀਂ", "ਬੱਸ", "kujh nahi"});
        NOTHING.put(Language.BN, new String[] {"কিছু না", "কিছুই না", "আর না", "kichu na"});
        NOTHING.put(Language.TA, new String[] {"எதுவும் வேண்டாம்", "ஒன்றும் வேண்டாம்", "போதும்", "ethuvum vendam", "podhum"});
        NOTHING.put(Language.TE, new String[] {"ఏమీ వద్దు", "ఏమీ లేదు", "చాలు", "emi vaddu", "chaalu"});
        NOTHING.put(Language.KN, new String[] {"ಏನೂ ಬೇಡ", "ಏನೂ ಇಲ್ಲ", "ಸಾಕು", "enu beda", "saaku"});
        NOTHING.put(Language.ML, new String[] {"ഒന്നും വേണ്ട", "ഒന്നുമില്ല", "മതി", "onnum venda", "mathi"});
        ALL.put(Language.EN, new String[] {});
        ALL.put(Language.HI, new String[] {"दोनों", "सब", "सभी", "dono"});
        ALL.put(Language.MR, new String[] {"दोन्ही", "सगळे", "donhi"});
        ALL.put(Language.GU, new String[] {"બંને", "બધું", "banne"});
        ALL.put(Language.PA, new String[] {"ਦੋਵੇਂ", "ਸਾਰੇ", "dovein"});
        ALL.put(Language.BN, new String[] {"দুটোই", "দুটো", "সব", "duto"});
        ALL.put(Language.TA, new String[] {"இரண்டும்", "ரெண்டும்", "எல்லாம்", "rendum"});
        ALL.put(Language.TE, new String[] {"రెండూ", "రెండు కూడా", "అన్నీ", "rendu"});
        ALL.put(Language.KN, new String[] {"ಎರಡೂ", "ಎಲ್ಲಾ", "eradu"});
        ALL.put(Language.ML, new String[] {"രണ്ടും", "എല്ലാം", "randum"});
    }
    private static final String[] EN_NOTHING = {"nothing", "none", "skip", "no thanks", "nothing else", "that's all", "thats all", "nahi chahiye"};
    private static final String[] EN_ALL = {"both", "all", "all of them", "everything"};

    public static List<String> nothing(Language l) { return words(NOTHING.get(l), EN_NOTHING); }

    public static List<String> all(Language l) { return words(ALL.get(l), EN_ALL); }

    // ------------------------------------------------------------------ synonyms per answer value
    private static final Map<String, String[]> SYNONYMS = new HashMap<>();
    private static void syn(String value, String... words) { SYNONYMS.put(value, words); }
    static {
        // Business type
        syn("MICRO:Kirana store", "kirana", "kirana store", "kirana shop", "general store", "grocery", "provision store", "provisions",
                "supermarket", "किराने की दुकान", "किराना दुकान", "परचून", "जनरल स्टोर", "किराणा दुकान", "किराणा माल", "કરિયાણાની દુકાન",
                "ਕਰਿਆਨੇ ਦੀ ਦੁਕਾਨ", "মুদি", "মুদির দোকান", "মুদিখানা", "மளிகை", "மளிகை கடை", "கிராணா", "కిరాణా కొట్టు", "కిరాణా షాప్",
                "ದಿನಸಿ", "ದಿನಸಿ ಅಂಗಡಿ", "ಕಿರಾಣಿ ಅಂಗಡಿ", "പലചരക്ക് കട", "പലവ്യഞ്ജനം");
        syn("MICRO:Pharmacy", "medical", "medical store", "medical shop", "chemist", "pharmacy", "medicine shop", "medicines",
                "दवाई", "दवाई की दुकान", "दवाइयों की दुकान", "केमिस्ट", "मेडिकल स्टोर", "मेडिकल", "औषधाचे दुकान", "औषध दुकान",
                "દવાની દુકાન", "મેડિકલ સ્ટોર", "ਦਵਾਈਆਂ ਦੀ ਦੁਕਾਨ", "ਮੈਡੀਕਲ ਸਟੋਰ", "ওষুধ", "ওষুধের দোকান", "মেডিকেল", "মেডিক্যাল",
                "மருந்தகம்", "மருந்து கடை", "மெடிக்கல்", "మందుల షాప్", "మందుల దుకాణం", "మెడికల్", "ಔಷಧಿ ಅಂಗಡಿ", "ಮೆಡಿಕಲ್ ಸ್ಟೋರ್",
                "ಮೆಡಿಕಲ್", "മരുന്നുകട", "ഫാർമസി", "മെഡിക്കൽ");
        syn("MICRO:Salon", "salon", "saloon", "parlour", "parlor", "beauty parlour", "beauty parlor", "barber", "barber shop",
                "hair cutting", "haircut", "spa", "पार्लर", "ब्यूटी पार्लर", "सैलून", "सलून", "नाई", "हेयर कटिंग", "ब्युटी पार्लर",
                "પાર્લર", "બ્યુટી પાર્લર", "વાળંદ", "ਨਾਈ", "ਪਾਰਲਰ", "ਬਿਊਟੀ ਪਾਰਲਰ", "পার্লার", "বিউটি পার্লার", "নাপিত", "সেলুন",
                "பார்லர்", "பியூட்டி பார்லர்", "முடி திருத்தும் கடை", "சலூன்", "పార్లర్", "బ్యూటీ పార్లర్", "బార్బర్ షాప్", "సెలూన్",
                "ಪಾರ್ಲರ್", "ಬ್ಯೂಟಿ ಪಾರ್ಲರ್", "ಕ್ಷೌರದ ಅಂಗಡಿ", "ಸಲೂನ್", "പാർലർ", "ബ്യൂട്ടി പാർലർ", "ബാർബർ ഷോപ്പ്", "സലൂൺ");
        syn("MICRO:Bakery", "bakery", "bakers", "cake shop", "cakes", "बेकरी", "केक की दुकान", "बेकर्स", "બેકરી", "ਬੇਕਰੀ", "বেকারি",
                "கேக் கடை", "பேக்கரி", "బేకరీ", "ಬೇಕರಿ", "ബേക്കറി", "കേക്ക്");
        syn("MICRO:Sweet shop", "sweets", "sweet shop", "mithai", "mithai shop", "halwai", "मिठाई की दुकान", "हलवाई", "मिठाईवाला",
                "मिठाईचे दुकान", "મીઠાઈની દુકાન", "ਹਲਵਾਈ", "ਮਿਠਾਈ ਦੀ ਦੁਕਾਨ", "মিষ্টি", "মিষ্টির দোকান", "ஸ்வீட் கடை", "ஸ்வீட்ஸ்",
                "இனிப்பு கடை", "స్వీట్స్", "స్వీట్ షాప్", "ಸ್ವೀಟ್ ಅಂಗಡಿ", "ಸಿಹಿತಿಂಡಿ", "സ്വീറ്റ്സ്", "മധുരപലഹാരം");
        syn("MICRO:Mobile repair shop", "mobile shop", "mobile repair", "phone shop", "mobile store", "phone repair", "recharge shop",
                "मोबाइल रिपेयर", "मोबाइल दुकान", "फोन की दुकान", "मोबाइल शॉप", "मोबाईल शॉप", "મોબાઇલ શોપ", "ਮੋਬਾਈਲ ਸ਼ਾਪ",
                "মোবাইলের দোকান", "மொபைல் ஷாப்", "மொபைல் கடை", "మొబైల్ షాప్", "ಮೊಬೈಲ್ ಶಾಪ್", "മൊബൈൽ ഷോപ്പ്");
        syn("MICRO:Boutique", "boutique", "clothes", "clothes shop", "cloth shop", "garments", "garment shop", "tailor", "readymade",
                "textiles", "कपड़े की दुकान", "कपड़ों की दुकान", "बुटीक", "रेडीमेड", "दर्जी", "कपड्यांचे दुकान", "કપડાંની દુકાન",
                "ਕੱਪੜੇ ਦੀ ਦੁਕਾਨ", "কাপড়ের দোকান", "বুটিক", "ஜவுளி கடை", "துணிக் கடை", "டெய்லர்", "బట్టల షాప్", "బట్టల కొట్టు",
                "బొటిక్", "ಬಟ್ಟೆ ಅಂಗಡಿ", "ಬೊಟಿಕ್", "ജൗളിക്കട", "തുണിക്കട", "ബൊട്ടീക്");
        syn("MICRO:Hardware store", "hardware", "hardware shop", "tools", "paint shop", "sanitary", "हार्डवेयर", "हार्डवेयर की दुकान",
                "औजार", "हार्डवेअर दुकान", "હાર્ડવેરની દુકાન", "ਹਾਰਡਵੇਅਰ ਦੀ ਦੁਕਾਨ", "হার্ডওয়্যারের দোকান", "ஹார்டுவேர்", "హార్డ్‌వేర్",
                "ಹಾರ್ಡ್‌ವೇರ್", "ഹാർഡ്‌വെയർ");
        syn("MICRO:Stationery shop", "stationery", "stationary", "book shop", "books", "xerox", "photocopy", "स्टेशनरी", "किताब की दुकान",
                "जेरॉक्स", "पुस्तकांचे दुकान", "સ્ટેશનરી", "ਸਟੇਸ਼ਨਰੀ", "স্টেশনারি", "বইয়ের দোকান", "ஸ்டேஷனரி", "ஜெராக்ஸ்", "స్టేషనరీ",
                "ಸ್ಟೇಷನರಿ", "സ്റ്റേഷനറി");
        syn("MICRO:Optician", "optician", "optical", "opticals", "spectacles", "glasses", "eye wear", "चश्मे की दुकान", "ऑप्टिकल",
                "चष्म्याचे दुकान", "ચશ્માની દુકાન", "ਐਨਕਾਂ ਦੀ ਦੁਕਾਨ", "চশমার দোকান", "ஆப்டிகல்", "కళ్లద్దాలు", "ఆప్టికల్", "ಆಪ್ಟಿಕಲ್",
                "കണ്ണട കട", "ഒപ്റ്റിക്കൽ");
        syn("PROF:*clinic", "clinic", "doctor", "hospital", "dental", "dentist", "physio", "physiotherapy", "lab", "diagnostic",
                "nursing home", "दवाखाना", "क्लिनिक", "डॉक्टर", "दांत का डॉक्टर", "अस्पताल", "दवाखान्यात", "દવાખાનું", "ડોક્ટર",
                "ਡਾਕਟਰ", "ਹਸਪਤਾਲ", "ডাক্তার", "ডাক্তারখানা", "হাসপাতাল", "மருத்துவமனை", "டாக்டர்", "கிளினிக்", "ఆసుపత్రి", "డాక్టర్",
                "ఆస్పత్రి", "ಆಸ್ಪತ್ರೆ", "ಡಾಕ್ಟರ್", "ആശുപത്രി", "ഡോക്ടർ");
        syn("PROF:*office", "office", "ca", "ca office", "chartered accountant", "accountant", "lawyer", "advocate", "consultant",
                "consultancy", "agency", "insurance", "real estate", "travel agency", "ऑफिस", "दफ्तर", "वकील", "सीए", "दफ्तर है",
                "ऑफिस आहे", "ઓફિસ", "વકીલ", "ਦਫ਼ਤਰ", "ਵਕੀਲ", "অফিস", "উকিল", "அலுவலகம்", "ஆபீஸ்", "வக்கீல்", "ఆఫీసు", "లాయర్",
                "ಆಫೀಸ್", "ವಕೀಲ", "ഓഫീസ്", "വക്കീൽ");
        syn("PROF:Coaching centre", "coaching", "coaching centre", "coaching center", "tuition", "tuitions", "classes", "institute",
                "academy", "school", "ट्यूशन", "कोचिंग", "क्लास", "कोचिंग सेंटर", "शिकवणी", "ટ્યુશન", "ક્લાસ", "ਟਿਊਸ਼ਨ", "টিউশন",
                "கோச்சிங்", "டியூஷன்", "ట్యూషన్", "ಟ್ಯೂಷನ್", "ട്യൂഷൻ");
        syn("MULTI:*chain", "chain", "branches", "many branches", "many shops", "outlets", "franchise", "कई दुकानें", "शाखाएं", "चेन",
                "कई ब्रांच", "अनेक दुकाने", "ઘણી દુકાનો", "ਕਈ ਦੁਕਾਨਾਂ", "অনেক দোকান", "பல கடைகள்", "చాలా షాపులు", "ಹಲವು ಅಂಗಡಿಗಳು",
                "പല കടകൾ");
        syn("MFG:*factory", "factory", "manufacturing", "workshop", "unit", "plant", "industry", "production", "फैक्ट्री", "कारखाना",
                "उत्पादन", "फॅक्टरी", "ફેક્ટરી", "કારખાનું", "ਫੈਕਟਰੀ", "কারখানা", "ফ্যাক্টরি", "தொழிற்சாலை", "பேக்டரி", "ఫ్యాక్టరీ",
                "కర్మాగారం", "ಫ್ಯಾಕ್ಟರಿ", "ಕಾರ್ಖಾನೆ", "ഫാക്ടറി");
        syn("MICRO:*other", "other", "something else", "different", "कुछ और", "दूसरा", "दूसरा काम", "वेगळे", "બીજું કંઈ", "ਹੋਰ ਕੁਝ",
                "অন্য কিছু", "வேறு", "వేరేది", "ಬೇರೆ", "മറ്റൊന്ന്");
        // Services
        syn("MOBILE_SIM", "sim", "sims", "sim card", "mobile", "mobile connection", "phone connection", "postpaid", "calling",
                "सिम", "सिम कार्ड", "मोबाइल", "फोन", "मोबाईल", "સિમ", "ਸਿਮ", "সিম", "சிம்", "సిమ్", "ಸಿಮ್", "സിം");
        syn("BROADBAND", "broadband", "wifi", "wi fi", "internet", "fibre", "fiber", "net", "airfiber", "इंटरनेट", "वाईफाई", "वाई फाई",
                "ब्रॉडबैंड", "नेट", "वायफाय", "ઇન્ટરનેટ", "વાઇફાઇ", "ਇੰਟਰਨੈੱਟ", "ਵਾਈਫਾਈ", "ইন্টারনেট", "ওয়াইফাই", "இன்டர்நெட்", "இணையம்",
                "வைஃபை", "வைபை", "ఇంటర్నెట్", "వైఫై", "ಇಂಟರ್ನೆಟ್", "ವೈಫೈ", "ഇന്റർനെറ്റ്", "വൈഫൈ");
        // Current internet
        syn("MOBILE_HOTSPOT", "phone data", "mobile data", "hotspot", "data pack", "phone internet", "फोन का नेट", "मोबाइल डेटा",
                "हॉटस्पॉट", "मोबाइल का नेट", "फोनचा डेटा", "ફોનનું નેટ", "ਫੋਨ ਦਾ ਨੈੱਟ", "ফোনের নেট", "மொபைல் டேட்டா", "మొబైల్ డేటా",
                "ಮೊಬೈಲ್ ಡೇಟಾ", "മൊബൈൽ ഡാറ്റ");
        syn("HOME_BROADBAND", "home wifi", "home internet", "ghar ka wifi", "घर का वाईफाई", "घर का इंटरनेट", "घरचे वायफाय",
                "ઘરનું વાઇફાઇ", "ਘਰ ਦਾ ਵਾਈਫਾਈ", "বাড়ির ওয়াইফাই", "வீட்டு வைஃபை", "ఇంటి వైఫై", "ಮನೆ ವೈಫೈ", "വീട്ടിലെ വൈഫൈ");
        syn("BUSINESS_BROADBAND", "business wifi", "office wifi", "shop wifi", "business broadband", "दुकान का वाईफाई",
                "ऑफिस का वाईफाई", "दुकानचे वायफाय", "દુકાનનું વાઇફાઇ", "ਦੁਕਾਨ ਦਾ ਵਾਈਫਾਈ", "দোকানের ওয়াইফাই", "கடை வைஃபை",
                "షాప్ వైఫై", "ಅಂಗಡಿ ವೈಫೈ", "കടയിലെ വൈഫൈ");
        syn("LEASED_LINE", "leased line", "lease line", "dedicated", "ILL", "लीज लाइन", "लीज्ड लाइन");
        syn("MIXED", "mixed", "many types", "different", "अलग अलग", "मिला जुला", "दोनों", "वेगवेगळे", "અલગ અલગ", "ਵੱਖ ਵੱਖ",
                "আলাদা আলাদা", "கலவை", "వేర్వేరు", "ಬೇರೆ ಬೇರೆ", "പലതരം");
        syn("NONE", "none", "no internet", "nothing", "कोई नहीं", "इंटरनेट नहीं", "नहीं है", "कुछ नहीं", "काही नाही", "નથી",
                "ਕੋਈ ਨਹੀਂ", "নেই", "কিছু নেই", "இல்லை", "எதுவும் இல்லை", "లేదు", "ఏమీ లేదు", "ಇಲ್ಲ", "ಏನೂ ಇಲ್ಲ", "ഇല്ല", "ഒന്നുമില്ല");
        // Extras
        syn("BILLING_POS", "upi", "billing", "bill machine", "billing machine", "pos", "payment machine", "card machine", "qr code",
                "gpay", "phonepe", "paytm", "बिलिंग", "बिल", "यूपीआई", "बिल मशीन", "पेमेंट", "બિલિંગ", "ਬਿਲਿੰਗ", "বিলিং", "பில்லிங்",
                "బిల్లింగ్", "ಬಿಲ್ಲಿಂಗ್", "ബില്ലിംഗ്");
        syn("WHATSAPP", "whatsapp", "whats app", "व्हाट्सएप", "वाट्सएप", "व्हॉट्सॲप", "વોટ્સએપ", "ਵਟਸਐਪ", "হোয়াটসঅ্যাপ",
                "வாட்ஸ்அப்", "వాట్సాప్", "ವಾಟ್ಸಾಪ್", "വാട്സ്ആപ്പ്");
        syn("CCTV", "cctv", "camera", "cameras", "security camera", "सीसीटीवी", "कैमरा", "कॅमेरा", "કેમેરા", "ਕੈਮਰਾ", "ক্যামেরা",
                "கேமரா", "కెమెరా", "ಕ್ಯಾಮೆರಾ", "ക്യാമറ");
        syn("BACKUP_INTERNET", "backup internet", "second internet", "extra internet", "spare internet", "दूसरा इंटरनेट",
                "बैकअप इंटरनेट", "बॅकअप इंटरनेट");
        syn("FAST_INTERNET", "fast internet", "high speed", "speed", "fast net", "तेज इंटरनेट", "तेज नेट", "फास्ट इंटरनेट",
                "वेगवान इंटरनेट", "ઝડપી ઇન્ટરનેટ", "ਤੇਜ਼ ਇੰਟਰਨੈੱਟ", "দ্রুত ইন্টারনেট", "வேகமான இணையம்", "వేగమైన ఇంటర్నెట్",
                "ವೇಗದ ಇಂಟರ್ನೆಟ್", "വേഗമുള്ള ഇന്റർനെറ്റ്");
        syn("DEDICATED_LINE", "dedicated line", "always on", "always on line", "never down");
        syn("GUEST_WIFI", "customer wifi", "guest wifi", "free wifi", "wifi for customers", "ग्राहकों के लिए वाईफाई", "कस्टमर वाईफाई");
        syn("WEBSITE", "website", "web site", "online shop", "online store", "वेबसाइट", "वेबसाईट", "વેબસાઇટ", "ਵੈੱਬਸਾਈਟ", "ওয়েবসাইট",
                "வெப்சைட்", "వెబ్‌సైట్", "ವೆಬ್‌ಸೈಟ್", "വെബ്സൈറ്റ്");
        syn("BULK_SMS", "sms", "bulk sms", "messages", "offer messages", "एसएमएस", "मैसेज", "મેસેજ", "ਮੈਸੇਜ", "মেসেজ", "மெசேஜ்",
                "మెసేజ్", "ಮೆಸೇಜ್", "മെസ്സേജ്");
        syn("EMAIL_OFFICE", "email", "e mail", "mail", "business email", "ईमेल", "ई मेल", "ઈમેલ", "ਈਮੇਲ", "ইমেল", "ইমেইল",
                "மின்னஞ்சல்", "ஈமெயில்", "ఈమెయిల్", "ಇಮೇಲ್", "ഇമെയിൽ");
        syn("CLOUD_PHONE", "office phone", "landline", "office line", "intercom", "ऑफिस फोन", "लैंडलाइन", "लँडलाइन");
        syn("TOLL_FREE", "toll free", "tollfree", "1800", "1800 number", "टोल फ्री", "ટોલ ફ્રી", "ਟੋਲ ਫ੍ਰੀ", "টোল ফ্রি", "டோல் ஃப்ரீ",
                "టోల్ ఫ్రీ", "ಟೋಲ್ ಫ್ರೀ", "ടോൾ ഫ്രീ");
        syn("CYBER_SECURITY", "virus", "antivirus", "anti virus", "cyber security", "hacking", "वायरस", "एंटीवायरस", "व्हायरस", "વાયરસ",
                "ਵਾਇਰਸ", "ভাইরাস", "வைரஸ்", "వైరస్", "ವೈರಸ್", "വൈറസ്");
        syn("DATA_BACKUP", "data backup", "backup of data", "cloud backup", "डेटा बैकअप", "डेटा बॅकअप", "ડેટા બેકઅપ");
        syn("REMOTE_ACCESS", "remote", "remote access", "work from home", "access from home", "घर से काम", "रिमोट");
        syn("MULTI_SITE_NETWORK", "network between branches", "connect branches", "all branches", "सब शाखाएं जोड़ना", "branch network");
        syn("MACHINE_MONITORING", "machine", "machines", "iot", "sensor", "sensors", "मशीन", "मशीनें", "મશીન", "ਮਸ਼ੀਨ", "মেশিন",
                "இயந்திரம்", "మెషిన్", "ಯಂತ್ರ", "മെഷീൻ");
        syn("FLEET_TRACKING", "vehicle", "vehicles", "gps", "truck", "trucks", "delivery bikes", "गाड़ी", "गाड़ियां", "ट्रक", "गाडी",
                "ગાડી", "ਗੱਡੀ", "গাড়ি", "வாகனம்", "లారీ", "వాహనం", "ವಾಹನ", "വാഹനം");
        // Budget / people
        syn("0", "best", "best plan", "any", "any budget", "no limit", "no budget", "doesn't matter", "सबसे अच्छा", "सबसे बढ़िया",
                "कोई भी", "जो भी", "बजट नहीं", "सर्वात चांगला", "શ્રેષ્ઠ", "ਸਭ ਤੋਂ ਵਧੀਆ", "সেরা", "சிறந்தது", "ఉత్తమమైనది", "ಅತ್ಯುತ್ತಮ",
                "ഏറ്റവും മികച്ചത്");
        syn("2", "just me", "only me", "alone", "myself", "akela", "akele", "अकेला", "अकेले", "सिर्फ मैं", "मैं ही", "मी एकटा", "मी एकटी",
                "હું એકલો", "ਮੈਂ ਇਕੱਲਾ", "শুধু আমি", "நான் மட்டும்", "నేను ఒక్కడినే", "ನಾನೊಬ್ಬನೇ", "ഞാൻ മാത്രം");
    }

    /** Extra phrases for an answer value (any language). */
    public static List<String> synonyms(String value) {
        String[] s = SYNONYMS.get(value);
        return s == null ? Collections.<String>emptyList() : words(s);
    }

    // ------------------------------------------------------------------ cities
    private static final Map<String, String[]> CITY_ALIASES = new HashMap<>();
    static {
        CITY_ALIASES.put("Bengaluru", new String[] {"bangalore", "bengaluru", "blr"});
        CITY_ALIASES.put("Mumbai", new String[] {"bombay", "mumbai", "बॉम्बे", "बम्बई"});
        CITY_ALIASES.put("Kolkata", new String[] {"calcutta", "kolkata", "কোলকাতা"});
        CITY_ALIASES.put("Chennai", new String[] {"madras", "chennai", "மெட்ராஸ்"});
        CITY_ALIASES.put("Visakhapatnam", new String[] {"vizag", "vishakhapatnam", "visakhapatnam", "వైజాగ్", "విశాఖ"});
        CITY_ALIASES.put("Vadodara", new String[] {"baroda", "vadodara", "બરોડા"});
        CITY_ALIASES.put("Mysuru", new String[] {"mysore", "mysuru", "ಮೈಸೂರ್"});
        CITY_ALIASES.put("Kochi", new String[] {"cochin", "kochi", "ernakulam", "എറണാകുളം"});
        CITY_ALIASES.put("Pune", new String[] {"poona", "pune"});
        CITY_ALIASES.put("Delhi", new String[] {"delhi", "new delhi", "dilli", "नई दिल्ली"});
        CITY_ALIASES.put("Ahmedabad", new String[] {"ahmedabad", "amdavad", "અમદાવાદ"});
        CITY_ALIASES.put("Coimbatore", new String[] {"coimbatore", "kovai", "கோவை"});
        CITY_ALIASES.put("Hyderabad", new String[] {"hyderabad", "hyd", "secunderabad"});
    }

    public static List<String> cityAliases(String city) {
        String[] a = CITY_ALIASES.get(city);
        return a == null ? Collections.<String>emptyList() : words(a);
    }

    // ------------------------------------------------------------------ language names (language screen)
    private static final Map<Language, String[]> LANGUAGE_NAMES = new EnumMap<>(Language.class);
    static {
        LANGUAGE_NAMES.put(Language.EN, new String[] {"english", "अंग्रेजी", "इंग्लिश"});
        LANGUAGE_NAMES.put(Language.HI, new String[] {"hindi", "हिंदी", "हिन्दी"});
        LANGUAGE_NAMES.put(Language.MR, new String[] {"marathi", "मराठी"});
        LANGUAGE_NAMES.put(Language.GU, new String[] {"gujarati", "ગુજરાતી", "गुजराती"});
        LANGUAGE_NAMES.put(Language.PA, new String[] {"punjabi", "panjabi", "ਪੰਜਾਬੀ", "पंजाबी"});
        LANGUAGE_NAMES.put(Language.BN, new String[] {"bengali", "bangla", "বাংলা", "बंगाली"});
        LANGUAGE_NAMES.put(Language.TA, new String[] {"tamil", "தமிழ்", "तमिल"});
        LANGUAGE_NAMES.put(Language.TE, new String[] {"telugu", "తెలుగు", "तेलुगु"});
        LANGUAGE_NAMES.put(Language.KN, new String[] {"kannada", "ಕನ್ನಡ", "कन्नड़"});
        LANGUAGE_NAMES.put(Language.ML, new String[] {"malayalam", "മലയാളം", "मलयालम"});
    }

    public static List<String> languageNames(Language l) { return words(LANGUAGE_NAMES.get(l)); }

    // ------------------------------------------------------------------ reading labels aloud
    /**
     * An option label as it should be spoken: "₹5,000" -> "पाँच हज़ार रुपये", "3–4" -> "3 से 4",
     * "50+" -> "50 या ज़्यादा", "SIM / Mobile" -> "SIM, Mobile". Short and in the language.
     */
    public static String spoken(String label, Language l) {
        String s = label.trim();
        java.util.regex.Matcher rupee = java.util.regex.Pattern.compile("₹\\s?([0-9,]+)").matcher(s);
        StringBuffer b = new StringBuffer();
        while (rupee.find()) {
            long n = Long.parseLong(rupee.group(1).replace(",", ""));
            rupee.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(NumberWords.rupees(n, l)));
        }
        rupee.appendTail(b);
        s = b.toString();
        String[] r = {com.udyam.digital.i18n.I18n.get(l, "voice_range_to"), com.udyam.digital.i18n.I18n.get(l, "voice_or_more")};
        s = s.replaceAll("([0-9]+)\\s*[–-]\\s*([0-9]+)", "$1 " + r[0] + " $2");
        s = s.replaceAll("([0-9]+)\\+", "$1 " + r[1]);
        return s.replace(" / ", ", ").replace("/", ", ");
    }

    // ------------------------------------------------------------------ helpers
    private static List<String> words(String[]... lists) {
        List<String> out = new ArrayList<>();
        for (String[] list : lists) if (list != null) for (String w : list) out.add(VoiceText.norm(w));
        return out;
    }
}
