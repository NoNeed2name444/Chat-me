# §22a Jev Deep Research Brief (2026-09-30)

## Jev factual baseline
1. Verified from primary sources: site, docs, live API, status page [1][2][3][4]. TypeSafe AI, founder-CEO Diogo Almeida (ex-OpenAI); early access opened 15–16 Sep 2026 [5][6]. Current model `jev-1.13.0`; `jev-latest` and `jev-preview` both resolve to it; earlier versions UNKNOWN [7].
2. API: `POST https://api.typesafe.ai/v1/systemone`, body `{state, model, questions}`. Choice/Score return pick, probabilities, confidence; Noul returns 0–1 only [2][3]. Confidence is derived from the probabilities [8].
3. $0.042/M input, output free; no free tier or trial credits [7][9]. Also via OpenRouter [10], Vercel AI Gateway (free tier throttled) [11] and Cloudflare Workers AI as `typesafe/jev` [12]; coverage by the free 10,000 neurons/day UNKNOWN (dollar-priced like paid partner models) [13].
4. 64k tokens/request, 32k for state plus longest question; text only; 100K tok/s, 40 req/s, "adjusting dynamically" [7] (secondary sources say 250K/1,200 rpm; docs win).
5. Latency claimed 70–500 ms [5]; independent medians 127–313 ms [14][15][16][17]; 580–942 ms from Turkey and Taiwan [18][19].

## Hard limits (TypeSafe's jaggedness page [20] unless noted)
- No generation, no chain-of-thought; negations and multi-hop indirection degrade accuracy; text only [7]; no rationale or trace [21].
- Not a calculator: counts, numeric comparison, dates unreliable; Score levels "weak in numerical calibration".
- No invariants: Noul and yes/no Choice disagree; P(A)+P(not A) reached 1.19; thresholds do not transfer.
- No fine-tuning, English-first, hosted only [7]; ToS disclaims all warranties, no medical clause [22].
- Rarely abstains: chose "I don't know" on 10.5% of items where it was correct [16].

## Independent benchmarks
| Benchmark | Jev | Frontier LLM | Gap | Source |
|---|---|---|---|---|
| PubMedQA (500) | 78.4% | GPT-6 Sol medium 78.2% | +0.2 | [16] |
| MetaMedQA (1,373) | 74.8% | 82.7% | −7.9 | [16] |
| DiagnosisArena-MCQ (915) | 59.8% | 82.4% | −22.6 | [16] |
| NEJM cases (34) | 61.8% | 82.4% | −20.6 | [16] |
| RAGTruth hallucination, tuned | 87% (0.80) | Opus 5 87%; Terra 80% | 0 | [23] |
| TypeSafe's 4 workflows (vendor data) | 67.8% | Sol 74.1%, Opus 5 73.1% | −6.3 | [9] |
| LiteLLM tier routing (240 calls) | 95.0% | Haiku 73.75% | +21 (self-labeled) | [14] |
| BAAI analysis | NOT FOUND | | | |

## Production reports
| Report | Finding | Relevance |
|---|---|---|
| Arize AX [24] | Default cutoff "materially worse"; tune on human labels first | Layers 3, 4 |
| Amplitude [15] | Own test: precision 55.8%, recall 100%. Cited 791-decision cascade at 0.80: frontier accuracy at 1/4 cost, but 5 confident errors on look-alike intents | Layers 1, 6, 8 |
| Beam.ai [25], DigitalOcean [21] | Explainers; "headline numbers are TypeSafe's own"; no compaction savings measured | Plan's claims unsupported |
| Status page [4] | 99.827% uptime; four incidents 20–25 Sep; 529 overload; gateway 429 shedding [26] | Circuit breaker mandatory |

## Calibration
- Task-dependent: MetaMedQA ECE 0.063 vs 0.146, AUROC 0.845 vs 0.801, Brier worse; DiagnosisArena ECE 0.105 vs 0.084, AUROC 0.645 vs 0.768 [16].
- p≥0.9 covers 52.9% at 93.4%; GPT-6 Sol matched it at equal coverage [16].
- Underconfident: 0.5 gives 76% (Opus 5 83%), 0.80 gives 87%; tuned on ~1,300 items, scored held-out; "a few hundred" own labels advised [23].
- Binary Choice hides a "don't know" mass; recovering it lifts soft accuracy 0.771 to 0.978 [27].

