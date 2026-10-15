# §23e AskMed brief: how it earns trust, and how Stethoscore reaches it and beats it

Date 15 Oct 2026. For the owner's request of 15 Oct: "a comprehensive deep research on the competitive app AskMed and how to teach his accuracy and confidence."

**Reading note.** "Teach" is read as "reach": how Stethoscore matches the accuracy AskMed offers and the confidence it gives students, then goes past both. If another meaning was intended, §7 is the part to re-aim.

**How it was made.** Three passes on 15 Oct:
- **AskMed's public surfaces:** App Store pages in 47 storefronts, nine screenshots, the website, privacy policy and terms. No sign-up, purchase or login.
- **The field:** methods that raise accuracy or calibrate trust, leading products, interface studies, exam-item studies and source licences.
- **Stethoscore's own layer,** read from git: red-pen-ios personal 2b7ef11e (RP), the verification-bench-results branch at 16e2f927 (Bench) and Chat-me personal cda5d2c4 (CM; "CM plan.md" is its docs/architecture/handoff/plan.md).

[Sn] points to Sources. "Inference" marks a reading, not a source's words. "Vendor" marks a company's own, unchecked claim. UNKNOWN means nothing public was found.

## Summary

1. **AskMed shows accuracy; it does not measure it.** AskMed says each answer searches 36 sources, which it does not list. In its screenshots every sentence or bullet ends in numbered citations, and a source sheet shows the passage used and "How AskMed used it" [S2][S10][S12]. It publishes no method, benchmark or accuracy figure, and its own policy says results "may be incomplete or inaccurate" [S18].
2. **Its confidence is the visible sources, not a score.** A banner, an "N sources" chip and typed source cards. None of its screenshots shows a confidence level, an evidence grade or a "not enough evidence" answer, though Research Mode promises "uncertainty" [S1][S9][S10][S14].
3. **Its trust claims have no public backing.** "100% physician-reviewed" and "Trusted by 250,000+ medical professionals & students" [S8] sit beside 16 ratings across 47 storefronts [S1].
4. **Stethoscore checks harder than anything AskMed shows, but shows far less.** On a 90-question bench with a wrong key planted in each question, no wrong key was marked Verified, all 27 Flagged verdicts were right, and no correct question was wrongly Flagged. The word-for-word proof made no false proof in 906,828 machine-mutated statements, a test of that one gate and not the accuracy of Verified (§6). The layer is also very cautious: 85% of checks ended as Check this, and no right key reached Verified. Meanwhile the app leaves out much of what the server sends: proof quotes, source details and the reasons list.
5. **To reach AskMed:** show what the server already returns. Source cards with type, date and a quote where the licence allows; numbered claims; "Checked on <date> against N sources". None of this lowers the bar for Verified.
6. **To beat it:** check that each citation supports its sentence; say "Not in our sources" and "Sources disagree" when that is the truth; show confidence as a measured band; publish real numbers; give exam items a visible origin and a flaw check; use licensed sources only; track no one.

## 1. What AskMed is

| | Fact | Source |
|---|---|---|
| Name | "Medical Assistant: AskMed AI" (com.loopstudio.askmed) | [S1][S2] |
| Seller | An individual developer, trading as Loop Studio ("© 2026 Loop Studio"), per the App Store and the privacy policy. Other apps: GrailScan, Roomy AI, SwipeAway, Mold Identifier, Mimori | [S2][S18][S20] |
| Category | Education, with Medical second; 16+ (17+ in the lookup API); 7 languages | [S1][S2] |
| History | Released 18 Sep 2026. 1.1 (24 Sep): 36 sources, a source count, filters, no doses, thumbs. 1.2 (29 Sep): languages, source-type labels. 1.5 (8 Oct): Max with Exam Prep and Deep Dive (20+ cited sources, PDF export), iPad, Arabic | [S2] |
| Ratings, 15 Oct | 16 across 47 storefronts: US 5.0 (3), GB 5.0 (2), EG 5.0 (1); only FR (4★) and TR (1★) below 5. Both written reviews are 1★ and about price | [S1][S3][S4] |
| Charts, 14 Oct (Top Free Education) | PK #17, NG #33, YE #33, LY #35, KE #38, PH #46, then LB, TN, ZA, EG, JO, down to IQ #93. Outside the Education and Medical top 100 in US, GB, IN, TR, SA, AE and 20+ others | [S7] |
| Prices | $14.99 a week; $24.99 and $29.99 a month; $129.99 a year; Max $999.99, billing period not stated (₹99,900 in India). In Egypt: EGP 799.99 a week, EGP 1,299.99 and 1,499.99 a month, EGP 6,999.99 a year, Max EGP 49,999.99 | [S2][S4][S5][S6] |
| UNKNOWN | The AI models or providers; the list of 36 sources; where drug-label text comes from; how Exam Prep items are made; real user numbers; any accuracy test. No press, Reddit, X or YouTube coverage found; no Android app | [S2][S17][S18][S21] |

Inference: AskMed is four weeks old, from a studio that ships AI utility apps fast. Its traction is in Pakistan, Africa and the Arab region, Egypt included, and it added Arabic on 8 Oct. It competes for Stethoscore's students.

## 2. How AskMed produces accuracy

**What it says:**
- The developer, replying to a review: "every answer searches current guidelines, journals and drug labels in real time… that research costs us real money on every question" [S3].
- Release notes: "36 authoritative medical sources, including clinical guidelines, PubMed, major journals and the MSD Manual Professional"; answers stream while "AskMed searches and reads sources"; after a dropped connection the app fetches "the finished answer from the server" [S2].
- Its cards cite msdmanuals.com, StatPearls, pmc.ncbi.nlm.nih.gov, ATS/IDSA and NHLBI [S10][S14].
- Guards: medicines are discussed "without specific doses"; over-long questions are refused; thumbs with "tell us what went wrong"; it "does not provide medical advice, diagnosis, or treatment" [S2]. The terms give "no guarantees regarding the accuracy" [S19].

