# Verification contract v1.8

The Python server and iOS client share a versioned verification contract.

## Contract identifier

Current contract: `1.8`

A client sends `verification_contract_version`.

The client also sends explicit question context when an answer is being evaluated. This prevents a dangerous treatment question from becoming invisible to the server safety gate.

A mismatch produces:

`CONTRACT_MISMATCH`

with zero confidence and human review required.

## Required semantic guarantees

Both implementations must preserve:

- curriculum modes: `current_medical`, `curriculum_faithful`, `curriculum_update_aware`
- explicit source provenance
- abstention when evidence is insufficient
- safety escalation for direct treatment actions
- separation of curriculum correctness from current medical currency
- confidence is not a probability until calibrated
- model/provider disagreement resolves to uncertainty
- current medical evidence must not silently rewrite curriculum answers

## Online-first iOS contract

The iOS app should treat the server verifier as the authoritative current-medical lane.

The local iOS core is responsible for source integrity checks, curriculum fidelity checks, immediate safety gates, and question provenance.

If network verification fails, the app may still report the curriculum result, but must label the current-medical result unavailable.

It must never relabel a curriculum result as current medical truth merely because the server is unreachable.

## v1.8 additions

The contract preserves the earlier v1.5/v1.6 ingestion, provenance, temporal, safety, and atomic-claim guarantees, and adds a hardened benchmark boundary:

- versioned benchmark records with expected verdict labels
- deterministic benchmark snapshots with parent-hash lineage
- manifest case-ID binding and recomputed digest verification
- duplicate case-ID and exact duplicate detection
- optional train/test separation enforcement
- train/test source-family, study-family, and canonical-ID overlap detection
- provenance-bound benchmark mode requiring source family, canonical source ID, source snapshot hash, and passage hash
- optional provenance-bound production verification that refuses evidence lacking those bindings
- conservative explicit-date entailment: a dated claim requires matching dated evidence
- explicit alias-only entity equivalence in atomic subject/object alignment
- descriptive subgroup and abstention summaries without claiming clinical validity
- supplied-confidence bin diagnostics without claiming calibration

These controls are deterministic engineering checks. They do not establish clinical validity, representativeness, independence, calibration, or regulatory compliance.

## Non-goals

A clean verifier result is not a diagnosis, treatment recommendation, medical-device authorization, or guarantee of correctness. High-risk or unresolved cases must remain reviewable.
