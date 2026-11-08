package com.udyam.digital.help;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline keyword matcher over the KB (English, Hindi Devanagari, Hinglish). Works with no
 * internet so the demo never breaks. Score = sum of matched phrase lengths (in words) times
 * the entry's boost, plus a small bonus when the entry belongs to the user's current stage.
 * Confidence = score / (score + 1): one matched word = 0.5, two = 0.67, none = 0.
 */
public final class IntentMatcher {

    public static final double LOW_CONFIDENCE = 0.4;

    public static final class Match {
        public final String intentId;
        public final double confidence;
        public final double rawScore;
        public final List<String> matchedKeywords;
        public final String runnerUpId;

        Match(String intentId, double confidence, double rawScore, List<String> matchedKeywords, String runnerUpId) {
            this.intentId = intentId;
            this.confidence = confidence;
            this.rawScore = rawScore;
            this.matchedKeywords = matchedKeywords;
            this.runnerUpId = runnerUpId;
        }

        public boolean isConfident() {
            return intentId != null && confidence >= LOW_CONFIDENCE;
        }

        @Override
        public String toString() {
            return intentId + " (" + String.format(java.util.Locale.ROOT, "%.2f", confidence) + ") " + matchedKeywords;
        }
    }

    private final KnowledgeBase kb;
    /** intent -> normalised keyword phrases, computed once. */
    private final Map<String, List<String>> normKeywords = new HashMap<>();

    public IntentMatcher(KnowledgeBase kb) {
        this.kb = kb;
        for (KbEntry e : kb.all()) {
            List<String> ks = new ArrayList<>();
            for (String k : e.allKeywords()) {
                String n = TextNorm.norm(k, kb.synonyms());
                if (!n.isEmpty()) ks.add(n);
            }
            normKeywords.put(e.id, ks);
        }
    }

    /** Words ignored by the any-order match ("fibre IS not available AT my shop"). */
    private static final java.util.Set<String> STOP = new java.util.HashSet<>(java.util.Arrays.asList(
            "a", "an", "the", "is", "are", "my", "me", "i", "to", "at", "in", "on", "of", "for", "it", "this",
            "hai", "ka", "ki", "ke", "se", "mera", "meri", "है", "का", "की", "के", "मेरा"));

    /** Exact phrase = 1 point per word; same content words in any order = 0.75 per word. */
    static double keywordScore(String text, java.util.Set<String> textWords, String keyword) {
        if (TextNorm.containsPhrase(text, keyword)) return TextNorm.tokens(keyword);
        String[] kw = keyword.split(" ");
        int content = 0;
        for (String w : kw) {
            if (STOP.contains(w)) continue;
            if (!textWords.contains(w)) return 0;
            content++;
        }
        return content >= 2 ? 0.75 * content : 0;
    }

    public Match match(String message, JourneyStage stage) {
        String text = TextNorm.norm(message, kb.synonyms());
        java.util.Set<String> words = new java.util.HashSet<>(java.util.Arrays.asList(text.split(" ")));
        String best = null, second = null;
        double bestScore = 0, secondScore = 0;
        List<String> bestHits = new ArrayList<>();
        for (KbEntry e : kb.all()) {
            double score = 0;
            List<String> hits = new ArrayList<>();
            for (String k : normKeywords.get(e.id)) {
                double ks = keywordScore(text, words, k);
                if (ks > 0) {
                    score += ks;
                    hits.add(k);
                }
            }
            if (score == 0) continue;
            score *= e.boost;
            if (stage != null && e.stage == stage) score += 0.5;
            if (score > bestScore) {
                second = best;
                secondScore = bestScore;
                best = e.id;
                bestScore = score;
                bestHits = hits;
            } else if (score > secondScore) {
                second = e.id;
                secondScore = score;
            }
        }
        double conf = bestScore / (bestScore + 1.0);
        return new Match(best, conf, bestScore, bestHits, second);
    }

    /** Finds a glossary term (mbps, static ip, sla, lock-in, dlt, failover, sd-wan...). */
    public String findTerm(String message) {
        String text = TextNorm.norm(message, kb.synonyms());
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, Map<String, Object>> e : kb.glossary().entrySet()) {
            for (Object k : MiniJson.arr(e.getValue().get("keywords"))) {
                String n = TextNorm.norm((String) k, kb.synonyms());
                if (TextNorm.containsPhrase(text, n) && n.length() > bestLen) {
                    best = e.getKey();
                    bestLen = n.length();
                }
            }
        }
        return best;
    }
}
