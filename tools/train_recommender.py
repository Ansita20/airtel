#!/usr/bin/env python3
"""
Offline trainer for the Udyam Digital learned ranking layer.

    RULES decide what is ALLOWED  ->  LEARNED SCORES decide the ORDER  ->
    the USER's stated needs and budget decide what is SHOWN.

This script only produces the LEARNED SCORES (app/src/main/assets/product_scores.json).
The rules engine in the app still filters every product first; nothing here can make
an ineligible product appear. This mirrors the hybrid used by telecom B2B recommenders
such as VodafoneZiggo's SMB tool: a tree model ranks, a rule layer blocks.

Run from the UdyamDigital/ folder:
    pip install -r tools/requirements.txt
    python3 tools/train_recommender.py

Inputs : data/smb_leads.csv, data/product_catalogue.csv (falls back to ../ if data/ is absent)
Outputs: app/src/main/assets/product_scores.json, tools/eval_report.md
Optional: tools/app_events.csv (exported from the app's debug screen) adds weak labels.
"""
import argparse
import json
import math
import os
import sys
from collections import defaultdict
from datetime import date

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # UdyamDigital/

# Core products are chosen by the rules engine (main link + mobile lines), not ranked.
CORE = {"P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08"}
SMOOTHING_M = 5            # ranker B: pseudo-count pulling small business types to their segment
LR_C = 0.5                 # ranker C: inverse regularisation strength
LR_MIN_POSITIVES = 10      # ranker C: train a product model only with this many positives
NPS_BONUS = 0.5            # sample weight bonus for NPS >= 7
APP_EVENT_WEIGHT = 0.3     # weight of "shown -> accepted" pairs from the app log
TIE_MARGIN = 0.02          # prefer the simpler ranker if within this Recall@3 of the best
MIN_PROOF = 5              # app hides "x of y kept this" below this many businesses (documented here too)
MAX_SUGGEST = 3            # "Businesses like yours often add": at most this many, all OFF by default
CHAIN_FIRST = ["P09", "P10"]   # for 3+ sites these come first (if eligible): failover, then SD-WAN
CHAIN_MIN_SITES = 3
CUTOFF_GRID = [round(x * 0.05, 2) for x in range(0, 13)]   # 0.00 .. 0.60
DEFAULT_CUTOFF = 0.15

CAT_FEATURES = ["business_type", "segment", "city_tier", "has_gstin", "current_internet"]
NUM_FEATURES = ["log_employees", "log_sites"]
# Never used as features: contact_name, business_name, state, city, phone, gstin, udyam_number, ...

UNIT_BASIS = {  # how a product's quantity scales, from the catalogue unit
    "per site": "site", "per counter": "site", "per camera": "site", "per vehicle": "site",
    "per user": "employee", "per line": "employee", "per device": "employee", "per channel": "employee",
    "per IP": "fixed", "per number": "fixed", "per pack": "fixed", "per account": "fixed",
}


def find_input(name, explicit):
    if explicit:
        return explicit
    for cand in (os.path.join(ROOT, "data", name), os.path.join(os.path.dirname(ROOT), name), os.path.join(ROOT, name)):
        if os.path.exists(cand):
            return cand
    sys.exit(f"Cannot find {name}. Put it in data/ or pass --{name.split('_')[0]}.")


def parse_bundle(s):
    """'P01x1;P07x2' -> {'P01': 1, 'P07': 2}"""
    out = {}
    if isinstance(s, str):
        for part in s.split(";"):
            part = part.strip()
            if not part:
                continue
            pid, _, qty = part.partition("x")
            out[pid] = int(qty) if qty.isdigit() else 1
    return out


def features(df):
    f = pd.DataFrame({
        "business_type": df["business_type"].fillna("Unknown").astype(str),
        "segment": df["segment"].astype(str),
        "city_tier": df["city_tier"].astype(str),
        "has_gstin": df["has_gstin"].astype(str),
        "current_internet": df["current_internet"].fillna("Unknown").astype(str),
        "log_employees": np.log(df["employee_count"].clip(lower=1).astype(float)),
        "log_sites": np.log(df["num_sites"].clip(lower=1).astype(float)),
    }, index=df.index)
    return f


