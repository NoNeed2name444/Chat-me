# §23f Question banks brief: how the big banks are made, and how Stethoscore builds its own from free commercial sources

Date 21 Oct 2026. For the owner's question: how the big competitors (AMBOSS, UWorld and others) build their question banks and from which sources; how Stethoscore can build similar ones from sources free for commercial use; what else the app needs from them; and a plan for integration.

**How it was made.** Four research passes, October 2026, public sources only. Each pass fetched sources, extracted claims and put the top 25 to three adversarial verifiers; a claim stays only with a majority vote.
- **Competitor landscape:** 25 sources, 113 claims, 7 survived.
- **Competitor follow-up** (UK, MENA, UWorld and Kaplan, mid tier, trust benchmarks, note-to-quiz generators): 22 sources, 66 claims, 9 survived.
- **How the leading banks are made:** 22 sources, 64 claims, 12 survived.
- **Licence follow-up** (datasets, open textbooks, PMC, drugs and guidelines, Arabic): 22 sources, 72 claims, 17 confirmed, 6 findings after synthesis.

Only what survived is stated as fact. Each finding carries the confidence and vote the verifiers gave it. "Refuted" means the evidence did not establish a claim, not that the claim is false. "Inference" marks a reading, not a source's words. "Vendor" marks a company's own, unchecked claim. **UNVERIFIED** means the passes found nothing that survived; §7 lists all of it. [Sn] points to Sources. Nothing here is legal advice.

## Summary

1. **The big banks do not publish how they are made.** AMBOSS and UWorld describe their items only in marketing terms ("aligned to NBME", "match the real exam"). Neither publishes an item-writing guide, distractor rules, psychometrics, a review workflow or validation data. Whether their own physicians write the items did not survive verification [S1][S2].
2. **Their sources are not public either.** No verified claim says which guidelines, textbooks or papers any big bank writes from, or how its explanations cite them. Stethoscore cannot copy a sourcing method that is not published. It can publish its own.
3. **Only CC0 and CC BY text is safe to build from.** That means openFDA (CC0 unless an item is flagged) and PMC Open Access articles one by one, where the article's own licence is CC0 or CC BY [S30][S31][S32]. Not safe: OpenStax Anatomy & Physiology 2e, NICE, WHO's default licence, MedlinePlus's licensed content, StatPearls. Unclear: every exam-derived dataset (MedQA, MedMCQA, MMLU) and everything not verified in October. This corrects §22d, which listed MedlinePlus and OpenStax's legacy copies as usable (§8).
4. **AI-drafted items need checking; the evidence agrees on that much.** Two 2026 studies found AI-written items close to faculty items on item statistics, but the samples were small, both item sets were weak, and one design relied on expert validation [S19][S20]. A 2026 radiology study kept experts as the final check on AI-made flashcards [S18]. The one independent study of a trust benchmark checked that citations exist, not that they support the claim [S17].
5. **The defensible edge is checkable provenance.** No competitor page in the verified evidence describes a claim-by-claim check of items against sources (inference from absence, not proof). Stethoscore's triple lock (facts and templates, independent agreement, a small perturbation-trained checker, under conformal risk control) is that check. It is built in Chat-me's question_bank package (§4, §6).
6. **What the app needs from them, within the owner's rules:** an error report on every stem and option, aggregate item analysis that retires weak items, blueprint tags per item, a probabilistic readiness estimate worded as no guarantee, and a public correction log. No ranks, leaderboards, XP or challenge-a-friend (§5).

## 1. How the big banks are made: what is public

