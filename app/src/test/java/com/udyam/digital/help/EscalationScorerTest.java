package com.udyam.digital.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One test per scoring rule in section 4 of the spec, plus bands and priority. */
public class EscalationScorerTest {

    private final EscalationScorer scorer = new EscalationScorer();

    private static EscalationScorer.Input in() {
        EscalationScorer.Input i = new EscalationScorer.Input();
        i.stage = JourneyStage.QUOTE;
        return i;
    }

    private int score(EscalationScorer.Input i) {
        return scorer.score(i).score;
    }

    @Test public void emptyConversationScoresZero() {
        EscalationScorer.Result r = scorer.score(in());
        assertEquals(0, r.score);
        assertEquals(EscalationScorer.Band.BOT, r.band);
    }

    @Test public void addsIssueBasePoints() {
        EscalationScorer.Input i = in();
        i.issuePoints = 25;
        assertEquals(25, score(i));
    }

    @Test public void plus15PerFailedBotAnswer() {
        EscalationScorer.Input i = in();
        i.failedBotAnswers = 1;
        assertEquals(15, score(i));
        i.failedBotAnswers = 3;
        assertEquals(45, score(i));
    }

    @Test public void plus10OnlyFromThirdTimeSameIntent() {
        EscalationScorer.Input i = in();
        i.sameIntentCount = 2;
        assertEquals(0, score(i));
        i.sameIntentCount = 3;
        assertEquals(10, score(i));
    }

    @Test public void plus15Frustration() {
        EscalationScorer.Input i = in();
        i.frustration = true;
        assertEquals(15, score(i));
    }

    @Test public void plus15LanguageWithoutOfflineKbAndNoLlm() {
        EscalationScorer.Input i = in();
        i.languageWithoutOfflineKb = true;
        assertEquals(15, score(i));
    }

    @Test public void plus20Money() {
        EscalationScorer.Input i = in();
        i.moneyInvolved = true;
        assertEquals(20, score(i));
    }

    @Test public void plus10PriorSupportContact() {
        EscalationScorer.Input i = in();
        i.priorSupportContact = true;
        assertEquals(10, score(i));
    }

    @Test public void plus50OnlyWhenAskingForPersonAgainAfterOneAttempt() {
        EscalationScorer.Input i = in();
        i.explicitHumanRequests = 1;
        assertEquals("first ask: bot tries once", 0, score(i));
        i.explicitHumanRequests = 2;
        assertEquals(50, score(i));
    }

    @Test public void plus100FraudIsAlwaysHandoff() {
        EscalationScorer.Input i = in();
        i.fraud = true;
        EscalationScorer.Result r = scorer.score(i);
        assertEquals(100, r.score);
        assertEquals(EscalationScorer.Band.HANDOFF, r.band);
    }

    @Test public void minus20AcceptedAction() {
        EscalationScorer.Input i = in();
        i.issuePoints = 30;
        i.acceptedAction = true;
        assertEquals(10, score(i));
    }

    @Test public void minus10Thanks() {
        EscalationScorer.Input i = in();
        i.issuePoints = 30;
        i.thanks = true;
        assertEquals(20, score(i));
    }

    @Test public void neverBelowZeroNorAbove100() {
        EscalationScorer.Input i = in();
        i.acceptedAction = true;
        i.thanks = true;
        assertEquals(0, score(i));
        i = in();
        i.fraud = true;
        i.moneyInvolved = true;
        i.frustration = true;
        assertEquals(100, score(i));
    }

    @Test public void forcedHandoffRaisesScoreTo70() {
        EscalationScorer.Input i = in();
        i.issuePoints = 35;
        i.moneyInvolved = true;
        i.forceHandoff = true;
        i.forceReason = "money taken but no order";
        EscalationScorer.Result r = scorer.score(i);
        assertEquals(70, r.score);
        assertEquals(EscalationScorer.Band.HANDOFF, r.band);
        assertTrue(r.reasons.toString().contains("money taken but no order"));
    }

    @Test public void bandBoundaries() {
        EscalationScorer.Input i = in();
        i.issuePoints = 39;
        assertEquals(EscalationScorer.Band.BOT, scorer.score(i).band);
        i.issuePoints = 40;
        assertEquals(EscalationScorer.Band.CALLBACK, scorer.score(i).band);
        i.issuePoints = 69;
        assertEquals(EscalationScorer.Band.CALLBACK, scorer.score(i).band);
        i.issuePoints = 70;
        assertEquals(EscalationScorer.Band.HANDOFF, scorer.score(i).band);
    }

    @Test public void priorityHighForBigBundleInstallPaymentOrMoneyDispute() {
        EscalationScorer.Input i = in();
        assertEquals(EscalationScorer.Priority.NORMAL, scorer.score(i).priority);
        i.monthlyBundleValue = 15000;
        assertEquals(EscalationScorer.Priority.HIGH, scorer.score(i).priority);
        i = in();
        i.stage = JourneyStage.INSTALL;
        assertEquals(EscalationScorer.Priority.HIGH, scorer.score(i).priority);
        i = in();
        i.stage = JourneyStage.PAYMENT;
        assertEquals(EscalationScorer.Priority.HIGH, scorer.score(i).priority);
        i = in();
        i.moneyDispute = true;
        assertEquals(EscalationScorer.Priority.HIGH, scorer.score(i).priority);
    }

    @Test public void priorityNeverDecidesWhetherSomeoneGetsHelp() {
        // Rs 2,500/month kirana owner, NORMAL priority, still handed over at 70+.
        EscalationScorer.Input i = in();
        i.monthlyBundleValue = 2500;
        i.issuePoints = 40;
        i.failedBotAnswers = 2;
        EscalationScorer.Result r = scorer.score(i);
        assertEquals(EscalationScorer.Priority.NORMAL, r.priority);
        assertEquals(EscalationScorer.Band.HANDOFF, r.band);
    }

    @Test public void everyPointHasAReason() {
        EscalationScorer.Input i = in();
        i.failedBotAnswers = 1;
        i.frustration = true;
        i.moneyInvolved = true;
        EscalationScorer.Result r = scorer.score(i);
        assertEquals(50, r.score);
        assertEquals(3, r.reasons.size());
    }
}
