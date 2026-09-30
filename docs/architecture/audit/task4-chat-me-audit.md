# Task 4 — Audit of the Chat-me medical verifier vs the Stethoscore plan

Read-only audit, 2026-09-30. Branch `origin/medical-verifier-v0.1-commercial-safe` @ `2f4fd4e` (2026-09-28), read from worktree **CM** = `/tmp/claude-0/-home-user/d15410a3-d322-5482-9bdc-4b0252bf3160/scratchpad/chatme-verifier/medical-verifier`. Stethoscore engine: **RP** = `/home/user/red-pen-ios`. Spec: plan §7/§8/§10/§13/§17/§22/§22e/§22g; owner constraints from context.md §1–3 (free tiers only, iPhone-only owner, no leaderboards). Offline probe scripts and outputs: `scratchpad/audit/exp/probe*.py`, `pytest.log`; probe results are cited as E/V numbers.

**Headline.** A serious, well-tested (170/170 offline tests) *deterministic curriculum-fidelity and source-integrity engine* with a heuristic "current medical" lane. It is not the §8 pipeline: no knowledge base, embeddings, NLI model, generation, Jev, LangGraph, hash-chained audit, review queue or authentication. Its current-medical lane cannot return SUPPORTED for any claim containing a causal/quantity/negation word (`pipeline.py:440`) and mis-anchors real evidence text (E11, V6), so it abstains on nearly every clinically interesting claim. Reuse its guards, schema, hashing and test corpus; do not treat it as a working spine.

## 1. What the branch actually is

| Item | Fact | Proof |
|---|---|---|
| Size | 547 commits (all 2026-09-26..28); 165 files; 63 `.py` in `app/`; 60 test files / 172 tests; 30 docs; Swift package (3 sources, 5 test files) | `git log`, `find` |
| Runtime | Python ≥3.11 FastAPI; deps only fastapi, pydantic v2, pydantic-settings, httpx, pypdf. No ML/LLM/orchestration deps (grep langgraph/jev/typesafe/mdeberta/medcpt/qdrant/pgvector/torch: 0 hits) | `CM/pyproject.toml` |
| Entry points | `uvicorn app.main:app`; `GET /health`; `POST /v1/verify`, `/v1/drug/check`, `/v1/documents/ingest`, `/v1/documents/pdf/ingest`, `/v1/questions`, `GET /v1/questions/{id}`, `POST /v1/benchmark/integrity`, `/v1/benchmark/snapshot/verify`; all sync `def` (0 `async def`) | `CM/app/main.py`, `app/api/*.py` |
| Verify flow | contract check → normalize → decompose (≤4 clauses) → risk/context → regex safety gate (critical ⇒ SAFETY_ESCALATION) → per assertion: PubMed esearch+esummary (metadata only) + openFDA labels (only drugs named in `context.medications`) → provenance → supersession → overlap → heuristic entailment → aggregate → policy → curriculum lane from SQLite → divergence → openFDA revalidation (high risk) → SQLite audit row | `CM/app/verification/pipeline.py:202-765` |
| External services | `eutils.ncbi.nlm.nih.gov` esearch/esummary (never efetch: no abstracts); `api.fda.gov/drug/label.json`. Nothing else | `ncbi.py:36-57`, `openfda.py:27-31`, `revalidation.py:44-49` |
| Storage | One SQLite file: `verification_audit`, `source_manifests`, `questions`, `evidence` | `CM/app/audit/store.py:37-116` |
| Auth / tenancy / logs | None: no middleware, `Depends`, tokens, CORS, rate limit or `logging` (grep). Anyone can ingest under any snapshot id and read any evidence id (E17) | `api/documents.py`, `retrieval/local.py:42-57` |
| Swift port | `MedicalVerifierCore` (iOS 16): curriculum lane only (`CurriculumVerifier`, `SourceIntegrityChecker`, `SourceManifest`), HTTPS-only client pinned to contract 1.8, `OnlineFirstVerifier` marks current-medical "unavailable" offline | `CM/ios/MedicalVerifierCore/Sources/**` |
| Versions | README "v0.1"; pyproject/config 1.6.0; contract 1.8; policy "0.2-multi-axis"; `.env.example` 0.2.0; Swift "ios-core-0.9"; corpus 1.3; benchmark schema 1.6 | respective files |
| Licences | Code MIT (© 2026 NoNeed2name444); root `NOTICE` = pypdf BSD-3; `medical-verifier/NOTICE` disclaims FDA/NIH affiliation. `docs/COMMERCIAL_USE.md` is cited by `README.md:33` but exists in no commit of any branch; no `THIRD_PARTY_NOTICES`. Runtime sources are US-government data; uploads are never licence-checked | `CM/LICENSE`, `NOTICE`, `git log --all` |
| Other branches (not audited) | `main` = `claude/new-session-eskvv8` @ f1bfe1f (adds `docs/COMMERCIAL_ACCURACY_STACK.md`, an ASR design); `verification-layer-adversarial-50` @ 9f1b1cd; `verification-layer-commercial-accuracy-v1` @ 702d722. Audited branch and `main` have diverged | `git branch -r`, `merge-base` |
| Tests run | `pip install -e ".[dev]"` OK (16 s via proxy). `pytest -q`: **170 passed, 1 warning, 1.03 s**, fully offline (providers monkeypatched). Swift tests **not run** — no toolchain here (CI runs them on ubuntu + macOS) → UNKNOWN | `audit/pytest.log`, `CM/.github/workflows/ci.yml` |

