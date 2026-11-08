package com.udyam.digital.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides, with a visible 0-100 score, whether the user really needs a human agent.
 * Pure Java: no Android imports, no access to the person's name or any personal trait.
 * The only inputs are what happened in the conversation and the order (see {@link Input}).
 *
 *   0-39   BOT        bot handles it
 *  40-69   CALLBACK   bot keeps helping and offers a callback with time slots
 *  70-100  HANDOFF    create a HandoffTicket now
 *
 * Priority is separate: it orders the agent queue and never decides WHETHER someone is helped.
 */
public final class EscalationScorer {

    public enum Band { BOT, CALLBACK, HANDOFF }

    public enum Priority { NORMAL, HIGH }

    public static final int CALLBACK_AT = 40;
    public static final int HANDOFF_AT = 70;
    public static final int HIGH_VALUE_BUNDLE_RS = 15000;

    public static final int PER_FAILED_ANSWER = 15;
    public static final int SAME_INTENT_THIRD_TIME = 10;
    public static final int FRUSTRATION = 15;
    public static final int LANGUAGE_NO_OFFLINE_KB = 15;
    public static final int MONEY = 20;
    public static final int PRIOR_CONTACT = 10;
    public static final int EXPLICIT_REQUEST = 50;
    public static final int FRAUD = 100;
    public static final int ACCEPTED_ACTION = -20;
    public static final int THANKS = -10;

    /** Snapshot of the conversation. Deliberately has no field for name, religion, caste, gender... */
    public static final class Input {
        /** base_escalation_points + condition points of the worst open issue. */
        public int issuePoints;
        public final List<String> issueReasons = new ArrayList<>();
        /** A KB rule says this issue must go to a person (spec: "escalate"). */
        public boolean forceHandoff;
        public String forceReason = "";
        public int failedBotAnswers;
        /** How many times the current intent has come up this session. */
        public int sameIntentCount;
        public boolean frustration;
        /** User's language has no offline KB and the LLM is unavailable. */
        public boolean languageWithoutOfflineKb;
        public boolean moneyInvolved;
        public boolean priorSupportContact;
        public int explicitHumanRequests;
        public boolean fraud;
        public boolean acceptedAction;
        public boolean thanks;
        // ---- priority only ----
        public int monthlyBundleValue;
        public JourneyStage stage;
        public boolean moneyDispute;
    }

    public static final class Result {
        public final int score;
        public final Band band;
        public final Priority priority;
        public final List<String> reasons;

        Result(int score, Band band, Priority priority, List<String> reasons) {
            this.score = score;
            this.band = band;
            this.priority = priority;
            this.reasons = Collections.unmodifiableList(reasons);
        }

        @Override
        public String toString() {
            return score + " " + band + " " + priority + " " + reasons;
        }
    }

    public Result score(Input in) {
        List<String> why = new ArrayList<>();
        int s = 0;

        if (in.issuePoints != 0) {
            s += in.issuePoints;
            if (in.issueReasons.isEmpty()) why.add(signed(in.issuePoints) + " issue");
            else why.addAll(in.issueReasons);
        }
        if (in.failedBotAnswers > 0) {
            int p = PER_FAILED_ANSWER * in.failedBotAnswers;
            s += p;
            why.add(signed(p) + " " + in.failedBotAnswers + " failed bot answer(s)");
        }
        if (in.sameIntentCount >= 3) {
            s += SAME_INTENT_THIRD_TIME;
            why.add(signed(SAME_INTENT_THIRD_TIME) + " same problem raised " + in.sameIntentCount + " times");
        }
        if (in.frustration) {
            s += FRUSTRATION;
            why.add(signed(FRUSTRATION) + " frustration words");
        }
        if (in.languageWithoutOfflineKb) {
            s += LANGUAGE_NO_OFFLINE_KB;
            why.add(signed(LANGUAGE_NO_OFFLINE_KB) + " no offline answers in user's language and AI unavailable");
        }
        if (in.moneyInvolved) {
            s += MONEY;
            why.add(signed(MONEY) + " money involved");
        }
        if (in.priorSupportContact) {
            s += PRIOR_CONTACT;
            why.add(signed(PRIOR_CONTACT) + " already contacted support about this");
        }
        if (in.explicitHumanRequests >= 2) {
            s += EXPLICIT_REQUEST;
            why.add(signed(EXPLICIT_REQUEST) + " asked for a person again after the bot tried once");
        }
        if (in.fraud) {
            s += FRAUD;
            why.add(signed(FRAUD) + " fraud or safety");
        }
        if (in.acceptedAction) {
            s += ACCEPTED_ACTION;
            why.add(signed(ACCEPTED_ACTION) + " accepted the bot's suggestion");
        }
        if (in.thanks) {
            s += THANKS;
            why.add(signed(THANKS) + " said thanks / ok");
        }

        s = Math.max(0, Math.min(100, s));
        if (in.forceHandoff && s < HANDOFF_AT) {
            why.add("raised to " + HANDOFF_AT + ": " + in.forceReason);
            s = HANDOFF_AT;
        }

        Band band = s >= HANDOFF_AT ? Band.HANDOFF : s >= CALLBACK_AT ? Band.CALLBACK : Band.BOT;
        return new Result(s, band, priority(in), why);
    }

    /** Orders the agent queue only. A Rs 2,500/month kirana owner still gets a person at 70+. */
    public Priority priority(Input in) {
        if (in.monthlyBundleValue >= HIGH_VALUE_BUNDLE_RS) return Priority.HIGH;
        if (in.stage == JourneyStage.INSTALL || in.stage == JourneyStage.PAYMENT) return Priority.HIGH;
        if (in.moneyDispute) return Priority.HIGH;
        return Priority.NORMAL;
    }

    private static String signed(int p) {
        return p >= 0 ? "+" + p : String.valueOf(p);
    }
}
