package com.udyam.digital.help;

import java.util.List;

/** Conversation signals that feed the escalation score. Word lists live in help_kb.json. */
public final class Signals {
    private final KnowledgeBase kb;

    public Signals(KnowledgeBase kb) {
        this.kb = kb;
    }

    private boolean any(String listName, String message) {
        String t = TextNorm.norm(message, kb.synonyms());
        List<String> words = kb.signal(listName);
        for (String w : words) {
            if (TextNorm.containsPhrase(t, TextNorm.norm(w, kb.synonyms()))) return true;
        }
        return false;
    }

    /** "no", "not helpful", "galat", "still not working", "abhi bhi nahi"... */
    public boolean negativeFeedback(String m) {
        return any("negative_feedback", m);
    }

    /** "thanks", "ok", "samajh gaya", "dhanyavad"... */
    public boolean positiveFeedback(String m) {
        return any("positive_feedback", m);
    }

    /** "bakwas", "fraud", "worst", "kitni baar", "!!!" or mostly CAPITALS. */
    public boolean frustration(String m) {
        if (m == null) return false;
        if (m.contains("!!!")) return true;
        int letters = 0, upper = 0;
        for (char c : m.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                upper++;
                letters++;
            } else if (c >= 'a' && c <= 'z') {
                letters++;
            }
        }
        if (letters >= 8 && upper >= 0.7 * letters) return true;
        return any("frustration", m);
    }
}