**What the screenshots suggest (inference):**
- **Search over allowed websites, not PubMed's API.** Card titles are the pages' own HTML titles, word for word [S14][S24].
- **Answers grounded in passages.** "Relevant information" shows four passages joined by "…", each found word for word in the PMC copy of the ATS/IDSA guideline [S16][S22].
- **Type labels follow the website, not the document.** The same ATS/IDSA guideline is labelled "Guideline" in one screenshot and "Research" when served from PMC [S12][S15].
- **A server job per question** that searches, reads, writes and stores. Paying for search on every question explains the hard paywall [S3][S4].

**What nothing public shows:** a check that each cited passage supports its sentence; any measured accuracy; an "I don't know" answer; physician review. A live answer cannot be reviewed by a physician before the student reads it, so "100% physician-reviewed" [S8] cannot mean each answer (inference).

## 3. How AskMed shows confidence, against Stethoscore today

| # | AskMed | Stethoscore today | Verdict |
|---|---|---|---|
| 1 | A banner on home and on every answer: "For medical education. Verify sources; consult a clinician." [S9][S10] | The footer of the Why sheet's first section, Result: "…Still use your judgement" (RP ios/RedPen/Features/Support/AccuracyBadge.swift:152-156) | **Adapt: one reminder, not a banner on every screen.** Apple's guideline 1.4.1: "Apps should remind users to check with a doctor in addition to using the app and before making medical decisions" [S27]. Don't rely on it for trust: in one study, specific first-person hedges inside answers cut overreliance, and impersonal ones did less [S74]. Put the hedge on the item (§7 C3) |
| 2 | Chips over the answer: "AskMed Evidence", "N sources", "Detailed" [S10] | A badge: Verified, Checked, Check this, Flagged, Not checked yet (RP ios/RedPen/Shared/Accuracy/AccuracyModel.swift:7-38) | **Adopt** a line under the badge: "Checked on <date> against N sources" (§7 B2) |
| 3 | Numbered source cards with a coloured type pill (Guideline, Reference, Research), title and publisher [S10][S14] | "Sources the checkers were shown": numbered, linked titles only (AccuracyBadge.swift:212-226). The app keeps only id, source, title and url (AccuracyModel.swift:87-92) | **Adopt**, with the type taken from the document's own metadata, never from the website (§7 A2, B1) |
| 4 | Numbered citations after every sentence or bullet, often two; red-flag bullets in bold [S10][S15] | No numbers in explanations. The server already numbers its sources, keeps each checker's cites, and sends a chain per claim with its passage (RP server/accuracy.js:144-167, 231-254, 370-373) | **Adopt**, but only where a support check passes (§7 A4, B3) |
| 5 | A source sheet: type, title, publisher and year, "Open full source", "Relevant information" (a summary or verbatim excerpts) and "How AskMed used it" [S12][S16] | Proof quotes (claim, quote, source, title, url) are decoded and never shown (RP ios/RedPen/Shared/Accuracy/AccuracyLedger.swift:53-81). Source details (type, grade, publisher, date, edition, retraction) and claim chains (passage, section) are sent and never decoded (accuracy.js:370-373) | **Adopt**, quoting only what the licence allows (§7 A3, B1) |
| 6 | "Was this answer helpful?" thumbs; suggested follow-ups; numbered references filtered by source and study type [S11][S15] | Report an error, in the Why sheet | **Keep ours**, and sort each report into a typed error (§16a, layer 8) |
| 7 | A scope line over the composer: "8 sources · guidelines, research, labels" [S9][S13] | The footer of the Why sheet's Result section: "Checked against this <kind>'s lecture and current literature by free checker models voting…" (AccuracyBadge.swift:152-156) | **Adapt**: state the scope in the header line of row 2 |
| 8 | Research Mode promises "agreement, uncertainty, and conflicting evidence" [S1]; no screenshot shows it | The server finds contradicting literature, blind dissent, and sources read against an item: a hold (shadh) when stronger than its support, a note (munkar) when weaker (RP server/accuracy-model.js:215-220; server/provenance.js:404-405); CM's verifier has MIXED_EVIDENCE. None of it reaches the screen | **Beat**: "Sources disagree", with both sides (§7 C2) |
| 9 | None shown: no screenshot has a confidence level, an evidence grade or a "not enough evidence" answer [S8]–[S16] | "(N% likely accurate)", where N is the accuracy model's P × 100 (AccuracyBadge.swift:160-171); Check this | **Beat**: a measured band, and "Not in our sources" (§7 C1, C3) |

## 4. Where AskMed is weak

