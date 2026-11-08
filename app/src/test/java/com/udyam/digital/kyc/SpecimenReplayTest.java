package com.udyam.digital.kyc;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

/**
 * Accuracy proof without a phone: replays the on-device ML Kit OCR output for K01-K12
 * (src/test/resources/kyc_ocr, written by the debug "Run all specimens" screen and pulled with
 * tools/pull_specimen_dumps.sh) through the same document processing and checks as the app,
 * writes build/reports/kyc/specimen_report.md, and FAILS if any applicant in the answer key
 * (src/debug/assets/kyc/expected_results.json) doesn't match.
 */
public class SpecimenReplayTest {

    static File file(String path) {
        for (String base : new String[] {"", "app/"}) {
            File f = new File(base + path);
            if (f.exists()) return f;
        }
        throw new IllegalStateException("Missing " + path);
    }

    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    static List<SpecimenDump> dumps() throws Exception {
        File[] files = file("src/test/resources/kyc_ocr").listFiles((d, n) -> n.endsWith(".json"));
        Arrays.sort(files);
        List<SpecimenDump> out = new ArrayList<>();
        for (File f : files) out.add(SpecimenDump.parse(read(f)));
        return out;
    }

    static Map<String, Map<Slot, KycDocument>> load(KycRuleEngine engine) throws Exception {
        Map<String, Map<Slot, KycDocument>> out = new TreeMap<>();
        for (SpecimenDump d : dumps()) {
            out.computeIfAbsent(d.applicant, k -> new EnumMap<>(Slot.class)).put(d.slot, d.process(engine.registry()));
        }
        return out;
    }

    static List<SpecimenRunner.Row> runAll() throws Exception {
        KycRuleEngine engine = KycTestData.engine();
        List<SpecimenRunner.Row> rows = new ArrayList<>();
        for (Map.Entry<String, Map<Slot, KycDocument>> e : load(engine).entrySet()) {
            rows.add(SpecimenRunner.run(engine, e.getKey(), e.getValue()));
        }
        return rows;
    }

    @Test
    public void allTwelveApplicantsAreProcessed() throws Exception {
        List<SpecimenRunner.Row> rows = runAll();
        assertEquals(12, rows.size());
        StringBuilder b = new StringBuilder("\nSPECIMEN TABLE (today = " + KycClock.TODAY + ")\n");
        for (SpecimenRunner.Row r : rows) {
            b.append(r.applicant).append(" | ").append(r.overall).append(" | ").append(String.join(", ", r.codes))
                    .append(" | ").append(r.message).append('\n');
        }
        System.out.println(b);
    }

    @Test
    public void printExtractedFields() throws Exception {
        StringBuilder b = new StringBuilder("\nEXTRACTED FIELDS\n");
        for (Map.Entry<String, Map<Slot, KycDocument>> e : load(KycTestData.engine()).entrySet()) {
            for (KycDocument d : e.getValue().values()) {
                b.append(e.getKey()).append(' ').append(d.slot).append(" -> ").append(d.type)
                        .append(" q=").append(d.quality.problem).append(' ').append(d.fields);
                if (!d.autoCorrected.isEmpty()) b.append(" auto-corrected=").append(d.autoCorrected);
                if (d.looksTampered()) b.append(" C20=").append(d.passDisagreement).append(d.fontMismatch);
                b.append('\n');
            }
        }
        System.out.println(b);
    }

    @Test
    public void matchesAnswerKey() throws Exception {
        List<SpecimenRunner.Row> rows = runAll();
        Map<String, SpecimenReport.Expected> key = SpecimenReport.parseExpected(read(file("src/debug/assets/kyc/expected_results.json")));
        SpecimenReport report = SpecimenReport.compare(rows, key);

        List<String> mapping = new ArrayList<>();
        for (SpecimenDump d : dumps()) {
            mapping.add(d.file + " -> " + d.applicant + " " + d.slot + (d.pass2.isEmpty() ? " (1 OCR pass)" : " (2 OCR passes)"));
        }
        String md = report.markdown("replay of on-device OCR (src/test/resources/kyc_ocr)", mapping, rows, "");
        File out = new File(file("build.gradle").getParentFile(), "build/reports/kyc/specimen_report.md");
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), md.getBytes(StandardCharsets.UTF_8));
        System.out.println("\nSpecimen report: " + out.getAbsolutePath());
        System.out.printf("Answer key: %d compared, %d matched, %d false rejects, %d false accepts%n",
                report.compared(), report.matched(), report.falseRejects(), report.falseAccepts());

        StringBuilder why = new StringBuilder();
        for (SpecimenReport.Line l : report.mismatches()) {
            why.append('\n').append(l.applicant).append(": expected ").append(SpecimenReport.label(l.expected.overall))
                    .append(" ").append(l.expected.checks).append(", got ").append(SpecimenReport.label(l.actual))
                    .append(" ").append(l.flagged).append(l.missing.isEmpty() ? "" : " - missing " + l.missing);
        }
        assertTrue("Specimens that don't match the answer key:" + why, report.mismatches().isEmpty());
    }
}
