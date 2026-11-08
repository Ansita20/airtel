package com.udyam.digital.help.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal RFC-4180 CSV reader (quoted fields, escaped quotes). No Android dependency. */
public final class Csv {
    private Csv() {}

    public static List<Map<String, String>> read(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<Map<String, String>> rows = new ArrayList<>();
        String headerLine = r.readLine();
        if (headerLine == null) return rows;
        if (headerLine.startsWith("﻿")) headerLine = headerLine.substring(1);
        List<String> header = split(headerLine);
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) continue;
            // A quoted field may contain a newline: keep reading until quotes balance.
            while (countQuotes(line) % 2 != 0) {
                String next = r.readLine();
                if (next == null) break;
                line = line + "\n" + next;
            }
            List<String> cells = split(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < cells.size() ? cells.get(i) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static int countQuotes(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '"') n++;
        return n;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
