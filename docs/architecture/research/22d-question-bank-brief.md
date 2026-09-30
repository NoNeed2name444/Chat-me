# §22d Medical Question Bank Brief: Free Sources to Commercial Use (2026-09-30)

Stethoscore has no shared bank; items are generated from students' uploads and checked by the accuracy engine, so a bank is a new surface. Every licence was read from its text via tools; UNKNOWN means not usable.

## 1. Source list

| Source | Licence (verified) | Commercial? | Attribution? | Share-alike? | Volume | Quality | Risk |
|---|---|---|---|---|---|---|---|
| Open Osmosis; Wikipedia; WikiDoc | Osmosis redirects to Elsevier; only 2015-18 videos archived CC BY-SA 4.0 [1], current terms non-commercial [2]; Wikipedia CC BY-SA 4.0 + GFDL [25]; WikiDoc CC BY-SA, version unstated [26] | Wikis yes, Osmosis no | Yes | **YES** | No Osmosis question set; wikis very large | Variable | Med-High |
| OpenStax (current); Radiopaedia; LITFL; StatPearls | OpenStax moved to CC BY-NC-SA 4.0 on 22 Apr 2026 [3][4], legacy CC BY copies stay CC BY; Radiopaedia CC BY-NC-SA 3.0 [27]; LITFL CC BY-NC-SA 4.0 [28]; StatPearls CC BY-NC-ND 4.0 [29] | No | Yes | **YES** (not StatPearls) | Test banks behind instructor login; large sites | High | High |
| OER Commons; MedEdPORTAL | Per-item licences; OER Commons default CC BY-NC-SA 4.0 [5]; MedEdPORTAL 2016 mostly CC BY-NC-SA, 2019+ mostly CC BY, some CC BY-NC [17] | CC BY items only | Yes | Often | Few MCQ sets; 1,226 OA items | Peer-reviewed | Med |
| Pathology Bites; Ottawa Question Bank | Code MIT [6] but content "personal, non-commercial use only" [7] (conflict); Ottawa paid, no licence stated [8][9] | No | n/a | No | UNKNOWN; 4,438 MCQs | Reviewed; 5 review rounds | High |
| EnterMedSchool; iatroX; Medbullets; Lecturio; Anki decks (AnKing, Zanki); AAMC; NBME/USMLE; GMC (MLA map, 85 PLAB items) | Custom non-commercial licence covering AI derivatives [10][11]; proprietary or "personal studies" only [12][13][14][15][16]; non-commercial permission or all rights reserved [18][19][20] | No | Yes | No | Large; regulator sample items | Variable to gold | High |
| PMC Open Access Subset; abstracts | "License terms vary... refer to the license statement in each article" [21]; abstracts may be copyrighted [22] | CC0/CC BY items; facts only from abstracts | Yes | CC BY-SA items | Millions (text) | Peer-reviewed | Low if filtered |
| Open RN | CC BY 4.0 "except where otherwise noted", NCLEX-style items [23] | **Yes** | Yes | No | Several textbooks | Peer-reviewed nursing | Low |
| MedlinePlus (NLM-authored) | US public domain; excludes A.D.A.M. and ASHP content [24] | **Yes** | Requested | No | Thousands of topics (text) | High, lay | Low |

Research datasets (file licence vs. underlying question copyright, which governs reuse):

| Dataset | Repo licence | Underlying items | Usable as content? |
|---|---|---|---|
| MedQA | HF "unknown" [30]; GBaker "cc-by-4.0" relabel unsupported [31] | Scraped from Medbullets, AMBOSS, Lecturio under US fair use [32] | No |
| MedMCQA | Apache-2.0 [33] | "open websites and books", AIIMS/NEET PG official questions [34] | No |
| MedXpertQA | MIT on HF, "CC BY 4.0" in paper (conflict) [36][37] | USMLE, COMLEX, 17 boards, NEJM Image Challenge; LLM-rephrased | No |
| PubMedQA; HeadQA; MMLU medical | MIT [35][38][39] | Questions from titles with abstract contexts; Spanish Ministry exam papers; "freely available sources online", USMLE practice items [40] | Labels only; UNKNOWN; No |
| NEJM/JAMA challenges | None | NEJM terms bot-blocked (UNKNOWN); AMA terms: noncommercial, no derivatives [41] | No |

