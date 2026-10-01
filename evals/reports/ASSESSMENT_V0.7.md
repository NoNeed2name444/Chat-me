# Assessment v0.7

## Semantic and extraction milestone

Added safeguards for uploaded medical study material beyond simple entailment.

### Dose and frequency semantics

The verifier now distinguishes:
- 500 mg twice daily
- 500 mg once daily
- 1000 mg daily

Simple equivalent daily mass doses can be recognized conservatively. Frequency mismatches are rejected when the daily-dose equivalence does not resolve them.

### Conditional and universal scope

Claims cannot invent stronger scope than the source.

Examples:
- 'works in all patients' requires explicit universal source scope.
- 'works only in adults' requires explicit exclusive source scope.

### Extraction quality

Evidence now stores extraction quality and extraction warnings.
Sources below the configured quality threshold or carrying extraction warnings
are not auto-validated.

### Conflicting curriculum material

When supplied curriculum chunks support and contradict the same answer, the result becomes an explicit conflict requiring review.

### Local evidence integrity

The local provider's source column mapping was corrected and regression-tested; it now preserves exact locator, canonical ID, dates, extraction quality, and extraction warnings.

## Validation boundary

The new semantic guards remain heuristic. They are intended to reduce unsafe false support, not to prove clinical truth.