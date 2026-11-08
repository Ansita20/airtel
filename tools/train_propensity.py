#!/usr/bin/env python3
"""
"Which leads to call": a propensity score for outcome == Activated.

    python3 -m venv tools/.venv && tools/.venv/bin/pip install -r tools/requirements.txt
    tools/.venv/bin/python tools/train_propensity.py

Inputs : smb_leads.csv, product_catalogue.csv (data/, the folder above the app, or the app root)
Outputs: tools/propensity_report.md
         app/src/main/assets/propensity_model.json      (scorer for PropensityScorer.java)
         app/src/main/assets/partner_leads.json         (masked dropped leads for the partner view)
         app/src/main/assets/partner_dashboard.json     (funnel + cost figures from the data)
         app/src/test/resources/partner/propensity_samples.json (20 leads + Python scores, for the parity test)

Features: profile + journey only - segment, business_type, city_tier, log employees, log sites,
has_gstin, has_udyam, current_internet, monthly_telecom_spend_inr, lead_source, and the journey stage.
No names, contacts, phone numbers, GSTIN/Udyam numbers or state.

THE STAGE FEATURE: stage_reached as given is the FINAL stage, and it equals "Activated" for exactly
the activated leads, so it is the answer itself (AUC 1.0 - reported below). We use the stage the lead
had reached 7 days after it was created (from the stage dates), i.e. what is known when you decide
whom to call. Median activated lead accepts the quote on day 9, so day 7 is when the score helps.
"""
import json
import math
import os
import re

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score
from sklearn.tree import DecisionTreeClassifier

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # UdyamDigital/
SNAPSHOT_DAYS = 7
TODAY = pd.Timestamp("2026-07-15")  # the app's fixed "today" (AppClock / KycClock)
STAGES = ["Lead", "Contacted", "Needs assessed", "Quote accepted", "KYC complete"]
STAGE_DATES = ["contacted_date", "needs_assessed_date", "quote_accepted_date", "kyc_complete_date", "activated_date"]
MIN_TYPE_COUNT = 40  # business types rarer than this in training become "Other"


def find(name):
    for cand in (os.path.join(ROOT, "data", name), os.path.join(os.path.dirname(ROOT), name), os.path.join(ROOT, name)):
        if os.path.exists(cand):
            return cand
    raise SystemExit(f"Can't find {name} (looked in data/, the folder above, and the app root)")


# ---------------------------------------------------------------------------------- features
def stage_at(row, days):
    """Index into STAGES of the furthest stage reached within `days` of creation (5 = already activated)."""
    s = 0
    for i, c in enumerate(STAGE_DATES):
        d = row[c]
        if pd.notna(d) and (d - row["created_date"]).days <= days:
            s = i + 1
    return s


class Design:
    """Turns raw lead fields into the model's columns. Mirrored exactly by PropensityScorer.java."""

    def __init__(self, train):
        self.types = sorted(t for t, n in train["business_type"].value_counts().items() if n >= MIN_TYPE_COUNT)
        self.cats = {
            "segment": sorted(train["segment"].unique()),
            "business_type": self.types + ["Other"],
            "current_internet": sorted(train["current_internet"].unique()),
            "lead_source": sorted(train["lead_source"].unique()),
            "stage": STAGES,
        }
        self.numeric = ["log_employees", "log_sites", "log_spend"]
        self.binary = ["city_tier2", "has_gstin", "has_udyam"]
        raw = self.raw(train)
        self.mean = {c: float(raw[c].mean()) for c in self.numeric}
        self.std = {c: float(raw[c].std(ddof=0)) or 1.0 for c in self.numeric}

    @staticmethod
    def raw(df):
        return pd.DataFrame({
            "log_employees": np.log1p(df["employee_count"].astype(float)),
            "log_sites": np.log1p(df["num_sites"].astype(float)),
            "log_spend": np.log1p(df["monthly_telecom_spend_inr"].astype(float)),
        }, index=df.index)

    def columns(self):
        cols = list(self.numeric) + list(self.binary)
        for k, vals in self.cats.items():
            cols += [f"{k}={v}" for v in vals]
        return cols

    def matrix(self, df):
        raw = self.raw(df)
        out = pd.DataFrame(index=df.index)
        for c in self.numeric:
            out[c] = (raw[c] - self.mean[c]) / self.std[c]
        out["city_tier2"] = (df["city_tier"].astype(int) == 2).astype(float)
        out["has_gstin"] = (df["has_gstin"] == "Y").astype(float)
        out["has_udyam"] = (df["has_udyam"] == "Y").astype(float)
        btype = df["business_type"].where(df["business_type"].isin(self.types), "Other")
        values = {"segment": df["segment"], "business_type": btype, "current_internet": df["current_internet"],
                  "lead_source": df["lead_source"], "stage": df["stage"]}
        for k, vals in self.cats.items():
            for v in vals:
                out[f"{k}={v}"] = (values[k] == v).astype(float)
        return out[self.columns()]


