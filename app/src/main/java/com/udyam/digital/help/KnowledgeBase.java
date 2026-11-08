package com.udyam.digital.help;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads assets/help_kb.json: intents, glossary, shared phrases, stage chips. */
public final class KnowledgeBase {

    /** A topic card in the step-by-step help flow, with its problem cards. */
    public static final class Topic {
        public final String id;
        public final String icon;
        private final Map<String, Object> label;
        private final Map<String, Object> questions;

        Topic(Map<String, Object> j) {
            id = MiniJson.str(j.get("id"), "");
            icon = MiniJson.str(j.get("icon"), "");
            label = MiniJson.obj(j.get("label"));
            questions = MiniJson.obj(j.get("questions"));
        }

        public String label(Lang l) {
            String s = MiniJson.str(label.get(l.code), null);
            return s != null ? s : MiniJson.str(label.get("en"), id);
        }

        public List<String> questions(Lang l) {
            List<Object> raw = MiniJson.arr(questions.containsKey(l.code) ? questions.get(l.code) : questions.get("en"));
            List<String> out = new ArrayList<>();
            for (Object o : raw) out.add((String) o);
            return out;
        }
    }

    private final List<Topic> topics = new ArrayList<>();
    private final Map<String, KbEntry> entries = new LinkedHashMap<>();
    /** term -> {en, hi, keywords[]} */
    private final Map<String, Map<String, Object>> glossary = new LinkedHashMap<>();
    /** key -> {en, hi}: shared sentences (reasons, notes, fallbacks). */
    private final Map<String, Map<String, Object>> phrases = new LinkedHashMap<>();
    /** stage -> {en:[...], hi:[...]} */
    private final Map<String, Map<String, Object>> stageChips = new LinkedHashMap<>();
    /** Signal word lists: negative_feedback, positive_feedback, frustration, human_request. */
    private final Map<String, List<String>> signals = new LinkedHashMap<>();
    /** Canonical word -> spelling variants (Hinglish). */
    private final Map<String, List<String>> synonyms = new LinkedHashMap<>();
    private String version = "";

    public static KnowledgeBase load(InputStream in) throws IOException {
        return parse(readAll(in));
    }

    public static KnowledgeBase parse(String json) {
        KnowledgeBase kb = new KnowledgeBase();
        Map<String, Object> root = MiniJson.obj(MiniJson.parse(json));
        kb.version = MiniJson.str(root.get("version"), "");
        for (Object o : MiniJson.arr(root.get("intents"))) {
            KbEntry e = KbEntry.fromJson(MiniJson.obj(o));
            if (kb.entries.containsKey(e.id)) throw new IllegalStateException("Duplicate intent " + e.id);
            kb.entries.put(e.id, e);
        }
        for (Map.Entry<String, Object> e : MiniJson.obj(root.get("glossary")).entrySet()) {
            kb.glossary.put(e.getKey(), MiniJson.obj(e.getValue()));
        }
        for (Map.Entry<String, Object> e : MiniJson.obj(root.get("phrases")).entrySet()) {
            kb.phrases.put(e.getKey(), MiniJson.obj(e.getValue()));
        }
        for (Map.Entry<String, Object> e : MiniJson.obj(root.get("stage_chips")).entrySet()) {
            kb.stageChips.put(e.getKey(), MiniJson.obj(e.getValue()));
        }
        for (Map.Entry<String, Object> e : MiniJson.obj(root.get("signals")).entrySet()) {
            List<String> words = new ArrayList<>();
            for (Object w : MiniJson.arr(e.getValue())) words.add((String) w);
            kb.signals.put(e.getKey(), words);
        }
        for (Object o : MiniJson.arr(root.get("topics"))) kb.topics.add(new Topic(MiniJson.obj(o)));
        for (Map.Entry<String, Object> e : MiniJson.obj(root.get("synonyms")).entrySet()) {
            List<String> words = new ArrayList<>();
            for (Object w : MiniJson.arr(e.getValue())) words.add((String) w);
            kb.synonyms.put(e.getKey(), words);
        }
        return kb;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    public KbEntry get(String id) {
        KbEntry e = entries.get(id);
        if (e == null) throw new IllegalArgumentException("No KB intent " + id);
        return e;
    }

    public boolean has(String id) {
        return entries.containsKey(id);
    }

    public Collection<KbEntry> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public String version() {
        return version;
    }

    public Map<String, Map<String, Object>> glossary() {
        return glossary;
    }

    public String phrase(String key, Lang l) {
        Map<String, Object> p = phrases.get(key);
        if (p == null) return "";
        String s = MiniJson.str(p.get(l.code), null);
        return s != null ? s : MiniJson.str(p.get("en"), "");
    }

    public List<String> stageChips(JourneyStage stage, Lang l) {
        Map<String, Object> c = stageChips.get(stage.name());
        if (c == null) return Collections.emptyList();
        List<Object> raw = MiniJson.arr(c.containsKey(l.code) ? c.get(l.code) : c.get("en"));
        List<String> out = new ArrayList<>();
        for (Object o : raw) out.add((String) o);
        return out;
    }

    public List<Topic> topics() {
        return Collections.unmodifiableList(topics);
    }

    public List<String> signal(String name) {
        List<String> s = signals.get(name);
        return s == null ? Collections.<String>emptyList() : s;
    }

    public Map<String, List<String>> synonyms() {
        return synonyms;
    }
}