| # | Finding | Confidence (vote) | Sources |
|---|---|---|---|
| 1.1 | AMBOSS and UWorld describe their Step 1 items only as aligned to NBME or the USMLE content outline. UWorld: items "test the exact integrated reasoning skills the National Board of Medical Examiners (NBME®) evaluates". AMBOSS: "Length, format, and reasoning style match the real exam throughout". Neither publishes an item-writing guide, distractor rules, psychometrics or validation data. One tutor's review partly contradicts AMBOSS's claim (a single opinion) | Medium (2-1 each) | [S1][S2][S3] |
| 1.2 | AMBOSS takes error and improvement reports through a feedback function "found throughout our content". It publishes no error log, no triage process and no promise of a reply. Its monthly update listing says not every change is reported | Medium (2-1) | [S4][S5] |
| 1.3 | Passmedicine targets UKMLA and finals, and its package includes an OSCE resource and PSA (prescribing) questions. It says questions are "constantly reviewed" for currency and guideline consistency, but its student page names no authors or reviewers and no method. Vendor. Its About page, seen only in a verifier's search, says questions come from working clinicians and that feedback and scoring data drop weak questions | High (3-0) | [S13][S14] |
| 1.4 | Geeky Medics' iOS listing advertises "900+ AI patients that listen and respond like a real patient" (text, voice or video). Its only provenance wording is "expert-authored resources", with no method for checking the AI patients' clinical content | Medium (2-1) | [S15] |
| 1.5 | Quesmed (seller Sysmed LTD) lists UKMLA AKT and CPSA/OSCE banks, MRCP Part 1 and Part 2, MSRA, MRCP PACES, Anatomy and Medical Sciences. PLAB is not named | High (3-0) | [S16] |
| 1.6 | AMBOSS US pricing: students $19.99 a month, or $12.50 a month billed yearly; clinicians $29.99, or $21.58 billed yearly. Each plan includes 50 Qbank questions a month. A separate Yearly Membership + Qbank Bundle is listed at $448, with a $149 renewal | High (3-0) | [S8][S9] |
| 1.7 | NBME's CBSSA gives an estimated probability of passing Step 1 and an equated percent correct, and says the probability "is not a guarantee". Its 2026 guidance: a 1-99% statistical model, updated yearly, and a 90% estimate still means 1 in 10 fail | High (3-0) | [S10][S11] |
| 1.8 | The AMBOSS Anki add-on needs an AMBOSS account and membership for full features, with a 5-day trial | Medium (2-1) | [S6][S7] |
| 1.9 | AnkiHub puts its AI study assistant and chatbot in Premium and Lifetime only, on top of existing shared decks | Medium (2-1) | [S21][S22] |
| 1.10 | The private-equity firm LLR took a majority stake in TrueLearn in 2020. Current ownership is not confirmed: no later sale was found, but who holds the stake now was not checked directly | Medium (2-1) | [S12] |
| 1.11 | Small AI answer apps (AskMed, MediSearch) say their answers are grounded in sources, but only in App Store copy. Neither listing describes a per-item check. The only accuracy comparison found is a small preprint by a competitor's authors | Medium (2-1, 3-0) | [S23][S24][S25] |

**What the evidence on AI-written items says:**

| # | Finding | Confidence (vote) | Sources |
|---|---|---|---|
| 1.12 | German anaesthesiology exam (157 students, 30 items): fine-tuned AI items and faculty items did not differ significantly in difficulty (P=.38), point-biserial (P=.96) or discrimination (P=.59). Both sets were weak (mean discrimination 0.09 faculty, 0.08 AI). The paper names a different model in its Methods than in its Abstract. Not evidence of equivalence | Medium (3-0) | [S19] |
| 1.13 | A 2026 study generated items zero-shot with no refinement, discarded and re-prompted failures, and had three experts validate in two passes (criteria, then solving and checking distractors). The authors' equivalence conclusion did not survive verification; human validation was part of the design | High (3-0) | [S20] |
| 1.14 | Asan Medical Center, 2026: experts reviewed 1,284 of 6,000 AI-made radiology flashcards for accuracy, appropriateness and errors. The authors call a human-in-the-loop system with expert final verification essential. The reviewer count and the error rate in coverage did not survive | Medium (3-0, 2-1) | [S18] |
| 1.15 | OpenEvidence: a peer-reviewed 2026 study of 4,979 citations from 150 prompts found none confirmed fabricated and three abstracts with attribution errors. It did not test whether the references support the claims | Medium (2-1, 3-0) | [S17] |

Inference: the big banks sell trust on brand and physician authorship they do not document. The verified research says AI drafting is viable only with checking, and the checking that exists in the field verifies that a citation exists, not that it supports the claim.