- **Claims with no public backing.** "100% physician-reviewed" and "250,000+" [S8], against 16 ratings [S1]. Apple's guideline 2.3.1 makes misleading marketing grounds for removal [S27]. Stethoscore's own rule already bans unverifiable claims (CM plan §23c).
- **Privacy.** The App Privacy label says Purchase History, Device ID and Product Interaction may be used to track users across other companies' apps and websites, and are linked to them for the developer's advertising or marketing. What users type ("Other User Content") and their User ID are linked to them for analytics and app functionality; health data is "Not Linked", for app functionality only [S2]. The policy says submitted content may be stored "to provide the feature, keep your history, and improve quality and safety", and "We do not sell it or use it for advertising" [S18]. Private Mode keeps new questions out of History [S1]; whether the server still stores them: UNKNOWN. Inference: what students type is not used for advertising, but their purchases, device and in-app behaviour may be used to track them. Stethoscore can promise no tracking at all (§7 C8).
- **Licences.** It shows verbatim paragraphs of the ATS/IDSA guideline, text © ATS, taken from PMC [S16][S22]. PMC says to "assume that standard copyright protection applies" and prohibits systematic downloading [S23]. MSD Manuals content needs permission to reuse [S25]. StatPearls is CC BY-NC-ND [S26]. Inference: an exposure for a paid app, not a legal finding.
- **The seller.** Apple's guideline 5.1.1(ix): apps that provide services in highly regulated fields such as healthcare "should be submitted by a legal entity… not by an individual developer" [S27]. AskMed's seller is an individual. The rule may touch Stethoscore too (§9, question 4).
- **Exam Prep.** In Max only, at $999.99: questions "with an explanation for every answer option" for USMLE Step 1 and Step 2 CK, PLAB, NEET-PG, TUS and ENARM, with timed mocks and spaced review, shipped 20 days after launch [S2]. How the items are written and checked: UNKNOWN. For scale, unrevised ChatGPT USMLE-style items had writing flaws in 49% and factual errors in 22% [S81]. If Max is yearly, it costs 7.7 times Pro's $129.99 year; AMBOSS lists US$149 a year plus US$299 for a 12-month Qbank [S29].
- **Price.** Both written reviews complain about it: "way too much expensive not worth it" and "Published as free but it's premium" [S3][S4].
- **Regulation.** FDA's revised guidance on clinical decision support (January 2026) keeps the test that a professional can independently review the basis of a recommendation, and is silent on generative AI [S28]. Inference: "educational only" plus visible sources is AskMed's shield; selling to students at 16+ weakens it.

## 5. What the field knows

### 5.1 Methods, ranked for Stethoscore

| # | Method | Measured effect | Fits the owner's rules? |
|---|---|---|---|
| 1 | Retrieval from a closed, trusted corpus | Accuracy up to 18% higher than with chain-of-thought prompting across six models, points or relative not stated [S30]; pooled odds ratio 1.35 (1.19–1.53) over 20 studies [S31] | Yes, if the corpus is the licensed sources of §5.5 |
| 2 | Checking that each cited passage supports its sentence | 50–90% of medical answers are not fully supported by their own citations [S32]; even the best models leave about half unsupported on a general set [S33]; a 770M checker matched GPT-4 at 400 times lower cost [S34] | Yes, with MIT-licensed checkers on CPU [S35][S36]; not Bespoke-MiniCheck-7B, which is CC BY-NC [S37] |
| 3 | Splitting answers into claims and checking each | ChatGPT: 58% of atomic facts supported [S38]; automatic checking agreed with people 72% of the time and was right in 76% of the disagreements [S39]; condition-keeping medical splits: up to 3 times more valid claims [S40]; 92.7% agreement with clinicians, against 88.5% between clinicians [S41] | Yes, against our own corpus; SAFE searches through a keyed web API, which is ruled out [S39] |
| 4 | Anchoring to a guideline | Hepatitis C questions from 43% to 99% correct [S42]; calibrated wording from 74.6% to 94.9% once prompts carried recommendation strength and evidence quality [S43] | Yes, where the guideline's licence allows |
| 5 | Abstaining | Reasoning training cut abstention by 24% [S44]; 12 models answered confidently when the right option was missing [S45] | Yes, free |
| 6 | Agreement across samples and model families | Agreement between sampled answers was the best sign of a wrong answer on diagnosis and treatment: it told right from wrong with an AUROC of 0.68–0.79 (0.5 is chance, 1.0 perfect), while the models' stated confidence ran high [S46]. How much the meaning of several sampled answers varies ("semantic entropy") scored 0.79 against about 0.69 for simpler signals [S47]; model-checks-model cut harm only with a strong final reviewer, and mixed vendors beat self-review [S48] | Yes, with free models; no keyed or paid APIs |
| 7 | Conformal risk control: a threshold set on labelled items so that the error rate of what is shown stays under a chosen level | 80–90% correctness guarantees while keeping most content [S49]; conditional guarantees on medical long-form answers [S50]; set error rates met on MedQA and MedMCQA [S51] | Yes; needs an expert-labelled calibration set |
| 8 | Evidence grades shown | No trial of their effect on users found. GRADE [S52] and OCEBM [S53] are the standards; DynaMed shows levels 1–3 and GRADE strength [S67]; OpenEvidence's A–D grade is unvalidated [S58] | Yes, from metadata |
| 9 | Stated confidence | About 50% lower calibration error than token probabilities on general questions [S54]; on 1,965 clinical questions, confidence on right and wrong answers differed by only 0.6–5.4 points [S55] | Only as wording derived from rows 2, 6 and 7 |

### 5.2 What leading products do

- **OpenEvidence.** Its "100% on the USMLE" (vendor) used the official sample questions without the image items and overruled one Step 3 key [S56]. It licenses NEJM, 13 JAMA journals and NCCN guidelines [S70][S71][S72], and says why each source was cited: "Highly Relevant", "Top Journal", "New Research" [S57]. An independent check of 4,979 citations found none invented and 3 misattributed; whether they support the claims was not tested [S60]. NYU's study in Nature Medicine: 89.6% on MedQA against Gemini 3.1 Pro's 97.4%, and 62.6 on HealthBench against GPT-5.2's 88 [S61][S62]. A physician study it co-designed and paid for reports wins of 25–39 points (vendor-funded) [S63]. iatroX, a rival product, argues on its blog that one grade per answer can hide claims of mixed strength [S59].
- **UpToDate Expert AI** shows its assumptions, sources and reasoning, and cites a validation framework of 7,600 experts but no accuracy figure [S64]; 88.4% on MedQA in the NYU study [S62].
- **ClinicalKey AI.** Elsevier's own evaluation: 426 answers rated by 41 physicians and pharmacists, 95.5% correct and 0.47% potentially harmful, though the first two raters agreed only 60.6% of the time [S65].
- **Dyna AI** answers only from DynaMed, labels AI output, shows sources and keeps clinicians in charge; no accuracy figure found [S66].
- **AMBOSS.** AI Mode answers only from its library, curated guidelines and a drug database [S68]. Its learning mode tells students they need not double-check sources [S69]: the overtrust to avoid.
- **NOHARM** (preprint): severe-harm potential in up to 24.6% of cases; omissions were over 80% of severe errors; clinical tools beat general models [S48].