# ----------------------------------------------------------------------------- rules (mirror of RulesEngine.hardFilter)
class Rules:
    def __init__(self, cat):
        self.cat = cat.set_index("product_id")

    def hard_ok(self, pid, row):
        p = self.cat.loc[pid]
        tiers = {int(t) for t in str(p["available_city_tiers"]).split(";") if t}
        if int(row["city_tier"]) not in tiers:
            return False
        if int(row["employee_count"]) < int(p["min_employees"]):
            return False
        if int(row["num_sites"]) < int(p["min_sites"]):
            return False
        if str(p["requires_gstin"]).upper() == "Y" and row["has_gstin"] != "Y":
            return False
        return True

    def requires(self, pid):
        r = self.cat.loc[pid, "requires_one_of"]
        return [x for x in str(r).split("|") if x and x != "nan"]

    def eligible(self, pid, row, core_links):
        """Hard filter + requires_one_of. Core links come from the customer's real bundle,
        because the core link is chosen by the rules engine, not by the ranker."""
        if not self.hard_ok(pid, row):
            return False
        req = self.requires(pid)
        return not req or any(r in core_links for r in req)


# ----------------------------------------------------------------------------- rankers
class SegmentPopularity:  # A
    name, label, complexity = "A", "Segment popularity", 0

    def fit(self, X, Y, w, products):
        self.rate = {}
        for seg, idx in X.groupby("segment").groups.items():
            ww = w.loc[idx]
            self.rate[seg] = {p: float((Y.loc[idx, p] * ww).sum() / ww.sum()) for p in products}
        self.products = products
        return self

    def score(self, X):
        return pd.DataFrame([[self.rate.get(s, {}).get(p, 0.0) for p in self.products] for s in X["segment"]],
                            index=X.index, columns=self.products)


class TypePopularity:  # B
    name, label, complexity = "B", "Business-type popularity (smoothed, m=5)", 1

    def fit(self, X, Y, w, products):
        self.seg = SegmentPopularity().fit(X, Y, w, products)
        self.type_rate, self.type_seg = {}, {}
        for bt, idx in X.groupby("business_type").groups.items():
            ww = w.loc[idx]
            seg = X.loc[idx, "segment"].mode()[0]
            n = ww.sum()
            self.type_seg[bt] = seg
            self.type_rate[bt] = {
                p: float(((Y.loc[idx, p] * ww).sum() + SMOOTHING_M * self.seg.rate[seg][p]) / (n + SMOOTHING_M))
                for p in products}
        self.products = products
        return self

    def score(self, X):
        rows = []
        for bt, seg in zip(X["business_type"], X["segment"]):
            src = self.type_rate.get(bt) or self.seg.rate.get(seg, {})
            rows.append([src.get(p, 0.0) for p in self.products])
        return pd.DataFrame(rows, index=X.index, columns=self.products)


class PerProductLogReg:  # C
    name, label, complexity = "C", f"Logistic regression per product (C={LR_C}), fallback B", 2

    def fit(self, X, Y, w, products):
        self.fallback = TypePopularity().fit(X, Y, w, products)
        self.models = {}
        for p in products:
            if int(Y[p].sum()) < LR_MIN_POSITIVES or Y[p].nunique() < 2:
                continue
            pre = ColumnTransformer([
                ("cat", OneHotEncoder(handle_unknown="ignore"), CAT_FEATURES),
                ("num", StandardScaler(), NUM_FEATURES)])
            m = Pipeline([("pre", pre), ("lr", LogisticRegression(C=LR_C, max_iter=2000))])
            m.fit(X[CAT_FEATURES + NUM_FEATURES], Y[p], lr__sample_weight=w.values)
            self.models[p] = m
        self.products = products
        return self

    def score(self, X):
        out = self.fallback.score(X)
        for p, m in self.models.items():
            out[p] = m.predict_proba(X[CAT_FEATURES + NUM_FEATURES])[:, 1]
        return out


