package com.udyam.digital.i18n;

import com.udyam.digital.model.Language;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Reads res/values[-xx]/strings*.xml straight from disk, for JVM tests and tools. Applies the
 * same unescaping as Android (\' \" \n, and quoted strings). Pure Java.
 */
public final class XmlSource implements I18n.Source {

    private final File resDir;
    private final Map<Language, Map<String, String>> cache = new EnumMap<>(Language.class);

    public XmlSource(File resDir) { this.resDir = resDir; }

    public static String folder(Language l) { return l == Language.EN ? "values" : "values-" + l.tag; }

    @Override public synchronized String pattern(String key, Language l) { return strings(l).get(key); }

    /** All strings of one language, key -> text, in file order. */
    public synchronized Map<String, String> strings(Language l) {
        Map<String, String> m = cache.get(l);
        if (m == null) {
            m = read(new File(resDir, folder(l)), "string");
            cache.put(l, m);
        }
        return m;
    }

    /** Plurals of one language: key -> (quantity -> text). */
    public Map<String, Map<String, String>> plurals(Language l) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        File[] files = new File(resDir, folder(l)).listFiles((d, n) -> n.endsWith(".xml"));
        if (files == null) return out;
        for (File f : files) {
            Document doc = parse(f);
            if (doc == null) continue;
            NodeList ps = doc.getElementsByTagName("plurals");
            for (int i = 0; i < ps.getLength(); i++) {
                Element p = (Element) ps.item(i);
                Map<String, String> q = new LinkedHashMap<>();
                NodeList items = p.getElementsByTagName("item");
                for (int j = 0; j < items.getLength(); j++) {
                    Element it = (Element) items.item(j);
                    q.put(it.getAttribute("quantity"), unescape(it.getTextContent()));
                }
                out.put(p.getAttribute("name"), q);
            }
        }
        return out;
    }

    private static Map<String, String> read(File dir, String tag) {
        Map<String, String> m = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, n) -> n.startsWith("strings") && n.endsWith(".xml"));
        if (files == null) return m;
        java.util.Arrays.sort(files);
        for (File f : files) {
            Document doc = parse(f);
            if (doc == null) continue;
            NodeList ns = doc.getElementsByTagName(tag);
            for (int i = 0; i < ns.getLength(); i++) {
                Element e = (Element) ns.item(i);
                if ("false".equals(e.getAttribute("translatable")) && !dir.getName().equals("values")) continue;
                m.put(e.getAttribute("name"), unescape(e.getTextContent()));
            }
        }
        return m;
    }

    private static Document parse(File f) {
        try {
            DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
            fac.setExpandEntityReferences(false);
            return fac.newDocumentBuilder().parse(f);
        } catch (Exception e) {
            throw new IllegalStateException("Bad XML " + f, e);
        }
    }

    /** Android's string escaping: \' \" \\ \n \t, and a fully double-quoted value. */
    static String unescape(String s) {
        String t = s;
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\\' && i + 1 < t.length()) {
                char n = t.charAt(++i);
                switch (n) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (i + 4 < t.length()) { b.append((char) Integer.parseInt(t.substring(i + 1, i + 5), 16)); i += 4; }
                        break;
                    default: b.append(n);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** For tools: map of every language's strings. */
    public Map<Language, Map<String, String>> all() {
        Map<Language, Map<String, String>> m = new HashMap<>();
        for (Language l : Language.values()) m.put(l, strings(l));
        return m;
    }
}