## 2. Their sources: what is public

Nothing verified. No surviving claim names the guidelines, textbooks or literature UWorld, AMBOSS, Kaplan, NBME, TrueLearn, BoardVitals, Passmedicine, Pastest, Quesmed, Lecturio or Osmosis write from, or how their explanations cite them. Claims that AMBOSS's library is physician peer-reviewed and that its AI Mode answers only from its own library were refuted 0-3 [S2][S8]. Osmosis's claims of grounding in one publisher's sources were refuted 1-2. NBME's item-writing guide was in scope but nothing about it survived.

For Stethoscore this means two things (inference). There is no published method to copy, so the method has to be its own. And publishing the method (source, licence, quote and check status on each item) is something none of them does in the verified evidence.

## 3. Licence table

The rule in code: **CC0 and CC BY only, read per document.** CC BY-SA is excluded because every derived item would have to carry ShareAlike (§22d §2). CC BY-ND is excluded because items are derivatives. Non-commercial (NC) and UNKNOWN are refused.

| Source | Licence found | Verdict | Confidence | Sources |
|---|---|---|---|---|
| openFDA | Public domain, CC0 1.0 "unless otherwise noted"; works where CC0 does not apply "will be clearly marked by a warning" (GMDN content is licensed separately) | **Safe**, minus flagged items; check each endpoint for the warning | High (3-0) | [S30] |
| PMC Open Access Subset | Per article. PMC groups licences as commercial allowed (CC0, CC BY, CC BY-SA, CC BY-ND), non-commercial (CC BY-NC, -NC-SA, -NC-ND) and other (none, untagged, custom). "License terms vary. Please refer to the license statement in each article" | **Safe per article** for CC0 and CC BY only; SA, ND, NC and other refused | High (3-0) | [S31][S32] |
| OpenStax Anatomy & Physiology 2e (current) | CC BY-NC-SA 4.0; "Commercial use requires permission"; derivatives ShareAlike; the page also bars LLM training | **Not safe** | High (2-1 to 3-0) | [S33][S34] |
| NICE | UK Open Content Licence for UK-setting use; outside the UK a fee and agreement; AI uses excluded per search excerpts (pages returned 403 to the verifiers) | **Not safe** without written agreement | Medium (2-1) | [S35] |
| WHO publications (default) | CC BY-NC-SA 3.0 IGO; "Permission is required for commercial uses". ICD, ICF and ICHI are CC BY-ND 3.0 IGO (no adaptation) | **Not safe** | High (3-0) | [S39] |
| MedlinePlus | Content other than NLM and federal material is copyrighted and licensed to NLM for MedlinePlus; A.D.A.M. and ASHP named as protected. The claim that unmarked federal content is public domain was refuted 0-3 | **Not safe** as a source: the line between parts could not be verified | High (3-0) | [S40] |
| StatPearls (NCBI Bookshelf) | CC BY-NC-ND 4.0 per §22d; not re-checked in October | **Not safe** | §22d [S43] | [S43] |
| MedQA | Release paper read; the claim that its questions come from board exams was refuted 0-3, so this pass drew no conclusion. §22d read the paper as scraped exam-prep items under US fair use | **Unclear, exam-derived**: evaluation and novelty screening only, never content | Low | [S42] |
| MedMCQA | Hugging Face sidebar apache-2.0 (2-1); the card's licensing section reads "[Needs More Information]" (3-0); the upstream repo reportedly MIT. Source of the questions: the claim that they are AIIMS and NEET PG exam questions was refuted 0-3. Read directly on 21 Oct, the card's summary says "AIIMS & NEET PG entrance exam MCQs", but its splits section says the 182,822 training questions come from mock and online test series; only the test set (AIIMS PG) and the dev set (NEET PG) are said to be real exam items | **Unclear, exam-derived**: screening only | Medium on the licence fields; Low on the source | [S36][S37] |
| MMLU medical subsets | Only a loader script's Apache 2.0 header was verified; no data licence or provenance | **Unclear**: screening only | Low (3-0, loader only) | [S38] |
| Bulletin of the WHO | CC BY 3.0 IGO, per WHO's policy page; pre-2016 and translated items case by case | **Unclear** until checked per article | High (policy line) | [S39] |
| CDC | Some resources and images are restricted (contractor, grantee or third-party); the blanket public-domain claim was refuted 0-3 | **Unclear**, per item | High (3-0) | [S41] |
| Open RN; legacy OpenStax CC BY copies; LibreTexts; HEAD-QA; PubMedQA; DailyMed; NCBI Bookshelf titles; Europe PMC; GMC UKMLA content map; WHO EMRO Unified Medical Dictionary; Arabic Wikipedia | Nothing verified in October. §22d found Open RN CC BY 4.0 "except where otherwise noted"; Arabic Wikipedia is expected CC BY-SA (would be excluded anyway) | **Unclear**: not admitted until a licence is read per title or page | n/a | §22d |

