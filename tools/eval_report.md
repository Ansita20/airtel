# Recommender evaluation

Generated 2026-11-06 by `tools/train_recommender.py`.

**Label.** A product is *kept* if it is in the bundle of a lead that activated and did not churn within 6 months (312 customers). Customers with NPS ≥ 7 count 1.5×.

**Why not stated interest?** Only 28% of the products activated leads said they were interested in ended up in their final bundle, so `products_of_interest` is not used as a label.

**Split.** Train on 206 customers created Apr–May 2026, test on 106 created Jun 2026 (104 of them kept at least one extra). Only extras (P09–P25) are ranked; the main link and mobile lines are chosen by rules. Candidates are filtered with the app's rules before ranking.

| Ranker | Recall@3 | Recall@5 | Rule violations |
|---|---|---|---|
| A. Segment popularity **(winner)** | 0.662 | 0.853 | 0 |
| B. Business-type popularity (smoothed, m=5) | 0.621 | 0.827 | 0 |
| C. Logistic regression per product (C=0.5), fallback B | 0.634 | 0.824 | 0 |

### Per segment (Recall@3 / Recall@5, test customers)

| Segment | n | A | B | C |
|---|---|---|---|---|
| Micro retail & services | 29 | 0.78 / 0.97 | 0.66 / 0.97 | 0.71 / 0.95 |
| Professional offices | 36 | 0.69 / 0.82 | 0.69 / 0.81 | 0.69 / 0.84 |
| Small manufacturers | 13 | 0.65 / 0.88 | 0.64 / 0.82 | 0.63 / 0.78 |
| Small multi-site businesses | 26 | 0.50 / 0.76 | 0.48 / 0.70 | 0.47 / 0.69 |

## What the app now does

Only extras the owner **tapped** are switched on. The learned top 3 are shown under *Businesses like yours often add*, switched **off**. For 3+ sites, failover (P09) and SD-WAN (P10) come first if eligible. The ranker below is the winner, trained on Apr–May; all numbers are on June.

### June, two scenarios side by side

Scenario 1: the owner taps nothing. Scenario 2: the owner taps what they said they were interested in (`products_of_interest`, extras only, filtered by the rules). Stated interest is only a stand-in for what owners would tap in the app.

| | 1. Taps nothing | 2. Taps stated interest |
|---|---|---|
| Extras switched ON per customer | 0.00 | 1.13 |
| …of which not kept (unwanted cost) | 0.00 | 0.94 |
| Share of ON extras actually kept | – | 15% |
| Suggestions shown (OFF) per customer | 3.00 | 2.95 |
| Share of suggestions actually kept | 51% | 53% |
| At least one suggestion kept | 94% | 95% |
| Kept extras found in ON + suggestions | 67% | 74% |
| *Before this change:* unwanted extras switched ON | 1.44 | 1.59 |

### Suggestion cutoff (chosen on April–May only)

Trained on April, validated on May, picked the cutoff with the best F1 of suggestions (ties → higher cutoff). **Chosen: 0.15.**

| Cutoff | May precision | May recall | May F1 | Suggestions per customer |
|---|---|---|---|---|
| 0.00 | 0.52 | 0.63 | 0.57 | 3.00 |
| 0.05 | 0.52 | 0.63 | 0.57 | 3.00 |
| 0.10 | 0.52 | 0.63 | 0.57 | 3.00 |
| 0.15 **←** | 0.52 | 0.63 | 0.57 | 3.00 |
| 0.20 | 0.51 | 0.57 | 0.54 | 2.76 |
| 0.25 | 0.51 | 0.57 | 0.54 | 2.76 |
| 0.30 | 0.53 | 0.54 | 0.53 | 2.52 |
| 0.35 | 0.57 | 0.49 | 0.53 | 2.28 |
| 0.40 | 0.59 | 0.49 | 0.53 | 2.22 |
| 0.45 | 0.65 | 0.43 | 0.52 | 1.80 |
| 0.50 | 0.65 | 0.43 | 0.52 | 1.80 |
| 0.55 | 0.81 | 0.35 | 0.49 | 1.30 |
| 0.60 | 0.82 | 0.31 | 0.45 | 1.03 |

June check (model trained on Apr–May):

| Cutoff | Precision | Recall | F1 | Suggestions per customer |
|---|---|---|---|---|
| 0.15 (chosen) | 0.51 | 0.67 | 0.58 | 3.00 |

The data picked the same value as the old hand-set default, 0.15. Cutoffs 0.00-0.15 all keep the full 3 suggestions, so they tie; ties go to the higher cutoff.

### Chains: P09 / P10 first for 3+ sites

| | Recall@3, chains (n=26) | Recall@3, all customers |
|---|---|---|
| Learned order only | 0.504 | 0.662 |
| P09/P10 first, then learned | 0.515 | 0.665 |
| …and with the 0.15 cutoff | 0.515 | 0.665 |

Weights: the −0.20 budget penalty (a suggestion that would push the plan over budget is less likely to be shown) is a design choice, not learned. The old +0.30 bonus for tapped needs is retired: tapped needs are now always switched on. Budgets are not in the leads data, so the penalty is not part of this evaluation.

### Conclusion

The simplest ranker, segment popularity, won: its top 3 suggestions contain 66% of the extras that June customers actually kept, and the richer rankers did not beat it (B 62%, C 63%), so the app uses A. No ranker ever suggested a product the rules forbid (0 violations), because rules filter before ranking. With only 106 test customers from one month of synthetic data, differences of a few points are within noise, so treat these numbers as a sanity check rather than proof.

App-log weak labels used in the exported model: 0 accepted sessions.
