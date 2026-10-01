# Assessment v0.4

## New milestones

### Dual-truth curriculum architecture

The verifier now separates curriculum fidelity from current medical evidence.

Curriculum modes:
- current_medical
- curriculum_faithful
- curriculum_update_aware

A supplied source can remain the educational reference even when newer evidence
disagrees. Divergence is surfaced rather than silently rewriting the expected
answer.

### Question-generation safety

Generated questions are source-bound.

Each question records:
- curriculum snapshot ID
- source evidence IDs
- source locators
- document versions
- passage hashes
- original source-file hashes when supplied
- validation status
- supporting source IDs
- review requirement

Unsupported generated answers are not auto-validated as curriculum-grounded.

### Source revalidation

High-risk and curriculum-conflict verification can revalidate bound regulatory
source snapshots.

Changed, missing, or failed revalidation blocks automatic current-medical
support. Curriculum grading can remain source-faithful when the curriculum
snapshot itself remains unchanged.

### Benchmark execution

A deterministic benchmark runner now evaluates clinician-reviewed cases using
sensitivity, specificity, PPV, NPV, abstention rate, Brier score, and expected
calibration error.

## Important limitation

The framework still does not have a clinician-labeled production benchmark or
a learned medical entailment model. Those are required before treating the
confidence output as clinically calibrated.

External-source access and licensing remain source-specific.
