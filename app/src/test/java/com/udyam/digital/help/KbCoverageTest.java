package com.udyam.digital.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * KB sanity checks, and a report of intents that no test mentions yet. The report prints
 * the list instead of failing, so adding a KB intent doesn't break the build.
 */
public class KbCoverageTest {

    @Test public void everyIntentHasEnglishAndHindiAndKeywords() {
        for (KbEntry e : Fixtures.kb().all()) {
            assertFalse(e.id + " en", e.answer(Lang.EN).isEmpty());
            assertFalse(e.id + " hi", e.answer.get("hi") == null || e.answer.get("hi").isEmpty());
            if (!e.id.equals("fallback_unknown")) assertFalse(e.id + " keywords", e.allKeywords().isEmpty());
        }
    }

    @Test public void coversAtLeastTheFortySpecProblems() {
        assertTrue(Fixtures.kb().all().size() >= 40);
    }

    @Test public void everyTopicCardMatchesAnIntent() {
        IntentMatcher m = new IntentMatcher(Fixtures.kb());
        assertEquals(7, Fixtures.kb().topics().size()); // incl. "How the app works"
        for (KnowledgeBase.Topic t : Fixtures.kb().topics()) {
            for (Lang l : new Lang[]{Lang.EN, Lang.HI}) {
                assertFalse(t.id + " label " + l, t.label(l).isEmpty());
                for (String q : t.questions(l)) {
                    IntentMatcher.Match r = m.match(q, null);
                    assertTrue(t.id + "/" + l + ": '" + q + "' -> " + r, r.isConfident());
                    assertFalse("'" + q + "' fell back", "fallback_unknown".equals(r.intentId));
                }
            }
        }
    }

    private static KnowledgeBase.Topic internetTopic() {
        for (KnowledgeBase.Topic t : Fixtures.kb().topics()) if ("internet".equals(t.id)) return t;
        throw new AssertionError("no internet topic");
    }

    @Test public void solvedAndStillButtonsMoveTheScore() {
        for (Lang l : new Lang[]{Lang.EN, Lang.HI}) {
            HelpSession s = Fixtures.session(Fixtures.context(HelpPersonaTest.kiranaPatna(), l, JourneyStage.ACTIVE, null));
            s.handle(internetTopic().questions(l).get(1)); // "Internet is not working"
            HelpReply still = s.handle(Fixtures.kb().phrase("flow_still_msg", l));
            assertEquals(l + " still -> same issue", "internet_down", still.intentId);
            assertTrue(l + " still raises score", still.score.score >= 40);
            HelpReply solved = s.handle(Fixtures.kb().phrase("flow_solved_msg", l));
            assertEquals(l + " solved -> thanks", "thanks", solved.intentId);
            assertTrue(l + " solved lowers score", solved.score.score < still.score.score);
        }
    }

    @Test public void reportIntentsWithoutTests() throws IOException {
        File testDir = new File(Fixtures.assets(), "../../test/java/com/udyam/digital/help");
        StringBuilder all = new StringBuilder();
        File[] files = testDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".java") && !f.getName().equals("KbCoverageTest.java")) {
                    all.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (KbEntry e : Fixtures.kb().all()) {
            if (!all.toString().contains("\"" + e.id + "\"")) missing.add(e.id);
        }
        System.out.println("KB intents: " + Fixtures.kb().all().size() + ", without a test: "
                + (missing.isEmpty() ? "none" : missing));
        assertEquals("sanity: test sources found", true, all.length() > 0);
    }
}