## Medical fit
- Abstracts: parity on PubMedQA, 2,823 items for $0.08 [16]; fits yes/no over Europe PMC abstracts but is not entailment; validate on own claims.
- Exams: 74.8% vs 82.7% [16]; MedXpertQA comparison UNKNOWN. Classify questions only.
- Diagnosis: 59.8–61.8%, AUROC 0.645; "not a stand-alone diagnostic tool" [16]; never for diagnosis or reasoning grades.
- Safe: routing, format and presence checks, card triage. Never: dose correctness, numeric ranges, sole P0 verdicts.

## Claims vs evidence
| Claim | Verified? | Source |
|---|---|---|
| Cannot hallucinate | Partly: 0% type errors while accuracy collapses; wrong 25–40% on medical MCQs | [28][16][6] |
| 40–200x faster; Arize 23x | Partly: 23x vs Opus 5, 5.4x vs Haiku, 5–14x vs Sol medium | [23][14][16] |
| Calibrated; default 0.5 "7 points worse than Opus 5" | Partly: ECE best on one benchmark, worse on another; hidden "don't know"; 76 vs 83 verified | [16][27][23] |
| Price, context, endpoint, primitives | Verified | [3][7][10] |
| BAAI | NOT FOUND | |
| DataCamp 68% | 67.8%, but TypeSafe's own workflows | [9] |
| arXiv medical numbers | Exact; two independent authors; NEJM n=34 | [16] |
| Beam.ai, DigitalOcean production data; Amplitude | Explainers only; Amplitude verified | [25][21][15] |
| Seven reference repos | All exist; xerify, jev-guard, jev-git, abide MIT | [19][18][29][30][31][32][33] |

## Integration patterns
1. Adapter pins `jev-1.13.0`, validates responses (argmax, sums, size), maps 401/422/429/529, invalid output becomes "unclear"; 10/10 live checks in 308–942 ms (Xerify) [19].
2. Three-way gate on probability and confidence [8]: pi-jev denies at Noul 0.90 [31]; Xerify p≥0.90, confidence≥0.80 [19].
3. Fan-out all questions in one request: 12.2x cheaper, 10x faster [34].
4. KV cache keyed on (state, questions, model) in a Worker [35]; AI Gateway adds caching and logs [12].
5. Log `response.model`, probabilities, latency per decision [7].

## Failure modes
1. Option-name polarity: 0/1 to no/yes flipped 70 answers per 100; hosted Jev AUC .81 to .58 [28].
2. Confident-but-wrong clusters that confidence gates never see [15].
3. Hidden "don't know" mass [27].
4. Literal reading, negations, indirection; numbers, dates; injection; distractor-heavy long state [20].
5. Threshold drift when `jev-latest` moves; Noul/Choice non-equivalence [7][20].
6. 429/529 throttling, incidents [4][26]; metered cost, ~$7/h at 10 QPS [5].
7. Schema escape: unobserved in 9,669 requests [16][28]; validate anyway.

## Layer-by-layer verdict
| Layer | Verdict | Reason | Measurement |
|---|---|---|---|
| 1 Routing | Reject | Mode is known from the UI; a paid 130–900 ms hop worsens UX | None |
| 2 Generation gate | Defer | Format belongs in code; "safe to show" untested in medicine | 300 labeled outputs: AUROC ≥0.85, false-safe <2% |
| 3 Pre-verification gate | Defer, shadow pilot | Semantic, batchable; long-state and number limits; ~$0.0003 per 8k-token call | Shadow ≥300 claims: AUROC ≥0.85, ≥30% fewer downstream calls, no new misses |
| 4 Claim verifier (secondary) | Defer, pilot | Fits the EntailmentProvider agreement gate; never alone | 300 labeled pairs: accuracy ≥ current provider; audit confident disagreements |
| 5 Self-learning triage | Defer | Low volume, latency irrelevant | Precision/recall on 100 labeled signals |
| 6 In-app | Split: pilot card quality, MCQ type; defer OSCE, teach-back; reject concept collision | ~$0.00002/call; polarity and phone p95 risks; OSCE grades face students | Kappa ≥0.8 on 200 labels, neutral option names; OSCE shadow vs clinician checklist, p95 <1 s |
| 7 Oath layer | Adopt, regex first, fail-closed | Bounded presence detection; timeout routes to verification | Recall ≥0.99 on 200 labeled claims; any miss blocks launch |
| 8 Revenue funnel | Reject | Numeric, temporal signals are Jev's weakest area | None |

