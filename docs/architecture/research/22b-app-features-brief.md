# §22b Medical-Student AI App Features — Research Brief

2026-09-30. Ratings: US App Store today unless stated. [S#] = sources. "Vendor" = developer claim. Reddit is blocked here; sentiment comes from App Store reviews, Trustpilot and a Reddit digest [S29].

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
