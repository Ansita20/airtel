# Udyam Digital (Android, Java)

## Run from the terminal

```sh
cd UdyamDigital
./gradlew test            # JUnit tests (RulesEngine + GSTIN lookup)
adb devices               # phone must show as "device" (USB debugging on, prompt accepted)
./gradlew installDebug    # build and install on the connected phone
adb shell am start -n com.udyam.digital/.ui.LanguageActivity   # optional: launch it
```

Test report: `app/build/reports/tests/testDebugUnitTest/index.html`

## Notes

- JDK 17 is pinned in `gradle.properties` (`org.gradle.java.home`) because Gradle 8.14 can't run on JDK 26.
- `compileSdk 35` (android-34 isn't installed); `targetSdk 34`, `minSdk 26`.
- Engine rules and assumptions are documented at the top of `engine/RulesEngine.java`.

## How the recommender learns

### The hybrid design

```
RULES decide what is ALLOWED  ->  LEARNED SCORES decide the ORDER  ->  the USER's needs and budget decide what is SHOWN
```

This is the "knowledge-based + learned ranking" hybrid used by real telecom B2B recommenders
(VodafoneZiggo's SMB tool: a tree model ranks products, a rule layer blocks incompatible
combinations). The rules are the safety net. Nothing learned can override them.

1. **Rules** (`engine/RulesEngine.java`): city tier, minimum staff and sites, GSTIN, and
   `requires_one_of` remove ineligible products. The main link and mobile lines are chosen by
   rules alone. Someone who only wants a mobile plan gets P07/P08 with no extras.
2. **Learned scores** (`engine/ProductScorer.java`, data in `app/src/main/assets/product_scores.json`):
   how often businesses like this one *kept* each extra. The lookup uses the business type,
   then the segment, then 0.
3. **What is shown**:
   - **You asked for** (ON): only extras matching a need the owner tapped. Nothing else is
     switched on, so the price only contains what was asked for.
   - **Businesses like yours often add** (OFF, at most 3): for 3+ sites, failover (P09) and
     SD-WAN (P10) come first if eligible. After that, untapped extras are taken in order of
     `learned score − 0.20 if switching it on would exceed the budget`, as long as that is
     at least the cutoff. Switching one on is logged.
   - **You can also add** (OFF): every other eligible extra.
   - **Plan screen packs.** The top card shows **Your plan**: tapped needs plus the top
     learned picks, switched on while the total stays within the budget (all picks if no
     budget is chosen). Below it, **More options** (collapsed) lists:
     | Pack | What it is |
     |---|---|
     | Basic | Core link + SIM lines + only the tapped needs |
     | Premium | Next internet tier if eligible (100 → 300 Mbps; 300 Mbps → 1 Gbps in Tier 1), ₹599 SIMs instead of ₹299, failover (P09) if eligible, plus the learned picks |
     | Premium+ | Premium + every other eligible extra kept by ≥ 10% of similar businesses |

     Each row shows the monthly total, the difference from Your plan, and what it adds or
     leaves out. Suggested packs may be **up to 20% over budget**: they show "₹X over
     budget" in amber, are never preselected, and are hidden if more than 20% over. A pack
     identical to another is hidden. Learned additions need a score of at least 0.10, and no
     quantity exceeds the number of staff or sites. Tapping **Choose this plan** makes a
     pack the selected plan; switches and quantities still work on it. The choice is
     logged (`PACK_CHOSEN`), and the KYC event carries the pack name. The leads data has no
     budgets, so the June evaluation below assumes no budget was chosen.
   - **Weights:** the cutoff (0.15) was chosen on April–May data only (train on April,
     validate on May, best F1) and then checked on June. The **−0.20 budget penalty is a
     design choice, not learned**; budgets aren't in the leads data, so it can't be tested.
     The earlier **+0.30 bonus for tapped needs was also a design choice**. It's now retired,
     because tapped needs are always switched on. Both constants are at the top of `RulesEngine`.
4. **Why**: each recommended extra shows at most 2 facts: the need the owner tapped ("You said
   you want UPI billing"), and real counts ("9 of 22 businesses like yours kept this for 6+
   months"). The counts are hidden below 5 businesses. Quantities start from the median of real
   bundles (per site or per employee, capped by staff or sites), and the owner can change them.

### Label definition

A product is **kept** if it is in `bundle_products` of a lead with `outcome = Activated` and
`churned_within_6_months = N` (312 customers). Customers with NPS ≥ 7 count 1.5×.
`products_of_interest` is **not** used: only 28% of the products activated leads said they
wanted ended up in their final bundle.

Features are only what the questionnaire collects: business type, segment, city tier, staff,
sites, GSTIN yes/no and current internet. Names, state, city and any other personal fields
are never used. A unit test checks that two leads differing only in name and state get the
same plan.

### Training and evaluation

```sh
python3 -m venv tools/.venv && tools/.venv/bin/pip install -r tools/requirements.txt
tools/.venv/bin/python tools/train_recommender.py      # writes product_scores.json + tools/eval_report.md
```

The script reads `data/smb_leads.csv` and `data/product_catalogue.csv`, or the copies one folder
up if `data/` doesn't exist. It trains on leads created April–May 2026 (206 kept customers) and
tests on June 2026 (106). Candidates are filtered with the app's rules before ranking.
**Recall@k** is the share of the extras a June customer really kept that appear in the top k.

| Ranker | Recall@3 | Recall@5 | Rule violations |
|---|---|---|---|
| A. Segment popularity **(winner)** | 0.662 | 0.853 | 0 |
| B. Business-type popularity (smoothed, m=5) | 0.621 | 0.827 | 0 |
| C. Logistic regression per product (C=0.5), fallback B | 0.634 | 0.824 | 0 |

**Result:** plain segment popularity (A) won. The business-type ranker (B) and the per-product
logistic regression (C) did not beat it on June customers, so the app ships A. There are 0 rule
violations by construction.

**What the plan screen does with it (June, model trained on April–May).** The second column
pretends owners tap what they said they were interested in (`products_of_interest`). That's
only a stand-in for real taps.

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

Before this change, about 1.4 unwanted extras were switched on per customer; now none are
unless the owner taps them. Stated interest turns out to be a poor guide: only 15% of the
extras people said they wanted were kept. So even tapped needs deserve the "switch it off"
option the plan already gives.

June check (model trained on Apr–May):

| Cutoff | Precision | Recall | F1 | Suggestions per customer |
|---|---|---|---|---|
| 0.15 (chosen) | 0.51 | 0.67 | 0.58 | 3.00 |

The data picked the same value as the old hand-set default, 0.15. Cutoffs 0.00-0.15 all keep the full 3 suggestions, so they tie; ties go to the higher cutoff.

**Chains, P09/P10 first for 3+ sites:**

| | Recall@3, chains (n=26) | Recall@3, all customers |
|---|---|---|
| Learned order only | 0.504 | 0.662 |
| P09/P10 first, then learned | 0.515 | 0.665 |
| …and with the 0.15 cutoff | 0.515 | 0.665 |

Putting P09/P10 first helps chains only slightly, because the learned order already
ranked them high. Full numbers, including the cutoff grid, are in `tools/eval_report.md`. In
the app, long-press the "Udyam Digital" title on the language screen to see them.

### Learning from use (privacy-safe)

`engine/RecommendationLog.java` appends one row per event to an app-private file:
`session_id` (random UUID), timestamp, business type, segment, city tier, employee band,
sites band, GSTIN yes/no, product id, and the event (`SHOWN` = on when the plan appeared, `SUGGESTED` = shown off as "often add",
`TOGGLED_ON`, `TOGGLED_OFF`, `PACK_CHOSEN`, `PROCEEDED_TO_KYC`, `ABANDONED`). The trainer prints what share of
suggestions owners switched on. There are no names, GSTIN numbers, phone numbers, cities or
free text. Rows older than 90 days are deleted every time the app starts. The plan screen's
"How we picked this" card says this to the owner.

To retrain with it, open the debug screen, tap **Export log**, and save the file as
`tools/app_events.csv`. Then rerun the script. In sessions that reached KYC, products that were
shown and kept on, or switched on, become extra labels with weight 0.3. The script prints how
many sessions it used.

### Limits

- **Small sample.** There are 312 kept customers and only 106 test customers from one month.
  Differences of a few points are within noise.
- **One quarter of data, and it is synthetic.** The leads look generated by segment, which
  is probably why segment popularity is hard to beat. Real data may favour B or C. The script
  re-runs the comparison every time, so the winner can change.
- **Coarse tiles.** The Clinic, Office, Chain and Factory tiles each cover several business
  types, so they use segment-level scores. Splitting those tiles would let the type-level
  counts help.
- **Untranslated reasons.** The plan's reason text is in English and Hindi only. The other 8
  languages show English reasons.
- **Rules stay the safety net.** A product the rules forbid is never shown, whatever its score.

## Smart KYC

KYC starts from **Next: KYC** on the plan screen. It ends with **KYC complete** on the order
tracker. Every date check uses `KycClock.TODAY` = **2026-07-15**, never the phone's own date.

**Flow.** One step per screen, with "Step n of 5" at the top. It is saved after every change,
so the owner can leave and resume. Each screen is in English and Hindi.
1. Consent (DPDP Act, 2023)
2. GSTIN (or "I don't have a GSTIN"), Udyam number, and an optional installation address
3. Upload the GST certificate, Udyam certificate, signatory ID and address proof (camera,
   gallery, or **DigiLocker (Demo)**)
4. Review: each check shown as ✓ / ⚠ / ✗, with "Fix this" or "Yes, this is correct"
5. Result: Approved, Please fix N items, or Sent for manual review with the reason

The Approved result offers **e-Sign with Aadhaar OTP (Demo)**.

**Code.**
- `kyc/` is pure Java and unit-tested: `GstinValidator`, `UdyamValidator`, `NameMatcher`
  (Jaro-Winkler), `KycDates`, `DocumentClassifier`, `FieldExtractor`, `ImageQuality`
  (Laplacian variance), `PerceptualHash` (dHash) and `KycRuleEngine` (checks C01–C19).
- `kycui/` is the Android side: on-device ML Kit OCR (Latin + Devanagari, bundled models),
  CameraX capture, encrypted storage and the screens.

**OCR on the specimens.** The documents carry a diagonal "SYNTHETIC SPECIMEN" stamp. The
extractor drops those lines (by their angle and keywords), straightens tilted scans using
ML Kit's line angles (K02 is rotated about 7°), rejoins addresses split mid-word, and
rebuilds a Udyam number whose "UDYAM" prefix the stamp hid. A GSTIN character is repaired
only where a digit sits in a letter-only position, and only if the repair gives a valid
checksum. The check character itself is never changed, so a real typo (K06) is still caught.

**Decision.**
- **Manual review:** any FAIL the owner can't fix, such as a cancelled or suspended GSTIN,
  not in the registry, PAN mismatch, a name far off, or under 18. This creates a ticket and
  adds 30 to the help bot's escalation score.
- **Please fix:** any fixable FAIL (old bill, wrong document, blurry photo, typo) or any
  warning not yet confirmed.
- **Approved:** otherwise.

A cancelled or suspended GSTIN also offers "See a plan without GST products", which re-runs
the plan without GSTIN products.

### Privacy note

- **What and why:** the GST certificate (business registered and active), Udyam certificate
  (MSME status), signatory ID (who can sign) and address proof (installation address).
  Nothing else is collected.
- **On the phone only:** OCR runs on the device, so photos are never uploaded for reading.
  Photos are stored with `EncryptedFile`, and details with `EncryptedSharedPreferences`
  (Android Keystore), in app-private storage only. KYC screens set `FLAG_SECURE`, which
  blocks screenshots and screen recording.
- **Masked everywhere:** GSTIN `27******34X1Z5`, PAN `ABCXX****F`, and only the last 4
  Aadhaar digits. A full Aadhaar number is never stored: an unmasked Aadhaar is refused, and
  the owner is asked for a masked one.
- **Retention:** documents are deleted 30 days after KYC is approved or rejected. Only the
  result and masked IDs are kept for the contract. This runs automatically; the debug
  screen also has "Run KYC retention now" and a "+31 days" simulation.
- **Withdraw:** "Withdraw consent and delete my documents" (⚙ on the KYC screens) deletes
  every KYC file and record from the phone.
- **Consent record:** timestamp, notice version, and the KYC and marketing choices. It
  contains no document data. Marketing is never pre-ticked.
- **Logs and help bot:** the log holds only application id, check code, result and
  timestamp. The help bot sees only check codes and masked IDs.

### Specimen harness (K01–K12)

- The 47 images are copied to `app/src/debug/assets/kyc_samples/`, so they ship in the
  **debug build only**. The release APK contains none of them.
- Debug screen (long-press the title) → **Run all KYC specimens** runs OCR and every check on
  the phone. It shows applicant | result | flagged checks | message, and saves the OCR output.
- That output is checked in under `app/src/test/resources/kyc_ocr/`. `SpecimenReplayTest`
  replays it through the same code in `./gradlew test`.
- Fill in `app/src/test/resources/kyc/expected_results.json` (overall result plus the check
  codes that must be flagged). The test then compares actual against expected. Empty
  entries are skipped.
- The specimens have no typed-in numbers, so the "entered" GSTIN and Udyam are read from each
  applicant's certificates. This means C03 and C06 can't catch a typing mistake in the harness.

**Known limits.**
- The ML Kit OCR library is not 16 KB page-size aligned. Android 15+ runs it in
  compatibility mode and shows a one-time warning. CameraX is on 1.4.2, which is aligned.
- The KYC screens are in English and Hindi only; other languages fall back to English.

## Smart KYC: scanning and accuracy

- **Scan**: Google ML Kit Document Scanner (edge detection, auto-capture, crop, perspective fix,
  shadow/stain cleanup, 1 page, gallery import). Without Google Play services the app falls back
  to a CameraX screen with a document-shaped guide frame.
- **Quality gate before OCR**: short side ≥ 1000 px for pages (ID cards: long side ≥ 1000 px,
  since the specimen IDs are 1012 × 638), brightness, blur (Laplacian variance), glare. A failing
  photo is never read; the owner sees the exact reason and a Rescan button.
- **OCR**: ML Kit Text Recognition v2 on device, Latin + Devanagari, run twice (scan + enhanced
  copy); per field the more confident reading wins. **ML Kit does not read Tamil, Bengali,
  Telugu, Kannada, Malayalam, Gujarati or Gurmukhi script.** KYC documents are English/Hindi,
  so this is fine; the app UI languages are unaffected.
- **ID repair**: GSTIN / Udyam / PAN look-alikes (O/0, I/1, L/1, S/5, B/8, Z/2, G/6, and 0 for
  D/Q/U) are repaired only if exactly one candidate passes format + checksum AND is in the
  registry. Names and dates are never auto-corrected; ambiguous dates (03/04/2026) are asked.
- **Unsure = rescan**: a FAIL resting on a field read below 0.80 confidence that the owner didn't
  confirm becomes "We couldn't read this clearly - please rescan", never a rejection.
- **C20 possible tampering** (two confident OCR passes disagree on an identity field, or mixed
  font sizes in one field) → manual review.

### Specimen accuracy proof

1. Fill in `app/src/debug/assets/kyc/expected_results.json` (Approved / Please fix / Manual review
   + checks that must be flagged).
2. On the phone (debug build): Debug → **Run all specimens**. It prints the file → applicant/slot
   mapping, runs the full pipeline and shows expected vs actual, false rejects and false accepts.
3. `tools/pull_specimen_dumps.sh` copies the phone's OCR output into the JUnit replay, then
   `./gradlew test`: `SpecimenReplayTest` fails if any applicant mismatches and writes
   `app/build/reports/kyc/specimen_report.md`.

## Partner mode (back office) and "which lead to call"

- **Open it:** long-press the logo on the welcome screen, PIN **2468** (demo), then pick
  *Accel field sales (all cities)* or *Channel partner · {city}*. A partner only ever sees their city.
- **Tabs:** Stuck in app · Needs a person · Install delayed · Dashboard. Seeded with June leads from
  `smb_leads.csv` that dropped for a reason someone can act on (masked, no contacts), plus live
  leads from this phone (unfinished KYC, manual review, help-bot handoff, late install, plan not accepted).
- **Score:** `tools/train_propensity.py` -> `assets/propensity_model.json`; `PropensityScorer.java`
  gives 0-100 (percentile; 80+ = top 20%) + top 2 reasons. See `tools/propensity_report.md`.
  `stage_reached` in the CSV is the final outcome (AUC 1.0), so the model uses the stage reached by day 7.
- **Privacy:** masked names, no documents, screenshots blocked, every partner action in the encrypted access log.
- **Cost assumptions** (digital-only / assisted cost) are constants in `partner/PartnerDashboard.java`.

```sh
tools/.venv/bin/python tools/train_propensity.py   # retrain; rewrites the model, seed and report
```
