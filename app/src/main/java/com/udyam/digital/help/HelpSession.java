package com.udyam.digital.help;

import com.udyam.digital.help.core.GstinLookup;
import com.udyam.digital.help.core.Recommendation;
import com.udyam.digital.help.core.RulesEngine;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One help conversation. Pure Java so the whole flow (persona tests) runs on the JVM.
 * For each user message: language -> intent -> counters -> answer -> score -> maybe ticket.
 */
public final class HelpSession {

    /** Intents that are small talk, not a problem the user is trying to solve. */
    static final List<String> GENERAL = java.util.Arrays.asList(
            "greeting", "thanks", "are_you_bot", "off_topic", "fallback_unknown", "plan_choose");

    private static final AtomicInteger TICKET_SEQ = new AtomicInteger(1000);

    /** One open problem in this chat. */
    static final class Issue {
        final KbEntry entry;
        boolean accepted;

        Issue(KbEntry entry) {
            this.entry = entry;
        }
    }

    private final KnowledgeBase kb;
    private final IntentMatcher matcher;
    private final Signals signals;
    private final AnswerComposer composer;
    private final EscalationScorer scorer = new EscalationScorer();
    private final HelpContext ctx;
    private final HelpStats stats;

    private final Map<String, Issue> openIssues = new LinkedHashMap<>();
    private final Map<String, Integer> intentCounts = new LinkedHashMap<>();
    private final List<String> botActions = new ArrayList<>();
    private final List<String> userMessages = new ArrayList<>();
    private String lastIntent;
    private String lastUserNorm = "";
    private boolean frustrationSeen;
    private boolean fraudSeen;
    private int humanRequests;
    private boolean thanksThisTurn;
    private Recommendation pendingProposal;
    private HandoffTicket ticket;
    private EscalationScorer.Result lastScore;
    private Lang lastLang = Lang.EN;

    public HelpSession(KnowledgeBase kb, RulesEngine engine, GstinLookup gstin, HelpContext ctx, HelpStats stats) {
        this.kb = kb;
        this.matcher = new IntentMatcher(kb);
        this.signals = new Signals(kb);
        this.composer = new AnswerComposer(kb, engine, matcher, gstin);
        this.ctx = ctx;
        this.stats = stats == null ? new HelpStats() : stats;
        this.stats.chatStarted();
        this.lastScore = scorer.score(new EscalationScorer.Input());
    }

    public HelpContext context() {
        return ctx;
    }

    public KnowledgeBase knowledgeBase() {
        return kb;
    }

    public AnswerComposer composer() {
        return composer;
    }

    public EscalationScorer.Result lastScore() {
        return lastScore;
    }

    public HandoffTicket ticket() {
        return ticket;
    }

    public Recommendation pendingProposal() {
        return pendingProposal;
    }

    /** Quick-reply chips shown when Help opens, based on the journey stage. */
    public List<String> openingChips() {
        return kb.stageChips(ctx.stage, answerLanguage(LanguageDetector.detect(ctx.appLanguage, "")));
    }

    /** Language for flow screens (topic cards etc.), which exist in English and Hindi only. */
    public Lang uiLanguage() {
        Lang l = ctx.appLanguage == null ? Lang.EN : ctx.appLanguage;
        return l.offlineFallback();
    }

    public String greeting() {
        Lang l = answerLanguage(LanguageDetector.detect(ctx.appLanguage, ""));
        return kb.phrase("welcome", l);
    }

    private Lang answerLanguage(Lang user) {
        return user.offlineKb || ctx.llmAvailable ? user : user.offlineFallback();
    }

    // ------------------------------------------------------------------ main entry