REASON = {
    "log_employees": "more staff", "log_sites": "more sites", "log_spend": "high telecom spend",
    "city_tier2": "tier 2 city", "has_gstin": "has GSTIN", "has_udyam": "has Udyam",
    "stage=Lead": "not contacted yet", "stage=Contacted": "was contacted", "stage=Needs assessed": "needs assessed",
    "stage=Quote accepted": "reached quote stage", "stage=KYC complete": "finished KYC",
    "lead_source=Referral": "came by referral", "lead_source=Website form": "filled the website form",
    "lead_source=WhatsApp campaign": "replied on WhatsApp", "lead_source=Inbound call": "called in",
    "lead_source=Field sales": "met field sales", "lead_source=Channel partner": "came via a partner",
}


def reason_label(col):
    if col in REASON:
        return REASON[col]
    k, _, v = col.partition("=")
    if k == "current_internet":
        return "uses " + v.lower()
    if k in ("segment", "business_type"):
        return v.lower()
    return col


# ---------------------------------------------------------------------------------- masking
SUFFIXES = {"llp", "pvt", "ltd", "&", "and", "co", "co.", "pvt.", "ltd."}


def mask_name(name):
    """'Balaji Dental Care LLP' -> 'Ba**** De**** Ca** LLP'. Same rule as PartnerMask.java."""
    out = []
    for w in str(name).split():
        if w.lower() in SUFFIXES or len(w) <= 2:
            out.append(w)
        else:
            out.append(w[:2] + "*" * (len(w) - 2))
    return " ".join(out)


# ---------------------------------------------------------------------------------- partner lists
TAB = {
    # Digital drop-offs a nudge or the app can fix: questionnaire/quote abandoned, KYC "please fix" left unfinished.
    "Price too high": "STUCK", "Quote took too long to arrive": "STUCK", "Decided to wait": "STUCK",
    "Competitor offered cheaper plan": "STUCK", "Found paperwork too long": "STUCK",
    "Missing GST or Udyam document": "STUCK", "Only wanted a mobile plan": "STUCK", "Locked in with current provider": "STUCK",
    # A person has to step in: explanations, manual KYC review, site/visit problems.
    "Quote too complex to understand": "PERSON", "Document details did not match": "PERSON",
    "Authorised signatory not available": "PERSON", "Site not feasible for fibre": "PERSON",
    "Owner had no time for a visit": "PERSON", "Visit not scheduled by sales": "PERSON",
    # Orders that stalled at installation.
    "Installation delayed beyond 21 days": "INSTALL", "Customer cancelled before install": "INSTALL",
    # Not listed (no one to call back): Unreachable after 3 attempts, Wrong or invalid number, Not interested on first call.
}


def plan_value(row, cat):
    """Estimated monthly value of the products the lead asked about (catalogue minimum price x quantity)."""
    total = 0
    for pid in str(row["products_of_interest"]).split(";"):
        if pid not in cat.index:
            continue
        p = cat.loc[pid]
        unit = str(p["unit"]).lower()
        if "site" in unit:
            q = int(row["num_sites"])
        elif any(u in unit for u in ("user", "line", "sim", "device", "seat", "account")):
            q = min(int(row["employee_count"]), 20)
        else:
            q = 1
        total += int(p["price_min_inr_per_month"]) * q
    return total