Pattern: the established products answer from content they own or license. None publishes calibrated confidence.

### 5.3 Trust that matches accuracy

- Citations raised trust even when they were random; users who checked them trusted less [S73].
- First-person hedges ("I'm not sure, but…") cut overreliance and raised accuracy on medical questions (n = 404); impersonal hedges did less [S74].
- Longer explanations raised users' confidence, not their accuracy; wording matched to the model's confidence narrowed the gap [S75].
- People over-relied on convincing wrong explanations; explanations that argued both sides helped [S76].
- Automation bias is large even in experts. Wrong AI categories cut mammography reading accuracy from 79.7% to 19.8% in inexperienced readers and from 82.3% to 45.5% in very experienced ones [S77]. A biased model cost clinicians 11.3 points, and explanations did not fix it [S78]. Wrong advice cost physicians 14.0 points despite 20 hours of AI training [S79].

Inference: AskMed's pattern, sources on every sentence and no doubt shown, is likely to raise trust whether or not it is earned [S73]. Stethoscore should raise trust only as far as its evidence goes.

### 5.4 Exam items

- NBME's free guide sorts item flaws into testwiseness and irrelevant difficulty [S80].
- ChatGPT USMLE-style items: 49% had writing flaws and 22% factual errors; 91% were a reasonable start for revision [S81].
- A retrieval pipeline with schema checks: 87% of 565 items accurate and NBME-compliant after faculty review [S82].
- GPT-4o against human-written items: more factual errors (6% vs 4%) and more at the wrong difficulty (14% vs 1%); easier, with similar discrimination [S83]. Another study found no psychometric difference, and readers could not tell the two apart [S84].
- Checking items with AI: on non-medical items, a rule-based detector found 91% of human-found flaws against GPT-4's 79% [S85]; high overall accuracy can hide weak detection of flawed items [S86].
- Benchmarks carry errors too: 7.4% of MedQA items were unfit [S87] and 6.49% of MMLU items wrong [S88]. No peer-reviewed audit of a commercial Qbank was found.

### 5.5 Licences for commercial use

| Source | Commercial use | Note |
|---|---|---|
| openFDA, drug labels included | Yes: CC0 | GMDN device content excluded [S89][S90] |
| DailyMed | Unclear: no licence stated | Labels may not be FDA-verified; use openFDA's copy [S91] |
| PubMed abstracts | Not cleared | Metadata and links only [S92] |
| PMC open access | The CC0 and CC BY subset only | Check each article [S93] |
| USPSTF | Only with AHRQ's written permission | "may not be… incorporated into a profit-making venture without the express written permission of AHRQ" [S94] |
| CDC | Yes, mostly public domain | No implied endorsement; third-party images excluded [S95] |
| NIH and NLM | Yes: US government works | Third-party items excluded [S96] |
| MedlinePlus | Yes, for NLM-written summaries | A.D.A.M. and ASHP content excluded [S97] |
| WHO | No: CC BY-NC-SA 3.0 IGO | Needs WHO's permission [S98] |
| NICE | No: the open licence is UK-only | AI use needs a signed licence [S99] |
| MSD Manuals | No | Reuse needs permission [S25] |
| StatPearls | No | CC BY-NC-ND [S26]; excluded by the owner's rule |

## 6. Stethoscore today

**What it has** (RP unless marked):
- **A staged pipeline:** rules, claims, lookup, evidence, proof, votes, Jev, verdict and cache, each with a time budget and a safe fallback, plus kill switches and per-lane breakers (server/accuracy.js:21-25, 468-485; server/switches.js; server/breakers.js).
- **Evidence** from Europe PMC (reviews and guidelines, retractions excluded), MedlinePlus and openFDA. Sources are numbered for the checkers (server/evidence.js:1-23, 162; accuracy.js:144-167). A checker's cites are kept as given, any "S1" to "S99" (accuracy.js:248); they are matched to fetched records only when claim chains are built (provenance.js:333), and the app shows the unmatched list as "Cites" (AccuracyBadge.swift:203-204).
- **Checkers from different model families.** One family is one witness, and Verified needs three. Three families first solve each question blind, without the key (server/accuracy-model.js:114-122, 140; accuracy.js:612-722).
- **A strict Verified:** P ≥ 0.85, no rule hit, three voters from three families, no flag and no contradicting literature, blind agreement on the key, and backing: a word-for-word proof from a current FDA label or MedlinePlus page, or a vignette proof the owner has signed (accuracy-model.js:191-226).
- **Safety parts:** a fail-closed oath check on dose, diagnosis and treatment items (server/oath.js); provenance rules from the §17 brief, with isnad chains, source grades, a hold when a stronger source is read against an item (shadh) and a note when a weaker one is (munkar) (server/provenance.js:404-405); flagged items held out of review and mock papers (ios/RedPen/Shared/Accuracy/VerificationScreen.swift:3-17).