    public HelpReply handle(String message) {
        ctx.messagesThisSession++;
        userMessages.add(message);
        Lang userLang = LanguageDetector.detect(ctx.appLanguage, message);
        Lang lang = answerLanguage(userLang);
        lastLang = userLang;
        boolean langPenalty = !userLang.offlineKb && !ctx.llmAvailable;

        IntentMatcher.Match match = matcher.match(message, ctx.stage);
        boolean negative = signals.negativeFeedback(message);
        boolean positive = signals.positiveFeedback(message);
        boolean frustrated = signals.frustration(message);
        String norm = TextNorm.norm(message, kb.synonyms());
        boolean repeated = !norm.isEmpty() && norm.equals(lastUserNorm);
        lastUserNorm = norm;

        // ---- which intent?
        String intent;
        if (match.isConfident()) {
            intent = match.intentId;
            // "no, still not working" right after a fix: stay on the same problem.
            if (negative && lastIntent != null && !GENERAL.contains(lastIntent) && intent.equals("thanks")) intent = lastIntent;
        } else if ((negative || repeated) && lastIntent != null && !GENERAL.contains(lastIntent)) {
            intent = lastIntent;
        } else if (positive) {
            intent = "thanks";
        } else {
            intent = "fallback_unknown";
        }
        KbEntry entry = kb.get(intent);

        // ---- failed bot answer?
        boolean followUpFail = lastIntent != null && (negative || repeated) && intent.equals(lastIntent) && !positive;
        boolean notUnderstood = !match.isConfident() && !positive && !intent.equals(lastIntent);
        if (followUpFail || notUnderstood) ctx.failedBotAnswers++;
        if (followUpFail) ctx.bump("fix_failed:" + intent);

        frustrationSeen |= frustrated;
        if (intent.equals("fraud_otp")) fraudSeen = true;
        thanksThisTurn = positive && !negative;
        if (intent.equals("human_request")) humanRequests++;

        Integer prev = intentCounts.get(intent);
        intentCounts.put(intent, prev == null ? 1 : prev + 1);
        updateCounters(intent, message);

        // ---- issues
        if (!GENERAL.contains(intent)) {
            Issue is = openIssues.get(intent);
            if (is == null) {
                openIssues.put(intent, new Issue(entry));
            } else {
                is.accepted = false; // came back: the earlier fix did not hold
            }
        }
        if (thanksThisTurn) {
            for (Issue is : new ArrayList<>(openIssues.values())) {
                if (is.entry.canBotResolve && !forced(is.entry)) openIssues.remove(is.entry.id);
            }
        }

        // ---- answer
        AnswerComposer.Composed c = composer.compose(entry, ctx, message, lang, followUpFail);
        if (c.proposal != null) pendingProposal = c.proposal;
        botActions.add(c.botAction);
        lastIntent = intent;

        // ---- score
        lastScore = scorer.score(buildInput(intent, langPenalty));
        stats.messageScored(lastScore.score);

        StringBuilder text = new StringBuilder(c.text);
        List<String> chips = new ArrayList<>(c.quickReplies);
        if (langPenalty) {
            text.append("\n\n").append(kb.phrase("lang_agent_offer", lang)
                    .replace("{lang_name}", userLang.displayName)
                    .replace("{fallback_lang}", lang.displayName));
        }
        HandoffTicket newTicket = null;
        if (lastScore.band == EscalationScorer.Band.HANDOFF) {
            newTicket = createTicket(intent, "score");
            // KB answers for fraud, abuse, money-taken already say "connecting you": add just the ticket.
            String key = "OFFER_HANDOFF".equals(entry.action) && !entry.canBotResolve ? "handoff_ticket_only" : "handoff_now";
            text.append("\n\n").append(kb.phrase(key, lang)
                    .replace("{ticket_id}", newTicket.ticketId)
                    .replace("{wait_minutes}", String.valueOf(newTicket.expectedWaitMinutes())));
            chips.add(0, kb.phrase("chip_callback", lang));
            chips.add(1, kb.phrase("chip_chat_agent", lang));
        } else if (lastScore.band == EscalationScorer.Band.CALLBACK) {
            text.append("\n\n").append(kb.phrase("offer_callback", lang));
            chips.add(0, kb.phrase("chip_callback", lang));
        }
        String talk = kb.phrase("chip_talk_person", lang);
        if (!chips.contains(talk)) chips.add(talk);

        return new HelpReply(intent, match.confidence, text.toString(), lang, dedupe(chips), c.action, lastScore, newTicket, entry);
    }

    /** "Talk to a person" button: always allowed, never blocked, whatever the score. */
    public HelpReply requestHuman() {
        Lang lang = answerLanguage(lastLang);
        String intent = lastIntent == null ? "human_request" : lastIntent;
        HandoffTicket t = createTicket(intent, "button");
        String text = kb.phrase("handoff_now", lang)
                .replace("{ticket_id}", t.ticketId)
                .replace("{wait_minutes}", String.valueOf(t.expectedWaitMinutes()));
        List<String> chips = new ArrayList<>();
        chips.add(kb.phrase("chip_callback", lang));
        chips.add(kb.phrase("chip_chat_agent", lang));
        return new HelpReply(intent, 1.0, text, lang, chips, "OFFER_HANDOFF", lastScore, t, kb.get("human_request"));
    }