# ----------------------------------------------------------------------------- evaluation
def evaluate(ranker, Xte, test_rows, kept_te, rules, products):
    S = ranker.score(Xte)
    res = {3: [], 5: []}
    per_seg = defaultdict(lambda: {3: [], 5: []})
    violations = 0
    for i, row in test_rows.iterrows():
        kept = {p for p in kept_te[i] if p in products}
        if not kept:
            continue
        core_links = {p for p in kept_te[i] if p in CORE}
        cands = [p for p in products if rules.eligible(p, row, core_links)]
        ranked = sorted(cands, key=lambda p: (-S.at[i, p], p))
        for k in (3, 5):
            top = ranked[:k]
            # Independent re-check: every shown product must pass the rules.
            violations += sum(1 for p in top if not rules.eligible(p, row, core_links))
            r = len(set(top) & kept) / len(kept)
            res[k].append(r)
            per_seg[row["segment"]][k].append(r)
    return {
        "recall_at_3": float(np.mean(res[3])), "recall_at_5": float(np.mean(res[5])),
        "violations": violations, "n": len(res[3]),
        "per_segment": {s: {"recall_at_3": float(np.mean(v[3])), "recall_at_5": float(np.mean(v[5])), "n": len(v[3])}
                        for s, v in sorted(per_seg.items())},
    }


# ----------------------------------------------------------------------------- app policy (mirror of RulesEngine)
def suggest(scores, row, eligible, tapped, cutoff, chain_rule=True):
    """What the app puts under "Businesses like yours often add" (all OFF):
    for 3+ sites P09 and P10 first if eligible, then learned picks with score >= cutoff,
    never something the owner already tapped, at most MAX_SUGGEST."""
    out = []
    if chain_rule and int(row["num_sites"]) >= CHAIN_MIN_SITES:
        out += [p for p in CHAIN_FIRST if p in eligible and p not in tapped]
    for p in sorted(eligible, key=lambda p: (-scores[p], p)):
        if len(out) >= MAX_SUGGEST:
            break
        if p not in tapped and p not in out and scores[p] >= cutoff:
            out.append(p)
    return out[:MAX_SUGGEST]


def interest_extras(row, products):
    return {p for p in str(row["products_of_interest"]).split(";") if p in products}


def policy_eval(ranker, X, rows, products, rules, cutoff, use_interest, chain_rule=True):
    """Simulates the plan screen for each customer.
    ON  = tapped needs (here: stated interest, if use_interest) that pass the rules.
    SUG = suggestions, OFF by default."""
    S = ranker.score(X)
    m = defaultdict(list)
    for i, row in rows.iterrows():
        kept = {p for p in row["bundle"] if p in products}
        core = {p for p in row["bundle"] if p in CORE}
        elig = {p for p in products if rules.eligible(p, row, core)}
        tapped = (interest_extras(row, products) & elig) if use_interest else set()
        sug = suggest(S.loc[i], row, elig, tapped, cutoff, chain_rule)
        m["n_on"].append(len(tapped))
        m["unwanted_on"].append(len(tapped - kept))
        if tapped:
            m["on_precision"].append(len(tapped & kept) / len(tapped))
        m["n_sug"].append(len(sug))
        if sug:
            m["sug_precision"].append(len(set(sug) & kept) / len(sug))
            m["sug_any_useful"].append(len(set(sug) & kept) > 0)
        if kept:
            m["sug_recall"].append(len(set(sug) & kept) / len(kept))
            m["coverage"].append(len((set(sug) | tapped) & kept) / len(kept))
    out = {k: float(np.mean(v)) if v else 0.0 for k, v in m.items()}
    p, r = out.get("sug_precision", 0), out.get("sug_recall", 0)
    out["sug_f1"] = 2 * p * r / (p + r) if p + r else 0.0
    out["customers"] = len(rows)
    return out


def old_policy_unwanted(ranker, X, rows, products, rules, use_interest):
    """Previous behaviour: top 3 by (score + 0.30 if tapped) with final >= 0.15 were switched ON."""
    S = ranker.score(X)
    unwanted = []
    for i, row in rows.iterrows():
        kept = {p for p in row["bundle"] if p in products}
        core = {p for p in row["bundle"] if p in CORE}
        elig = [p for p in products if rules.eligible(p, row, core)]
        tapped = (interest_extras(row, products) & set(elig)) if use_interest else set()
        fin = {p: S.at[i, p] + (0.30 if p in tapped else 0) for p in elig}
        on = [p for p in sorted(elig, key=lambda p: (-fin[p], p)) if fin[p] >= 0.15][:3]
        unwanted.append(len(set(on) - kept))
    return float(np.mean(unwanted))