**Attribution.** For every CC BY source the item records creator, licence notice, licence link and that the text was changed (§22d [43]); openFDA needs no attribution under CC0 but the item still records the endpoint and record. **Arabic.** No Arabic medical source was verified as safe, so Arabic items are made by translating checked English items (§22d §6), not from Arabic sources.

Inference: today's safe pool is small and lopsided: drug labels and per-article open-access papers. That favours pharmacology and evidence-based items and leaves anatomy and physiology thin until a CC BY textbook is verified.

## 4. How Stethoscore builds its own: the triple lock

No source can be copied, so every item is original and must earn its status. The design, one lock at a time:

| Lock | What it does | Why (evidence) |
|---|---|---|
| **1. Facts and templates** | A fact is extracted from an admitted CC0 or CC BY passage and stored as a claim unit: source id, paragraph locator, text hash, the exact quote. Items are written from those units into vetted item templates (vignette, lead-in, one best answer, distractors from the same category). The quote is checked mechanically against the source text before anything else runs | No vendor shows its sources (§2); quoting makes each item checkable. Templates enforce the item-writing rules §22d §4 step 3 lists |
| **2. Independent agreement** | Chat-me's deterministic checks run first (question validation against the source, independent entailment, citation integrity). Then a support review and a challenge review, each blind to the other, from different model families. Any dissent sends the item back | Unreviewed AI items carry factual errors and weak distractors (§22d §3); experts stayed the final check in every verified study (1.13, 1.14) |
| **3. A small perturbation-trained checker** | A small classifier trained on machine-perturbed versions of admitted facts (swapped drug, flipped direction, wrong number, wrong key) to catch the errors lock 2 misses. Trained only on licensed source text, never on students' data, and shared by all users: not a personal self-learning model and not scratch pretraining | §23e §6: the mutation test made no false proof in 906,828 mutated statements. That tests the word-for-word gate only, not the accuracy of Verified. The same idea, aimed at items |
| **Conformal risk control** | One threshold, set on an expert-labelled calibration set, so that the error rate among items shown as Verified stays under a chosen level. Items below it are shown as "Check this" or held | §23e §5.1 row 7 and A5 [§23e S49] |

**Status.** An item passing all three locks under the conformal threshold is exported with its provenance and the SOURCE status from Chat-me's QUESTION_GENERATION_CONTRACT.md (VALIDATED, SOURCE_UNCERTAIN, SOURCE_UNSUPPORTED, SOURCE_UNAVAILABLE). A failing item is revised within a fixed budget, then quarantined. Live answers then drive §22d §5's item analysis (difficulty, discrimination, distractor efficiency), which retires weak items: the step Passmedicine describes (1.3) and NBME's statistical framing (1.7) depend on.

## 5. What else the app needs from them

Only features with verified evidence behind them, filtered by the owner's rules (never ranks, leaderboards, XP, challenge-a-friend or a personal self-learning model).