def main():
    # keep_default_na=False: current_internet "None" is a real answer (no internet), not a missing value.
    leads = pd.read_csv(find("smb_leads.csv"), parse_dates=["created_date"] + STAGE_DATES,
                        keep_default_na=False, na_values=[""])
    cat = pd.read_csv(find("product_catalogue.csv")).set_index("product_id")
    leads["y"] = (leads["outcome"] == "Activated").astype(int)
    stage_idx = leads.apply(lambda r: stage_at(r, SNAPSHOT_DAYS), axis=1)
    already = int((stage_idx == 5).sum())
    data = leads[stage_idx < 5].copy()  # already activated by day 7: nothing left to predict
    data["stage"] = [STAGES[i] for i in stage_idx[stage_idx < 5]]

    month = data["created_date"].dt.month
    train, test = data[month.isin([4, 5])], data[month == 6]
    design = Design(train)
    Xtr, Xte = design.matrix(train), design.matrix(test)

    lr = LogisticRegression(C=1.0, max_iter=5000).fit(Xtr, train["y"])
    tree = DecisionTreeClassifier(max_depth=4, min_samples_leaf=40, random_state=0).fit(Xtr, train["y"])
    p_lr, p_tree = lr.predict_proba(Xte)[:, 1], tree.predict_proba(Xte)[:, 1]
    auc_lr, auc_tree = roc_auc_score(test["y"], p_lr), roc_auc_score(test["y"], p_tree)
    use_tree = auc_tree > auc_lr + 0.02  # pick the simpler model unless the tree is clearly better
    chosen = "decision_tree" if use_tree else "logistic_regression"
    p_test = p_tree if use_tree else p_lr
    p_train = (tree if use_tree else lr).predict_proba(Xtr)[:, 1]

    # Leaky baseline and stage-only references, for the report.
    final_stage = leads["stage_reached"].map({s: i for i, s in enumerate(STAGES + ["Activated"])})
    auc_leaky = roc_auc_score(leads["y"], final_stage)
    auc_stage_only = roc_auc_score(test["y"], test["stage"].map({s: i for i, s in enumerate(STAGES)}))

    def lift(p, y, share=0.2):
        n = max(1, int(round(len(p) * share)))
        top = np.argsort(-p)[:n]
        return float(y.iloc[top].mean()), float(y.mean()), float(y.iloc[top].sum() / max(1, y.sum()))

    top_rate, base_rate, captured = lift(p_test, test["y"])
    top_rate_lr, _, captured_lr = lift(p_lr, test["y"])
    top_rate_tree, _, captured_tree = lift(p_tree, test["y"])

    # 0-100 score = percentile among April-May leads (100 cut points).
    cuts = np.quantile(p_train, np.arange(1, 101) / 100.0)

    cols = design.columns()
    col_mean = Xtr.mean().to_dict()
    model = {
        "version": "propensity-1",
        "trained_on": "smb_leads.csv, leads created April-May 2026 (tested on June)",
        "target": "outcome == Activated",
        "snapshot_days": SNAPSHOT_DAYS,
        "model": chosen,
        "columns": cols,
        "numeric": {c: {"mean": design.mean[c], "std": design.std[c]} for c in design.numeric},
        "categories": design.cats,
        "intercept": float(lr.intercept_[0]),
        "coefficients": {c: float(w) for c, w in zip(cols, lr.coef_[0])},
        "column_means": {c: float(col_mean[c]) for c in cols},
        "reasons": {c: reason_label(c) for c in cols},
        "score_cuts": [float(x) for x in cuts],
        "test_auc": {"logistic_regression": auc_lr, "decision_tree": auc_tree},
    }
    if use_tree:
        t = tree.tree_
        model["tree"] = {
            "feature": [int(f) for f in t.feature], "threshold": [float(x) for x in t.threshold],
            "left": [int(x) for x in t.children_left], "right": [int(x) for x in t.children_right],
            "p1": [float(v[0][1] / v[0].sum()) for v in t.value],
        }
    assets = os.path.join(ROOT, "app", "src", "main", "assets")
    with open(os.path.join(assets, "propensity_model.json"), "w") as f:
        json.dump(model, f, indent=1)

    # ---- parity samples: 20 June leads, raw fields + Python's probability, score and reasons
    def raw_fields(r):
        return {"segment": r["segment"], "business_type": r["business_type"], "city_tier": int(r["city_tier"]),
                "employees": int(r["employee_count"]), "sites": int(r["num_sites"]),
                "has_gstin": r["has_gstin"] == "Y", "has_udyam": r["has_udyam"] == "Y",
                "current_internet": r["current_internet"], "spend": int(r["monthly_telecom_spend_inr"]),
                "lead_source": r["lead_source"], "stage": r["stage"]}

    def reasons(xrow):
        contrib = {c: lr.coef_[0][i] * (xrow[c] - col_mean[c]) for i, c in enumerate(cols)}
        pos = sorted(((v, c) for c, v in contrib.items() if v > 0), reverse=True)[:2]
        return [reason_label(c) for _, c in pos]

    sample = test.sample(20, random_state=7)
    Xs = design.matrix(sample)
    ps = (tree if use_tree else lr).predict_proba(Xs)[:, 1]
    samples = [{"id": r["lead_id"], "fields": raw_fields(r), "probability": float(p),
                "score": int(np.searchsorted(cuts, p, side="right")), "reasons": reasons(Xs.loc[i])}
               for (i, r), p in zip(sample.iterrows(), ps)]
    test_res = os.path.join(ROOT, "app", "src", "test", "resources", "partner")
    os.makedirs(test_res, exist_ok=True)
    with open(os.path.join(test_res, "propensity_samples.json"), "w") as f:
        json.dump(samples, f, indent=1, ensure_ascii=False)

    # ---- partner seed: June leads that dropped for a reason someone can act on. Masked; no contacts.
    dropped = leads[(leads["outcome"] == "Dropped") & (leads["created_date"].dt.month == 6) & leads["drop_reason"].isin(TAB)]
    seed = []
    for _, r in dropped.iterrows():
        last = max([d for d in [r["created_date"]] + [r[c] for c in STAGE_DATES] if pd.notna(d)])
        products = [p for p in str(r["products_of_interest"]).split(";") if p in cat.index]
        seed.append({
            "id": r["lead_id"], "name": mask_name(r["business_name"]), "type": r["business_type"], "segment": r["segment"],
            "city": r["city"], "city_tier": int(r["city_tier"]), "language": r["preferred_language"],
            "employees": int(r["employee_count"]), "sites": int(r["num_sites"]),
            "has_gstin": r["has_gstin"] == "Y", "has_udyam": r["has_udyam"] == "Y",
            "current_internet": r["current_internet"], "spend": int(r["monthly_telecom_spend_inr"]),
            "lead_source": r["lead_source"], "stage": r["stage_reached"], "reason": r["drop_reason"], "tab": TAB[r["drop_reason"]],
            "last_activity": last.strftime("%Y-%m-%d"), "products": products,
            "plan": [str(cat.loc[p, "product_name"]) for p in products], "plan_value": plan_value(r, cat),
        })
    with open(os.path.join(assets, "partner_leads.json"), "w") as f:
        json.dump(seed, f, ensure_ascii=False, separators=(",", ":"))

    # ---- dashboard figures from the data
    act = leads[leads["y"] == 1]
    blended = float(leads["acquisition_cost_inr"].sum() / len(act))
    self_serve = act["lead_source"].isin(["Website form", "WhatsApp campaign"])
    funnel = [
        ["Questionnaire started", int(leads["contacted_date"].notna().sum())],
        ["Plan shown", int(leads["needs_assessed_date"].notna().sum())],
        ["Quote accepted", int(leads["quote_accepted_date"].notna().sum())],
        ["KYC complete", int(leads["kyc_complete_date"].notna().sum())],
        ["Activated", int(leads["activated_date"].notna().sum())],
    ]
    dashboard = {
        "source": "smb_leads.csv, 5,000 leads, April-June 2026",
        "leads": int(len(leads)), "funnel": funnel,
        "blended_cost_per_activation_inr": round(blended),
        "acquisition_cost_total_inr": int(leads["acquisition_cost_inr"].sum()),
        "activations": int(len(act)),
        "self_serve_share_of_activations": float(self_serve.mean()),
        "funnel_note": "Historical stages mapped to the app: contacted = questionnaire started, needs assessed = plan shown.",
    }
    with open(os.path.join(assets, "partner_dashboard.json"), "w") as f:
        json.dump(dashboard, f, indent=1)

    # ---- report
    coef = sorted(zip(lr.coef_[0], cols), reverse=True)
    lines = [
        "# Propensity model - which leads to call",
        "",
        f"Target: `outcome == Activated`. Train: leads created April-May 2026 ({len(train):,}, "
        f"{train['y'].mean():.1%} activated). Test: June ({len(test):,}, {test['y'].mean():.1%} activated).",
        "",
        "Features: segment, business type, city tier, log employees, log sites, has GSTIN, has Udyam, current internet, "
        "log monthly telecom spend, lead source, journey stage. No names, contacts, numbers or state.",
        "",
        "## The stage feature (important)",
        "",
        f"`stage_reached` in the file is the **final** stage: it is \"Activated\" for exactly the activated leads. "
        f"Used as a feature it gives **AUC {auc_leaky:.3f}** - it is the answer, not a predictor. We use the stage the lead "
        f"had reached **{SNAPSHOT_DAYS} days after it was created** (from the stage dates): what a salesperson knows when "
        f"deciding whom to call. {already} lead(s) had already activated by day {SNAPSHOT_DAYS} and are left out. "
        f"Stage alone (day {SNAPSHOT_DAYS}) scores AUC {auc_stage_only:.3f} on the June test.",
        "",
        "## Results (June test set)",
        "",
        "| Model | AUC | Activation rate in top 20% | Share of activations in top 20% |",
        "|---|---|---|---|",
        f"| Logistic regression | {auc_lr:.3f} | {top_rate_lr:.1%} | {captured_lr:.0%} |",
        f"| Decision tree (depth 4) | {auc_tree:.3f} | {top_rate_tree:.1%} | {captured_tree:.0%} |",
        "",
        f"Average activation rate: {base_rate:.1%}. **Chosen: {chosen.replace('_', ' ')}** "
        f"({'the tree is more than 0.02 AUC better' if use_tree else 'within 0.02 AUC of the tree or better, and simpler to explain'}). "
        f"**Lift: calling the top 20% finds {top_rate / base_rate:.1f}x the average activation rate** "
        f"({top_rate:.1%} vs {base_rate:.1%}) and {captured:.0%} of all June activations.",
        "",
        "## Score in the app",
        "",
        "`PropensityScorer.java` reads `propensity_model.json` and gives a 0-100 score = the lead's percentile among "
        "April-May leads (80+ = top 20%), with the top 2 reasons (largest positive contributions, e.g. \"has GSTIN\", "
        "\"reached quote stage\"). In the app the stage is the stage the lead is at now. "
        "`PropensityScorerTest` checks it matches this script on 20 June leads.",
        "",
        "## Strongest signals (logistic regression coefficients)",
        "",
        "| Pushes the score up | weight | Pushes it down | weight |",
        "|---|---|---|---|",
    ]
    for (wu, cu), (wd, cd) in zip(coef[:8], coef[::-1][:8]):
        lines.append(f"| {reason_label(cu)} | {wu:+.2f} | {reason_label(cd)} | {wd:+.2f} |")
    lines += [
        "",
        "## Partner view seed",
        "",
        f"{len(seed)} June leads that dropped for a reason someone can act on, masked (\"Ba**** De**** Ca** LLP\"), "
        "no contact names, phone numbers, GSTIN or Udyam numbers. Not listed: unreachable, wrong number, not interested.",
        "",
        "| Tab | Leads |",
        "|---|---|",
    ]
    for tab, n in pd.Series([s["tab"] for s in seed]).value_counts().items():
        lines.append(f"| {dict(STUCK='Stuck in app', PERSON='Needs a person', INSTALL='Install delayed')[tab]} | {n} |")
    lines += [
        "",
        "## Cost figures (dashboard)",
        "",
        f"Blended acquisition cost: total ₹{dashboard['acquisition_cost_total_inr']:,} / {len(act)} activations = "
        f"**₹{round(blended):,} per activated SMB**. Activations that started self-serve (website form or WhatsApp): "
        f"{self_serve.mean():.0%} - used in the app as the starting estimate of \"could need no human\".",
        "",
        "## Limits",
        "",
        "- One quarter of synthetic data; June leads had until 26 July to activate, like earlier months.",
        "- The score ranks leads; it is not a calibrated probability (base rate is ~7%).",
        "- Re-train when real app events arrive (the stage then comes from the app, not sales notes).",
    ]
    with open(os.path.join(HERE, "propensity_report.md"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
