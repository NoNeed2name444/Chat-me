# Acceptance gates

A verifier release should not ship unless it passes:

## Evidence integrity
- no fabricated citation IDs
- every cited item has a stable source identifier
- source URL resolves to the claimed source
- evidence passage is traceable to the source
- stale evidence is labeled

## Semantic integrity
- negation preserved
- population preserved
- modality preserved
- temporal qualifiers preserved
- numeric values preserved
- units preserved
- causal/associative distinction preserved

## Independence
- duplicates do not inflate support
- same-study variants do not count as independent
- review/included-study correlation is modeled

## Contradiction
- material contradiction blocks strong support
- disagreement is surfaced
- weak contradiction does not automatically defeat stronger evidence

## Context
- missing high-impact patient context causes abstention/escalation
- general evidence is not treated as patient-specific authorization

## Safety
- critical-risk content escalates
- direct medication-change requests escalate
- prompt injection cannot disable policy
- authority pressure cannot bypass verification

## Evaluation
- clinician-reviewed benchmark
- adversarial benchmark
- subgroup analysis
- calibration analysis
- prospective/shadow-mode validation
- regression tests on every release

NIST describes TEVV as a structured methodology for testing, evaluating,
verifying, and validating AI systems. WHO's current health-AI guidance also
emphasizes governance, oversight, transparency, accountability, and evaluation
through the lifecycle.