| # | Feature | Who shows it | Fits the rules? | Priority |
|---|---|---|---|---|
| F1 | Report an error on the stem and on each option, from the item itself | AMBOSS (1.2) | Yes | 1 |
| F2 | A public correction log: what changed, when, and why | Nobody verified: AMBOSS has no published log (1.2) | Yes; beats them | 1 |
| F3 | Source, licence, quote and check status on every item and explanation | Nobody verified (§2, 1.15) | Yes; beats them | 1 |
| F4 | Aggregate item analysis across all students: retire or revise weak items | Passmedicine (1.3, vendor); §22d §5 | Yes; no student is compared with another on screen | 2 |
| F5 | Blueprint tags per item (USMLE outline, UKMLA content map, EMLE provisional) and coverage per node | AMBOSS and UWorld alignment claims (1.1); Quesmed exam list (1.5) | Yes; GMC map as an internal taxonomy only (§22d §7) | 2 |
| F6 | Prescribing items (PSA-style), from openFDA labels | Passmedicine (1.3) | Yes; doses only from label text, with the dosing-safety review of §22d §4 step 6 | 3 |
| F7 | A readiness estimate shown as a probability with "not a guarantee", only once calibrated | NBME CBSSA (1.7) | Yes, if personal and never a percentile against other students | 4 (needs data) |
| F8 | Export of verified items to a flashcard deck | AMBOSS Anki add-on (1.8) | Yes; exports keep each item's attribution | 4 |