**"Fail closed on malformed evidence"** is not in this branch's README (which makes no fail-closed claim; the repo-root `README.md` is 0 bytes). The sentence is line 47 of `docs/COMMERCIAL_ACCURACY_STACK.md` on `origin/main` (f1bfe1f): "The verifier should fail closed when a required component returns malformed evidence or an uncalibrated confidence value." Whether the code does it: §6.

## 2. §7 report — §8 stages and §10 features

Stages: VERIFIED 0 · PARTIAL 3 · MISSING 2 · DIVERGENT 5.

| §8 stage | Status | Proof |
|---|---|---|
| 1 KB (abstracts, PMC, textbooks, guidelines; MedCPT; pgvector) | DIVERGENT | No KB/embeddings; live NCBI metadata, live openFDA, user-uploaded text/PDF blocks in SQLite (`api/documents.py`, `pdf_structure.py`, `store.py:124-202`) |
| 2 Retrieval (embed→cosine→rerank) | DIVERGENT | Raw assertion as `esearch` term (`ncbi.py:24-48`); exact generic-name search; whitespace-token overlap over rows (`local.py:62-79`) |
| 3 Generation | MISSING | Post-hoc verifier of caller text; no model call |
| 3.5 Jev gate | MISSING | Nearest: regex `inspect_claim` (`adversarial.py:10-28`) |
| 4 Decomposition (S→R→O) | PARTIAL | Clause split ≤4 (`assertions.py`); atoms with relation/polarity/temporal/safety + 2-token anchors (`claim_reasoning.py:174-240`); anchors break on prefixed text (V6) |
| 5 Verification (mDeBERTa + Jev, KG) | DIVERGENT | No NLI; `AgreementGate([StructuredProvider()])` = one heuristic provider (`entailment_provider.py:21-23`) + guards; KG = 3-entry alias table (`entity_normalization.py:10-14`) |
| 6 Citation enforcement | PARTIAL | Unbound evidence cannot support (`citation_integrity.py:39-82`, E1); default `permissive` mode admits NULL-hash rows (E13c); input claims carry no citations |
| 7 Triage (P0 dose/contraindication) | DIVERGENT | Keyword tiers (`risk.py:1-28`): "dose"/"contraindication" ⇒ moderate; "contraindicated", "500 mg" ⇒ low (V6). No P0/P1 |
| 8 Human loop | PARTIAL | `requires_human_review` bool only (`pipeline.py:690-706`) |
| 9 Audit (SHA-256 chain) | DIVERGENT | JSON rows, `INSERT OR REPLACE`, no hash/prev-hash (`store.py:204-220`). Only source manifests and benchmark snapshots are hash-bound with parent links |

§10 features: VERIFIED 1 · PARTIAL 13 · MISSING 26.

