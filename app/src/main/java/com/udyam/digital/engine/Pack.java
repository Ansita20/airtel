package com.udyam.digital.engine;

import com.udyam.digital.engine.Recommendation.Line;
import com.udyam.digital.model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * One plan the owner can pick on the plan screen.
 *   YOUR_PLAN    tapped needs + top learned picks, within budget, preselected
 *   BASIC        core link + lines + only tapped needs
 *   PREMIUM      next internet tier, Rs 599 SIMs, failover if eligible, + learned picks
 *   PREMIUM_PLUS Premium + every other eligible extra with score >= 0.10
 * Suggested packs (all but YOUR_PLAN) may be up to 20% over budget, never more.
 */
public final class Pack {

    public enum Kind { YOUR_PLAN, BASIC, PREMIUM, PREMIUM_PLUS }

    public final Kind kind;
    public final Recommendation plan;
    /** Rupees over the budget at the default selection, 0 if within it or no budget. */
    public final int overBudgetInr;

    Pack(Kind kind, Recommendation plan, int overBudgetInr) {
        this.kind = kind;
        this.plan = plan;
        this.overBudgetInr = overBudgetInr;
    }

    public int monthly() { return plan.getMonthlyTotal(); }

    /** Products switched on here but not in {@code other}: what this pack adds. */
    public List<Product> adds(Pack other) {
        List<Product> out = new ArrayList<>();
        for (Line l : plan.getLines()) {
            if (l.isSelected() && !other.plan.isSelected(l.getProduct().getId())) out.add(l.getProduct());
        }
        return out;
    }

    /** Extras switched on in {@code other} but not here: what this pack leaves out. */
    public List<Product> leaves(Pack other) {
        List<Product> out = new ArrayList<>();
        for (Line l : other.plan.getLines()) {
            if (l.getRole() == Recommendation.Role.ADD_ON && l.isSelected()
                    && !plan.isSelected(l.getProduct().getId())) {
                out.add(l.getProduct());
            }
        }
        return out;
    }

    /** Same products and quantities switched on. */
    boolean sameAs(Pack other) { return signature(plan).equals(signature(other.plan)); }

    private static String signature(Recommendation r) {
        List<String> parts = new ArrayList<>();
        for (Line l : r.getLines()) if (l.isSelected()) parts.add(l.getProduct().getId() + "x" + l.getQuantity());
        java.util.Collections.sort(parts);
        return parts.toString();
    }
}