**What it has measured** (Bench runs/38554454303/report.md unless marked):
- 90 real questions (MedXpertQA, MedQA and CareQA, 30 each), each checked with its true key and with a planted wrong key: 180 checks.
- No wrong key was marked Verified (90/90). All 27 Flagged verdicts were right. No correct question was wrongly Flagged (0/90).
- No right key reached Verified (0/90), and 85% of checks ended as Check this.
- Each checker's own answers were right: Claude 84.0% (163 answers), Nemotron 82.8% (58), gpt-oss 74.4% (43), Flash-Lite 69.2% (156).
- The word-for-word proof made no false proof in 906,828 mutated statements (docs/verification/proof-mutation-bench.md:33-53). The changes are machine-made and test one gate; the plan says the figure "must not be quoted as the accuracy of Verified" (docs/verification/proof-plan.md:26).
- The proof ladder has checked 660 items: 0 Verified, 214 Flagged, 446 Check this, for $5.42 of the $90 monthly cap (Bench proof-ladder/progress.md:7-55).

**Where it falls short:**
- **Verified is out of reach for vignettes.** 88 of the 90 right keys failed the stem test, because the word-for-word proof reads only one-sentence stems of 30 words or fewer; the other 2 had no source (docs/verification/why-not-verified.md:9-25). Only about 2.5% of MedMCQA is readable by the proof (docs/verification/proof-plan.md:61). The vignette route is built but waits on: a second, independent transcription; the owner's one-tap sign-off and its D1 endpoint; a real proposer; denial rules; more sources (plan.md:1842-1851).
- **Safety recall is short of its bar.** The oath check's held-out recall is 87.1% against 99%; triage agreement (kappa) is at most 0.197 against 0.8 (proof-plan.md:17-24, 77-92).
- **The app hides what the server knows.** Proof quotes are decoded and never shown; source details and claim chains are never decoded; the sheet shows rule hits, each checker's issues and the sources, but the reasons list, with the claim-gate holds and why no source proves the item, is used only in tests, though Check this tells the student to "read the reasons below" (AccuracyLedger.swift:53-81, 87-114, 128-141; AccuracyBadge.swift:164).
- **Nothing is published.** The numbers above live in docs and a bench branch; the student sees none of them. CM's verifier, with SUPPORTED, CONTRADICTED, MIXED_EVIDENCE and INSUFFICIENT_EVIDENCE verdicts, runs offline only (CM orchestration/graph.py:380-438), and the Task 4 audit keeps it a GitHub Actions batch tool beside the Worker's engine (CM docs/architecture/audit/task4-chat-me-audit.md).

On checking, Stethoscore is ahead of anything AskMed publishes. On showing, AskMed is ahead. The fastest gain is on the screen, not in the checker.

## 7. Plan: reach, then beat

The order follows red-pen-ios's rule: what can be checked on Linux first; what changes how the app looks last, in one batched Mac run. Worker changes still deploy only on the owner's word.

### Phase A: reach, with no visual change

| # | What | Where | Verdict and basis |
|---|---|---|---|
| A1 | Decode the source details, the claim chains and the reasons list in the app | RP ios/RedPen/Shared/Accuracy/AccuracyLedger.swift (Foundation-only; tested on Linux) | **Adopt.** §17 component A: one chain per claim, shown as "Why trust this?" |
| A2 | Complete every source record. The server already sends type, grade, publisher, date, edition and retraction (accuracy.js:370-373); add the licence, and take the type from the document's own metadata (guideline, systematic review, review, drug label, patient information, textbook) | RP server/evidence.js, server/provenance.js | **Adopt.** §17 component B; CM plan.md:590-594 (evidence strength, conflict flag). Avoids AskMed's website-based labels |
| A3 | Quote a passage only when its licence allows: openFDA labels, CDC, NIH and NLM, NLM-written MedlinePlus, CC0 or CC BY PMC articles. Otherwise show title, publisher, date and link | RP server; the licence allowlists (server/vignette-proof.js:112; governance/licences/allowlist.json) | **Adopt.** §22d §4 step 1: the licence allowlist enforced in code; §5.5 |
| A4 | Check each claim against its cited passage with an MIT-licensed checker [S35][S36] on GitHub's CPU runners, for generated items. Measure it on a labelled set before it gates anything. A claim shows a citation number only when the check passes | A GitHub Actions batch job beside the keyless runner voters (RP server/runner-voters.js; tools/jev-standin) | **Adopt, measure first.** §22d §4 step 2 (entailment of the key claim against its cited passage); §16a rule 1 (layers that differ in mechanism multiply); the Task 4 audit (batch tools run in Actions); [S32][S34] |
| A5 | Calibrate before showing numbers: the error rate of each tier on the bench, and a threshold set on labelled items so that the error rate of what is shown stays under a chosen level (conformal risk control, §5.1 row 7), below which the app says "Not in our sources" | Bench; server/accuracy-model.js:398-451 (calibration metrics) | **Adopt; blocked on expert labels** (§9, question 5). §16a MEDICAL IMPROVEMENTS item 7; [S49][S50][S51] |

### Phase B: reach, on screen (one Mac run)

The tier names and their meanings stay as they are; this phase adds evidence, not tiers.

| # | What | Verdict and basis |
|---|---|---|
| B0 | Keep the promise: Check this says "read the reasons below", but the commonest reasons, the claim-gate holds and why no source proves the item, are never shown. Show the full reasons list | **Adopt, first in the Phase B run.** AccuracyBadge.swift:164; AccuracyLedger.swift:128-141 |
| B1 | Source cards in the Why sheet: number, type, title, publisher, date, the licensed quote, and "Supports: <the claim>" | **Adopt.** AskMed rows 3 and 5 (§3); §17 component A |
| B2 | "Checked on <date> against N sources" under the badge | **Adopt.** §22b MEDICAL IMPROVEMENTS item 3 |
| B3 | Numbers on the claims of an explanation, only where A4 passed, each opening its source card | **Adopt.** §22b MEDICAL IMPROVEMENTS item 9 |

### Phase C: beat

