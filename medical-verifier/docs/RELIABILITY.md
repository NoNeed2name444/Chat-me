# Reliability architecture v0.2

The verifier now uses multiple independent dimensions instead of treating one
retrieved document as sufficient.

## 1. Claim decomposition

The input is split into atomic assertions. Each assertion gets:
- polarity
- modality
- subject terms
- required context

This prevents one easy-to-verify clause from incorrectly validating an entire
compound claim.

## 2. Evidence quality

Every evidence item receives separate signals:
- source authority
- lexical relevance
- temporal recency
- passage availability
- provenance URL

The signals are combined into a quality score.

## 3. Independence

Two records from the same publisher/source family do not automatically count as
two independent confirmations.

The engine tracks:
- source family
- publisher
- canonical identifier
- independence group

## 4. Cross-family corroboration

For moderate/high-risk claims, support normally must come from at least two
source families and two independent evidence groups.

## 5. Contradiction handling

A single conflicting record is not blindly counted as equivalent to a
high-authority source, but material contradictions prevent a strong-support
verdict until policy thresholds are met.

## 6. Temporal validity

Evidence is penalized when it is stale. Regulatory material can carry an
effective date, while literature can carry a publication date.

Because medicine changes, the result stores the knowledge snapshot and source
dates.

## 7. Context gating

The engine can refuse verification when required context is missing, e.g.
age, medications, gestational age, or renal function.

## 8. Abstention

Failure to meet the evidence threshold produces INSUFFICIENT_EVIDENCE rather
than forcing a yes/no answer.

## 9. Validation

Confidence remains a policy score until calibrated against clinician-reviewed
ground truth. NIST's current TEVV guidance emphasizes structured test,
evaluation, verification and validation; medical deployment also requires
clinical governance and prospective validation.