| Feature | Status | Proof |
|---|---|---|
| Confidence 0–100 % | PARTIAL | 0–1 "policy score; not calibrated" (`pipeline.py:563-587`); isotonic calibrator unused in path |
| Population tag | PARTIAL | Mismatch guard for adult/child/pregnant/renal/hepatic/elderly only (`consistency.py:3-7`); no field on verdict |
| Evidence strength | PARTIAL | Static authority per `source_type` (`reliability.py:7-15`); PubMed publication type never read |
| Time sensitivity | PARTIAL | 3-y half-life, same-`canonical_id` supersession, `knowledge_divergence` (`reliability.py:27-32`, `provenance.py:54-85`) |
| Conflict flag + count | VERIFIED | `MIXED_EVIDENCE`, `contradictions[]`, `independent_contradiction_groups` (`policy.py:34-38`) |
| Doctrine drift | PARTIAL | `curriculum_may_be_outdated`/`_vs_current_conflict` + `study_hint`; not tracked over time |
| Claim decay clock | PARTIAL | `current_claim_relies_on_old_evidence` only when claim says "currently" (`temporal_guard.py`) |
| Silent contradiction alert | PARTIAL | Verdict + review flag; no queue |
| Statistical literacy | PARTIAL | percent vs percentage-point vs RR/OR/HR mismatch refused (`independent_entailment_base.py:288-304`) |
| Population mismatch | PARTIAL | Listed terms only; ethnicity/sex pass (V14) |
| Multi-source consensus | PARTIAL | `independent_support_groups`, families, risk minimums (`policy.py:3-9,40-51`) |
| Cross-language | PARTIAL | Narrow Spanish/French map (`independent_entailment_base.py:55-91`); no Arabic/German |
| Disagreement register | PARTIAL | provider disagreement ⇒ UNKNOWN (`entailment_provider.py:65-74`); one provider, no expert side |
| On-device fallback | PARTIAL | Swift curriculum lane offline; no local model |
| Why-not, primary source chain, isnad (retractions never read), cost, accessibility, bias audit, pre-print, withdrawn-from-guideline, mechanism/outcome, surrogate, guideline-vs-practice, what-if, consensus map, streaming, multi-modal, voice, reviewer credit, per-claim history, challenge, transparency, differential privacy, federated, Jev-gated, Jev confidence, Jev audit, LangGraph | MISSING (26) | grep: zero references; `ncbi.py` reads no retraction fields |

## 3. Adversarial pass on paper (§13)