Already in Stethoscore or its plan: OSCE stations and a spoken examiner (Geeky Medics' AI patients, 1.4, are the nearest rival); AI from the student's own lectures (AnkiHub's AI sits on shared decks only, 1.9). Tutor and timed modes, self-assessments, study planners and offline apps were in scope but **UNVERIFIED** for every competitor; §22b covers them from its own sources.

## 6. Integration plan

The bank is built in Chat-me, inside the verifier, never beside it. The package is `agents/specialists/question_bank` on Chat-me's **design/question-bank** branch (task T0078). Per that task, it holds the evidence loop (claim units, quote and lineage checks, then `agents/specialists/verification_agent/question_validation.py`, `independent_entailment.py` and `agents/specialists/retrieval_agent/citation_integrity.py`, then the two blind reviews as file packets, bounded revision, quarantine and export), the CC0 and CC BY licence gate, its tests in `tests/unit`, and `docs/architecture/verifier/QUESTION_BANK_PIPELINE.md`. The Worker's accuracy engine stays the spine (Task 4 audit); the package feeds it items, not verdicts.

| Step | Work | Where | Checkable here? | Accept when |
|---|---|---|---|---|
| P1 | Licence gate matches §3: CC0 and CC BY only; PMC per article from its licence statement; openFDA records with a non-CC0 warning refused; every "Unclear" row refused with its reason | question_bank licence gate | Yes (Python) | A test per §3 row: safe admitted, everything else refused with a reason |
| P2 | Admitted-source adapters: openFDA labels and PMC OA articles, through the existing `retrieval_agent/openfda.py` and `ncbi.py` | retrieval_agent, question_bank | Yes | Each claim unit's quote found word for word in the stored snapshot |
| P3 | Lock 1: claim units and item templates | question_bank | Yes | A misquoted unit is refused; a template without one best answer is refused |
| P4 | Lock 2: deterministic checks, then blind support and challenge packets | question_bank, verification_agent | Yes (packets offline) | Any dissent or failed check stops export |
| P5 | Lock 3: perturbation set from admitted facts; train and freeze the small checker as a GitHub Actions batch job | evals/, question_bank | Yes (CI) | Recall on held-out perturbations reported; never trained on student data |
| P6 | Conformal threshold on an expert-labelled calibration set | evals/, governance/ | Blocked on labels (§23e §9) | Interim target met on held-out labels: Verified error bound with its n, plus coverage (see below) |
| P7 | Seed: 300 to 500 items on pharmacology and evidence nodes first, where the safe pool is (§3) | question_bank export | Yes | Rejection rate and per-lock failure counts published |
| P8 | Server: item store, responses, item statistics, error reports, correction log (F1, F2, F4) | red-pen-ios server/ | Yes (node tests) | Item retired automatically under §22d §5 thresholds |
| P9 | App: item source card, report button, blueprint coverage (F1, F3, F5), batched into one Mac run | red-pen-ios ios/ | Mac run | Screenshots show source, licence and status on every item |

Order follows red-pen-ios's rule: what Linux, node and Python can check first (P1 to P5, P7, P8), what changes the screen last (P9). No paid AI outside Pro (PRO_PAYS) and no Worker deploy without the owner's word.

**The 99.999% target (P6).** The owner wants Verified items right 99.999% of the time: at most 1 error in 100,000. That cannot be proved yet. By the rule of three, proving an error rate of 1e-5 or less with 95% confidence needs about 300,000 independent items, each checked by an expert and found free of error. The current bench is 90 questions (§23e). It cannot prove this, and no calibration set the project can label soon will. The cited conformal evidence shows guarantees at 80 to 90% [§23e S49]. So P6 sets an interim target that can be measured: the error bound on items shown as Verified, as a 95% Wilson upper bound stated with its n, plus coverage (the share of items shown as Verified rather than held or "Check this"). For example: Wilson upper bound at or under 1% on at least 500 labelled Verified items, with coverage reported beside it. The bound tightens as labels grow. The 99.999% target is approached, not proved, through the triple lock: facts, not prose (lock 1); independent agreement (lock 2); a checker (lock 3); conformal abstention, which holds what it cannot vouch for; and a small blast radius, since every item carries its source and a report button (F1, F3) and the correction log (F2) fixes a bad item fast.

## 7. Unverified, said plainly

- **Competitors:** nothing survived for Kaplan, Pastest, BoardVitals, Lecturio, Sketchy, Pixorize, Picmonic, Boards and Beyond, Osmosis, UpToDate, Medscape, Glass Health, StudyFetch, Knowt, Quizlet or Mopiq. UWorld authorship, review and AI use; Quesmed authorship and pricing; every App Store rating and free tier beyond 1.6 and 1.11.
- **How banks are made:** no item-writing guide, psychometrics, update cadence or AI use verified for any vendor, NBME included. AMBOSS's Score Predictor and Equated Percent Correct methods, and UWorld's scoring, did not survive.
- **Sources:** what any vendor writes from (§2).
- **Egypt, MENA and Arabic:** no app, bank or terminology resource survived; claims about one Egyptian platform's Arabic support were refuted.
- **Licences:** every "Unclear" row in §3, the PMC machine-readable licence field and Europe PMC's filter method.

## 8. Corrections to earlier briefs

- §22d §1 lists **MedlinePlus (NLM-authored)** as commercially usable. The public-domain half was refuted 0-3 in October; treat MedlinePlus as not safe until NLM-authored pages can be told apart reliably [S40].
- §22d §1 says legacy OpenStax copies "stay CC BY". Not verified in October; the current A&P 2e is CC BY-NC-SA and bars commercial use [S33].
- §22d §1 lists **Open RN** as safe. Not re-verified; it stays unclear until its licence is read per page.
- §22d's MedMCQA row (Apache-2.0) is now "unclear": the card's own licensing section is a placeholder [S36].

## 9. Decisions for the owner

1. Accept §3's table as the gate in code, which leaves the seed bank thin on anatomy and physiology until a CC BY textbook is verified?
2. Seed on pharmacology and evidence nodes first (P7)?
3. Who labels the conformal calibration set (P6), and which interim target is Verified held to? The 99.999% target needs about 300,000 error-free labelled items to prove at 95% (rule of three), so it cannot be proved with the current bench. The proposal is a 95% Wilson upper bound on the Verified error rate, stated with its n, plus coverage, reviewed as labels grow (§6, below P6).
4. Publish the correction log (F2) and the per-lock failure counts (P7)?
5. Run a second licence pass on the §3 "Unclear" rows, Open RN and Arabic sources first?

## Sources

- [S1] UWorld, USMLE Step 1: https://medical.uworld.com/usmle/usmle-step-1/
- [S2] AMBOSS, Step 1: https://www.amboss.com/us/usmle/step1
- [S3] Blueprint tutor review: https://blog.blueprintprep.com/medical/amboss-step-1-self-assessment-week-a-tutors-experience/
- [S4] AMBOSS content updates: https://www.amboss.com/us/knowledge/amboss-content-updates-20172025
- [S5] AMBOSS support: https://support.amboss.com/hc/en-us/articles/360031918492
- [S6] AMBOSS Anki add-on: https://www.amboss.com/us/anki-amboss
- [S7] AMBOSS blog, Anki add-on: https://blog.amboss.com/us/how-to-use-the-amboss-add-on-for-anki
- [S8] AMBOSS pricing: https://www.amboss.com/us/pricing
- [S9] AMBOSS pricing help: https://support.amboss.com/hc/en-us/articles/32345265867921-AMBOSS-Pricing
- [S10] NBME CBSSA: https://www.nbme.org/examinees/self-assessments/comprehensive-basic-science-self-assessment
- [S11] NBME CBSSA guidance 2026: https://www.nbme.org/wp-content/uploads/2026/04/CBSSA_CBSE_Guidance.pdf
- [S12] TrueLearn, About: https://truelearn.com/about/
- [S13] Passmedicine, students: https://www.passmedicine.com/student/index.php
- [S14] Passmedicine, About: https://www.passmedicine.com/common/about.php
- [S15] Geeky Medics, App Store (GB): https://apps.apple.com/gb/app/geeky-medics-osce-revision/id1050169390
- [S16] Quesmed, App Store: https://apple.co/3bMVxNz
- [S17] Lotan et al. 2026, OpenEvidence reference quality: https://research.bidmc.org/radiology-research/publications/reference-quality-openevidence-across-five-medical-specialties
- [S18] Asan Medical Center, AI radiology flashcards: https://news-en.amc.seoul.kr/news/eng/detail.do?cntId=3283
- [S19] JMIR Formative Research 2026, AI vs faculty anaesthesiology items: https://pmc.ncbi.nlm.nih.gov/articles/PMC12916093/
- [S20] Crk and Gultepe 2026, Frontiers in Education: https://www.frontiersin.org/journals/education/articles/10.3389/feduc.2026.1837523/full
- [S21] AnkiHub: https://www.ankihub.net/
- [S22] AnkiHub AI: https://community.ankihub.net/t/what-is-ankihub-ai/358186
- [S23] AskMed, App Store: https://apps.apple.com/us/app/medical-assistant-askmed-ai/id6776077826
- [S24] MediSearch, App Store: https://apps.apple.com/us/app/-/id6473300940
- [S25] medRxiv preprint, AI medical answer apps: https://www.medrxiv.org/content/10.1101/2025.09.23.25336206v2.full
- [S30] openFDA terms: https://open.fda.gov/terms
- [S31] PMC text mining: https://pmc.ncbi.nlm.nih.gov/tools/textmining/
- [S32] PMC open full-text list: https://pmc.ncbi.nlm.nih.gov/tools/openftlist
- [S33] OpenStax A&P 2e, chapter 1: https://openstax.org/books/anatomy-and-physiology-2e/pages/1-introduction
- [S34] OpenStax A&P 2e, preface: https://openstax.org/books/anatomy-and-physiology-2e/pages/preface
- [S35] NICE, reusing our content: https://www.nice.org.uk/re-using-our-content
- [S36] MedMCQA dataset card: https://huggingface.co/datasets/openlifescienceai/medmcqa
- [S37] MedMCQA paper: https://arxiv.org/pdf/2203.14371
- [S38] MMLU loader script: https://huggingface.co/datasets/lighteval/mmlu/raw/e24764f1fb58c26b5f622157644f2e5fe77e5b01/mmlu.py
- [S39] WHO copyright policy: https://www.who.int/about/policies/publishing/copyright
- [S40] MedlinePlus, using content: https://medlineplus.gov/about/using/usingcontent/
- [S41] CDC agency materials: https://www.cdc.gov/other/agencymaterials.html
- [S42] MedQA paper: https://arxiv.org/pdf/2009.13081
- [S43] StatPearls licence (via §22d [29]): https://www.ncbi.nlm.nih.gov/books/NBK430685/
- [§23e S49] Mohri and Hashimoto 2024, conformal factuality: https://proceedings.mlr.press/v235/mohri24a.html