| # | What | Verdict and basis |
|---|---|---|
| C1 | "Not in our sources": when the evidence does not settle an item, say so in words, and score that as correct on the bench | **Adopt.** §17 row 20 and component M ("la adri"); §22b item 7; §22f (any error maps to Unverifiable); [S44][S45] |
| C2 | "Sources disagree": show both sides, resolved in order by scope, then dated supersession, then evidence grade, else "Unresolved"; exam mode leaves Unresolved items out | **Adopt.** §17 component I and rows 14–15; CM plan.md:594; two-sided explanations reduce overreliance [S76] |
| C3 | Confidence as a band with its reason, for example "Strong: two current sources state it word for word", and a first-person hedge when weak, in place of "(N% likely accurate)" until A5 shows that P is calibrated | **Adapt; the owner decides,** because CM plan.md:590 asks for "Confidence 0–100%" in §10, which is marked BINDING ("All required. Do not ship without them", :584-587). [S46][S54][S55][S74][S75] |
| C4 | A public "How we check" page: what each tier means, the bench numbers with dates, what is not yet proven, and a corrections log | **Adopt.** CM plan.md:683-684 (transparency report); CM plan §23c (honest limitations); §16a item 7 |
| C5 | Exam items with an origin and a flaw check: "Made from your lecture, slide N"; an NBME-style flaw linter; every distractor explained; flagged items kept out of review and mocks (built) | **Adopt.** §22d §4 steps 3 and 6; §22b item 2; §16a item 6; [S80]–[S86] |
| C6 | Doses: show a dose only when a current label or licensed guideline states it word for word, with the quote and date; otherwise "Dose not shown: no current source states it word for word". Doses are quoted, never computed: Apple limits dosage calculators to approved entities (guideline 1.4.2) [S27] | **Adapt AskMed's blanket "no doses"; the owner decides.** §17 component J; §16a item 2 |
| C7 | Must-include checklists for high-yield topics, from licensed guidelines, to catch omissions | **Later,** after A to C. Omissions were over 80% of severe errors [S48]; guideline anchoring [S42] |
| C8 | An App Privacy label with no tracking, confirmed at submission | **Adopt.** Goes beyond CM plan §23d, which asks only for a privacy policy URL; AskMed's label shows tracking [S2] |
| C9 | Price | **The owner's call.** §20's US student prices ($19.99 a month, $149 a year) sit near AskMed's ($24.99–29.99 a month, $129.99 a year). In Egypt, §20's prices (EGP 299.99 a month, EGP 1,999.99 a year) are 23% of AskMed's cheaper monthly price (EGP 1,299.99) and 29% of its yearly one (EGP 6,999.99) [S4] |

## 8. What not to copy

- Claims with no public backing, such as "100% physician-reviewed" and "250,000+" [S8]; CM plan §23c bans them.
- Source types by website [S12][S15].
- Verbatim text from sources whose licence forbids it: MSD Manuals, StatPearls, copyrighted guideline text [S23][S25][S26].
- One grade per answer [S59]; grade each claim.
- "No need to double-check" [S69].
- Raw percentages as confidence [S55].
- Tracking, and linking purchases, device ID and in-app behaviour to users for marketing [S2].
- A generic disclaimer as the main trust device [S74].

## 9. Decisions for the owner

1. **Order.** Phase A now (no visual change, checked on Linux, Worker deploys on your word); Phase B in one batched Mac run after the current design work; Phase C after that.
2. **Confidence wording (C3).** Replace "(N% likely accurate)" with a band and its reason until the number is shown to be calibrated? The rulebook asks for "Confidence 0–100%" (CM plan.md:590) in §10, which is marked BINDING: "All required. Do not ship without them" (:584-587). Changing it needs your word. This also belongs in the open wording glossary.
3. **Doses (C6).** Quoted doses only, or none at all, as AskMed does?
4. **Apple 5.1.1(ix).** Apps that provide healthcare services "should be submitted by a legal entity… not by an individual developer" [S27]. Whether a study app counts is Apple's call; it bears on the open App Store account decision.
5. **Labels for calibration (A5).** A set of items for your one-tap sign-off, once that endpoint exists.

## 10. Corrections found on the way

- **USPSTF.** RP docs/verification/vignette-proof-research.md:167 calls its guidelines public domain, and RP plan.md:1849 lists it as a source to add. Its copyright notice forbids use in "a profit-making venture without the express written permission of AHRQ" [S94], as CM plan.md:555 already says. Treat it as excluded until AHRQ gives permission.
- **MedQA's licence** is stated three ways across RP and CM: CC BY 4.0, MIT and unknown. §22d's verdict stands: not usable as content.

## Sources