def choose_cutoff(rank_cls, apr, may, products, rules, xyw):
    """Pick the suggestion cutoff on April-May only: train on April, validate on May,
    maximise F1 of the suggestions (precision of what is shown vs share of kept extras found).
    Ties go to the higher cutoff (fewer, surer suggestions)."""
    Xa, Ya, wa = xyw(apr)
    r = rank_cls().fit(Xa, Ya, wa, products)
    Xm = features(may)
    grid = []
    for c in CUTOFF_GRID:
        e = policy_eval(r, Xm, may, products, rules, c, use_interest=False)
        grid.append((c, e))
    best = max(grid, key=lambda ce: (round(ce[1]["sug_f1"], 4), ce[0]))
    return best[0], grid


# ----------------------------------------------------------------------------- app log -> weak labels
# Band midpoints, matching RecommendationLog.employeeBand / sitesBand in the app.
EMPLOYEE_MID = {"1-2": 2, "3-4": 3, "5-9": 6, "10-24": 15, "25-49": 35, "50+": 60}
SITES_MID = {"1": 1, "2": 2, "3-4": 3, "5-9": 6, "10+": 12}


def load_app_events(path):
    """Sessions that reached KYC: products SHOWN and not switched off, or switched on, count as accepted."""
    ev = pd.read_csv(path, dtype=str).fillna("")
    rows = []
    for sid, g in ev.groupby("session_id"):
        if not (g["event"] == "PROCEEDED_TO_KYC").any():
            continue
        state = {}
        for _, e in g.sort_values("timestamp").iterrows():
            if e["product_id"]:
                if e["event"] in ("SHOWN", "TOGGLED_ON"):
                    state[e["product_id"]] = True
                elif e["event"] == "TOGGLED_OFF":
                    state[e["product_id"]] = False
        accepted = [p for p, on in state.items() if on]
        if not accepted:
            continue
        f = g.iloc[0]
        rows.append({
            "business_type": f["business_type"] or "Unknown", "segment": f["segment"],
            "city_tier": int(f["city_tier"] or 2), "has_gstin": "Y" if f["has_gstin"] in ("Y", "true", "1") else "N",
            "current_internet": "Unknown",
            "employee_count": EMPLOYEE_MID.get(f["employee_band"], 3), "num_sites": SITES_MID.get(f["sites_band"], 1),
            "bundle": {p: 1 for p in accepted}, "weight": APP_EVENT_WEIGHT,
        })
    return pd.DataFrame(rows)


