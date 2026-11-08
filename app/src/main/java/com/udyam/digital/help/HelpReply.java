package com.udyam.digital.help;

import java.util.ArrayList;
import java.util.List;

/** What the chat screen shows after one user message. */
public final class HelpReply {
    public final String intentId;
    public final double confidence;
    public final String text;
    public final Lang language;
    public final List<String> quickReplies;
    public final String action;
    public final EscalationScorer.Result score;
    /** Non-null when the score reached 70+ (or the user pressed Talk to a person). */
    public final HandoffTicket ticket;
    /** The KB entry the answer came from; the LLM rewrite uses it. */
    public final KbEntry entry;

    public HelpReply(String intentId, double confidence, String text, Lang language, List<String> quickReplies,
                     String action, EscalationScorer.Result score, HandoffTicket ticket, KbEntry entry) {
        this.intentId = intentId;
        this.confidence = confidence;
        this.text = text;
        this.language = language;
        this.quickReplies = new ArrayList<>(quickReplies);
        this.action = action;
        this.score = score;
        this.ticket = ticket;
        this.entry = entry;
    }

    public HelpReply withText(String newText) {
        return new HelpReply(intentId, confidence, newText, language, quickReplies, action, score, ticket, entry);
    }

    public EscalationScorer.Band band() {
        return score.band;
    }
}