    /** UI: the user accepted the bot's suggested action (opened tracker, re-uploaded, took cheaper plan). */
    public EscalationScorer.Result acceptAction(String intentId) {
        Issue is = openIssues.get(intentId);
        if (is != null) is.accepted = true;
        if (pendingProposal != null && ("too_expensive".equals(intentId) || "change_quantity".equals(intentId)
                || "fibre_not_available".equals(intentId) || "no_gstin".equals(intentId) || "gstin_cancelled".equals(intentId))) {
            ctx.recommendation = pendingProposal;
            pendingProposal = null;
        }
        botActions.add("Customer accepted: " + intentId);
        thanksThisTurn = false;
        lastScore = scorer.score(buildInput(intentId, !lastLang.offlineKb && !ctx.llmAvailable));
        return lastScore;
    }

    /** KYC/payment systems report events into the chat context (e.g. a second name mismatch). */
    public void onKycCheckFailed(HelpContext.KycCheck check) {
        ctx.failedChecks.add(check);
        ctx.kycStatus = HelpContext.KycStatus.FAILED;
        if (check == HelpContext.KycCheck.NAME_MISMATCH) ctx.bump("name_mismatch");
        if (check == HelpContext.KycCheck.GSTIN_NOT_FOUND) ctx.bump("gstin_invalid");
    }

    public void setCallbackSlot(String slot) {
        if (ticket != null) ticket.callbackSlot = slot;
    }

    // ------------------------------------------------------------------ internals

    private void updateCounters(String intent, String message) {
        switch (intent) {
            case "gstin_invalid":
                ctx.bump("gstin_invalid");
                break;
            case "kyc_name_mismatch":
                if (ctx.counter("name_mismatch") == 0) ctx.bump("name_mismatch");
                break;
            case "payment_failed":
                ctx.bump("payment_failed");
                break;
            case "payment_deducted_no_order":
                ctx.flags.put("money_taken_no_order", true);
                break;
            default:
                break;
        }
    }

    /** Points from base_escalation_points plus every KB condition that currently holds. */
    int issuePoints(KbEntry e, List<String> reasons) {
        int p = e.baseEscalationPoints;
        if (p != 0) reasons.add((p > 0 ? "+" : "") + p + " " + e.title + " (KB base)");
        for (KbEntry.Condition c : e.conditions) {
            if (holds(c, e)) {
                p += c.points;
                if (c.points != 0) reasons.add("+" + c.points + " " + c.reason);
            }
        }
        return p;
    }

    private boolean forced(KbEntry e) {
        for (KbEntry.Condition c : e.conditions) if (c.forceHandoff && holds(c, e)) return true;
        return false;
    }

    private String forceReason(KbEntry e) {
        for (KbEntry.Condition c : e.conditions) if (c.forceHandoff && holds(c, e)) return c.reason;
        return "";
    }

    boolean holds(KbEntry.Condition c, KbEntry e) {
        switch (c.when) {
            case "ALWAYS": return true;
            case "COUNTER_AT_LEAST": return ctx.counter(c.counter) >= c.value;
            case "FIX_FAILED_AT_LEAST": return ctx.counter("fix_failed:" + e.id) >= c.value;
            case "FLAG": return ctx.flag(c.counter);
            case "DAYS_LATE_OVER": return ctx.installPending() && ctx.daysLate() > c.value;
            case "DAYS_SINCE_ORDER_OVER": return ctx.installPending() && ctx.daysSinceOrder() > c.value;
            case "REPEAT_AT_LEAST": {
                Integer n = intentCounts.get(e.id);
                return n != null && n >= c.value;
            }
            default: return false;
        }
    }

