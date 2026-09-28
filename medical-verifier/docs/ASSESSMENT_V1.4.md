# Assessment v1.4

## Benchmark infrastructure

v1.4 adds a versioned benchmark-record schema with explicit expected verdicts and optional
subgroup metadata for risk level, population/subgroup, and source family.

Evaluation can now summarize abstention and exact-match observations by subgroup without
turning them into clinical rankings.

## Reliability diagnostics

The benchmark layer can bin supplied verifier confidence values and report observed match
rates. These are descriptive diagnostics only; they are not calibration claims and do not
establish clinical safety or clinical performance.

## Nemesis expansion

Added regression attacks for:
- entity substitution
- interaction-pair substitution
- contraindication-population substitution
- date-like temporal mismatch

The verifier should abstain rather than silently treating these mutations as equivalent.

## Safety boundary

A benchmark schema is infrastructure, not validation. Clinical use still requires an
independently designed, clinician-adjudicated dataset, appropriate sampling, prospective
evaluation, calibration analysis, and applicable quality/regulatory controls.
