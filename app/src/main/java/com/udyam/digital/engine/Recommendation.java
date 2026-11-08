package com.udyam.digital.engine;

import com.udyam.digital.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Output of the rules engine. Totals are computed from the currently selected lines,
 * so the plan screen can toggle add-ons and re-read the totals.
 */
public final class Recommendation {

    public enum Role { PRIMARY, MOBILE, ADD_ON }

    public static final class Line {
        private final Product product;
        private int quantity;
        private final Role role;
        private final String reason;
        private boolean selected = true;
        private boolean movedForBudget;
        private boolean recommended;
        private boolean suggested;
        private String note;

        Line(Product product, int quantity, Role role, String reason) {
            this.product = product;
            this.quantity = quantity;
            this.role = role;
            this.reason = reason;
        }

        public Product getProduct() { return product; }
        public int getQuantity() { return quantity; }
        /** The owner can change quantities on the plan screen; totals follow. */
        public void setQuantity(int quantity) { this.quantity = Math.max(1, quantity); }
        /** Extras only: the owner tapped this need, so it starts switched ON ("You asked for"). */
        public boolean isRecommended() { return recommended; }
        void setRecommended(boolean r) { this.recommended = r; }
        /** Extras only: one of at most 3 "Businesses like yours often add" picks, OFF by default. */
        public boolean isSuggested() { return suggested; }
        void setSuggested(boolean s) { this.suggested = s; }
        public Role getRole() { return role; }
        /** The "why" line shown to the customer. */
        public String getReason() { return reason; }
        public int getUnitPriceInr() { return product.getPriceMinInr(); }
        public int getMonthlyInr() { return product.getPriceMinInr() * quantity; }
        public int getSetupInr() { return product.getSetupInr() * quantity; }
        public int getContractMonths() { return product.getMinContractMonths(); }
        public boolean isSelected() { return selected; }
        /** The primary link cannot be switched off; everything else can. */
        public boolean isToggleable() { return role != Role.PRIMARY; }
        public void setSelected(boolean selected) {
            if (isToggleable() || selected) this.selected = selected;
        }
        /** True if the engine switched this off to fit the budget. */
        public boolean isMovedForBudget() { return movedForBudget; }
        /** Extra note, e.g. why it was switched off. May be null. */
        public String getNote() { return note; }

        void moveToOptional(String note) {
            this.selected = false;
            this.movedForBudget = true;
            this.note = note;
        }
    }

    public static final class Excluded {
        private final Product product;
        private final String reason;

        Excluded(Product product, String reason) {
            this.product = product;
            this.reason = reason;
        }

        public Product getProduct() { return product; }
        public String getReason() { return reason; }
    }

    private final List<Line> lines = new ArrayList<>();
    private final Map<String, Excluded> excluded = new LinkedHashMap<>();
    private final int budgetInr;
    private Pack.Kind kind = Pack.Kind.YOUR_PLAN;

    Recommendation(int budgetInr) { this.budgetInr = budgetInr; }

    void addLine(Line l) { lines.add(l); excluded.remove(l.getProduct().getId()); }

    void removeLine(Line l) { lines.remove(l); }

    /** Swap a line for an upgraded one in the same position. */
    void replaceLine(Line old, Line upgraded) {
        int i = lines.indexOf(old);
        if (i >= 0) lines.set(i, upgraded);
    }

    void exclude(Product p, String reason) {
        if (p != null && !contains(p.getId()) && !excluded.containsKey(p.getId())) {
            excluded.put(p.getId(), new Excluded(p, reason));
        }
    }

    public List<Line> getLines() { return Collections.unmodifiableList(lines); }

    public List<Excluded> getExcluded() { return Collections.unmodifiableList(new ArrayList<>(excluded.values())); }

    /** Whether the product is in the plan at all (selected or optional). */
    public boolean contains(String productId) { return line(productId) != null; }

    /** Whether the product is in the plan and switched on. */
    public boolean isSelected(String productId) {
        Line l = line(productId);
        return l != null && l.isSelected();
    }

    public boolean isExcluded(String productId) { return excluded.containsKey(productId); }

    public Excluded excluded(String productId) { return excluded.get(productId); }

    public Line line(String productId) {
        for (Line l : lines) if (l.getProduct().getId().equals(productId)) return l;
        return null;
    }

    public Line getPrimary() {
        for (Line l : lines) if (l.getRole() == Role.PRIMARY) return l;
        return null;
    }

    public int getMonthlyTotal() {
        int sum = 0;
        for (Line l : lines) if (l.isSelected()) sum += l.getMonthlyInr();
        return sum;
    }

    public int getSetupTotal() {
        int sum = 0;
        for (Line l : lines) if (l.isSelected()) sum += l.getSetupInr();
        return sum;
    }

    /** Longest minimum contract among selected lines. */
    public int getContractMonths() {
        int max = 0;
        for (Line l : lines) if (l.isSelected()) max = Math.max(max, l.getContractMonths());
        return max;
    }

    /** 0 means no budget limit. */
    public int getBudgetInr() { return budgetInr; }

    /** Which pack this plan was built as. */
    public Pack.Kind getKind() { return kind; }
    void setKind(Pack.Kind k) { kind = k; }

    public boolean isOverBudget() { return budgetInr > 0 && getMonthlyTotal() > budgetInr; }
}
