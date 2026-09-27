# Deep-Research Comparison: Verification Layer vs. verification-layer-adversarial-50

Date: 2026-09-27

Repository: NoNeed2name444/Chat-me
Branch audited: verification-layer-adversarial-50
Scope: medical-verifier/app/verification/, tests, and verification documentation.

## Executive conclusion

The earlier deep-research report was intentionally repository-agnostic, so it recommended a broad defense-in-depth program. The branch already implements a substantial subset of that program and is materially more advanced than a generic baseline.

The branch currently has:
- atomic claim decomposition;
- adversarial claim inspection;
- deterministic safety/policy checks;
- provenance validation;
- temporal supersession handling;
- source-family and publisher-based independence tracking;
- contradiction handling;
- semantic consistency checks;
- metamorphic helpers;
- risk/context gating;
- abstention;
- reliability scoring/reporting;
- an adversarial 50-claim benchmark;
- explicit acceptance gates;
- a nemesis/red-team playbook.

The main gap is therefore not "add more verification layers" in the abstract. The next phase should make the existing layers correct, composable, independently validated, and difficult to game.

## Highest-priority findings

### P0 — Semantic regexes appear double-escaped

semantic_guard.py and contradiction.py contain raw regex patterns using doubled backslashes before b, and the quantity/percentage patterns use the same form.

In Python regex syntax, a raw string containing two backslashes before b searches for a literal backslash sequence rather than the regex word-boundary token. That means the semantic guard's negation, quantity, percentage, and contradiction detection can silently fail.

This is especially important because the existing tests explicitly depend on those detectors.

Required fix:
- Use single regex word-boundary escapes in raw strings.
- Add direct tests for negation, units, percentages, contradiction, double negation, punctuation/whitespace variants, and mixed case.

### P0 — The benchmark is not yet a sufficient semantic oracle

test_adversarial_50_benchmark.py is valuable, but it uses one evidence snippet per claim and monkeypatches retrieval. It verifies pipeline behavior against a hand-authored reference set, not against an independently adjudicated clinical gold standard.

The 10 negative cases are useful because they deliberately avoid obvious trigger words, but the benchmark cannot establish clinical truth or production false-negative rates.

Required fix:
- Create a versioned, clinician-adjudicated corpus containing claim, atomic assertions, gold entailment label, evidence IDs, population, temporal validity, modality, numeric/unit constraints, adjudication notes, and disagreement state.
- Keep the current 50-case benchmark as a fast regression suite.

### P1 — Evidence entailment is still primarily lexical

contradiction.py classifies evidence largely through token overlap plus a negation check. reliability.py computes relevance with token intersection.

This is a weak semantic oracle. It can accept text that shares vocabulary while failing to establish the relationship asserted by the claim.

Add explicit attacks for:
- same drug and outcome but opposite direction;
- association used to support causation;
- subgroup result used as a general-population claim;
- relative risk used as absolute risk;
- numerator/denominator changes;
- "not statistically significant" treated as "no effect";
- surrogate outcome treated as clinical outcome.

Required fix:
Separate lexical retrieval/relevance, structured semantic checks, entailment/contradiction assessment, source-quality assessment, and policy decision. No stage should silently substitute for another.

### P1 — Independence can both under-count and over-count

Current independence is mostly source_family plus publisher. This is conservative for multiple studies from one publisher, but it can fail when the same study appears through different publishers or databases.

The deduplicate key can also miss duplicates when the same underlying study has different titles or publisher representations.

Required fix:
Use a normalized evidence identity hierarchy:
1. DOI / PMID / regulatory identifier / guideline identifier;
2. normalized canonical URL;
3. study registration identifier;
4. content fingerprint;
5. publisher fallback only when no stronger identity exists.

Independence should be based on underlying evidence lineage, not publisher alone.

### P1 — Temporal supersession can be too coarse

apply_temporal_supersession() falls back to source_family or publisher when a canonical identifier is absent. That can cause unrelated evidence within the same family or publisher to be compared as though it were a versioned series.

Required fix:
Only apply supersession when there is a demonstrable version lineage: same canonical identifier, same guideline/label family, explicit replacement metadata, or validated canonical URL lineage. Otherwise mark temporal uncertainty rather than penalizing the item automatically.

### P1 — Two independent sources is not equivalent to independent clinical confirmation

The policy requires source-family and independence-group thresholds, which is a good safety gate, but a pair of weak sources can satisfy structural independence.

The next policy version should require quality-weighted diversity across evidence lineage, authority, directness of entailment, population match, temporal validity, contradiction status, study design, and applicability.

### P1 — Confidence is explicitly a policy score, not calibrated probability

The branch correctly labels confidence as a policy score. The deep-research report recommended calibration, but the branch documentation says calibration is future work.

Required fix:
Add calibration evaluation against adjudicated labels:
- reliability diagram;
- Brier score;
- expected calibration error;
- calibration by risk tier;
- calibration by claim type;
- abstention-aware metrics.

Do not expose a probability interpretation until calibrated and validated.

## What the branch already covers well