## 2. Licensing deep dive

| Source | Licence type | Restrictions | Commercial viability |
|---|---|---|---|
| CC BY-NC-* and -ND (current OpenStax, Radiopaedia, LITFL, StatPearls, older MedEdPORTAL) | NC, ND | "not primarily intended for or directed towards commercial advantage or monetary compensation" [42]; a subscription app fails; ND bars adaptation | None without written permission |
| CC BY-SA (Wikipedia, WikiDoc, Osmosis archive) | SA | Adaptations must be CC BY-SA; SA binds "Adapted Material", not collections or app code [43][44] | Legal, but every derived item becomes freely copyable; avoid |
| CC BY, CC0, public domain (Open RN, MedlinePlus, filtered PMC, CC BY MedEdPORTAL) | Permissive | Attribution: creator, notice, licence link, changes marked [43] | Full |
| Custom NC (EnterMedSchool, GMC, AAMC); research datasets | Custom; MIT/Apache on files | Permission per case; a file licence cannot grant copyright the authors never held | None as content; screening only |

## 3. Generation methods

| Method | Legal | Quality | Scalability | Cost | Time | Recommendation |
|---|---|---|---|---|---|---|
| A Direct reuse | M (almost no CC BY questions exist) | M | S | S | S | Only Open RN items |
| B AI from open sources | S with allowlist and novelty screen | L unreviewed: 22% factual errors, 49% writing flaws [45]; 56% vs 27% flaws, weaker distractors [46]; 25% discarded [47]; GPT-4 matched ACR items, Llama 2 keyed 69% [50] | L | S (free quotas) | S | Use, only through pipeline |
| C Crowdsourced | M (copied text leaks in) | M: student items comparable in an RCT [48]; Ottawa used 5 review rounds [8] | M | S | M | After launch, contributor licence |
| D Blueprinted | S | S | M | S | M | The frame |
| E Hybrid | S-M | S-M | L | S | M | **Use** |

Evidence: GPT-4o given the reference text and item-writing rules matched an expert committee (median 9 vs 9), 95% novel [49]; yet LLM items discriminate worse (0.24 vs 0.36 [51]; 0.19 vs 0.29 [52]), run easier and lower-order [53], have fewer functional distractors (39% vs 55% [54]; 16.7% with none working [55]); 2/12 cases hallucinated [56]; generation is 5.6x faster [48].

## 4. Validation pipeline

1. Source verification: `licences` allowlist (PD, CC0, CC BY) enforced in code; NC, ND, SA, UNKNOWN rejected; attribution auto-built from `source_id`s.
2. Medical accuracy: accuracy engine (Europe PMC grounding, cross-model vote, dose/lab rules, weekly scorer), Jev triage, mDeBERTa entailment of the key claim against the cited passage; any dissent rejects.
3. Item quality: judge from a different model family than the generator (Gemini writes, Gemma judges [48]) against an NBME-style flaw list: one defensible key, no absolutes, cover-the-options test, realistic vignette, Bloom level apply or above.
4. Blueprint alignment: map to a node (USMLE system x task, MLA condition, EMLE specialty, lecture LO); reject saturated nodes; cosine >0.90 to an existing item is a duplicate.
5. Novelty: Levenshtein <60/100 [49] and cosine <0.85 against a private corpus of public question sets and the cited text; else regenerate.
6. Expert review: volunteer, credited (§10), unpaid pre-launch; mandatory for P0 nodes (dosing, emergencies, EMLE core); items show "AI-generated, unreviewed" until passed.
7. Student beta: metrics after 30 responses; flag or retire per §5.

