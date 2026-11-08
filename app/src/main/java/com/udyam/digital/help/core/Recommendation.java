package com.udyam.digital.help.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A bundle: product lines with quantities, one monthly total, plus what was left out and why. */
public final class Recommendation {

    public static final class Line {
        public final Product product;
        public final int quantity;

        public Line(Product product, int quantity) {
            this.product = product;
            this.quantity = Math.max(1, quantity);
        }

        public int monthly() {
            return product.monthlyPrice() * quantity;
        }

        public int setup() {
            return product.setupFee * quantity;
        }

        @Override
        public String toString() {
            return quantity + " x " + product.name;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    /** Products the profile asked for but could not get, with the reasons. */
    public final Map<String, List<RulesEngine.Reason>> skipped = new LinkedHashMap<>();
    /** Lines dropped or downgraded by a budget re-run, human-readable (English). */
    public final List<String> changes = new ArrayList<>();
    public final List<Line> removed = new ArrayList<>();
    /** Notes such as the per-state GST rule. Keys, rendered by AnswerComposer. */
    public final List<String> noteKeys = new ArrayList<>();

    public void add(Product p, int qty) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).product.id.equals(p.id)) {
                lines.set(i, new Line(p, lines.get(i).quantity + qty));
                return;
            }
        }
        lines.add(new Line(p, qty));
    }

    public void set(Product p, int qty) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).product.id.equals(p.id)) {
                lines.set(i, new Line(p, qty));
                return;
            }
        }
        lines.add(new Line(p, qty));
    }

    public boolean remove(String productId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).product.id.equals(productId)) {
                removed.add(lines.remove(i));
                return true;
            }
        }
        return false;
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean contains(String productId) {
        for (Line l : lines) if (l.product.id.equals(productId)) return true;
        return false;
    }

    public Line line(String productId) {
        for (Line l : lines) if (l.product.id.equals(productId)) return l;
        return null;
    }

    public Line base() {
        for (Line l : lines) if (l.product.isConnectivityBase()) return l;
        return null;
    }

    public int monthlyTotal() {
        int t = 0;
        for (Line l : lines) t += l.monthly();
        return t;
    }

    public int setupTotal() {
        int t = 0;
        for (Line l : lines) t += l.setup();
        return t;
    }

    /** Slowest product decides when the whole bundle is live. */
    public int maxInstallDays() {
        int d = 0;
        for (Line l : lines) d = Math.max(d, l.product.installDays);
        return d;
    }

    public Line slowestLine() {
        Line slow = null;
        for (Line l : lines) if (slow == null || l.product.installDays > slow.product.installDays) slow = l;
        return slow;
    }

    public int contractMonths() {
        int m = 0;
        for (Line l : lines) m = Math.max(m, l.product.minContractMonths);
        return m;
    }

    public Recommendation copy() {
        Recommendation r = new Recommendation();
        for (Line l : lines) r.lines.add(l);
        r.skipped.putAll(skipped);
        r.noteKeys.addAll(noteKeys);
        return r;
    }

    @Override
    public String toString() {
        return lines + " = Rs " + monthlyTotal() + "/month";
    }
}