AskMed, read 15 Oct 2026:
- [S1] iTunes lookup API, US and 46 other storefronts: https://itunes.apple.com/lookup?id=6776077826&country=us
- [S2] App Store, US: description, version history, in-app purchases, App Privacy: https://apps.apple.com/us/app/medical-assistant-askmed-ai/id6776077826
- [S3] App Store, Turkey: a 1★ review and the developer's reply: https://apps.apple.com/tr/app/medical-assistant-askmed-ai/id6776077826
- [S4] App Store, Egypt: a 1★ review and the prices: https://apps.apple.com/eg/app/medical-assistant-askmed-ai/id6776077826
- [S5] App Store, India: Max at ₹99,900: https://apps.apple.com/in/app/medical-assistant-askmed-ai/id6776077826
- [S6] App Store, France: Max as "abonnement": https://apps.apple.com/fr/app/id6776077826
- [S7] Top Free Education charts, one feed per storefront (genre 6017; Medical is 6020), e.g. https://itunes.apple.com/pk/rss/topfreeapplications/limit=200/genre=6017/json
- [S8] iPhone screenshot 1: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/68/83/53/6883531e-744f-0f03-b8ee-5b4b0535dfaa/First_Image.png/1242x2688bb.png
- [S9] iPhone screenshot 2, home: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/a0/e6/92/a0e69286-c06e-bf07-f3c8-2c775cdc3e32/02-home.png/1242x2688bb.png
- [S10] iPhone screenshot 3, an answer: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/e8/b8/cf/e8b8cf1d-8909-5281-57aa-74bf8d90bcac/03-detailed-answers.png/1242x2688bb.png
- [S11] iPhone screenshot 4, references: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/5a/71/2c/5a712c50-b476-5fea-a50f-9854c148e042/04-medical-concepts.png/1242x2688bb.png
- [S12] iPhone screenshot 5, a source sheet: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/30/03/a6/3003a6f6-a732-8820-b3ac-7729c64baf27/05-citations.png/1242x2688bb.png
- [S13] iPad screenshot 2, the composer: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/46/ec/47/46ec4749-7059-962e-f846-a99b6b0d2a73/02.png/2064x2752bb.png
- [S14] iPad screenshot 3, source cards: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/00/ce/59/00ce596c-ce81-fb8c-295a-fb562fcf4cfb/03.png/2064x2752bb.png
- [S15] iPad screenshot 4, an answer: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/6c/ae/7e/6cae7ebc-427c-ccb6-3859-ead1a21a5bb0/04.png/2064x2752bb.png
- [S16] iPad screenshot 5, verbatim excerpts: https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/47/99/f8/4799f817-8322-e6ee-ed37-e19f5806d61f/05.png/2064x2752bb.png
- [S17] Loop Studio website: https://www.loopstudio.tech/
- [S18] Privacy policy, 6 Oct 2026: https://www.loopstudio.tech/#privacy
- [S19] Terms: https://www.loopstudio.tech/#terms
- [S20] The seller's apps: https://itunes.apple.com/lookup?id=1822143619&entity=software&country=us
- [S21] Google Play (404): https://play.google.com/store/apps/details?id=com.loopstudio.askmed

Sources AskMed cites, and platform rules:
- [S22] ATS/IDSA guideline on community-acquired pneumonia, PMC copy: https://pmc.ncbi.nlm.nih.gov/articles/PMC6812437/
- [S23] PMC copyright notice: https://pmc.ncbi.nlm.nih.gov/about/copyright/
- [S24] MSD Manual Professional, Overview of Pneumonia: https://www.msdmanuals.com/professional/pulmonary-disorders/pneumonia/overview-of-pneumonia
- [S25] MSD Manuals permissions: https://www.msdmanuals.com/professional/content/permissions
- [S26] StatPearls (licence read from a search-index snippet; the page showed a CAPTCHA): https://www.ncbi.nlm.nih.gov/books/NBK430685/
- [S27] Apple App Review Guidelines, updated 8 Jun 2026: https://developer.apple.com/app-store/review/guidelines/
- [S28] Covington, five takeaways from FDA's revised CDS guidance (Jan 2026): https://www.cov.com/en/news-and-insights/insights/2026/01/5-key-takeaways-from-fdas-revised-clinical-decision-support-cds-software-guidance
- [S29] AMBOSS list prices, on an offer page that has expired: https://www.amboss.com/int/group-discount/gd-ou-sa-riyadh-pnu-0326

Methods:
- [S30] MedRAG: https://arxiv.org/abs/2402.13178
- [S31] Retrieval in medical LLMs, meta-analysis, JAMIA 2025: https://doi.org/10.1093/jamia/ocaf008
- [S32] SourceCheckup, Nature Communications 2025: https://doi.org/10.1038/s41467-025-58551-6
- [S33] ALCE: https://arxiv.org/abs/2305.14627
- [S34] MiniCheck: https://arxiv.org/abs/2404.10774
- [S35] MiniCheck-Flan-T5-Large (MIT): https://huggingface.co/lytang/MiniCheck-Flan-T5-Large
- [S36] DeBERTa-v3-large NLI (MIT): https://huggingface.co/MoritzLaurer/DeBERTa-v3-large-mnli-fever-anli-ling-wanli
- [S37] Bespoke-MiniCheck-7B (CC BY-NC): https://huggingface.co/bespokelabs/Bespoke-MiniCheck-7B
- [S38] FActScore: https://arxiv.org/abs/2305.14251
- [S39] SAFE: https://arxiv.org/abs/2403.18802
- [S40] MedScore: https://arxiv.org/abs/2505.18452
- [S41] VeriFact: https://arxiv.org/abs/2501.16672
- [S42] Kresevic 2024, guideline-anchored hepatitis C answers: https://pmc.ncbi.nlm.nih.gov/articles/PMC11039454/
- [S43] ESGE 2026, calibrated wording: https://iris.unikore.it/handle/11387/209713
- [S44] AbstentionBench: https://arxiv.org/abs/2506.09038
- [S45] MetaMedQA, Nature Communications 2024: https://doi.org/10.1038/s41467-024-55628-6
- [S46] Savage 2025, JAMIA: https://doi.org/10.1093/jamia/ocae254
- [S47] Farquhar 2024, semantic entropy, Nature: https://pmc.ncbi.nlm.nih.gov/articles/PMC11186750/
- [S48] NOHARM (preprint): https://arxiv.org/abs/2512.01241
- [S49] Mohri and Hashimoto 2024, conformal factuality: https://proceedings.mlr.press/v235/mohri24a.html
- [S50] Cherian 2024: https://arxiv.org/abs/2406.09714
- [S51] Ke 2025: https://arxiv.org/abs/2503.05505
- [S52] GRADE handbook: https://gdt.gradepro.org/app/handbook/handbook.html
- [S53] OCEBM levels of evidence: https://www.cebm.ox.ac.uk/resources/levels-of-evidence/ocebm-levels-of-evidence
- [S54] Tian 2023: https://arxiv.org/abs/2305.14975
- [S55] Omar 2025, JMIR Medical Informatics: https://medinform.jmir.org/2025/1/e66917