Flow: cron finds gaps -> allowlisted passages -> generate -> steps 1-5 -> `beta` -> serve -> stats -> `verified`, `revise` or `retired` (archived, replacement queued).

## 5. Quality metrics

| Metric | Definition | Acceptable | Action outside |
|---|---|---|---|
| Difficulty p | Proportion correct | 0.30-0.70 [57]; pooled real exams 0.52 [58] | p<0.20 or >0.90: check key |
| Discrimination D | Upper minus lower 27% | ≥0.40 good, 0.20-0.39 acceptable [57]; pooled 0.29 [58] | <0.20 revise; negative retire |
| Point-biserial | Item-total correlation | ≥0.20 target [59] | <0.10 revise |
| Distractor efficiency | Non-functional distractor chosen by <5% [57] | 100%; pooled 62% [58] | ≥2 NFDs rewrite |

## 6. Implementation in Stethoscore

D1 tables: `items` (stem, options, key, explanation, lang, blueprint_node, licence_id, attribution, source_ids, generator_model, status, version), `item_versions`, `sources`, `licences`, `validation_reports`, `reviews`, `responses`, `item_stats`, `retirements`. One Worker cron generates and validates on free-quota models; Workers AI embeddings for dedup and novelty; the delivery Worker records responses and updates stats; versions and reviewer ids form the audit trail. Arabic: generate in English, translate, back-translate check, same pipeline. Personal-material generation stays primary; the bank is an additive Pro feature.

## 7. Curriculum alignment

| Curriculum | Blueprint | Coverage target | Gap list |
|---|---|---|---|
| USMLE Step 1 (Step 2 CK outline not fetched: UNKNOWN) | 11 systems with published ranges (Reproductive/Endocrine 12-16%) and task weights [19] | Items per node proportional to range | Auto from node counts |
| UKMLA, PLAB 1 | MLA content map, Oct 2025 version applying from Sept 2026; GMC copyright non-commercial, so internal taxonomy only [20][60] | ≥3 items per condition | Same |
| MRCP Part 1 | 2 papers x 100 best-of-five; specialty counts (cardiology 15, clinical sciences 25) [61] | Mirror counts | Same |
| EMLE | Official blueprint NOT FOUND; MoH-run under Law 153/2019 [62]; proposed weights IM 25-30%, surgery 20-30%, ObGyn and paediatrics 15-25% [63] | Provisional weights | Same |
| Egyptian faculties | Lecture LOs from students' uploads (existing flow) | Per course | Per course |

## 8. Competitive analysis

| Competitor | Method | Volume | Quality | Cost |
|---|---|---|---|---|
| UWorld | Expert-written | ~3,600-4,000 Step 1 [64] | Reference standard | $319-$560 [64] |
| AMBOSS | Expert-written | 13,900+ total, 2,700+ Step 1 [65] | High | $448/yr [65] |
| Osmosis; Geeky Medics | In-house experts | 2,500+ Step 1, 7,500+ Step 2 [66]; 3,500+ MLA AKT SBAs [67] | High | Subscription |
| iatroX; Ottawa QB | AI-assisted; student-written | Large; 4,438 | Unverified; good | Free; nominal fee [8] |

## 9. Legal risk

| Risk | Severity | Mitigation |
|---|---|---|
| Copying proprietary items or laundered datasets: exam papers are literary works [68]; NBME registers USMLE forms and sued Optima [69]; ABIM won an injunction and damages against Arora [70]; MedQA and MedXpertQA lean on US fair use, absent in Egypt and UK | High | Allowlist only; novelty screen; datasets for screening only |
| NC, ND or SA breach | High | Licence table enforced in code; SA excluded from inputs |
| AI items uncopyrightable (Thaler v. Perlmutter, cert denied 2 Mar 2026 [71]) | Medium | Record human editing per item; rely on ToS and pace |
| Crowdsourced items with copied text | Med-High | Contributor grants CC BY plus originality warranty; similarity screen; takedown route |
| Medical error harm; no insurance or counsel budget (UNKNOWN) | Medium | Accuracy engine, visible review status, versioning, disclaimer; conservative allowlist until Pro revenue |

