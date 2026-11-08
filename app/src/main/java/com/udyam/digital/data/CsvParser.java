package com.udyam.digital.data;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal RFC-4180 CSV reader (quoted fields, escaped quotes, CRLF). Pure Java. */
public final class CsvParser {
    private CsvParser() {}

    /** Reads a CSV with a header row into one map per data row (header -> value). */
    public static List<Map<String, String>> readRecords(Reader in) throws IOException {
        List<List<String>> rows = parse(in);
        List<Map<String, String>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<String> header = rows.get(0);
        if (!header.isEmpty() && header.get(0).startsWith("﻿")) {
            header.set(0, header.get(0).substring(1));
        }
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                m.put(header.get(c).trim(), c < row.size() ? row.get(c).trim() : "");
            }
            out.add(m);
        }
        return out;
    }

    public static List<List<String>> parse(Reader in) throws IOException {
        StringBuilder all = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = in.read(buf)) != -1) all.append(buf, 0, n);
        String s = all.toString();

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < len && s.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < len && s.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
