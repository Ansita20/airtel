package com.udyam.digital.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One knowledge-base intent from assets/help_kb.json. */
public final class KbEntry {

    /** Extra points when something about this issue gets worse (data-driven escalation rules). */
    public static final class Condition {
        /** COUNTER_AT_LEAST | FIX_FAILED_AT_LEAST | FLAG | DAYS_LATE_OVER | DAYS_SINCE_ORDER_OVER */
        public final String when;
        public final String counter;
        public final int value;
        public final int points;
        /** Spec says "escalate": the score is raised to at least 70 so a person takes over. */
        public final boolean forceHandoff;
        public final String reason;

        Condition(String when, String counter, int value, int points, boolean forceHandoff, String reason) {
            this.when = when;
            this.counter = counter;
            this.value = value;
            this.points = points;
            this.forceHandoff = forceHandoff;
            this.reason = reason;
        }
    }

    public final String id;
    public final String title;
    public final JourneyStage stage; // null = any stage
    public final Map<String, List<String>> keywords; // en / hi / hinglish
    public final Map<String, String> answer;         // en / hi
    /** Optional: what to say when the user reports the first answer didn't work. */
    public final Map<String, String> followupAnswer; // en / hi
    public final Map<String, List<String>> quickReplies; // en / hi
    public final String action;
    public final int baseEscalationPoints;
    public final boolean canBotResolve;
    public final boolean moneyInvolved;
    /** Keyword weight multiplier (fraud and abuse must win over everything else). */
    public final double boost;
    public final List<Condition> conditions;

    KbEntry(String id, String title, JourneyStage stage, Map<String, List<String>> keywords,
            Map<String, String> answer, Map<String, String> followupAnswer, Map<String, List<String>> quickReplies, String action,
            int baseEscalationPoints, boolean canBotResolve, boolean moneyInvolved, double boost,
            List<Condition> conditions) {
        this.id = id;
        this.title = title;
        this.stage = stage;
        this.keywords = keywords;
        this.answer = answer;
        this.followupAnswer = followupAnswer;
        this.quickReplies = quickReplies;
        this.action = action;
        this.baseEscalationPoints = baseEscalationPoints;
        this.canBotResolve = canBotResolve;
        this.moneyInvolved = moneyInvolved;
        this.boost = boost;
        this.conditions = Collections.unmodifiableList(conditions);
    }

    public String answer(Lang l) {
        String a = answer.get(l.code);
        return a != null ? a : answer.get("en");
    }

    /** Null when the KB has no follow-up text for this intent. */
    public String followupAnswer(Lang l) {
        if (followupAnswer.isEmpty()) return null;
        String a = followupAnswer.get(l.code);
        return a != null ? a : followupAnswer.get("en");
    }

    public List<String> quickReplies(Lang l) {
        List<String> q = quickReplies.get(l.code);
        return q != null ? q : quickReplies.getOrDefault("en", Collections.<String>emptyList());
    }

    public List<String> allKeywords() {
        List<String> all = new ArrayList<>();
        for (List<String> k : keywords.values()) all.addAll(k);
        return all;
    }

    static KbEntry fromJson(Map<String, Object> j) {
        Map<String, List<String>> kw = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : MiniJson.obj(j.get("keywords")).entrySet()) {
            kw.put(e.getKey(), strings(e.getValue()));
        }
        Map<String, String> ans = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : MiniJson.obj(j.get("answer")).entrySet()) {
            ans.put(e.getKey(), MiniJson.str(e.getValue(), ""));
        }
        Map<String, String> fu = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : MiniJson.obj(j.get("answer_followup")).entrySet()) {
            fu.put(e.getKey(), MiniJson.str(e.getValue(), ""));
        }
        Map<String, List<String>> qr = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : MiniJson.obj(j.get("quick_replies")).entrySet()) {
            qr.put(e.getKey(), strings(e.getValue()));
        }
        List<Condition> conds = new ArrayList<>();
        for (Object o : MiniJson.arr(j.get("conditions"))) {
            Map<String, Object> c = MiniJson.obj(o);
            conds.add(new Condition(
                    MiniJson.str(c.get("when"), ""), MiniJson.str(c.get("counter"), null),
                    MiniJson.num(c.get("value"), 0), MiniJson.num(c.get("points"), 0),
                    MiniJson.bool(c.get("force_handoff"), false), MiniJson.str(c.get("reason"), "")));
        }
        String stage = MiniJson.str(j.get("stage"), "ANY");
        return new KbEntry(
                MiniJson.str(j.get("id"), ""), MiniJson.str(j.get("title"), ""),
                "ANY".equals(stage) ? null : JourneyStage.valueOf(stage), kw, ans, fu, qr,
                MiniJson.str(j.get("action"), "NONE"), MiniJson.num(j.get("base_escalation_points"), 0),
                MiniJson.bool(j.get("can_bot_resolve"), true), MiniJson.bool(j.get("money"), false),
                j.get("boost") instanceof Number ? ((Number) j.get("boost")).doubleValue() : 1.0, conds);
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        for (Object x : MiniJson.arr(o)) if (x instanceof String) out.add((String) x);
        return out;
    }
}
