# §22b MEDICAL APP FEATURE BRIEF (revision 2)

**What this revision is:**
- Revision 2, 1 October 2026, for the rulebook's Task 3.
- **Part A** is revision 1's market research (30 Sep 2026), kept whole: what exists, pricing, ratings, impact, UX, workflow, money, AI, gaps.
- **Part B** is new: the medical lens. For each app:
  - how it keeps content accurate;
  - how it teaches clinical reasoning;
  - what works and fails medically;
  - what is missing medically.
- It ends with MEDICAL IMPROVEMENTS THIS UNLOCKS.

**Keys:**
- Ratings are from the US App Store unless stated. [S#] is a source.
- "Vendor" marks a developer's own claim. "Preprint" marks a study not yet peer reviewed.

# Part A. The market (revision 1)

## 1. Market landscape

| App | Features | AI | Pricing | Strengths | Weaknesses |
|---|---|---|---|---|---|
| UWorld Medical | Qbank, exam sims, analytics | None [S1] | Step 1 $349–749 per window [S2]; Egypt EGP 25–45k [S3] | 4.7★/4.1K; 98% of a cohort used it [S27] | Auth errors, no offline, lost progress [S1]; Trustpilot 2.3/5 [S4] |
| AMBOSS | 13,900+ Qs, 1,500+ articles, Anki add-on, calculators [S5] | AI Mode uses uploaded course material, tunes Qbank/Anki, predicts scores (vendor) [S5] | $19.99/mo or $149/yr with 50 Qs/mo; 5-day trial [S7] | 4.9★/14K library; 4.8★/5.2K Qbank [S8,S9] | Paywall anger; iOS lacked highlighting, text size, dark mode [S8]; no outcome studies [S10]; "22K+" vs 13,900 conflict |
| Osmosis (Elsevier) | Videos, Qs, SRS, schedule | Cited answers from Elsevier texts; generates cards, quizzes, mnemonics [S11] | $149/3mo, $224/yr [S13]; IAP $16.99/mo (conflict) [S12] | 4.8★/3.9K | Video navigation; "oversimplified" [S12,S14] |
| Lecturio | 8,050+ videos, 8,000+ Qs, NEJM Healer sims | Lexi: NL search, summaries, custom tests, PDF chat [S15] | IAP $109.99/mo, $334.99/yr; web "from $19.99" (conflict) [S15,S16] | 4.8★/4.3K | Content errors, auto-renewal [S15] |
| Geeky Medics | 1,300+ OSCE stations, 800–900 virtual patients (text/voice/video) | AI examiner marking, tutor, station generator; 100 credits/yr [S17,S18] | £49.99/yr [S18] | 4.5★/85; v9.1 video avatars [S17] | App and web billed separately; credit caps [S19] |
| iatroX | Adaptive UK Qbank, 1,114 cases, calculators | Socratic Tutor asks before answering, names misconception [S20,S21] | $29/mo, $99/yr [S20] | UKMLA/PLAB focus | Too few ratings; independent reviews NOT FOUND |
| Oncourse AI | Qbank, 40k cards, notes, cases; "replaces Anki/UWorld" (vendor) [S22] | Rezzy tutor [S23] | $7.99/wk, $14.99/mo, $79.99/yr [S22] | 4.8★/445 | "90% locked", hallucinations, caps [S23,S24] |
| AnkiMobile | SRS, FSRS, add-ons | None | $24.99 once [S25] | 68% of US students use Anki [S28]; ~376K downloads/mo (estimate) [S26] | 4.0★/2.3K (elsewhere 4.8: conflict) [S25]; complex UI, sync bugs, backlogs [S29] |
| OpenEvidence | Cited clinical Q&A, EvidenceGrade, CE | RAG with citations [S30] | Free, verified US users [S31] | 4.9★/12K | Misleading if prompt vague; US-only [S30,S31] |
| StudyFetch | Uploads, recordings → cards, quizzes, notes | Spark.E tutor, voice beta | IAP $19.99/mo, $96/yr, voice $7.49/hr; web $7.99–11.99 (conflict) [S32,S33] | 4.8★/14K | 10-chat free tier; tutor repeats [S32,S33] |
| SyncAI | Coach from uploads, missions, streaks, leaderboards [S34] | Generative coach | Free [S34] | Habit loop | No ratings; not medical [S34] |
| CampusLearn | Uploads → adaptive exercises, spoken "Explain It" [S35] | Adaptive tutor | Free + subscription [S35] | 4.8★/2.5K; 2.8M Android installs [S36] | Not medical; forced camera [S35] |
| Egypt: AUA MCQ, MedC | 18,000+ Qs, AI explanations [S37]; 50k MCQs, iOS app, 7k students [S38] | Explanations | 50 EGP/mo [S37]; MedC UNKNOWN | Local curricula | Nothing from own material |

The iPhone Medical top 25 is patient portals; no study app appears [S39]. Complete Anatomy is #2 on iPad Medical [S40].

## 2. Feature frequency (12 named apps)

| Feature | % apps | Category |
|---|---|---|
| MCQ bank or quizzes | 83% | Table stakes |
| AI tutor or Q&A | 83% | Table stakes |
| Flashcards/SRS | 75% | Common |
| Progress analytics | 67% | Common |
| Generation from own uploads | 42% | Common |
| Cited answers | 42% | Common |
| Study plan | 42% | Common |
| OSCE, virtual patients, cases | 33% | Common |
| Voice interaction | 33% | Common |
| Video 25%; streaks 17%; calculators 17% | <30% | Differentiator |
| Lecture recording; leaderboards | 8% each | Differentiator (leaderboards rejected) |
| 3D anatomy; draw-from-memory recall; 3D note graph; spoken-answer commute audio; Arabic UI | 0% | Gap |

Requested but absent: heart/lung sound library [S17]; print/offline study [S32]; typing instead of forced camera [S35]; simpler deck filtering and breaks that spare the algorithm [S29]; in-app highlighting and notes [S8].

## 3. Feature impact

| Feature | Downloads | Retention | Conversion | Word-of-mouth | Effort |
|---|---|---|---|---|---|
| Qbank | High | Med | High: >2,500 Qs → 256 vs 252 Step 2 CK [S27] | High | L curated; S own material |
| Spaced repetition | Med | High: SMD 0.78 [S41]; Step 1 +4–13 [S42] | Med | High | Have |
| AI tutor | High | Low–Med; churns 30% faster [S43] | High: RCT 82.5 vs 74.1 [S44]; 61% vs 36% recall, non-randomised [S45] | Med | M |
| Textbook | Med | Med | Med [S8] | Low | Have |
| OSCE with AI feedback | Med | Med | Med: d=0.74, pass 97% vs 80% [S46] | High | Have |
| Video | High | Med | Med | Med [S14] | L |
| Flashcard generation | High | Med | Med [S32] | High | Have |
| Lecture recording | Med | Med | Med; premium-only at StudyFetch [S32] | Med | Have; 54% watch live [S28] |
| Analytics | Low | Med | Med [S5] | Low | S |
| Study planning | Low | High | Med [S13,S15,S20] | Low | S |
| Streaks | Low | High: 7-day streak 2.4× return (vendor) [S49] | Low | Med | S; RCT effects heterogeneous [S47,S48] |
| Peer sharing | Med | Med | Low | High [S29,S34] | M |
| Offline | Low | High [S1] | Med | Med | M |
| Search | Low | Med [S15] | Low | Low | S |
| Notes/graph | Low | Med [S8] | Low | Low | Have |
| 3D anatomy | High | Low | Med | Med [S40] | XL |
| Case simulations | Med | Med | Med [S46] | Med | Have |
| Voice | Med | Med | Med [S17,S32] | High | Have |

## 4. UI/UX patterns

| Pattern | Where used | Evidence | Recommendation |
|---|---|---|---|
| Tab bar, ≤5 tabs | UWorld, AMBOSS, Osmosis | Hidden navigation halves discoverability [S50] | Today/Study/Library/Ideas/More; Today shows due cards, plan, resume |
| Dark mode, reader controls | AMBOSS added after complaints [S8] | Mixed evidence, positive comfort reports [S51,S52] | Follow system; Dynamic Type; highlighting |
| Calm clinical density | AMBOSS, OpenEvidence | Dense, uncluttered apps rate 4.9 [S8,S30] | Tables, flowcharts; 3D themes opt-in |
| Never lose an answer | UWorld fails | Lost-progress reviews [S1] | Local-first autosave |
| Quota transparency | Geeky, StudyFetch, Oncourse | Top complaint cluster [S19,S23,S32] | Visible meter; generous free tier |
| Streaming, explained waits | OpenEvidence | >4s hurts; explained waits read as deliberation [S53,S54] | First token <4s; label deep jobs |
| Inline citations | Osmosis, OpenEvidence, AMBOSS | Trust tracks verifiability [S11,S30,S31] | Deep-link to page/slide/minute |
| Fast first value | CampusLearn, StudyFetch | Onboarding lifts retention [S55,S56] | Example → own upload in session one |
| Backlog forgiveness | Anki add-ons only | 10k-review backlogs [S29] | Cap due; catch-up mode |
| Cross-device, accessibility | Anki sync bugs [S25]; accessibility UNKNOWN | Reviews only | Conflict-free sync; VoiceOver; RTL |

## 5. Student workflow

| Stage | Need | Current app | Frustration | Opportunity |
|---|---|---|---|---|
| Morning | Clear due cards | Anki | Backlog guilt [S29] | Due ring, catch-up, commute audio |
| Between classes | Quick lookup | AMBOSS, OpenEvidence | Paywall; US-only [S8,S31] | Textbook search, Ward pocket offline |
| Lecture | Capture, convert | Recorder, StudyFetch | Premium-only assistant [S32] | Record → set free |
| Study session | Questions on this week's slides | UWorld, AMBOSS | EGP 25–45k vs EGP 7,000 wage [S3,S57] | Own-material questions, Arabic |
| Rotations | OSCE rehearsal | Geeky Medics | Credits, double billing [S19] | Spoken examiner |
| Evening, group | Share sets | WhatsApp, Anki decks | Sharing implies ranking | Share links, no leaderboards |
| Exam prep | Coverage, readiness | UWorld assessments | Percentile anxiety | Blueprint tags (EMLE, UKMLA, USMLE) [S58] |

Egypt: 40% of Menoufia students did not know which app to download [S59].

## 6. Monetisation

- Students pay per exam window ($349–749) or annual bundles ($79–335); one-off $24.99 works for Anki; OpenEvidence is free (ad-funded per [S60]; JMLA silent: conflict) [S2,S25,S31].
- Education benchmarks, not medical-specific: yearly median $44.99, monthly $9.99; Year-1 LTV per payer $22.82; trials ≥17 days convert 42.5% vs 25.5%; AI apps earn 41% more per payer yet churn 30% faster [S43,S61]. Medical free-to-paid rate: UNKNOWN.
- Triggers: exam date, locked features. Churn: post-exam, auto-renewal disputes, hallucinations, hidden caps [S4,S15,S23].
- Refused: paying twice, weekly traps, charges on "trial" [S19,S22,S23].
- Egypt: local Qbanks charge 50 EGP/month [S37]; price Pro in EGP tiers.

## 7. AI-specific

- Used: cited Q&A, generation from uploads, explanations of missed questions [S30,S32,S37].
- Distrusted: ungrounded chat; hallucinations plus caps [S23]; quality "depends on query clarity" [S31]; students lack verification skills [S62]; passive use causes offloading [S45].
- Accuracy bar: GPT-4 scored 71.3% on 900 AMBOSS questions vs 54.4% for users [S63]; the per-question checker must show confidence and source passage.
- Latency: first token under 4s, stream, explain long jobs [S53,S54].
- Retention: AI alone churns [S43]; feed output into the SRS queue and daily plan.

## 8. Gap analysis

| Gap | Severity | Opportunity | Recommendation |
|---|---|---|---|
| No exam-date daily plan | High | 42% of apps ship one | Auto-plan from coverage and due cards |
| No visible source per item | High | Trust battleground | Deep-link every item |
| Offline not guaranteed | High | UWorld's top complaint | Local-first cards, questions |
| Trial, quota opacity | High | Longer trials convert 70% better | 14–30 day trial; meter |
| Reader ergonomics | Med | AMBOSS complaint | Highlight, Dynamic Type |
| Backlog handling | Med | Anki pain | Catch-up mode |
| Sharing, Anki export | Med | Word-of-mouth | Share links; .apkg export |
| Socratic mistake dialogue | Med | iatroX; d=0.74 | Ask-before-answer |
| Blueprint tagging | Med | Nobody maps own lectures | Tag generated Qs |
| No curated Qbank | Low | Own material is the moat | Complement UWorld; Study Lens imports mistakes |
| Mac/web companion | Low | Demand UNKNOWN | "Designed for iPad" on Mac, P2 |

Matching leaders: MCQ, SRS, cases, OSCE examiner, textbook, analytics, calculators, cloud generation, sync. Unique: ten modes from own material, Recall drawing, 3D idea graph, commute audio, look-alike duels, Arabic.

## 9. Implementation recommendations

| Feature | Why | UI/UX | Effort | Revenue | Priority |
|---|---|---|---|---|---|
| Exam-date daily plan | Retention lever | Today hub, plan ring | M | Retention | P0 |
| Source citations | Cited apps rate 4.8–4.9 | Tap → page/slide/minute | M | Conversion | P0 |
| Offline-first review | UWorld complaint | Local store, sync badge | M | Retention | P0 |
| Long trial, quota meter | RevenueCat data | Remaining generations shown | S | Conversion | P0 |
| Catch-up mode | Anki backlog | Cap due, rescue button | S | Retention | P1 |
| Reader controls | AMBOSS complaint | Highlight, Dynamic Type, RTL | S | Retention | P1 |
| Share-a-set, .apkg export | Word-of-mouth | Share sheet, no ranks | M | Downloads | P1 |
| Socratic mistake dialogue | Feedback evidence | Ask-before-answer chips | M | Conversion | P1 |
| Streaming responses | Latency research | First token <4s | S | Retention | P1 |
| Blueprint tagging | Egypt-first edge | Coverage by blueprint | M | Conversion | P2 |

## Top 10 features to add

1. Exam-date daily plan (P0): commonest missing retention feature [S13,S15,S20].
2. Source citation on every item (P0): all 4.8–4.9★ AI apps cite [S11,S30]; GPT-4 hits 71% [S63].
3. Offline-first review (P0): loudest UWorld complaint [S1].
4. 14–30 day trial with quota meter (P0): 42.5% vs 25.5% conversion [S43].
5. Catch-up mode (P1): backlogs drive Anki burnout [S29].
6. Highlighting and Dynamic Type (P1): AMBOSS lost ratings here [S8].
7. Share links and .apkg export (P1): Osmosis exports to Anki [S11].
8. Socratic ask-before-answer (P1): AI feedback lifted OSCE pass 80%→97% [S46].
9. Streaming with explained waits (P1): >4s degrades perception [S53].
10. EMLE/UKMLA/USMLE blueprint tags (P2): no competitor maps own lectures to a blueprint [S58].

## Top 5 features to avoid

1. Leaderboards, percentiles, challenges: owner-rejected; effects heterogeneous [S47,S48].
2. Curated video library: L effort, owned by Osmosis/Lecturio, "oversimplified" [S14].
3. 3D anatomy atlas: 0/12 apps; Complete Anatomy owns it [S40].
4. Generic open-web chatbot: churns 30% faster [S43]; hallucination complaints [S23].
5. Weekly plans, AI micro-credits, hard paywalls: one-star magnets [S19,S23,S32].

# Part B. The medical lens

## B1. How each leader keeps content accurate, and what that teaches

| App | How content is made and checked | What works medically | What fails medically | What is missing medically |
|---|---|---|---|---|
| UWorld | Licensed, practising physicians write every question and explanation with an editorial team. Explanations walk through why each distractor is wrong and end with an "educational objective" (vendor) [S2][S64] | Exam-faithful items with explained distractors; >2,500 questions completed is associated with 256 vs 252 on Step 2 CK [S27] | US-only frame (US guidelines and exams); no offline mode; lost progress [S1] | The student's own lectures; Egyptian (EMLE) and UK frames; a visible source for each claim in an explanation (UNKNOWN in-app) |
| AMBOSS | 80+ US-trained physicians. One writes, a second reviews for accuracy, a copy editor checks logic, a third physician does a final review. Each article carries a "last edited" date and its references; content is continuously updated (vendor) [S65] | Dated, referenced articles; a cited AI Mode [S5]; library rated 4.9★ [S8] | No outcome studies [S10]; paywall anger [S8] | The student's own material; Arabic; jurisdictions beyond the US frame |
| Osmosis (Elsevier) | Osmosis AI answers with citations from Elsevier texts; it generates cards, quizzes and mnemonics [S11] | Answers grounded in vetted textbooks | Called "oversimplified" [S12][S14] | Whether generated cards are checked against current evidence: UNKNOWN |
| Lecturio | Lexi AI: natural-language search, summaries, custom tests, PDF chat; NEJM Healer simulations [S15] | Case simulations | Content errors reported in reviews [S15] | Visible verification of what Lexi generates |
| Geeky Medics | 1,300+ OSCE stations written by clinicians, not generated by AI, each with candidate, patient and examiner instructions and a checklist. ~900 virtual patients; an AI examiner completes the mark scheme and writes feedback; custom stations generated on demand (vendor) [S17][S66] | Real checklists with AI as examiner. AI feedback in OSCE practice: d = 0.74, pass rate 97% vs 80% [S46] | Credit caps and double billing [S19]. Generated custom stations are not clinician-written | Arabic- and Egyptian-dialect patients; a heart and lung sound library (requested) [S17] |
| iatroX | Citation-first retrieval over NICE, CKS, BNF/BNFC and SIGN; a UKCA-marked Class I medical device (vendor) [S67]. Its Socratic tutor asks before answering and names the misconception [S21] | Answers grounded in one jurisdiction's guidelines, with citations; Socratic feedback | Few ratings; no independent review found [S20] | Anything outside the UK frame; the student's own material |
| Oncourse AI | Qbank, 40k cards, the "Rezzy" tutor; "replaces Anki/UWorld" (vendor) [S22][S23] | Breadth | Hallucinations reported, "90% locked", caps [S23][S24] | Visible verification: its own reviews show what unverified generation costs in trust |
| AnkiMobile | A spaced-repetition scheduler with FSRS; the content is whatever the deck holds [S25] | Spacing works: SMD 0.78 [S41]; Step 1 +4–13 points [S42]; 68% of US students use Anki [S28] | It has no verification layer, so a wrong card is scheduled as faithfully as a right one; backlogs [S29] | Checking content before it is scheduled |
| OpenEvidence | Cited answers from licensed journals: NEJM (Feb 2025, back to 1990) and the JAMA Network (Jun 2025), among 300+ titles (secondary report) [S68]. Announced 100% on USMLE-style questions (vendor, Aug 2025) [S70] | A citation on every answer; free for verified US clinicians [S30][S31] | On 100 complex subspecialty board scenarios: 34% accuracy for the quick search and 41% for Deep Consult, with 77% and 72% concordance between two evaluators (preprint) [S71]. Misleading when the prompt is vague; US-only [S31] | A study workflow (questions, spacing); non-US guidelines; repeatability that users can see |

## B2. What none of them does, medically
1. **Checks the student's own lectures against current evidence with a visible chain.**
   - The leaders verify their own editorial content: AMBOSS with three physicians, UWorld with physicians plus editors [S65][S64].
   - None verifies what the student brings: slides, recordings, shared decks.
2. **Explains differences between guideline systems.**
   - iatroX is UK-only [S67]; UWorld and OpenEvidence are US-framed [S2][S31].
   - No app tells a student "your exam follows this system, the other one says that".
3. **Flags superseded or retracted sources to the student.**
   - AMBOSS shows "last edited" dates [S65]. No app found shows retraction or supersession at the item level.
4. **Reports accuracy on hard, multi-step cases, with repeatability.**
   - Exam-style scores overstate real accuracy: 100% on USMLE-style questions vs 34–41% on subspecialty cases [S70][S71].
   - None publishes how often its answers agree across runs.
5. **Says "not established".**
   - Every AI tutor answers. The only Socratic restraint found is iatroX asking before it answers [S21].
6. **Examines in the patient's language.**
   - No OSCE app has Arabic or Egyptian-dialect patients.

## Sources

S1 https://apps.apple.com/us/app/uworld-medical-exam-prep/id991621303
S2 https://medical.uworld.com/usmle/usmle-step-1/
S3 https://apps.apple.com/eg/app/uworld-medical-exam-prep/id991621303
S4 https://www.trustpilot.com/review/uworld.com
S5 https://www.amboss.com/us/students
S7 https://support.amboss.com/hc/en-us/articles/32345265867921-AMBOSS-Pricing
S8 https://apps.apple.com/us/app/id1169487026
S9 https://apps.apple.com/us/app/id1169403493
S10 https://medaiverdict.com/tools/amboss
S11 https://www.prnewswire.com/news-releases/elsevier-launches-study-companion-osmosis-ai-to-transform-how-medical-students-learn-302701471.html
S12 https://apps.apple.com/us/app/osmosis-med-school-study-app/id646540641
S13 https://www.osmosis.org/plans/md
S14 https://dcdm.doody.com/2026/09/a-review-of-osmosis/
S15 https://apps.apple.com/us/app/lecturio-medical-education/id1067957933
S16 https://www.lecturio.com/pricing
S17 https://apps.apple.com/us/app/geeky-medics-osce-revision/id1050169390
S18 https://app.geekymedics.com/purchase/bundles/
S19 https://apps.apple.com/us/app/geeky-medics-osce-revision/id1050169390?see-all=reviews
S20 https://apps.apple.com/us/app/iatrox/id6744710677
S21 https://www.iatrox.com/tutor
S22 https://getoncourse.ai/usmle/pricing/
S23 https://apps.apple.com/us/app/usmle-step-1-prep-oncourse-ai/id6504909373
S24 https://play.google.com/store/apps/details?id=com.oncourse.oncourselearning&hl=en_US
S25 https://apps.apple.com/us/app/ankimobile-flashcards/id373493387 vs https://www.mindomax.com/ankimobile-flashcards-app-store-ranking-march-2026
S26 https://appstor.io/app/ankimobile-flashcards
S27 https://doi.org/10.1186/s12909-024-06414-x
S28 https://doi.org/10.1177/23821205241228455
S29 https://www.usmleprivateers.com/journal-of-reddit/medicalschool/2025/march/anki-14032025
S30 https://apps.apple.com/us/app/openevidence/id6612007783
S31 https://doi.org/10.5195/jmla.2026.2247
S32 https://apps.apple.com/us/app/studyfetch-make-learning-easy/id6663574866
S33 https://dupple.com/reviews/study-fetch
S34 https://apps.apple.com/us/app/syncai/id6755302735
S35 https://apps.apple.com/us/app/campuslearn-ai-companion/id6449232300
S36 https://play.google.com/store/apps/details?id=ai.szl.mobileapp&hl=en_US
S37 https://mcqs.medicine-way.com/
S38 https://medci.org/
S39 https://apps.apple.com/us/iphone/charts/6020
S40 https://apps.apple.com/us/ipad/charts/6020
S41 https://doi.org/10.1111/tct.70353
S42 https://doi.org/10.1007/s40670-026-02643-5
S43 https://www.revenuecat.com/state-of-subscription-apps
S44 https://doi.org/10.1186/s12909-026-09469-0
S45 https://doi.org/10.7759/cureus.114362
S46 https://doi.org/10.2196/90368
S47 https://doi.org/10.1097/jnr.0000000000000772
S48 https://doi.org/10.2196/84310
S49 https://www.strivecloud.io/duolingo-gamification-explained
S50 https://www.nngroup.com/articles/hamburger-menus/
S51 https://arxiv.org/pdf/2409.10895
S52 https://ceur-ws.org/Vol-3575/Paper15.pdf
S53 https://dl.acm.org/doi/fullHtml/10.1145/3640794.3665550
S54 https://arxiv.org/html/2604.06183v1
S55 https://www.diva-portal.org/smash/get/diva2:1985428/FULLTEXT01.pdf
S56 https://growth-onomics.com/mobile-app-retention-benchmarks-by-industry-2026/ (education D30 2.1%, unsourced; others quote ~8%, UNVERIFIED)
S57 https://employsome.com/blog/minimum-wage-egypt/
S58 https://en.wikipedia.org/wiki/Egyptian_Medical_Licensing_Examination
S59 https://doi.org/10.1186/s12909-024-05216-5
S60 https://www.verahealth.ai/blog/best-ai-tools-medical-students-2026
S61 https://www.revenuecat.com/state-of-subscription-apps-2026-education
S62 https://doi.org/10.2196/94951
S63 https://pmc.ncbi.nlm.nih.gov/articles/PMC11756343
S27,S28,S31,S41,S42,S44–S48,S59,S62 retrieved via PubMed.

Added in revision 2 (vendor pages say what the vendor claims; S71 is a preprint):
- S64 https://medical.uworld.com/usmle/features/sample-questions/ (physician authors, editorial team, distractors explained, educational objective; vendor)
- S65 https://support.amboss.com/hc/en-us/articles/6365572887188-AMBOSS-Content-Policy ; https://support.amboss.com/hc/en-us/articles/360044236212-About-AMBOSS (80+ physicians, three-physician review, "last edited" dates; vendor)
- S66 https://geekymedics.com/osce-stations/ ; https://geekymedics.com/ai-simulated-patients-for-osce-preparation/ (clinician-written stations, AI examiner; vendor)
- S67 https://www.iatrox.com/compare/nice-cks-vs-iatrox (NICE, CKS, BNF, SIGN retrieval; UKCA Class I; vendor)
- S68 https://research.contrary.com/company/openevidence (NEJM Feb 2025 and JAMA Network Jun 2025 agreements; secondary report)
- S70 https://www.openevidence.com/announcements/openevidence-creates-the-first-ai-in-history-to-score-a-perfect-100percent-on-the-united-states-medical-licensing-examination-usmle ; https://www.fiercehealthcare.com/ai-and-machine-learning/openevidence-ai-scores-100-usmle-company-offers-free-explanation-model (vendor claim, Aug 2025)
- S71 Jagarapu, Babata, Chamarthi, Hoyt. The accuracy and repeatability of OpenEvidence on complex medical subspecialty scenarios: a pilot study. medRxiv preprint, posted 4 Dec 2025, https://doi.org/10.64898/2025.11.29.25341091 (not peer reviewed)

## MEDICAL IMPROVEMENTS THIS UNLOCKS

Each item says what the student gets, which leader or evidence it comes from, and its status in Stethoscore.

1. **The leaders' editorial standard, done by machine and person together.**
   - AMBOSS: writer → second physician → copy editor → final physician [S65]. UWorld: physician writers plus an editorial team [S2][S64].
   - Stethoscore's equivalent:
     - generator (the writer);
     - two checkers from different model families (the second physician);
     - rules and the claim gate (the copy editor);
     - a person's review for high-stakes items (the final physician).
   - Status: the first three are built. The person's review exists only in the question-bank pilot; the app has none.
2. **Every distractor explained, plus one "key point".**
   - UWorld explains why each wrong option is wrong and closes on an educational objective [S64].
   - Every generated MCQ should do both, with the key point checked like any other claim.
   - Status: the question-bank prompt requires the distractor explanations; the app's MCQ prompt and the key point need adding.
3. **"Checked on <date> against <sources>" on every item.**
   - AMBOSS dates and references every article [S65]. Items show when they were last verified and against what.
   - They are re-checked when a source changes; the supersession and retraction sweeps are in the Islamic brief.
   - Status: new.
4. **Answers grounded in the student's guideline system.**
   - iatroX grounds every answer in NICE, CKS, BNF and SIGN [S67]. Stethoscore picks the guideline set from the student's exam (EMLE, UK, US) and explains the differences instead of marking them as errors.
   - Status: new. The exam catalogue exists.
5. **OSCE marking against a fixed checklist.**
   - Geeky Medics: clinician-written stations, with AI only as the examiner [S66]. AI feedback lifted pass rates from 80% to 97% (d = 0.74) [S46].
   - Generated stations are labelled as generated, pass the accuracy checks, and are marked against a fixed checklist structure, not free judgement.
   - Status: partly built (the OSCE mode and its checklist model exist); the provenance label is new.
6. **Hard-case and repeatability benchmarks for our own checker.**
   - Exam-style scores overstate accuracy: 100% vs 34–41% [S70][S71].
   - The checker bench adds hard, multi-step subspecialty cases. Each check runs twice, and when the two runs disagree the item stays Unverified.
   - Status: checker-bench.yml exists; hard cases and repeatability are new.
7. **A tutor that asks first and can say "not established".**
   - iatroX's tutor asks before answering and names the misconception [S21].
   - Ours does the same, and abstains when the evidence does not settle the question.
   - Status: new (the abstention rule is in the Islamic brief).
8. **Imported decks checked before they are scheduled.**
   - Anki schedules whatever a deck holds [S25]. Imported cards (.apkg) go through the accuracy check, and only verified cards count as settled in review.
   - This stops a shared deck's errors spreading at the speed spaced repetition makes them stick [S41][S42].
   - Status: import exists; the verification gate on import is the Task 8 feature "SRS with verification gate".
9. **Every claim cited, deep-linked to the slide or minute and to the evidence.**
   - The highest-rated AI apps cite their answers [S11][S30][S65].
   - Status: partly built (the evidence citations); the per-claim chain and the deep links are new.
10. **Spacing kept, but only for verified content.**
    - Spacing's effect is large (SMD 0.78; Step 1 +4–13) [S41][S42], which is exactly why only verified items should be spaced as settled.
    - Status: FSRS is built; the verified-only rule is new.
11. **OSCE patients who speak Arabic and Egyptian dialect.**
    - No competitor has them, yet history-taking in the patient's own language is the clinical skill Egyptian students are examined on.
    - Status: new. The app's voice and OSCE pieces exist to build on.
12. **A heart and lung sound library from licence-checked recordings.**
    - Requested by students in Geeky Medics' reviews [S17].
    - Only openly licensed recordings, checked against the same allowlist as the question bank.
    - Status: new.
