# Propensity model - which leads to call

Target: `outcome == Activated`. Train: leads created April-May 2026 (3,295, 7.3% activated). Test: June (1,704, 7.5% activated).

Features: segment, business type, city tier, log employees, log sites, has GSTIN, has Udyam, current internet, log monthly telecom spend, lead source, journey stage. No names, contacts, numbers or state.

## The stage feature (important)

`stage_reached` in the file is the **final** stage: it is "Activated" for exactly the activated leads. Used as a feature it gives **AUC 1.000** - it is the answer, not a predictor. We use the stage the lead had reached **7 days after it was created** (from the stage dates): what a salesperson knows when deciding whom to call. 1 lead(s) had already activated by day 7 and are left out. Stage alone (day 7) scores AUC 0.827 on the June test.

## Results (June test set)

| Model | AUC | Activation rate in top 20% | Share of activations in top 20% |
|---|---|---|---|
| Logistic regression | 0.866 | 25.8% | 69% |
| Decision tree (depth 4) | 0.837 | 24.3% | 65% |

Average activation rate: 7.5%. **Chosen: logistic regression** (within 0.02 AUC of the tree or better, and simpler to explain). **Lift: calling the top 20% finds 3.5x the average activation rate** (25.8% vs 7.5%) and 69% of all June activations.

## Score in the app

`PropensityScorer.java` reads `propensity_model.json` and gives a 0-100 score = the lead's percentile among April-May leads (80+ = top 20%), with the top 2 reasons (largest positive contributions, e.g. "has GSTIN", "reached quote stage"). In the app the stage is the stage the lead is at now. `PropensityScorerTest` checks it matches this script on 20 June leads.

## Strongest signals (logistic regression coefficients)

| Pushes the score up | weight | Pushes it down | weight |
|---|---|---|---|
| reached quote stage | +2.14 | not contacted yet | -3.20 |
| has GSTIN | +1.39 | was contacted | -1.17 |
| finished KYC | +1.26 | restaurant chain | -0.62 |
| needs assessed | +0.96 | micro retail & services | -0.56 |
| came by referral | +0.69 | hardware store | -0.55 |
| uses leased line | +0.54 | tier 2 city | -0.47 |
| gym chain | +0.51 | pharmacy | -0.44 |
| electronics retail chain | +0.39 | bakery | -0.41 |

## Partner view seed

1025 June leads that dropped for a reason someone can act on, masked ("Ba**** De**** Ca** LLP"), no contact names, phone numbers, GSTIN or Udyam numbers. Not listed: unreachable, wrong number, not interested.

| Tab | Leads |
|---|---|
| Stuck in app | 581 |
| Needs a person | 398 |
| Install delayed | 46 |

## Cost figures (dashboard)

Blended acquisition cost: total ₹5,539,640 / 368 activations = **₹15,053 per activated SMB**. Activations that started self-serve (website form or WhatsApp): 14% - used in the app as the starting estimate of "could need no human".

## Limits

- One quarter of synthetic data; June leads had until 26 July to activate, like earlier months.
- The score ranks leads; it is not a calibrated probability (base rate is ~7%).
- Re-train when real app events arrive (the stage then comes from the app, not sales notes).