BREACH 13 (P0 × 3: #6, #10, #25) · HELD 8 · INCONCLUSIVE 11.

| [ATTACK n] vector | [PAYLOAD] | [RESULT] | [IMPACT] / code path | [SEV] |
|---|---|---|---|---|
| 1 Fabrication | ingest `url=https://doi.org/10.0000/fake`, `source_type=guideline`, `source_authority=1.0` | BREACH | Stored and surfaced as provenance, never resolved (E17); `api/documents.py:23-44`. Input claims carry no citations to check | P2 |
| 2 Misattribution | label says the opposite | HELD | polarity guards ⇒ None/CONTRADICTS (E3/E4); `semantic_guard.py:206-217`, `independent_entailment.py:121-131` | — |
| 3 Snippet drift | PDF whose condition is on the next line | BREACH | blocks are single lines, judged alone; `pdf_structure.py:65-108`, `curriculum.py:73-121` | P1 |
| 4 Contradiction smuggling | two labels, one negated | HELD | neither counts; MIXED when close (E3); `policy.py:34-38` | — |
| 5 Recency blindness | old guideline A supports, new guideline B contradicts | BREACH | supersession only within one `canonical_id`; `provenance.py:54-85` | P1 |
| 6 Severity escape | "Ibuprofen is contraindicated with methotrexate."; "Amoxicillin 500 mg tds" | BREACH | risk *low* (V6); interaction answers VALIDATED with `requires_review=False` (vector `matching_interaction`); `risk.py:10-28`, `question_validation.py:208-214` | **P0** |
| 7 Citation laundering | — | INCONCLUSIVE | no citation graph; PubMed never supports | — |
| 8 Paywall inference | claim matches a PubMed title | HELD | metadata never entails (E1); `citation_integrity.py:54-57` | — |
| 9 Ontology poisoning | bad synonym | HELD | 3 fixed aliases; unknown names never equated | — |
| 10 Hash-chain forgery | `UPDATE verification_audit …` | BREACH | no hash, chain or verifier; `store.py:42-50,204-220` | **P0** |
| 11 Licence at runtime | — | HELD | NCBI/openFDA only; but uploads never scanned (E5) | — |
| 12 Forced SUPPORTED | plant a "guideline" under victim's snapshot | BREACH | victim gets CURRICULUM_ALIGNED (E17); no auth/tenancy; `local.py:42-57` | P1 |
| 13 Silence failure | provider raises / providers disagree | HELD | INSUFFICIENT (E9); UNKNOWN; `pipeline.py:77-102` | — |
| 14 Edge population | "safe in Egyptian women" vs "safe in Finnish men" | BREACH | SUPPORTED 0.96 (V14); `consistency.py:3-7` | P1 |
| 15 Emerging drug | 2026 approval claim vs 2019 label | BREACH | SUPPORTED 0.96, no review (V15); `temporal_guard.py:13-32` | P2 |
| 16–20 Jev timeout/threshold/calibration/schema/collapse | — | INCONCLUSIVE | absent; note `verdict` is an unconstrained `str` (`models/verification.py:4`) | — |
| 21 Fail-open exploit | break the audit DB | HELD | HTTP 500, verdict lost, nothing unverified served (E10) | — |
| 22 Breaker bypass | keep upstream failing | BREACH | no breaker/backoff state; `ncbi.py:10-19` throttle only | P1 |
| 23 Timeout injection | slow openFDA to 9 s | BREACH | 10 s per call × ≤16 calls + sync revalidation, sync handlers, no deadline; `config.py:17`, `pipeline.py:342-378,598-610` | P1 |
| 24 Log tampering | edit SQLite | BREACH | no failure log exists; rows mutable | P1 |
| 25 Escalation failure | any P0 with `requires_human_review=True` | BREACH | no queue, alert or consumer; iOS carries only the bool | **P0** |
| 26 State injection | free-form `context` | INCONCLUSIVE | no graph; `str(context)` feeds risk regexes, any key satisfies `missing_context` (`risk.py:2,30-56`) | — |
| 27 Checkpoint replay | — | INCONCLUSIVE | no checkpoints | — |
| 28 Infinite loop | — | HELD | linear function, ≤4 assertions | — |
| 29 Node bypass | `provenance_mode=permissive` (default), `requested_evidence_level=any`; zero-width/full-width text | BREACH | client selects hash checks and authority floor (`provenance.py:12-13`, `pipeline.py:424-441`); obfuscation skips the critical gate (E6/E6c) | P1 |
| 30 Interrupt abuse | — | INCONCLUSIVE | no HITL state | — |
| 31–32 3D | — | INCONCLUSIVE | out of scope | — |

Probe facts behind the table: one bound FDA label makes a *descriptive* low-risk claim SUPPORTED at 0.96 with no review (E11a/b); any claim with a causal/quantity/negation word is forced to INSUFFICIENT_EVIDENCE even when entailed, because those regexes are "attack severity high" (E11c/d, V6b; `pipeline.py:440`, `adversarial.py:11-18`); relational claims also fail `atomic_subject_mismatch` because `title + passage` is anchored (`entailment.py:25`, `semantic_guard.py:180`; V6); `context.medications` satisfies `risk.missing_context` but not `assertions.required_context` (`pipeline.py:277-282`).

## 4. §17 "Application to verification layer"

| Item | Code? | Proof |
|---|---|---|
| S1 source = narrator; provenance, isnad | Partial: family/publisher/canonical id + static authority; no narrator history | `models/evidence.py`, `reliability.py:7-15` |
| S2 rank by reliability, not similarity | No: provider default order; weighting after retrieval | `reliability.py:54-61` |
| S3 isnad citation per claim | No generation; per-assertion evidence ids only | `pipeline.py:371-378` |
| S4 atomic claims, own chain | Partial: per-assertion verdicts, shared evidence pool | `pipeline.py:342-378` |
| S5 five conditions, ʿilal | Partial: hash continuity, contradiction, precision guards; no source-integrity history, no cross-version ʿilal | `citation_integrity.py`, `semantic_guard.py` |
| S6 reject no-isnad | Partial: unbound evidence blocked; permissive admits NULL hashes (E13c) | `provenance.py:12-13` |
| S7 severity + isnad strength | No: keyword tiers only | `risk.py` |
| S8 weak isnad → review | Partial: flag set, never delivered | `pipeline.py:690-706` |
| S9 record full isnad | Partial: result JSON, unchained | `store.py:204-220` |
| taʿāruḍ wa tarjīḥ | Partial: 0.75 weight ratio, supersession penalty | `policy.py:34-38` |
| hidden defects across versions | Partial: same-id supersession; precedence groups, tie ⇒ conflict | `provenance.py`, `curriculum.py:16-44` |
| mawḍūʿ (fabrication) signs | No (#1) | — |
| evidence hierarchy | Partial: static table | `reliability.py:7-15` |
| maqāṣid on ambiguity | No | — |
| taḥqīq on versions | Partial: precedence ranks | `curriculum.py:16-44` |
| tawaqquf / iḥtiyāṭ | Yes: UNKNOWN/INSUFFICIENT/CONTEXT_REQUIRED are the defaults | `policy.py` |
| Jev Noul/Choice/Score mappings | No Jev | — |

## 5. Jev readiness

| Seam | Where | Notes |
|---|---|---|
| Layer 4 verifier | `EntailmentProvider` Protocol + `AgreementGate(providers)`; add `JevProvider.assess()` mapping Noul p → SUPPORTS/CONTRADICTS/UNKNOWN; disagreement already ⇒ UNKNOWN | `entailment_provider.py:11-75`; one provider wired at `entailment.py:21-23` |
| Layer 3 (3.5) gate | No generation stage; nearest seam is between `decompose_claim` and retrieval, where regex `inspect_claim` gates today | `pipeline.py:240-336` |
| Layer 7 oath | `classify_risk` / `deterministic_checks` keyword lists | `risk.py`, `rules.py` |
| Thresholds (hard-coded) | independence 1/2/2/99 (`policy.py:3-9`); `support_ratio ≥ 0.78`; authority ≥ 0.85 (`pipeline.py:428-431`); group weight > 0.35 (`reliability.py:118-124`); overlap 0.25/0.35/0.45/0.50 (`contradiction.py:55`, `curriculum.py:94`, `claim_reasoning.py:344`, `independent_entailment.py:27`) | no config surface; no tuned Jev thresholds |
| Abstention | `decide_verdict` → INSUFFICIENT/CONTEXT_REQUIRED; gate → UNKNOWN; `revalidation_blocked` | `policy.py`, `pipeline.py:612-625` |
| Missing for §22 | `STETHOSCORE_JEV_*` constants, 1 s timeout, breaker, decision log (audit holds only final JSON), cache, calibration data | `config.py` |

## 6. Fail-safe readiness (§22e)

| Cat. | Handled (proof) | Fails open | Unhandled / absent |
|---|---|---|---|
| A models | N/A in Chat-me (no model calls). Stethoscore Worker: model chain, 429 backoff ≤30 s, day-out marking, 120 s timeout → next model, unreadable vote → next voter, no voter ⇒ "unchecked" never Verified (`RP/server/ai.js:416-480`, `accuracy.js:296-318`) | — | A5, A7 |
| B verification | B1 provider exception ⇒ INSUFFICIENT (E9); B10 unbound citation dropped; local passage-hash CHANGED blocks (`revalidation.py:97-120`) | — | B2 no 429 backoff; B3–B8 components absent; B9 no chain, audit write error ⇒ 500 (E10); B11 no overall deadline |
| C app | C1 Swift keeps curriculum result, marks current-medical unavailable (`OnlineFirstVerifier.swift:76-83`) | — | C2 disk full ⇒ 500; rest out of scope |
| D data | D4 openFDA re-fetch + hash compare ⇒ CHANGED blocks (`revalidation.py:21-95`, high risk only) | — | D1 no licence scan; D2 retraction fields unread; D3 only within one label lineage; D5 no question retirement |
| E safety | E3 INSUFFICIENT + limitations; E4 API never claims human verification | E1 keyword oath gate bypassed by zero-width/full-width text (E6/E6c) — still abstains, no escalation | E2 interaction/contraindication validated without review (V6); E5 |
| F LangGraph | — | — | all absent |
| G 3D | out of scope | — | — |

**Does the code fail closed on malformed evidence?** Mostly. Closed: malformed JSON in stored `extraction_warnings`/`related_block_ids` ⇒ warning ⇒ review (`local.py:88-107`); non-hex or wrong `passage_sha256` ⇒ `CURRICULUM_SOURCE_INTEGRITY_FAILED` (E13b/e); unbound external evidence cannot support (E1); provider/parse exceptions ⇒ INSUFFICIENT (E9); contract drift ⇒ CONTRACT_MISMATCH (E12). Open: (1) a row whose hashes are NULL/empty validates as CURRICULUM_ALIGNED with `requires_review=False` in the default `permissive` mode (E13c; `curriculum.py:75-79` skips a falsy hash; `provenance.py:12-13` checks nothing unless `bound`), whereas the Swift core fails closed on the same input (`SourceIntegrityChecker.verify` vs an empty hash ⇒ mismatch); (2) the "uncalibrated confidence" clause is unenforced — confidence is always emitted, only labelled.

## 7. LangGraph readiness

| Question | Finding |
|---|---|
| Orchestration seam | None: `verify()` is one 560-line function with locals; no state object, router or node registry (`pipeline.py:202-765`) |
| Sequencing today | Linear, synchronous: gate → decompose → per assertion (retrieve → evaluate) → combine → curriculum → divergence → revalidate → persist. No retries, parallelism, checkpoint or resume |
| What a graph replaces | The function body: nodes = contract, normalize+decompose, safety gate, retrieval fan-out (`Send`), evaluate, combine, curriculum, divergence, revalidation, persist; state = the locals; HITL interrupt replaces the `requires_human_review` bool |
| Existing graphs / stubs | None |
| Assessment | Bounded and sub-second with mocked providers; latency is network. LangGraph's payoff is checkpointed HITL and parallel retrieval, both achievable with a state dataclass + queue. §22g research must decide; nothing here presupposes either |

## 8. Folder structure readiness (§3c, Chat-me only)

| Current | Slot | Note |
|---|---|---|
| `app/main.py`, `api/{verify,drug,documents,questions}.py` | `api/routes/` | `api/benchmark.py` routes stay, logic → `evals/` |
| `app/models/*` | `api/schemas` | |
| `app/config.py`, nested `.env.example` | root `.env.example`; thresholds → `agents/orchestrator/policies.yaml` | root `README.md` empty; no `CLAUDE.md` |
| `verification/pipeline.py` | `orchestration/graph.py` (`state.py`, `router.py` have no counterpart yet) | |
| `policy.py` | `governance/policies/` | |
| `risk.py`, `rules.py`, `adversarial.py` | `governance/guardrails/` | |
| `entailment*`, `independent_entailment*`, `semantic_guard`, `consistency`, `temporal_guard`, `claim_reasoning`, `assertions`, `contradiction`, `reliability*`, `correlation`, `curriculum`, `question_validation`, `entity_normalization`, `temporal_normalization` | `agents/specialists/verification_agent/` | |
| `retrieval/*`, `terminology/normalize.py` | `tools/definitions/` + `agents/specialists/retrieval_agent/` | |
| `citation_integrity`, `provenance`, `source_manifest`, `revalidation` | `retrieval_agent/` (binding) + `governance/audit/` (manifest verification) | |
| `audit/store.py` | `governance/audit/` for `verification_audit`; evidence/question/manifest persistence has **no slot** | |
| `pdf_structure.py`, ingestion route | `tools/definitions/` — **no ingestion slot** | |
| `adversarial_mutations`, `metamorphic`, `benchmark*`, `dataset_integrity`, `validation/*` | `evals/{suites,datasets,reports}/` | |
| `tests/*` | `tests/unit/`, TestClient tests → `tests/integration/` | |
| `docs/*` | contracts → `docs/architecture/`; assessments/Nemesis rounds → `evals/reports/` | |
| `ios/MedicalVerifierCore` | **no slot** (client SDK; iOS repo or `clients/`) | |
| `Dockerfile`, `docker-compose.yml`, workflows | **no slot** (infra; keep at root) | |
| Empty slots | `compliance_agent/`, `prompts/`, `tools/mcp_servers/`, `orchestration/state.py`/`router.py` | |

## 9. Comparison with Stethoscore's accuracy engine

| Capability | Chat-me (Python + Swift core) | Stethoscore (Worker JS + Swift) |
|---|---|---|
| Unit | claim → ≤4 assertions, verdict each | whole item, one P(accurate) (`RP/server/accuracy.js:53-68`) |
| Sources | NCBI metadata, openFDA, uploaded text/PDF | Europe PMC abstracts (reviews/guidelines, 6 y), MedlinePlus, openFDA; 24 h cache, 6 s timeout, failure ⇒ no evidence (`evidence.js:16-134`) |
| Provenance | SHA-256 passage + snapshot, manifests with parent chain, revalidation | URL + title; verdict cached by item hash 365 d, never revalidated (`accuracy.js:322-337`) |
| Judgement | deterministic heuristics, no model | 2–3 free LLM voters (never the writer), third on disagreement (`accuracy.js:184-201,296-306`); "evidence supports" is the voter's own claim |
| Rules | negation, units, dose-frequency/daily-dose arithmetic, population, scope, temporal, anchors | dose ranges (100 drugs), lab plausibility/reference ranges, key–explanation conflicts; byte-identical JS/Swift tables, tested (`accuracy-rules.js`, `AccuracyRules.swift`, `tests/accuracy.test.mjs:86-93`) |
| Scorer | fixed thresholds, uncalibrated | logistic regression, 20 features, weekly CI training on MedQA/MedMCQA, CV, Brier/ECE/AUC, precision-picked cut-offs (`accuracy-model.js`, `bench/train-accuracy.mjs`, `accuracy-model.yml`) |
| Curriculum vs current | first-class dual truth + divergence + study hint | prompt instruction + `source_match` feature |
| Safety / human | 4 keyword tiers, critical escalation; review flag | voter risk 1–4; severe rule ⇒ never Verified; student reports feed training and block "Verified" (`AccuracyLedger.swift:97-98`) |
| Audit / auth / cost | SQLite JSON; none; none | D1 `accuracy_verdicts` by hash; Pro gate; per-account and global daily allowances (`accuracy.js:254-288`) |
| Deployment | none (needs a Python host) | live on Cloudflare free tier, wired into the app |
| Tests | 170 pytest + 35 shared Swift/Python vectors | node tests, Swift parity, benchmarks with Wilson CIs |

Overlap: openFDA; dose/unit checks; both abstain rather than force. Gaps: Chat-me lacks model judgement, abstracts, calibration, deployment; Stethoscore lacks claim decomposition, provenance hashing, a curriculum lane, revalidation, safety escalation, and passage-level entailment.

**Recommendation — spine = Stethoscore's Worker engine; Chat-me folds in as modules, not a service.** It is deployed on a free tier the owner has, needs no computer steps, is wired into the app and owns the only calibrated scorer. Fold-in: (a) port the deterministic guards to JS as a pre-vote claim gate and to the app through the existing rule-parity pattern; (b) adopt evidence hashing + revalidation for `accuracy_verdicts` (ends the 365-day replay); (c) add the curriculum lane and verdict schema (`curriculum_*`, `knowledge_divergence`, `limitations`, contract version) to `/accuracy/check`; (d) use `MedicalVerifierCore` in-app for offline integrity/curriculum checks; (e) make the 35 vectors + Nemesis tests the Worker's regression corpus with JS↔Python parity in CI; (f) keep the Python package as a GitHub Actions batch tool (benchmarks, re-verification). Risks: porting drift (mitigated by shared vectors); LLM votes remain the only semantic judge until an NLI step exists; a fatter Worker. Alternative B (Python service as spine, Worker proxies): richest semantics, but needs a CI-driven free Python host (HF Space/Render: cold starts, sleep, no SLA), duplicates auth/limits, and ships a lane that abstains on relational claims — reject for now. Alternative C (both engines live): violates "do not duplicate" and doubles free-quota spend — reject.

## 10. RECOMMENDED NEXT and INTEGRATION PLAN

Ordered (S ≤ 1 day, M ≤ 1 week, L > 1 week):

1. **S** Treat NULL/empty `passage_sha256` as integrity failure; default `provenance_mode=bound` (`curriculum.py:75`, `provenance.py:12`, `models/claim.py:29`) — E13c, matches Swift.
2. **S** Stop "attack severity high" vetoing entailed claims; route to review instead (`pipeline.py:440`) — revives the current-medical lane (E11c).
3. **S** Anchor on the passage, not `title + passage`; strip openFDA field prefixes (`entailment.py:25`, `semantic_guard.py:180`, `openfda.py:61-65`) — V6/E2.
4. **S** Alias `medications`→`current_medications` in the assertion context check (`pipeline.py:277-282`).
5. **S** NFKC + zero-width stripping before every gate (`terminology/normalize.py:15`) — E6/E6c.
6. **M** Triage table: dosage, contraindication, interaction, diagnosis, treatment ⇒ P0 with mandatory review; expose `severity` (`risk.py`, `question_validation.py:208-214`) — #6/E2.
7. **M** Hash-chained, append-only audit with `prev_hash`/`row_hash` and a verify endpoint (`store.py`) — #10/#24.
8. **M** Review queue with state and a disagreement register; owner alert via Actions — #25.
9. **M** Fetch abstracts (efetch, cached) and publication type; add Europe PMC; read retraction flags (`ncbi.py`) — Stages 1/2, D2.
10. **M** Per-request deadline, async providers, backoff and a per-host breaker (`pipeline.py`, `retrieval/*`) — #22/#23/B11.
11. **M** Auth + tenancy: bearer token, `account_id` on evidence/questions, snapshot ownership filter (`local.py`) — #12/E17.
12. **M** Cross-document recency ordering; claim-date vs evidence-date without needing "currently" (`provenance.py`, `temporal_guard.py`) — #5/#15.
13. **L** Learned entailment: mDeBERTa as a second `EntailmentProvider`; Jev third once §22a thresholds exist; keep the disagree ⇒ UNKNOWN gate.
14. **L** Enum the verdict, one version constant, restore or drop the `COMMERCIAL_USE.md` reference, root README/CLAUDE.md, move files per §8.

INTEGRATION PLAN (plan only):

| Step | Worker (`RP/server`) | iOS app (`RP/ios`) |
|---|---|---|
| 1 Contract | Add `verification_contract_version`, `limitations[]`, `curriculum_assessment`, `knowledge_divergence`, `requires_human_review`, `severity` to `/accuracy/check` replies; keep old fields | `AccuracyStore.CheckReply` decodes them; mismatch ⇒ "Not checked", never "Verified" |
| 2 Claim gate | `claims.js` = JS port of assertions/claim_reasoning/semantic_guard/consistency/temporal guards, run before voting on `itemText` vs source/evidence; a hard mismatch caps the grade at "Check this" | Same guards in `AccuracyRules.swift` (offline), checked by the 35 vectors in `swift-tests.yml` |
| 3 Provenance | Store passage, `passage_sha256`, `snapshot_sha256`, `retrieved_at` per evidence in `accuracy_verdicts`; revalidate openFDA by `set_id` when a cached verdict ages; CHANGED ⇒ re-check | Ledger keeps hashes; "why" sheet shows hash-verified provenance |
| 4 Curriculum lane | For items with a `source`, return `curriculum_*` (hash-bound source blocks from the app) separately from the current-evidence vote | `MedicalVerifierCore` (SPM, MIT) for local `SourceIntegrityChecker`/`CurriculumVerifier`; show "Correct per your source" and "Current evidence" as two lines (`docs/QUESTION_GENERATION_CONTRACT.md`) |
| 5 Triage + review | P0 ⇒ `requires_human_review`; D1 queue; owner digest via Actions | "Needs review" badge; never "Verified" while open |
| 6 Audit | Hash-chained rows for results and failures | — |
| 7 Jev (later) | Adapter behind a provider interface: 1 s timeout, breaker, logged into the chain; secondary signal only | — |
| 8 CI | Node tests + JS↔Python conformance on shared vectors; Python package runs benchmarks in Actions | Existing suites; Playgrounds package must still compile (Swift 5 mode, short expressions) |

Owner constraints respected: Cloudflare free tier, GitHub Actions and on-device only; no paid API, no computer steps, no leaderboards.