## 10. Recommended plan

- Phase 0 (pre-launch, $0): licence table, schema, pipeline; taxonomies for EMLE (provisional), MLA, USMLE; seed 300-500 English EMLE-core items from Open RN, MedlinePlus and CC0/CC BY PMC text, labelled "AI-generated, unreviewed"; expect 25-50% rejection.
- Phase 1 (launch to month 3): beta metrics, volunteer reviewer credits, Arabic pass, retire failures.
- Phase 2 (months 3-9): crowdsourcing under contributor licence; USMLE and MLA nodes.
- Phase 3 (Pro revenue): paid expert review of P0 nodes; license a commercial bank if metrics lag. Cost: owner cash $0. Revenue: a Pro differentiator, not a launch dependency.

Top 5 sources to use: 1 Open RN (CC BY 4.0, the only free reusable items); 2 MedlinePlus NLM text; 3 PMC articles filtered to CC0/CC BY; 4 MedEdPORTAL CC BY items, checked one by one; 5 public question datasets for evaluation and novelty screening only.

Top 5 to avoid: 1 MedQA, MedMCQA, MMLU, MedXpertQA, HeadQA as content; 2 NEJM/JAMA challenges; 3 Anki decks; 4 NC/ND sources (current OpenStax, Radiopaedia, LITFL, StatPearls, EnterMedSchool, GMC, AAMC, USMLE); 5 SA text (Wikipedia, WikiDoc) and proprietary banks (Medbullets, Lecturio, iatroX, Ottawa, Pathology Bites content).

Top 3 methods: 1 E hybrid, blueprint-driven AI generation from allowlisted text with the full pipeline; 2 C crowdsourcing post-launch under contributor licence with peer review; 3 D blueprinting as the coverage frame.