    EscalationScorer.Input buildInput(String currentIntent, boolean langPenalty) {
        EscalationScorer.Input in = new EscalationScorer.Input();
        // Worst open issue decides the issue points.
        int best = Integer.MIN_VALUE;
        List<String> bestReasons = new ArrayList<>();
        boolean money = false, force = false;
        String forceWhy = "";
        List<KbEntry> considered = new ArrayList<>();
        for (Issue is : openIssues.values()) considered.add(is.entry);
        if (kb.has(currentIntent) && !openIssues.containsKey(currentIntent)) considered.add(kb.get(currentIntent));
        for (KbEntry e : considered) {
            List<String> r = new ArrayList<>();
            int p = issuePoints(e, r);
            if (p > best) {
                best = p;
                bestReasons = r;
            }
            money |= e.moneyInvolved;
            if (!force && forced(e)) {
                force = true;
                forceWhy = forceReason(e);
            }
        }
        in.issuePoints = best == Integer.MIN_VALUE ? 0 : best;
        in.issueReasons.addAll(bestReasons);
        if (ctx.appEscalationPoints > 0) {
            in.issuePoints += ctx.appEscalationPoints;
            in.issueReasons.add(ctx.appEscalationReason + " (+" + ctx.appEscalationPoints + ")");
        }
        in.forceHandoff = force;
        in.forceReason = forceWhy;
        in.failedBotAnswers = ctx.failedBotAnswers;
        Integer cnt = intentCounts.get(currentIntent);
        in.sameIntentCount = GENERAL.contains(currentIntent) || cnt == null ? 0 : cnt;
        in.frustration = frustrationSeen;
        in.languageWithoutOfflineKb = langPenalty;
        in.moneyInvolved = money;
        Integer prior = ctx.priorSupportContacts.get(currentIntent);
        in.priorSupportContact = prior != null && prior > 0;
        in.explicitHumanRequests = humanRequests;
        in.fraud = fraudSeen;
        Issue cur = openIssues.get(currentIntent);
        in.acceptedAction = cur != null && cur.accepted;
        in.thanks = thanksThisTurn;
        in.monthlyBundleValue = ctx.monthlyBundleValue();
        in.stage = ctx.stage;
        in.moneyDispute = money;
        return in;
    }

    private HandoffTicket createTicket(String intent, String trigger) {
        if (ticket != null) return ticket; // one ticket per conversation
        KbEntry e = kb.has(intent) ? kb.get(intent) : kb.get("human_request");
        String id = "HT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + TICKET_SEQ.incrementAndGet();
        ticket = new HandoffTicket(id, lastScore.score, lastScore.reasons, intent, e.title, ctx.toMaskedMap(), ctx.stage,
                summary(e), lastLang, lastScore.priority, trigger);
        stats.handoff("button".equals(trigger) ? "Talk to a person button" : e.title);
        return ticket;
    }

    /** Three lines for the agent: the issue, what the bot tried, the customer's situation. */
    List<String> summary(KbEntry e) {
        Integer n = intentCounts.get(e.id);
        String last = userMessages.isEmpty() ? "" : Masking.freeText(userMessages.get(userMessages.size() - 1));
        if (last.length() > 80) last = last.substring(0, 77) + "...";
        String issue = "Issue: " + e.title + " (raised " + (n == null ? 1 : n) + "x). Last message: \"" + last + "\"";

        List<String> tried = new ArrayList<>();
        for (String a : botActions) if (!tried.contains(a) && a != null && !a.isEmpty()) tried.add(a);
        if (tried.size() > 3) tried = tried.subList(tried.size() - 3, tried.size());
        String botLine = "Bot tried: " + (tried.isEmpty() ? "nothing yet" : String.join("; ", tried));

        StringBuilder who = new StringBuilder("Customer: ");
        if (ctx.profile != null) {
            who.append(ctx.profile.businessType).append(", ").append(ctx.profile.city)
                    .append(" (Tier ").append(ctx.profile.cityTier()).append("), ")
                    .append(ctx.profile.employees).append(" staff, ").append(ctx.profile.sites).append(" site(s); ");
        }
        who.append("stage ").append(ctx.stage);
        if (ctx.recommendation != null) who.append(", bundle Rs ").append(AnswerComposer.rs(ctx.recommendation.monthlyTotal())).append("/month");
        if (frustrationSeen) who.append(", frustrated");
        who.append(", prefers ").append(lastLang.displayName);
        List<String> out = new ArrayList<>();
        out.add(issue);
        out.add(botLine);
        out.add(who.toString());
        return out;
    }

    private static List<String> dedupe(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (s != null && !s.isEmpty() && !out.contains(s)) out.add(s);
        return Collections.unmodifiableList(out);
    }
}