## Overall verdict
Better in specific layers (7; card and MCQ parts of 6; 3 and 4 as shadow pilots), neutral to worse elsewhere.

## Deployment recommendation
Now: Layer 7 and Layer 6 card/MCQ pilots behind Pro or a capped key. Later: 3 and 4 after shadow measurements, then 2 and 5. Never: 1, 8, concept collision, diagnosis, dose arithmetic, sole verifier.

## Threshold tuning plan
Per question and model version: 300 human-labeled Stethoscore items per gate (the plan's 100 is a smoke test), split 50/50, cutoff by balanced accuracy (Layer 7: recall ≥0.99), confirm on the held-out half, pin `jev-1.13.0`, re-tune when `response.model` changes, review confident disagreements monthly.

## Sources
[1] https://typesafe.ai/ [2] https://docs.typesafe.ai/api [3] https://api.typesafe.ai/openapi.json [4] https://status.typesafe.ai/ [5] https://typesafe.ai/blog/introducing-system-one-models-and-jev [6] https://www.theregister.com/ai-and-ml/2026/09/16/typesafe-ai-debuts-model-for-machines-that-plays-doom/5296711 [7] https://docs.typesafe.ai/models [8] https://docs.typesafe.ai/confidence [9] https://www.datacamp.com/blog/system-one-models-jev [10] https://openrouter.ai/typesafe/jev-1.13 [11] https://vercel.com/docs/ai-gateway/pricing [12] https://developers.cloudflare.com/ai/models/typesafe/jev/ [13] https://developers.cloudflare.com/workers-ai/platform/pricing/ [14] https://docs.litellm.ai/blog/jev-auto-router-benchmark [15] https://amplitude.com/blog/jev-analysis [16] https://arxiv.org/abs/2609.34024 [17] https://github.com/WallerChen/jev-measured [18] https://github.com/leepokai/jev-guard [19] https://github.com/Verhex/xerify/blob/main/docs/jev-testing.md [20] https://docs.typesafe.ai/model-jaggedness/jev-1.13 [21] https://www.digitalocean.com/resources/articles/what-is-jev [22] https://typesafe.ai/legal/terms [23] https://arize.com/blog/jev-llm-judge-benchmark [24] https://arize.com/blog/arize-ax-jev-as-a-judge/ [25] https://beam.ai/agentic-insights/jev-typesafe-ai-agents [26] https://community.vercel.com/t/ai-gateway-typesafe-ai-jev-returns-free-tier-429-despite-paid-credits/49935 [27] https://arxiv.org/abs/2609.35342 [28] https://arxiv.org/abs/2609.26758 [29] https://github.com/AkashPriyadarshii/jev-git [30] https://github.com/coldteadotai/abide [31] https://github.com/y0usaf/pi-jev [32] https://github.com/Kelbie/hunch [33] https://github.com/devagrawal09/jev-review [34] https://docs.typesafe.ai/cookbooks/parallel_questions [35] https://github.com/Jac0bJ/jev-worker

## CRITICAL GATE
Verdict: better in specific layers; deploy only those. Jev is real, cheap and fast, and independent evidence supports it for bounded semantic gates: presence checks, card and question triage, and, once thresholds are tuned on Stethoscore's own labeled claims, a secondary evidence-support signal. The same evidence shows it 20 points behind frontier models on diagnostic reasoning, rarely abstaining, swayed by option wording and injected text, weak on numbers and dates, with confidence that hides look-alike errors, so as a router, revenue oracle, student-facing OSCE grader or sole verifier it would worsen the experience. Every call costs money, so it belongs behind Pro or capped pilots. Deploy Layer 7 fail-closed and Layer 6 card/MCQ now; shadow-pilot Layers 3 and 4; reject Layers 1 and 8.