## Sources
1 https://archive.org/details/open-osmosis-videos-2015-2018 2 https://www.elsevier.com/en-au/legal/elsevier-website-terms-and-conditions/elsevier-osmosis-terms-and-conditions 3 https://openstax.org/blog/openstax-licensing 4 https://raw.githubusercontent.com/openstax/osbooks-anatomy-physiology/main/LICENSE 5 https://oercommons.org/terms 6 https://github.com/jjsanchezramirez/pathology-bites 7 https://www.pathologybites.com/terms 8 https://doi.org/10.36834/cmej.78869 9 https://www.theottawaquestionbank.ca/about 10 https://github.com/enterMedSchool/Non-Profit/blob/main/LICENSE 11 https://www.entermedschool.org/en/license 12 https://www.iatrox.com/terms 13 https://im.medbullets.com/terms 14 https://www.lecturio.com/termsofuse/ 15 https://ankiweb.net/account/terms 16 https://community.ankihub.net/tos 17 https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=JOURNAL%3A%22MedEdPORTAL%22&format=json&resultType=core 18 https://www.aamc.org/website-terms-conditions 19 https://www.usmle.org/prepare-your-exam/step-1-materials/step-1-content-outline-and-specifications 20 https://www.gmc-uk.org/disclaimer 21 https://pmc.ncbi.nlm.nih.gov/tools/openftlist/ 22 https://www.nlm.nih.gov/web_policies.html 23 https://wtcs.pressbooks.pub/nursingfundamentals/ 24 https://medlineplus.gov/about/using/usingcontent/ 25 https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use 26 https://www.wikidoc.org/index.php/Main_Page 27 https://radiopaedia.org/licence 28 https://litfl.com/ 29 https://www.ncbi.nlm.nih.gov/books/NBK430685/ 30 https://hf.co/datasets/bigbio/med_qa 31 https://hf.co/datasets/GBaker/MedQA-USMLE-4-options 32 https://arxiv.org/abs/2009.13081 33 https://hf.co/datasets/openlifescienceai/medmcqa 34 https://arxiv.org/abs/2203.14371 35 https://hf.co/datasets/qiaojin/PubMedQA 36 https://hf.co/datasets/TsinghuaC3I/MedXpertQA 37 https://arxiv.org/abs/2501.18362 38 https://hf.co/datasets/dvilares/head_qa 39 https://github.com/hendrycks/test/blob/master/LICENSE 40 https://arxiv.org/abs/2009.03300 41 https://www.ama-assn.org/about/terms-use 42 https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.en 43 https://creativecommons.org/licenses/by-sa/4.0/legalcode.en 44 https://creativecommons.org/faq/ 45 https://doi.org/10.1152/advan.00106.2024 46 https://doi.org/10.1136/bjo-2025-327632 47 https://doi.org/10.1016/j.acpath.2026.100275 48 https://doi.org/10.1186/s12909-026-09671-0 49 https://doi.org/10.1001/jamaophthalmol.2025.3622 50 https://doi.org/10.1016/j.acra.2024.06.046 51 https://doi.org/10.1097/ACM.0000000000005626 52 https://doi.org/10.1177/23821205261427885 53 https://doi.org/10.1186/s12909-025-06796-6 54 https://doi.org/10.1080/0142159X.2025.2513418 55 https://doi.org/10.7759/cureus.114830 56 https://doi.org/10.1186/s12909-025-08085-8 57 https://doi.org/10.7759/cureus.105651 58 https://doi.org/10.7759/cureus.99299 59 https://doi.org/10.1186/s12909-025-07528-6 60 https://www.gmc-uk.org/education/medical-licensing-assessment/mla-content-map 61 https://www.thefederation.uk/examinations/part-1/format 62 https://en.wikipedia.org/wiki/Egyptian_Medical_Licensing_Examination 63 https://www.researchgate.net/publication/351060460 64 https://www.iatrox.com/blog/best-usmle-step-1-question-banks-2026 65 https://www.amboss.com/us/pricing 66 https://www.osmosis.org/features/quiz-builder 67 https://geekymedics.com/ukmla-akt-medical-student-finals-question-bank/ 68 https://lawbhoomi.com/university-of-london-press-v-university-tutorial-press/ 69 https://www.thehealthlawfirm.com/wp-content/uploads/2023/12/NBME-v-Optima-University.pdf 70 https://www.plagiarismtoday.com/2026/01/28/a-15-year-long-fight-over-exam-questions/ 71 https://www.bakerdonelson.com/supreme-court-denies-certiorari-in-thaler-v-perlmutter-ai-cannot-be-an-author-under-the-copyright-act (45-59 via PubMed)

## CRITICAL GATE

Verdict: **yes, with restrictions.** No free source gives Stethoscore a reusable, exam-grade question bank with commercial rights: every free bank (Ottawa, iatroX, Pathology Bites content, EnterMedSchool, GMC and USMLE samples, Anki decks) is proprietary or non-commercial, OpenStax has just moved to NC-SA, and the research datasets are repackaged proprietary questions whose MIT or Apache tags transfer no copyright. What is clean is source text: Open RN (CC BY 4.0, the only free reusable items), MedlinePlus, CC0/CC BY PMC articles and CC BY MedEdPORTAL items. A reliable bank is therefore generated from that allowlisted text on free-quota models, every item passing the seven-step pipeline, because unreviewed LLM items carry about 22% factual errors, weaker distractors and lower discrimination. Restrictions: enforce the allowlist in code and reject NC, ND, SA and UNKNOWN sources; never ingest MedQA-style datasets, NEJM/JAMA challenges or Anki decks as content; screen novelty against public question sets; attribute per item; show review status and keep human review for high-stakes nodes; treat the EMLE blueprint as provisional; start with a few hundred items and let beta metrics, not volume, drive expansion. Fable decides; the user approves.