| Research recommendation | Branch status | Evidence |
|---|---|---|
| Claim decomposition | Implemented | assertions.py + pipeline |
| Adversarial input inspection | Implemented | adversarial.py |
| Safety escalation | Implemented | critical severity + policy |
| Input/context gating | Implemented | risk.py, assertions.py |
| Provenance requirements | Implemented | provenance.py |
| Temporal handling | Partially implemented | apply_temporal_supersession() |
| Contradiction handling | Implemented, but lexical | contradiction.py |
| Semantic checks | Implemented, but fragile | semantic_guard.py, consistency.py |
| Independent evidence | Implemented, but coarse | reliability.py |
| Abstention | Implemented | policy.py, pipeline |
| Metamorphic testing | Helpers/tests exist | metamorphic.py, test_metamorphic.py |
| Adversarial benchmark | Implemented | test_adversarial_50_benchmark.py |
| Acceptance criteria | Implemented | ACCEPTANCE_GATES.md |
| Nemesis methodology | Implemented | NEMESIS_PLAYBOOK.md |
| Formal verification | Not evident | no formal spec/model-checking layer found in audited verification tree |
| Grammar/property fuzzing | Not evident | no fuzz harness found in audited tests |
| Mutation testing | Not evident | no mutation configuration found in audited tree |
| Differential implementation testing | Not evident | no independent verifier/reference implementation found |
| Independent clinical adjudication | Not evident | benchmark is hand-authored |
| Production/shadow validation | Not evident | docs mention it as a gate, but no artifact was found in audited tree |
| Calibrated confidence | Not implemented | confidence is explicitly a policy score |

## Nemesis attacks that should be added next

Semantic:
- "X is not unsafe" vs "X is safe".
- "No evidence that X causes Y" vs "evidence that X does not cause Y".
- Causal claim supported only by observational association.
- Conditional claim where the condition is omitted.
- "May reduce" converted into "reduces".
- "Common" converted into a numeric prevalence claim.
- Directional polarity swaps that evade simple opposite-word pairs.

Numeric:
- 5% vs 5 percentage points.
- 0.5 mg vs 5 mg.
- mg vs mcg.
- mg/dL vs mmol/L.
- relative vs absolute risk.
- CI bounds copied incorrectly.
- NNT/NNH arithmetic changes.
- frequency changes: once daily vs twice daily.
- decimal/comma variants.

Population:
- adult evidence used for pediatric claim;
- pregnancy evidence used for non-pregnancy claim;
- renal impairment omitted;
- route mismatch;
- indication mismatch;
- disease-stage mismatch;
- concurrent medication omitted.

Evidence lineage:
- same study via two publishers;
- preprint + final article;
- systematic review + included trial;
- guideline summary + source guideline;
- duplicate URL with tracking parameters;
- mirrored regulatory label;
- updated label mixed with superseded label.

Adversarial input:
- Unicode confusables;
- zero-width characters;
- excessive whitespace;
- punctuation insertion;
- long irrelevant context;
- prompt injection;
- authority pressure;
- fake citation identifiers;
- citation to a related but non-entailing source.

## Recommended architecture for the next version

Treat verification as a typed evidence graph rather than a linear score.

Claim -> Atomic Assertion -> Evidence -> Entailment -> Contradiction -> Provenance -> Independence -> Applicability -> Policy

Each edge should be explicit and auditable.

A final SUPPORTED result should require:
1. every atomic assertion independently cleared;
2. no unresolved material contradiction;
3. evidence lineage validated;
4. population/context match;
5. temporal validity;
6. direct semantic entailment;
7. required independent corroboration;
8. policy/risk gate pass;
9. no critical adversarial finding;
10. reproducible provenance.

Anything else should abstain or escalate.

## Acceptance gates to strengthen

The existing ACCEPTANCE_GATES.md is directionally correct. Add hard gates for:
- semantic regex test suite passes;
- mutation score on verification modules;
- property-based tests for normalization and evidence aggregation;
- differential tests against a reference implementation;
- duplicate-lineage attack suite;
- calibrated confidence metrics;
- adversarial regression corpus with frozen seeds;
- reproducibility from a clean environment;
- deterministic decision trace for every verdict;
- explicit "why this evidence supports the assertion" records;
- fail-closed behavior when a verifier component errors or returns malformed evidence.

## Priority roadmap

### Phase 0 — correctness
- Fix and test regex escaping.
- Run the full branch test suite in CI.
- Add regression tests for every existing semantic guard.
- Fail closed on malformed verifier state.

### Phase 1 — semantic soundness
- Replace token-overlap entailment with structured semantic comparison.
- Add numeric/unit/direction/modality/population relation objects.
- Make contradiction a first-class assertion-level result.

### Phase 2 — evidence lineage
- Normalize DOI/PMID/regulatory/guideline identifiers.
- Build duplicate and same-study lineage detection.
- Make supersession lineage explicit.

### Phase 3 — adversarial evaluation
- Expand 50 claims into a versioned adversarial corpus.
- Add fuzz/property/metamorphic testing.
- Add mutation testing against every verification module.
- Add differential/reference implementation checks.

### Phase 4 — calibration and validation
- Build clinician-adjudicated ground truth.
- Measure precision, recall, false-negative rate, false-positive rate, abstention rate, calibration, and subgroup performance.
- Run prospective/shadow-mode validation before treating metrics as deployment evidence.

## Bottom line

The branch is not missing the basic defense-in-depth concepts from the research. It already implements many of them.

The largest remaining risk is false confidence in the correctness of the layers themselves. In particular, the apparent regex escaping defect, lexical entailment logic, coarse evidence independence, and lack of independently adjudicated validation mean that a large number of layers can exist while still sharing the same semantic blind spots.

The next nemesis cycle should therefore attack the verifier's assumptions and interfaces between layers, not merely add more checks.

