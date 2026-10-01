# §22c Student AI Helper App Features — Research Brief

2026-09-30. US App Store ratings today unless stated. [S#] = sources; "vendor" = developer claim; §22b = sister brief: Stethoscore facts, rejected features, medical apps, StudyFetch, SyncAI, CampusLearn. Reddit blocked; sentiment from App Store, Trustpilot, press.

## 1. Market landscape

iPhone Education chart today: 1 Duolingo, 2 Gauth, 3 Learna, 8 Quizlet, 9 Gizmo; photo-solvers and AI flashcard apps dominate [S1].

- ChatGPT Study Mode (OpenAI): in-chat toggle, scaffolded steps, knowledge checks; Socratic prompts, memory personalisation. Free on all tiers since 29 Jul 2025 [S2]. No peer-reviewed evaluation [S3]; ungrounded.
- Gemini Guided Learning, NotebookLM (Google): step-by-step, diagrams, videos, quizzes; notebook flashcards, Audio Overviews; LearnLM-tuned, source-grounded. Free; AI Pro free one year for students in 5 countries [S4,S5]. Efficacy is vendor claim.
- Quizlet: flashcards, Learn, Test, groups, lecture audio to guide; Magic Notes, Ask Quizlet; Q-Chat killed Jun 2025 [S7]. IAP $9.99/mo, $44.99/yr; web $7.99/$35.99 (conflict) [S8,S9]. 4.8★/1.1M. Learn paywalled, "cash grab", screen-reader and text-size failures [S8]; Trustpilot: ads, auto-renew [S10].
- Chegg Study: 60M solutions, expert Q&A, scanner, flashcards; "AI solutions backed by experts". $15.99/mo [S11]. 4.7★/205K. Revenue −51% YoY, traffic lost to AI Overviews, genAI [S12,S13]; Trustpilot 2.2 [S14]; 2-device cap.
- Photomath (Google): camera math, multiple methods, graphs; animated steps; textbook solutions paid. $5.99–9.99/mo [S15]. 4.8★/733K. Geometry errors; cheating stigma; users want practice, quizzes [S15].
- Brainly: community Q&A, Scan to Solve, live tutors; AI tutor. Plus $2–9.99/mo; Tutor $29.99/mo [S17]. 4.7★/284K; points, badges. 20 Q/mo caps; tutors quit sessions; auto-renew [S17,S18].
- Khanmigo (Khan Academy): tutor over KA content, writing coach, teacher tools; withholds answers. $4/mo, $44/yr; teachers free [S19]. KA app 4.6★/112K, free, offline [S20]. Used a third of days; "non-event for most students"; gains from KA, not AI [S21].
- Duolingo (Max): streaks, leagues, hearts, Video Call, Roleplay; LLM conversation; Explain My Answer now free. Super $9.99–119.99 [S22]; Max $29.99/mo, $168/yr (price guide) [S23]. 4.7★/5.5M; 58.7M DAU, 12.7M paid, CURR 84% [S24]. Streak anxiety, Max price [S22].
- Gauth (ByteDance): photo solve, expert network, notes to study tools; live voice tutor with whiteboard. Plus $11.99/mo, $99.99/yr [S25]. 4.8★/1.5M. Voice "creepy"; cheating, data-privacy criticism [S25,S26].
- Studdy: scan, chat, practice, videos; "98% accuracy", GPA +1.3 (vendor). Free 5 scans/day; from $6.99/wk [S27]. 4.8★/13K. "MORE SNAPS" cap anger [S27].
- Knowt: free Learn mode, SRS, Quizlet import, PDF/video to cards; Kai feedback, podcasts. Ultra $9.99/wk–$149.99/yr [S28]. 4.7★/11K; 4M students (vendor). No images in notes, no iPad parity; video tool Chrome-only; notifications [S28].
- Gizmo: import Quizlet/Anki/PDF/YouTube, games, leaderboards; explanations from uploads. $6.99–309.99 [S29]. 4.8★/14K; ADHD praise. SRS repeats too often; off-material questions [S29].
- Class Companion: rubric feedback on writing, hints, TTS, translation; Ditto tutor, AI-writing flags. Teachers free; school quotes [S30]. 25,000 schools (vendor). Teacher-side; no student app.
- Luna AI: phone-call tutor, notes to spoken quiz, languages; voice LLM. 20 free min/mo [S31]. Blind, commuter access. iOS app NOT FOUND; ratings UNKNOWN.
- EduAI; RIACT: prototypes, adaptive paths, Feynman board [S32]; study-log burnout rules, LLM coach, no user study [S33]. Free. Responsible-AI design; not shipping.
- NOT FOUND: Socratic (Google), folded into Lens 2025 [S16]; ElevatED, Zuno, PlanIC, LearnEscape; Zuno is only a fitness app [S34].

## 2. Feature frequency (18 profiled apps)

- Table stakes, 94%: AI tutor chat; capped freemium.
- Common: progress tracking ~50%; flashcard generation from uploads 44%; photo or math solving 39%; voice, grounding in own files, test prep, sharing, writing help, points or streaks 33–39%.
- Differentiators: lecture audio or video to study material 28%; spaced repetition, offline 22%; human experts, leaderboards, study plans 17%, leaderboards rejected in §22b; burnout signals, career tools 6%.
- Gaps at 0%: exam-date plan; deep links to slide or minute; drawing recall; medical modes.

## 3. Feature impact

Med unless stated.

- Downloads: very high for photo homework help [S1,S25]; high for AI tutor chat and flashcard generation [S28,S29]; low for quizzing and SRS, daily goal, offline, analytics, burnout.
- Retention: high for quizzing and SRS, g=0.50 over 50K students [S35], SRS SMD 0.78 (§22b); gamification, element-dependent: badges +2.4% DAU, streak wager +14% D7 (vendor) [S36,S37], g=.46 cognitive, reward-only weakest [S38], nursing SMD 0.81, I² 82–95% [S47]; daily goal [S37]; offline [S20]. Low–Med for AI tutor chat [S21]; AI apps churn faster (§22b). UNKNOWN for burnout.
- Conversion: high for photo help, scan caps sell [S27]; voice tutor, Max priced on Video Call [S23,S24]; human experts, high price but low trust [S14]. Med for flashcards [S8]. Low for analytics, burnout.
- Word-of-mouth: high for photo help, with cheating stigma [S26], flashcards, gamification, voice; low for daily goal, human experts, analytics.
- Effort: have flashcards, quizzing and SRS, voice, lecture capture; S for daily goal, analytics, burnout; S–M for gamification; M for photo help, AI tutor chat, offline; XL for human experts.
- Writing, math, language: high demand generally, low medical relevance. Career: low demand; Chegg's pivot [S12].

## 4. UI/UX patterns

Adaptations: §8, §10.

- Value before sign-up: Duolingo's delayed sign-up +20% DAU, vendor A/B [S36].
- Camera primary, typing kept: Gauth, Photomath, Brainly, Studdy dominate the chart [S1]; forced-camera complaint (§22b).
- Tutor-mode toggle: ChatGPT, Gemini; on/off mid-chat [S2,S4].
- Free core mode: Quizlet's paywalled Learn earns 1★ [S8]; Knowt's wedge [S28].
- Forgiving streaks: Duolingo amulet +4% D14, 5% fewer lost streaks, vendor [S37]; streak-anxiety reviews [S22].
- Flow: clear goal, matched difficulty, instant feedback; Duolingo, Khan mastery; flow–performance r=.49; goals r=.61, feedback r=.52, challenge–skill r=.49; 108 studies, correlational [S39].
- One tuned notification: Duolingo 23.5h cadence, copy +5% DAU, vendor [S36]; Knowt "intrusive" [S28].
- Multimodal answers: Gemini's inline diagrams, videos [S5].
- Accessibility: Quizlet fails [S8]; Luna call-in [S31]; Class Companion TTS [S30].
- Tab bar, dark mode, density, streaming, citations: §22b.

## 5. Student workflow (non-medical)

- Morning, due cards, streak: Quizlet, Knowt, Duolingo; guilt, paywalled Learn [S8,S22]. Opportunity: free due-ring with freeze.
- Between classes, quick answer: ChatGPT, Gemini, Gauth; ungrounded, stigma [S26]. Opportunity: answer citing own slide.
- Study session, deep help: Chegg, Photomath, Study Mode; wrong answers, billing [S14], answers without struggle [S21]. Opportunity: guide toggle, why-wrong.
- Evening, group, feedback: Brainly, Quizlet groups, Class Companion; caps, tutor drop-outs [S17]. Opportunity: share sets without ranks, feedback on OSCE notes.
- Exam prep, tests, weak spots: Quizlet Test, Knowt guides, Khan mastery; no exam-date plan. Opportunity: plan plus blueprint coverage (§22b).

## 6. Monetisation

- Ladder: $4 Khanmigo; $5.99–9.99 Photomath, Quizlet; $11.99 Gauth; $15.99 Chegg; $29.99 Brainly Tutor, Duolingo Max; weekly $6.99–9.99 at Studdy, Knowt, Gizmo [S15,S19,S23,S25,S27–S29].
- Conversion: Duolingo 12.7M paid of 140.6M MAU = 9% (computed) [S24]; Quizlet, Brainly, Chegg, Gauth UNKNOWN; education medians in §22b. LTV UNKNOWN for all.
- Paid for: unlimited scans and generation, voice AI, offline, no ads, human help. Refused: paywalled basics [S8], auto-renewal [S10,S14,S18], caps failing mid-task [S17,S27].
- Triggers: hitting the cap, exam week, voice. Churn: free substitutes, Study Mode and student AI Pro; Chegg −51% [S2,S5,S12].

## 7. AI-specific

- Used: photo-solve, generation from notes, explanations; 86% of students use AI, 54% feel unprepared [S40]; 26% of US teens used ChatGPT for schoolwork in 2024, double 2023 [S41].
- Ignored: Khanmigo [S21]; Q-Chat withdrawn after two years, reason UNKNOWN [S7].
- Distrusted: wrong answers, "6×3=45" [S20], Chegg "AI errors" [S14]; Gauth's "creepy" voice, ByteDance data [S25,S26].
- Works when guided: custom AI tutor beat active-learning class in an RCT [S42]; +0.27 SD, persisting only for "augmentation" users [S43]; nursing anatomy RCT (§22b). Contested: one ChatGPT meta-analysis was retracted [S44].
- Risks: Socratic prompting is the least stable strategy [S6]; benchmark harm rises 17.7%→77.8% over multi-turn dialogue [S45].
- Trust recovery: warning 252 students the tutor can err raised hint requests [S46]; pair with §22b citations and confidence. Latency: §22b.

## 8. Cross-pollination

- Transfers. Value before sign-up: sample-lecture demo. Free core mode: review free, meter generation. Tutor toggle: fixed Socratic templates, ask-before-answer (§22b). Challenge and narrative gamification: adaptive difficulty, case narratives, no points economy. Voice tutor: have; opt-in, labelled; add Luna's quiz-aloud.
- Transfers partly. Camera-first help: slide, question or ECG to grounded explanation, typing kept. Forgiving streaks: private, no leagues or ranks. Burnout detection: rules, observations, opt-in, no diagnosis [S33].
- No transfer, avoid. Human expert marketplace: faculty do it, liability, XL. Community Q&A with ranks: share sets only. Generic chat vs free giants: compete on own material, blueprint, offline. Weekly pricing, career or fitness bundles: Zuno, PlanIC NOT FOUND; Chegg pivot.

## 9. Gap analysis

Fixes are the §10 items, in order.

- High: sign-up before value [S36]; free tier undefined [S8,S28].
- Medium: no camera entry [S1]; no tutor toggle [S2,S4]; fixed MCQ difficulty [S39]; no streak forgiveness [S37]; no error disclosure [S46]; accessibility UNKNOWN [S8].
- Low: notifications undesigned [S36]; well-being [S33].

Matches: AI tutor, generation from uploads, SRS, test prep, voice, analytics. Unique against all 18: OSCE examiner, cases, drawing recall, 3D graph, Arabic, offline-first (§22b). Win: grounded medicine plus these patterns while the giants stay generic.

## 10. Implementation recommendations

Each: UI; effort, revenue lever, priority.

- Demo before sign-up: sample lecture to cards in 60s, then account; S, downloads, P0.
- Free review, generation meter: meter on Today, upsell at cap; S, conversion, P0.
- Guide/Answer toggle: sticky chip in tutor bar, answers pull figures from user slides; M, retention, P1.
- Snap-to-explain: camera in Study Lens beside upload, typing kept; M, downloads, P1.
- Adaptive difficulty, goal card: session goal, per-item feedback; M, retention, P1.
- Private streak, freeze: Today ring, two freezes a month, no ranks; S, retention, P1.
- Error disclosure, report: "May be wrong" banner, Report regenerates with source; S, trust, P1.
- VoiceOver, spoken quiz: audit, Dynamic Type, "quiz me aloud"; M, retention, P1.
- One tuned nudge: chosen hour, due count; S, retention, P2.
- Study-load insight: weekly observation, opt-in; S, P2.

## Top 10 features to add

1. Demo before sign-up (P0): Duolingo's delayed sign-up lifted DAU 20% (vendor) [S36].
2. Free review, visible generation meter (P0): Knowt grows on free Learn while Quizlet's paywall earns 1★ [S8,S28]; caps are the loudest complaint [S17,S27].
3. Guide/Answer toggle (P1): both AI giants ship it [S2,S4]; fixed templates, since Socratic output is prompt-fragile [S6].
4. Snap-to-explain (P1): the two biggest study apps are camera-first [S1,S15,S25].
5. Adaptive difficulty, session goal (P1): flow antecedents r=.49–.61 [S39]; quizzing g=0.50 [S35].
6. Private streak with freezes (P1): forgiveness raised retention (vendor) [S37]; anxiety when absent [S22].
7. Error disclosure, one-tap report (P1): admitting fallibility increased help-seeking [S46]; multi-turn harm grows [S45].
8. VoiceOver and quiz-aloud (P1): Quizlet's accessibility failures [S8]; Luna's blind-student mode [S31].
9. One tuned daily nudge (P2): copy alone moved DAU 5% (vendor) [S36]; Knowt's downside [S28].
10. Rules-based study-load insight (P2): RIACT's observation-not-diagnosis design [S33].

## Top 5 features to avoid

1. Human tutor marketplace: Chegg −51%, Trustpilot 2.2, Brainly drop-outs [S12,S14,S17].
2. Generic ungrounded chatbot: free Study Mode and Gemini own it [S2,S5]; Khanmigo's low uptake [S21].
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