# ----------------------------------------------------------------------------- export helpers
def default_quantities(kept_all, cat, products):
    unit = cat.set_index("product_id")["unit"].to_dict()
    out = {}
    for seg, g in kept_all.groupby("segment"):
        seg_out = {}
        for p in products:
            have = g[g["bundle"].apply(lambda b: p in b)]
            if len(have) < 3:
                continue  # too few real bundles: the app falls back to its built-in quantity rule
            basis = UNIT_BASIS.get(unit[p], "fixed")
            q = have["bundle"].apply(lambda b: b[p]).astype(float)
            if basis == "site":
                seg_out[p] = {"per": "site", "median": round(float(np.median(q / have["num_sites"])), 2)}
            elif basis == "employee":
                seg_out[p] = {"per": "employee", "ratio": round(float(np.median(q / have["employee_count"])), 2)}
            else:
                seg_out[p] = {"per": "fixed", "median": round(float(np.median(q)), 2)}
        out[seg] = seg_out
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--leads")
    ap.add_argument("--catalogue")
    ap.add_argument("--events", default=os.path.join(HERE, "app_events.csv"))
    ap.add_argument("--out", default=os.path.join(ROOT, "app", "src", "main", "assets", "product_scores.json"))
    ap.add_argument("--report", default=os.path.join(HERE, "eval_report.md"))
    args = ap.parse_args()

    leads_path = find_input("smb_leads.csv", args.leads)
    cat_path = find_input("product_catalogue.csv", args.catalogue)
    leads = pd.read_csv(leads_path, dtype={"has_gstin": str, "churned_within_6_months": str})
    cat = pd.read_csv(cat_path)
    products = sorted(p for p in cat["product_id"] if p not in CORE)
    rules = Rules(cat)
    print(f"Leads: {leads_path} ({len(leads)} rows)\nCatalogue: {cat_path} ({len(cat)} products, {len(products)} ranked extras)")

    # Stated interest is a weak signal: how much of it ends up in the real bundle?
    act = leads[leads["outcome"] == "Activated"]
    shares = []
    for _, r in act.iterrows():
        interest = {p for p in str(r["products_of_interest"]).split(";") if p.startswith("P")}
        if interest:
            shares.append(len(interest & set(parse_bundle(r["bundle_products"]))) / len(interest))
    interest_overlap = float(np.mean(shares))

    # Labels: kept = activated and not churned within 6 months. NPS >= 7 counts 1.5x.
    kept = leads[(leads["outcome"] == "Activated") & (leads["churned_within_6_months"] == "N")].copy()
    kept["bundle"] = kept["bundle_products"].apply(parse_bundle)
    kept["weight"] = 1.0 + NPS_BONUS * (kept["nps_score"].fillna(0) >= 7)
    kept["month"] = kept["created_date"].str[:7]
    print(f"Kept customers: {len(kept)} (activated, not churned in 6 months)")

    def xyw(df):
        X = features(df)
        Y = pd.DataFrame({p: df["bundle"].apply(lambda b, p=p: int(p in b)) for p in products}, index=df.index)
        return X, Y, df["weight"].astype(float)

    # Time split: no customer from the test month is seen during training.
    train = kept[kept["month"].isin(["2026-04", "2026-05"])]
    test = kept[kept["month"] == "2026-06"]
    Xtr, Ytr, wtr = xyw(train)
    Xte = features(test)
    kept_te = test["bundle"].apply(lambda b: set(b)).to_dict()
    print(f"Train: {len(train)} customers (Apr-May 2026) | Test: {len(test)} customers (Jun 2026)")

    rankers = [SegmentPopularity(), TypePopularity(), PerProductLogReg()]
    results = {}
    for r in rankers:
        r.fit(Xtr, Ytr, wtr, products)
        results[r.name] = evaluate(r, Xte, test, kept_te, rules, products)
        m = results[r.name]
        print(f"  {r.name} {r.label:48s} R@3={m['recall_at_3']:.3f} R@5={m['recall_at_5']:.3f} violations={m['violations']}")
    if any(m["violations"] for m in results.values()):
        sys.exit("Rule violations in evaluation: this must be 0. Aborting export.")

    best = max(results.values(), key=lambda m: m["recall_at_3"])["recall_at_3"]
    winner = next(r for r in sorted(rankers, key=lambda r: r.complexity)
                  if results[r.name]["recall_at_3"] >= best - TIE_MARGIN)
    raw_best = max(rankers, key=lambda r: results[r.name]["recall_at_3"])
    tie_note = ("" if winner is raw_best else
                f" {raw_best.name} scored {results[raw_best.name]['recall_at_3']:.3f}, but {winner.name} is within "
                f"{TIE_MARGIN} and simpler, so {winner.name} wins.")
    print(f"Winner: {winner.name} ({winner.label}).{tie_note}")

    # ---- Suggestion cutoff: chosen on April-May only, then checked on June.
    apr = kept[kept["month"] == "2026-04"]
    may = kept[kept["month"] == "2026-05"]
    cutoff, cutoff_grid = choose_cutoff(type(winner), apr, may, products, rules, xyw)
    print(f"Cutoff chosen on Apr->May validation: {cutoff:.2f}")
    final_tr = next(r for r in rankers if r.name == winner.name)  # already trained on Apr-May
    june_cut = policy_eval(final_tr, Xte, test, products, rules, cutoff, use_interest=False)
    june_015 = policy_eval(final_tr, Xte, test, products, rules, DEFAULT_CUTOFF, use_interest=False)

    # ---- June, two scenarios of what the owner taps.
    scen_none = june_cut
    scen_interest = policy_eval(final_tr, Xte, test, products, rules, cutoff, use_interest=True)
    old_unwanted_none = old_policy_unwanted(final_tr, Xte, test, products, rules, use_interest=False)
    old_unwanted_int = old_policy_unwanted(final_tr, Xte, test, products, rules, use_interest=True)

    # ---- Chain rule: Recall@3 for chains, before and after putting P09/P10 first.
    chains = test[test["segment"] == "Small multi-site businesses"]
    Xch = features(chains)
    chain_before = policy_eval(final_tr, Xch, chains, products, rules, 0.0, False, chain_rule=False)["sug_recall"]
    chain_after = policy_eval(final_tr, Xch, chains, products, rules, 0.0, False, chain_rule=True)["sug_recall"]
    chain_after_cut = policy_eval(final_tr, Xch, chains, products, rules, cutoff, False, chain_rule=True)
    all_before = policy_eval(final_tr, Xte, test, products, rules, 0.0, False, chain_rule=False)["sug_recall"]
    all_after = policy_eval(final_tr, Xte, test, products, rules, 0.0, False, chain_rule=True)["sug_recall"]
    print(f"Chain Recall@3: {chain_before:.3f} -> {chain_after:.3f} with P09/P10 first "
          f"(all customers {all_before:.3f} -> {all_after:.3f})")
    print(f"June suggestions @cutoff {cutoff:.2f}: precision {june_cut['sug_precision']:.2f}, "
          f"recall {june_cut['sug_recall']:.2f} | @0.15: precision {june_015['sug_precision']:.2f}, "
          f"recall {june_015['sug_recall']:.2f}")

    # ---- Retrain the winner on ALL kept customers (+ optional app log weak labels) and export.
    all_rows = kept[["business_type", "segment", "city_tier", "has_gstin", "current_internet",
                     "employee_count", "num_sites", "bundle", "weight"]].copy()
    n_events = 0
    if os.path.exists(args.events):
        extra = load_app_events(args.events)
        n_events = len(extra)
        if n_events:
            all_rows = pd.concat([all_rows, extra], ignore_index=True)
    if os.path.exists(args.events):
        ev = pd.read_csv(args.events, dtype=str).fillna("")
        sug = ev[ev["event"] == "SUGGESTED"][["session_id", "product_id"]].drop_duplicates()
        on = ev[ev["event"] == "TOGGLED_ON"][["session_id", "product_id"]].drop_duplicates()
        if len(sug):
            taken = len(sug.merge(on, on=["session_id", "product_id"]))
            print(f"App log: {taken} of {len(sug)} suggestions were switched on ({taken / len(sug):.0%})")
        kyc = ev[ev["event"] == "PROCEEDED_TO_KYC"]
        if len(kyc):
            counts = kyc["product_id"].replace("", "YOUR_PLAN").value_counts().to_dict()
            print(f"App log: plan taken to KYC by pack: {counts}")
    print(f"App-log weak labels used: {n_events} accepted sessions" + ("" if os.path.exists(args.events) else " (no tools/app_events.csv)"))

    Xall, Yall, wall = xyw(all_rows)
    final = type(winner)().fit(Xall, Yall, wall, products)
    seg_pop = SegmentPopularity().fit(Xall, Yall, wall, products)

    real = kept  # social-proof counts come only from real, verified customers
    by_type, by_seg = {}, {}
    for bt, g in real.groupby("business_type"):
        # Score for this business type: the winner's average score over its real customers.
        S = final.score(features(g)).mean()
        by_type[bt] = {p: {"score": round(float(S[p]), 4),
                           "kept": int(g["bundle"].apply(lambda b: p in b).sum()), "of": int(len(g))}
                       for p in products}
    for seg, g in real.groupby("segment"):
        by_seg[seg] = {p: {"score": round(seg_pop.rate[seg][p], 4),
                           "kept": int(g["bundle"].apply(lambda b: p in b).sum()), "of": int(len(g))}
                       for p in products}

    out = {
        "version": date.today().isoformat(),
        "label": "activated, not churned in 6 months, NPS>=7 weighted",
        "ranker": winner.label,
        "min_proof": MIN_PROOF,
        "suggest_cutoff": cutoff,
        "by_business_type": by_type,
        "by_segment": by_seg,
        "default_qty": default_quantities(kept, cat, products),
        "eval": {
            "winner": winner.name,
            "recall_at_3": round(results[winner.name]["recall_at_3"], 3),
            "recall_at_5": round(results[winner.name]["recall_at_5"], 3),
            "baseline_recall_at_3": round(results["A"]["recall_at_3"], 3),
            "test_customers": int(len(test)),
            "test_customers_with_extras": int(results[winner.name]["n"]),
            "train_customers": int(len(train)),
            "violations": 0,
            "app_event_sessions": n_events,
            "suggest_cutoff": cutoff,
            "suggest_precision": round(june_cut["sug_precision"], 3),
            "suggest_recall": round(june_cut["sug_recall"], 3),
            "suggest_any_useful": round(june_cut["sug_any_useful"], 3),
            "interest_on_precision": round(scen_interest["on_precision"], 3),
            "interest_coverage": round(scen_interest["coverage"], 3),
            "unwanted_on_before": round(old_unwanted_none, 2),
            "unwanted_on_now": round(scen_none["unwanted_on"], 2),
            "chain_recall_at_3_before": round(chain_before, 3),
            "chain_recall_at_3_after": round(chain_after, 3),
        },
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"Wrote {args.out}")

    # ---- Report
    L = ["# Recommender evaluation", "",
         f"Generated {date.today().isoformat()} by `tools/train_recommender.py`.", "",
         "**Label.** A product is *kept* if it is in the bundle of a lead that activated and did not churn within "
         f"6 months ({len(kept)} customers). Customers with NPS ≥ 7 count 1.5×.", "",
         f"**Why not stated interest?** Only {interest_overlap:.0%} of the products activated leads said they were "
         "interested in ended up in their final bundle, so `products_of_interest` is not used as a label.", "",
         f"**Split.** Train on {len(train)} customers created Apr–May 2026, test on {len(test)} created Jun 2026 "
         f"({results['A']['n']} of them kept at least one extra). Only extras (P09–P25) are ranked; the main link and "
         "mobile lines are chosen by rules. Candidates are filtered with the app's rules before ranking.", "",
         "| Ranker | Recall@3 | Recall@5 | Rule violations |", "|---|---|---|---|"]
    for r in rankers:
        m = results[r.name]
        mark = " **(winner)**" if r is winner else ""
        L.append(f"| {r.name}. {r.label}{mark} | {m['recall_at_3']:.3f} | {m['recall_at_5']:.3f} | {m['violations']} |")
    L += ["", "### Per segment (Recall@3 / Recall@5, test customers)", "",
          "| Segment | n | " + " | ".join(r.name for r in rankers) + " |", "|---|---|" + "---|" * len(rankers)]
    for seg in sorted(results["A"]["per_segment"]):
        cells = [f"{results[r.name]['per_segment'][seg]['recall_at_3']:.2f} / {results[r.name]['per_segment'][seg]['recall_at_5']:.2f}"
                 for r in rankers]
        L.append(f"| {seg} | {results['A']['per_segment'][seg]['n']} | " + " | ".join(cells) + " |")
    L += ["", "## What the app now does", "",
          "Only extras the owner **tapped** are switched on. The learned top 3 are shown under "
          "*Businesses like yours often add*, switched **off**. For 3+ sites, failover (P09) and SD-WAN (P10) "
          "come first if eligible. The ranker below is the winner, trained on Apr–May; all numbers are on June.", "",
          "### June, two scenarios side by side", "",
          "Scenario 1: the owner taps nothing. Scenario 2: the owner taps what they said they were interested in "
          "(`products_of_interest`, extras only, filtered by the rules). Stated interest is only a stand-in for "
          "what owners would tap in the app.", "",
          "| | 1. Taps nothing | 2. Taps stated interest |", "|---|---|---|",
          f"| Extras switched ON per customer | {scen_none['n_on']:.2f} | {scen_interest['n_on']:.2f} |",
          f"| …of which not kept (unwanted cost) | {scen_none['unwanted_on']:.2f} | {scen_interest['unwanted_on']:.2f} |",
          f"| Share of ON extras actually kept | – | {scen_interest['on_precision']:.0%} |",
          f"| Suggestions shown (OFF) per customer | {scen_none['n_sug']:.2f} | {scen_interest['n_sug']:.2f} |",
          f"| Share of suggestions actually kept | {scen_none['sug_precision']:.0%} | {scen_interest['sug_precision']:.0%} |",
          f"| At least one suggestion kept | {scen_none['sug_any_useful']:.0%} | {scen_interest['sug_any_useful']:.0%} |",
          f"| Kept extras found in ON + suggestions | {scen_none['coverage']:.0%} | {scen_interest['coverage']:.0%} |",
          f"| *Before this change:* unwanted extras switched ON | {old_unwanted_none:.2f} | {old_unwanted_int:.2f} |", "",
          "### Suggestion cutoff (chosen on April–May only)", "",
          f"Trained on April, validated on May, picked the cutoff with the best F1 of suggestions "
          f"(ties → higher cutoff). **Chosen: {cutoff:.2f}.**", "",
          "| Cutoff | May precision | May recall | May F1 | Suggestions per customer |", "|---|---|---|---|---|"]
    for c, e in cutoff_grid:
        mark = " **←**" if c == cutoff else ""
        L.append(f"| {c:.2f}{mark} | {e['sug_precision']:.2f} | {e['sug_recall']:.2f} | {e['sug_f1']:.2f} | {e['n_sug']:.2f} |")
    L += ["", "June check (model trained on Apr–May):", "",
          "| Cutoff | Precision | Recall | F1 | Suggestions per customer |", "|---|---|---|---|---|",
          f"| {cutoff:.2f} (chosen) | {june_cut['sug_precision']:.2f} | {june_cut['sug_recall']:.2f} | {june_cut['sug_f1']:.2f} | {june_cut['n_sug']:.2f} |",
          *([f"| 0.15 (old default) | {june_015['sug_precision']:.2f} | {june_015['sug_recall']:.2f} | {june_015['sug_f1']:.2f} | {june_015['n_sug']:.2f} |"]
            if cutoff != DEFAULT_CUTOFF else ["", "The data picked the same value as the old hand-set default, 0.15. "
                                              "Cutoffs 0.00-0.15 all keep the full 3 suggestions, so they tie; ties go to the higher cutoff."]),
          "", "### Chains: P09 / P10 first for 3+ sites", "",
          "| | Recall@3, chains (n=" + str(len(chains)) + ") | Recall@3, all customers |", "|---|---|---|",
          f"| Learned order only | {chain_before:.3f} | {all_before:.3f} |",
          f"| P09/P10 first, then learned | {chain_after:.3f} | {all_after:.3f} |",
          f"| …and with the {cutoff:.2f} cutoff | {chain_after_cut['sug_recall']:.3f} | {june_cut['sug_recall']:.3f} |", "",
          "Weights: the −0.20 budget penalty (a suggestion that would push the plan over budget is less likely to be "
          "shown) is a design choice, not learned. The old +0.30 bonus for tapped needs is retired: tapped needs are "
          "now always switched on. Budgets are not in the leads data, so the penalty is not part of this evaluation."]
    wa, wb = results[winner.name], results["A"]
    gain = wa["recall_at_3"] - wb["recall_at_3"]
    if winner.name == "A":
        others = ", ".join(f"{r.name} {results[r.name]['recall_at_3']:.0%}" for r in rankers if r.name != "A")
        first = (f"The simplest ranker, segment popularity, won: its top 3 suggestions contain "
                 f"{wa['recall_at_3']:.0%} of the extras that June customers actually kept, and the richer "
                 f"rankers did not beat it ({others}), so the app uses A.{tie_note} ")
    else:
        first = (f"{winner.name} ({winner.label}) is used in the app: its top 3 suggestions contain "
                 f"{wa['recall_at_3']:.0%} of the extras that June customers actually kept, versus "
                 f"{wb['recall_at_3']:.0%} for plain segment popularity ({gain:+.0%}).{tie_note} ")
    L += ["", "### Conclusion", "", first +
          f"No ranker ever suggested a product the rules forbid (0 violations), because rules filter before ranking. "
          f"With only {len(test)} test customers from one month of synthetic data, differences of a few points are "
          f"within noise, so treat these numbers as a sanity check rather than proof.", "",
          f"App-log weak labels used in the exported model: {n_events} accepted sessions."]
    with open(args.report, "w") as f:
        f.write("\n".join(L) + "\n")
    print(f"Wrote {args.report}")


if __name__ == "__main__":
    main()
