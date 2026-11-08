package com.udyam.digital.help;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the agent receives, so they never re-ask anything. Contains masked data only. */
public final class HandoffTicket {
    public final String ticketId;
    public final int score;
    public final List<String> reasons;
    public final String intentId;
    public final String intentTitle;
    public final Map<String, Object> maskedProfile;
    public final JourneyStage stage;
    /** Exactly three lines: the issue, what the bot tried, the customer's situation. */
    public final List<String> summary;
    public final Lang preferredLanguage;
    public final EscalationScorer.Priority priority;
    /** "button" when the user pressed Talk to a person; otherwise "score". */
    public final String trigger;
    public String callbackSlot = "";

    public HandoffTicket(String ticketId, int score, List<String> reasons, String intentId, String intentTitle,
                         Map<String, Object> maskedProfile, JourneyStage stage, List<String> summary,
                         Lang preferredLanguage, EscalationScorer.Priority priority, String trigger) {
        this.ticketId = ticketId;
        this.score = score;
        this.reasons = new ArrayList<>(reasons);
        this.intentId = intentId;
        this.intentTitle = intentTitle;
        this.maskedProfile = maskedProfile;
        this.stage = stage;
        this.summary = new ArrayList<>(summary);
        this.preferredLanguage = preferredLanguage;
        this.priority = priority;
        this.trigger = trigger;
    }

    /** Demo estimate of queue time shown to the user. */
    public int expectedWaitMinutes() {
        return priority == EscalationScorer.Priority.HIGH ? 5 : 15;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ticket_id", ticketId);
        m.put("score", score);
        m.put("reasons", reasons);
        m.put("intent", intentId);
        m.put("intent_title", intentTitle);
        m.put("stage", stage.name());
        m.put("priority", priority.name());
        m.put("language", preferredLanguage.displayName);
        m.put("callback_slot", callbackSlot);
        m.put("trigger", trigger);
        m.put("summary", summary);
        m.put("profile", maskedProfile);
        return m;
    }

    public String toJson() {
        return MiniJson.write(toMap());
    }
}
