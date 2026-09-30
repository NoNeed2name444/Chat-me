# §22c Student AI Helper App Features — Research Brief

2026-09-30. US App Store ratings today unless stated. [S#] = sources; "vendor" = developer claim; §22b = sister brief (Stethoscore facts, rejected features, medical apps; StudyFetch, SyncAI and CampusLearn are profiled there). Reddit blocked; sentiment from App Store, Trustpilot, press.

## 1. Market landscape

iPhone Education chart today: 1 Duolingo, 2 Gauth, 3 Learna, 8 Quizlet, 9 Gizmo; photo-solvers and AI flashcard apps dominate [S1].

| App | Features | AI | Pricing | Strengths | Weaknesses |
|---|---|---|---|---|---|
| ChatGPT Study Mode (OpenAI) | Toggle inside chat; scaffolded steps, knowledge checks | Socratic prompts, memory personalisation | Free on all tiers since 29 Jul 2025 [S2] | Zero cost; on/off mid-chat | No peer-reviewed evaluation [S3]; ungrounded |
| Gemini Guided Learning, NotebookLM (Google) | Step-by-step, diagrams, videos, quizzes; notebook flashcards, quizzes, Audio Overviews | LearnLM-tuned; source-grounded | Free; AI Pro free one year for students in 5 countries [S4,S5] | Multimodal, cited notebooks | Efficacy is vendor claim; Socratic output most prompt-sensitive across LLMs [S6] |
| Quizlet | Flashcards, Learn, Test, groups, lecture audio to guide | Magic Notes, Ask Quizlet; Q-Chat killed Jun 2025 [S7] | IAP $9.99/mo, $44.99/yr; web $7.99/$35.99 (conflict) [S8,S9] | 4.8★/1.1M | Learn paywalled, "cash grab", screen-reader failures [S8]; Trustpilot: ads, auto-renew [S10] |
| Chegg Study | 60M solutions, expert Q&A, scanner, flashcards | "AI solutions backed by experts" | $15.99/mo [S11] | 4.7★/205K | Revenue −51% YoY; traffic lost to AI Overviews and genAI [S12,S13]; Trustpilot 2.2 [S14]; 2-device cap |
| Photomath (Google) | Camera math, multiple methods, graphs | Animated steps; textbook solutions paid | $5.99–9.99/mo [S15] | 4.8★/733K | Geometry errors; cheating stigma; users ask for practice and quizzes [S15] |
| Socratic (Google) | Photo Q&A | — | — | — | NOT FOUND: removed, folded into Lens 2025 [S16] |
| Brainly | Community Q&A, Scan to Solve, live tutors | AI tutor | Plus $2–9.99/mo; Tutor $29.99/mo [S17] | 4.7★/284K; points, badges | 20 Q/mo caps; tutors quit sessions; auto-renew [S17,S18] |
| Khanmigo (Khan Academy) | Tutor over KA content, writing coach, teacher tools | Withholds answers | $4/mo, $44/yr; teachers free [S19] | KA app 4.6★/112K, free, offline [S20] | Used a third of days; "non-event for most students"; gains from KA, not AI [S21] |
| Duolingo (Max) | Streaks, leagues, hearts, Video Call, Roleplay | LLM conversation; Explain My Answer now free | Super $9.99–119.99 [S22]; Max $29.99/mo, $168/yr (price guide) [S23] | 4.7★/5.5M; 58.7M DAU, 12.7M paid, CURR 84% [S24] | Streak anxiety, Max price [S22] |
| Gauth (ByteDance) | Photo solve, expert network, notes to study tools | Live voice tutor with whiteboard | Plus $11.99/mo, $99.99/yr [S25] | 4.8★/1.5M; #2 | Voice "creepy"; cheating, data-privacy criticism [S25,S26] |
| Studdy | Scan, chat, practice, videos | "98% accuracy", GPA +1.3 (vendor) | Free 5 scans/day; from $6.99/wk [S27] | 4.8★/13K | "MORE SNAPS" cap anger [S27] |
| Knowt | Free Learn mode, SRS, Quizlet import, PDF/video to cards | Kai feedback, podcasts | Ultra $9.99/wk–$149.99/yr [S28] | 4.7★/11K; 4M students (vendor) | No images in notes; video tool Chrome-only; notifications [S28] |
| Gizmo | Import Quizlet/Anki/PDF/YouTube, games, leaderboards | Explanations from uploads | $6.99–309.99 [S29] | 4.8★/14K; ADHD praise | SRS repeats too often; off-material questions [S29] |
| Class Companion | Rubric feedback on writing, hints, TTS, translation | Ditto tutor, AI-writing flags | Teachers free; school quotes [S30] | 25,000 schools (vendor) | Teacher-side; no student app |
| Luna AI | Phone-call tutor: notes to spoken quiz; languages | Voice LLM | 20 free min/mo; no app [S31] | Blind, commuter access | iOS app NOT FOUND; ratings UNKNOWN |
| EduAI; RIACT | Prototypes: adaptive paths, Feynman board [S32]; study-log burnout rules, LLM coach, no user study [S33] | | Free | Responsible-AI design | Not shipping |
| ElevatED, Zuno, PlanIC, LearnEscape | NOT FOUND (Zuno exists only as a fitness app) [S34] | | | | |

## 2. Feature frequency (18 profiled apps)

| Feature | % apps | Category |
|---|---|---|
| AI tutor chat; freemium with capped free tier | 94% | Table stakes |
| Progress tracking | ~50% | Common |
| Flashcard generation from uploads 44%; photo or math solving 39% | | Common |
| Voice; grounding in own files; test prep; sharing; writing help; points or streaks | 33–39% | Common |
| Lecture audio or video to study material | 28% | Differentiator |
| Spaced repetition; offline | 22% | Differentiator |
| Human experts; leaderboards; study plans | 17% | Differentiator (leaderboards rejected, §22b) |
| Burnout signals; career tools | 6% | Differentiator |
| Exam-date plan; deep links to slide or minute; drawing recall; medical modes | 0% | Gap |

Requested but missing: practice in Photomath [S15]; more free scans [S27]; images in notes, iPad parity [S28]; tunable SRS [S29]; screen readers, text size [S8]; streak forgiveness [S22]; no device caps [S11].

## 3. Feature impact

| Feature | Downloads | Retention | Conversion | Word-of-mouth | Effort |
|---|---|---|---|---|---|
| Photo homework help | Very high [S1,S25] | Med | High: scan caps sell [S27] | High, cheating stigma [S26] | M |
| AI tutor chat | High | Low–Med [S21]; AI apps churn faster (§22b) | Med | Med | M |
| Flashcard generation | High [S28,S29] | Med | Med [S8] | High | Have |
| Quizzing, SRS | Low | High: g=0.50 over 50K students [S35]; SRS SMD 0.78 (§22b) | Med | Med | Have |
| Gamification | Med | High but element-dependent: badges +2.4% DAU, streak wager +14% D7 (vendor) [S36,S37]; g=.46 cognitive, reward-only weakest [S38]; nursing SMD 0.81, I² 82–95% [S47] | Med | High | S–M |
| Daily goal, plan | Low | High [S37] | Med | Low | S |
| Voice tutor | Med | Med | High: Max priced on Video Call [S23,S24] | High | Have |
| Human experts | Med | Med | High price, low trust [S14] | Low | XL |
| Lecture capture | Med | Med | Med | Med | Have |
| Offline | Low | High [S20] | Med | Med | M |
| Analytics, notes, search, tasks | Low | Med | Low | Low | S |
| Writing, math, language | High generally; low medical relevance | | | | — |
| Burnout, well-being | Low | UNKNOWN | Low | Med | S |
| Career | Low; Chegg pivot [S12] | | | | — |

## 4. UI/UX patterns

| Pattern | Where used | Evidence | Recommendation |
|---|---|---|---|
| Value before sign-up | Duolingo | Delayed sign-up +20% DAU (vendor A/B) [S36] | Generate from a sample lecture first |
| Camera primary, typing kept | Gauth, Photomath, Brainly, Studdy | Chart dominance [S1]; forced-camera complaint (§22b) | "Snap a slide" beside upload |
| Tutor-mode toggle | ChatGPT, Gemini | On/off mid-chat [S2,S4] | "Guide me / Just answer" chip |
| Free core mode | Knowt vs Quizlet | Paywalled Learn earns 1★ [S8]; Knowt's wedge [S28] | Review free; meter generation |
| Forgiving streaks | Duolingo | Amulet +4% D14, 5% fewer lost streaks (vendor) [S37]; anxiety reviews [S22] | Private streak, freezes, never ranked |
| Flow: clear goal, matched difficulty, instant feedback | Duolingo, Khan mastery | Flow–performance r=.49; goals r=.61, feedback r=.52, challenge–skill r=.49; 108 studies, correlational [S39] | Session goal card; adaptive MCQ difficulty; per-item feedback |
| One tuned notification | Duolingo 23.5h cadence, copy +5% DAU (vendor) [S36] | Knowt "intrusive" [S28] | One due-card nudge at chosen hour |
| Multimodal answers | Gemini | Diagrams, videos inline [S5] | Pull figures from user slides |
| Accessibility | Quizlet fails [S8]; Luna call-in [S31]; Class Companion TTS [S30] | Reviews | VoiceOver, Dynamic Type, spoken quiz |
| Tab bar, dark mode, density, streaming, citations | §22b | | |

## 5. Student workflow (non-medical)

| Stage | Need | Current app | Frustration | Opportunity |
|---|---|---|---|---|
| Morning | Due cards, streak | Quizlet, Knowt, Duolingo | Guilt, paywalled Learn [S8,S22] | Free due-ring with freeze |
| Between classes | Quick answer | ChatGPT, Gemini, Gauth | Ungrounded; stigma [S26] | Answer citing own slide |
| Study session | Deep help | Chegg, Photomath, Study Mode | Wrong answers, billing [S14]; answers without struggle [S21] | Guide toggle; why-wrong |
| Evening | Group, feedback | Brainly, Quizlet groups, Class Companion | Caps, tutor drop-outs [S17] | Share sets without ranks; feedback on OSCE notes |
| Exam prep | Tests, weak spots | Quizlet Test, Knowt guides, Khan mastery | No exam-date plan | Plan plus blueprint coverage (§22b) |

## 6. Monetisation

- Ladder: $4 Khanmigo; $5.99–9.99 Photomath, Quizlet; $11.99 Gauth; $15.99 Chegg; $29.99 Brainly Tutor, Duolingo Max; weekly $6.99–9.99 at Studdy, Knowt, Gizmo [S15,S19,S23,S25,S27–S29].
- Conversion: Duolingo 12.7M paid of 140.6M MAU = 9% (computed) [S24]; Quizlet, Brainly, Chegg, Gauth UNKNOWN; education medians in §22b.
- Paid for: unlimited scans and generation, voice AI, offline, no ads, human help. Refused: paywalled basics [S8], auto-renewal [S10,S14,S18], caps failing mid-task [S17,S27].
- Triggers: hitting the cap, exam week, voice. Churn: free substitutes; Study Mode free, AI Pro free for students; Chegg −51% [S2,S5,S12].
- LTV: UNKNOWN for every profiled app.

## 7. AI-specific

- Used: photo-solve, generation from notes, explanations; 86% of students use AI, 54% feel unprepared [S40]; 26% of US teens used ChatGPT for schoolwork in 2024, double 2023 [S41].
- Ignored: Khanmigo [S21]; Q-Chat withdrawn after two years, reason UNKNOWN [S7].
- Distrusted: wrong answers ("6×3=45" [S20]; Chegg "AI errors" [S14]); "creepy" voice [S25]; ByteDance data [S26].
- Works when guided: custom AI tutor beat active-learning class in an RCT [S42]; +0.27 SD, persisting only for "augmentation" users [S43]; nursing anatomy RCT (§22b). Contested: one ChatGPT meta-analysis was retracted [S44].
- Risks: Socratic prompting is the least stable strategy [S6]; benchmark harm rises 17.7%→77.8% over multi-turn dialogue [S45].
- Trust recovery: warning 252 students the tutor can err raised hint requests [S46]; pair with §22b citations and confidence.
- Latency: §22b.

## 8. Cross-pollination

| Finding | Transfers? | Adaptation | Recommendation |
|---|---|---|---|
| Camera-first help | Partly | Slide, question, ECG to grounded explanation; typing kept | Snap-to-explain, P1 |
| Tutor toggle | Yes | Fixed Socratic templates; ask-before-answer (§22b) | P1 |
| Free core mode | Yes | Review free; meter generation | P0 |
| Forgiving streaks | Partly | Private; no leagues, ranks | P1 |
| Value before sign-up | Yes | Sample-lecture demo | P0 |
| Challenge, narrative gamification | Yes | Adaptive difficulty, case narratives; no points economy | P1 |
| Human expert marketplace | No | Faculty do it; liability; XL | Avoid |
| Community Q&A with ranks | No | Share sets only | Avoid |
| Voice tutor | Yes | Opt-in, labelled; Luna's quiz-aloud | Have; add quiz-aloud |
| Burnout detection | Partly | Rules, observations, opt-in, no diagnosis [S33] | P2 |
| Generic chat vs free giants | No | Compete on own material, blueprint, offline | Avoid |
| Weekly pricing; career or fitness bundles | No | Zuno, PlanIC NOT FOUND; Chegg pivot | Avoid |

## 9. Gap analysis

| Gap | Severity | Opportunity | Recommendation |
|---|---|---|---|
| Sign-up before value | High | +20% DAU pattern [S36] | Demo first |
| Free tier undefined | High | Knowt wedge; cap complaints | Free review, metered generation |
| No camera entry | Med | Top-2 chart apps camera-first | Snap-to-explain |
| No tutor toggle | Med | Both AI giants ship one | Guide/Answer |
| Fixed MCQ difficulty | Med | Flow antecedents [S39] | Adaptive sessions |
| No streak forgiveness | Med | Vendor A/B [S37] | Private streak, freezes |
| No error disclosure | Med | [S46] | "May be wrong" plus report |
| Accessibility UNKNOWN | Med | Quizlet failures [S8] | VoiceOver, spoken mode |
| Notifications undesigned | Low | [S36] | One nudge |
| Well-being | Low | RIACT [S33] | Rules-based insight |

Matches: AI tutor, generation from uploads, SRS, test prep, voice, analytics. Unique against all 18: OSCE examiner, cases, drawing recall, 3D graph, Arabic, offline-first (§22b). Win: grounded medicine plus these patterns while the giants stay generic.

## 10. Implementation recommendations

| Feature | Why | UI/UX | Effort | Revenue | Priority |
|---|---|---|---|---|---|
| Demo before sign-up | [S36] | Sample lecture to cards in 60s, then account | S | Downloads | P0 |
| Free review, generation meter | [S8,S28] | Meter on Today; upsell at cap | S | Conversion | P0 |
| Guide/Answer toggle | [S2,S4,S6] | Sticky chip in tutor bar | M | Retention | P1 |
| Snap-to-explain | [S1,S25] | Camera in Study Lens; typing kept | M | Downloads | P1 |
| Adaptive difficulty, goal card | [S35,S39] | Session goal; per-item feedback | M | Retention | P1 |
| Private streak, freeze | [S22,S37] | Today ring; two freezes a month; no ranks | S | Retention | P1 |
| Error disclosure, report | [S45,S46] | Banner; Report regenerates with source | S | Trust | P1 |
| VoiceOver, spoken quiz | [S8,S31] | Audit; "quiz me aloud" | M | Retention | P1 |
| One tuned nudge | [S28,S36] | Chosen hour, due count | S | Retention | P2 |
| Study-load insight | [S33] | Weekly observation, opt-in | S | — | P2 |

## Top 10 features to add

1. Demo before sign-up (P0): Duolingo's delayed sign-up lifted DAU 20% (vendor) [S36].
2. Free review plus visible generation meter (P0): Knowt grows on free Learn while Quizlet's paywall earns 1★ [S8,S28]; caps are the loudest complaint [S17,S27].
3. Guide/Answer toggle (P1): both AI giants ship it [S2,S4]; use fixed templates because Socratic output is prompt-fragile [S6].
4. Snap-to-explain (P1): the two biggest study apps are camera-first [S1,S15,S25].
5. Adaptive difficulty with a session goal (P1): flow antecedents r=.49–.61 [S39]; quizzing g=0.50 [S35].
6. Private streak with freezes (P1): forgiveness raised retention (vendor) [S37]; anxiety when absent [S22].
7. Error disclosure and one-tap report (P1): honesty about fallibility increased help-seeking [S46]; multi-turn harm grows [S45].
8. VoiceOver and quiz-aloud (P1): Quizlet's accessibility failures [S8]; Luna's blind-student mode [S31].
9. One tuned daily nudge (P2): copy alone moved DAU 5% (vendor) [S36]; Knowt shows the downside [S28].
10. Rules-based study-load insight (P2): RIACT's observation-not-diagnosis design [S33].

## Top 5 features to avoid

1. Human tutor marketplace: Chegg −51%, Trustpilot 2.2, Brainly drop-outs [S12,S14,S17].
2. Generic ungrounded chatbot: free Study Mode and Gemini own it [S2,S5]; Khanmigo shows low uptake [S21].
3. Leaderboards, ranks, points economies: owner-rejected (§22b); reward-only elements weakest [S38].
4. Weekly plans, paywalled basics, auto-renew traps: 1★ magnets across Quizlet, Chegg, Brainly [S8,S10,S14,S18].
5. Answer-engine positioning and data-hungry permissions: Gauth's cheating and privacy backlash [S26]; ElevatED, Zuno, PlanIC, LearnEscape bundles NOT FOUND.

## Sources

S1 https://apps.apple.com/us/iphone/charts/6017
S2 https://venturebeat.com/ai/chatgpt-just-got-smarter-openais-study-mode-helps-students-learn-step-by-step (openai.com blocked)
S3 https://learnworkecosystemlibrary.com/initiatives/chatgpt-study-mode/
S4 https://blog.google/products-and-platforms/products/education/guided-learning/
S5 https://blog.google/products/gemini/new-gemini-tools-students-august-2025/
S6 https://arxiv.org/abs/2603.26673
S7 https://quizgecko.com/blog/best-q-chat-alternative
S8 https://apps.apple.com/us/app/quizlet-ai-powered-flashcards/id546473125
S9 https://aistudymaster.com/quizlet-plus-pricing-2026/
S10 https://www.trustpilot.com/review/www.quizlet.com
S11 https://apps.apple.com/us/app/chegg-study-homework-help/id385758163
S12 https://www.sec.gov/Archives/edgar/data/0001364954/000136495426000085/a9901-financialresultsq220.htm
S13 https://www.sec.gov/Archives/edgar/data/0001364954/000136495426000048/chgg-20260331.htm
S14 https://uk.trustpilot.com/review/www.chegg.com
S15 https://apps.apple.com/us/app/photomath/id919087726
S16 https://en.wikipedia.org/wiki/Socratic_(Google)
S17 https://apps.apple.com/us/app/brainly-ai-homework-helper/id745089947
S18 https://www.trustpilot.com/review/brainly.com
S19 https://www.khanmigo.ai/learners
S20 https://apps.apple.com/us/app/khan-academy/id469863705
S21 https://www.chalkbeat.org/2026/08/25/ai-tutoring-students-khanmigo-khan-academy-engagement-study/
S22 https://apps.apple.com/us/app/duolingo-language-lessons/id570060128
S23 https://lingoly.io/duoling-max-cost/
S24 https://www.sec.gov/Archives/edgar/data/0001562088/000162828026053299/q2fy26duolingo6-30x26share.htm
S25 https://apps.apple.com/us/app/gauth-ai-study-companion/id1542571008
S26 https://www.forbes.com/sites/emilybaker-white/2024/04/03/gauth-bytedance-tiktok-homework-app/
S27 https://apps.apple.com/us/app/studdy-ai-tutor-math-solver/id6450114499
S28 https://apps.apple.com/us/app/knowt-ai-flashcards-notes/id6463744184
S29 https://apps.apple.com/us/app/gizmo-ai-tutor/id1610516671
S30 https://www.softwarecurio.com/blog/class-companion/ (classcompanion.com blocked)
S31 https://trylunaai.com/
S32 https://ieeexplore.ieee.org/abstract/document/11624814/
S33 https://arxiv.org/abs/2608.21379
S34 https://zuno.fit/
S35 https://scholars.hkbu.edu.hk/en/publications/testing-quizzing-boosts-classroom-learning-a-systematic-and-meta-
S36 https://review.firstround.com/the-tenets-of-a-b-testing-from-duolingos-master-growth-hacker/
S37 https://blog.duolingo.com/how-streaks-keep-duolingo-learners-committed-to-their-language-goals/
S38 https://doi.org/10.1007/s10648-019-09498-w
S39 https://doi.org/10.1111/bjep.70075
S40 https://campustechnology.com/articles/2024/08/28/survey-86-of-students-already-use-ai-in-their-studies.aspx
S41 https://www.pewresearch.org/short-reads/2025/01/15/about-a-quarter-of-us-teens-have-used-chatgpt-for-schoolwork-double-the-share-in-2023/
S42 https://doi.org/10.1038/s41598-025-97652-6
S43 https://arxiv.org/abs/2607.08849
S44 https://www.nature.com/articles/s41599-025-04787-y
S45 https://arxiv.org/html/2603.17373v2
S46 https://arxiv.org/html/2606.03822
S47 https://doi.org/10.1097/jnr.0000000000000772
S42, S47 retrieved via PubMed.
