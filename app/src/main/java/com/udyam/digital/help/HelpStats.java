package com.udyam.digital.help;

import java.util.LinkedHashMap;
import java.util.Map;

/** Numbers for the "Help stats" debug card and the business-case slide. */
public final class HelpStats {
    private int chats;
    private int messages;
    private int handoffs;
    private long scoreSum;
    private int scoreCount;
    private final Map<String, Integer> handoffsByReason = new LinkedHashMap<>();

    public synchronized void chatStarted() {
        chats++;
    }

    public synchronized void messageScored(int score) {
        messages++;
        scoreSum += score;
        scoreCount++;
    }

    public synchronized void handoff(String reason) {
        handoffs++;
        Integer v = handoffsByReason.get(reason);
        handoffsByReason.put(reason, v == null ? 1 : v + 1);
    }

    public synchronized int chats() {
        return chats;
    }

    public synchronized int handoffs() {
        return handoffs;
    }

    /** Share of chats that ended without a person. */
    public synchronized int percentResolvedByBot() {
        if (chats == 0) return 0;
        return Math.max(0, Math.round(100f * (chats - Math.min(handoffs, chats)) / chats));
    }

    public synchronized double averageScore() {
        return scoreCount == 0 ? 0 : (double) scoreSum / scoreCount;
    }

    public synchronized Map<String, Integer> handoffsByReason() {
        return new LinkedHashMap<>(handoffsByReason);
    }

    public synchronized String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chats", chats);
        m.put("messages", messages);
        m.put("handoffs", handoffs);
        m.put("score_sum", scoreSum);
        m.put("score_count", scoreCount);
        m.put("by_reason", new LinkedHashMap<String, Object>(handoffsByReason));
        return MiniJson.write(m);
    }

    public static HelpStats fromJson(String json) {
        HelpStats s = new HelpStats();
        if (json == null || json.isEmpty()) return s;
        try {
            Map<String, Object> m = MiniJson.obj(MiniJson.parse(json));
            s.chats = MiniJson.num(m.get("chats"), 0);
            s.messages = MiniJson.num(m.get("messages"), 0);
            s.handoffs = MiniJson.num(m.get("handoffs"), 0);
            s.scoreSum = MiniJson.num(m.get("score_sum"), 0);
            s.scoreCount = MiniJson.num(m.get("score_count"), 0);
            for (Map.Entry<String, Object> e : MiniJson.obj(m.get("by_reason")).entrySet()) {
                s.handoffsByReason.put(e.getKey(), MiniJson.num(e.getValue(), 0));
            }
        } catch (RuntimeException ignored) {
            // Corrupt prefs: start fresh rather than crash the help screen.
        }
        return s;
    }
}