Products:
- [S56] OpenEvidence, USMLE announcement: https://www.openevidence.com/announcements/openevidence-creates-the-first-ai-in-history-to-score-a-perfect-100percent-on-the-united-states-medical-licensing-examination-usmle
- [S57] OpenEvidence, why a source was cited: https://openevidence.com/announcements/new-feature-why-was-this-source-cited
- [S58] TechTarget on OpenEvidence's evidence grades: https://www.techtarget.com/healthtechanalytics/news/366645822/OpenEvidence-adds-real-time-evidence-quality-grading-to-AI
- [S59] iatroX, a rival product, on its blog about one grade per answer: https://www.iatrox.com/blog/openevidence-evidencegrade-useful-signal-or-false-precision
- [S60] npj 2026, OpenEvidence's citations: https://doi.org/10.1038/s44401-026-00142-8
- [S61] Nature Medicine 2026, NYU study: https://www.nature.com/articles/s41591-026-04431-5
- [S62] Becker's on the NYU study: https://www.beckershospitalreview.com/healthcare-information-technology/ai/chatgpt-gemini-claude-beat-clinical-ai-tools-study
- [S63] Physician study co-designed and paid for by OpenEvidence: https://arxiv.org/abs/2606.28960
- [S64] UpToDate Expert AI, HIT Consultant: https://hitconsultant.net/2025/09/24/wolters-kluwer-health-launches-uptodate-expert-ai-to-accelerate-clinical-decisions/
- [S65] ClinicalKey AI evaluation, JAMIA Open: https://doi.org/10.1093/jamiaopen/ooaf054
- [S66] EBSCO, Dyna AI's editorial approach: https://about.ebsco.com/blogs/health-notes/ebscos-clinical-decisions-releases-editorial-approach-generative-artificial-intelligence
- [S67] EBSCO, DynaMed's editorial process: https://about.ebsco.com/clinical-decisions/dynamed-solutions/about/evidence-based-process/editorial-process
- [S68] AMBOSS AI Mode for clinicians: https://www.amboss.com/us/newsroom/introducing-ai-clinical-mode
- [S69] AMBOSS AI Mode Learning: https://www.amboss.com/us/newsroom/introducing-amboss-ai-mode-learning
- [S70] OpenEvidence and NEJM: https://openevidence.com/announcements/openevidence-and-nejm
- [S71] Fierce Healthcare, JAMA's deal with OpenEvidence: https://www.fiercehealthcare.com/ai-and-machine-learning/jama-signs-multi-year-deal-openevidence-inform-ai-powered-medical-search
- [S72] ASCO Post, NCCN guidelines in OpenEvidence: https://ascopost.com/news/november-2025/nccn-guidelines-to-be-integrated-into-openevidence-medical-ai-platform/

Interface:
- [S73] Ding 2025, AAAI: https://ojs.aaai.org/index.php/AAAI/article/view/34550
- [S74] Kim 2024: https://arxiv.org/abs/2405.00623
- [S75] Steyvers 2025: https://arxiv.org/abs/2401.13835
- [S76] Si 2024, NAACL: https://aclanthology.org/2024.naacl-long.81
- [S77] Dratsch 2023, Radiology: https://doi.org/10.1148/radiol.222176
- [S78] Jabbour 2023, JAMA: https://doi.org/10.1001/jama.2023.22295
- [S79] Qazi 2025 (preprint): https://www.medrxiv.org/content/10.1101/2025.08.23.25334280

Exam items:
- [S80] NBME item-writing guide: https://www.nbme.org/institutions/nbme-item-writing-guide/
- [S81] Advances in Physiology Education 2025: https://doi.org/10.1152/advan.00106.2024
- [S82] Academic Medicine 2026: https://doi.org/10.1093/acamed/wvaf074
- [S83] BMC Medical Education 2025: https://pmc.ncbi.nlm.nih.gov/articles/PMC11806894/
- [S84] npj Digital Medicine 2026: https://pmc.ncbi.nlm.nih.gov/articles/PMC12881591/
- [S85] Moore 2023: https://arxiv.org/abs/2307.08161
- [S86] Moore and Diana 2026: https://aclanthology.org/2026.aimecon-wip.58/
- [S87] Med-Gemini, MedQA relabelling: https://arxiv.org/abs/2404.18416
- [S88] Gema 2025, errors in MMLU: https://aclanthology.org/2025.naacl-long.262

Licences:
- [S89] openFDA terms: https://open.fda.gov/terms/
- [S90] openFDA drug labels (SPL): https://open.fda.gov/data/spl/
- [S91] About DailyMed: https://dailymed.nlm.nih.gov/dailymed/about-dailymed.cfm
- [S92] NLM data download terms: https://www.nlm.nih.gov/databases/download.html
- [S93] PMC text mining and open-access subsets: https://pmc.ncbi.nlm.nih.gov/tools/textmining/
- [S94] USPSTF copyright notice (read 15 Oct 2026): https://www.uspreventiveservicestaskforce.org/uspstf/recommendation-topics/copyright-notice
- [S95] CDC agency materials: https://www.cdc.gov/other/agencymaterials.html
- [S96] NLM web policies: https://www.nlm.nih.gov/web_policies.html
- [S97] MedlinePlus, using its content: https://medlineplus.gov/about/using/usingcontent/
- [S98] WHO copyright: https://www.who.int/about/policies/publishing/copyright
- [S99] NICE UK open content licence: https://www.nice.org.uk/reusing-our-content/nice-uk-open-content-licence
