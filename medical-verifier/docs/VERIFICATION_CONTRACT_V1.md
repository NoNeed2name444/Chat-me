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

- curriculum modes:
  - current_medical
  - curriculum_faithful
  - curriculum_update_aware
- explicit source provenance
- abstention when evidence is insufficient
- safety escalation for direct treatment actions
- separation of curriculum correctness from current medical currency
- confidence is not a probability until calibrated
- model/provider disagreement resolves to uncertainty
- current medical evidence must not silently rewrite curriculum answers

## Online-first iOS contract

The iOS app should treat the server verifier as the authoritative current-medical
lane.

The local iOS core is responsible for:
- source integrity checks
- curriculum fidelity checks
- immediate safety gates
- question provenance

If network verification fails, the app may still report the curriculum result,
but must label the current-medical result unavailable.

It must never relabel a curriculum result as current medical truth merely
because the server is unreachable.


## v1.5 additions

The contract now preserves:

- explicit page, section, block-type, and block-index provenance for extracted curriculum evidence
- explicit document precedence groups and ranks; conflicting versions are not silently resolved without an explicit precedence declaration
- conditional scope across sentence boundaries
- conservative equivalence for direct dose, concentration-volume dose, and weight-based dose arithmetic


## v1.5 additions

The contract now supports:
- language metadata for extracted source blocks
- explicit table/figure/caption linkage metadata
- deterministic source manifests containing source/passage hashes and structural provenance
- parent-linked manifest lineage verification
- conservative supported Spanish and French semantic normalization

Manifest verification is cryptographic hash verification. No detached digital
signature is claimed unless an external signing/trust system is configured.


## v1.5 additions

The contract now includes a bounded PDF-ingestion lane with:

- raw PDF SHA-256 provenance
- page and extracted-block evidence records
- conservative caption/table heuristics
- explicit extraction-quality warnings
- source-manifest persistence and parent-manifest continuity
- parser-local block relationships rewritten to persisted evidence IDs

PDF structure is never treated as native figure/table truth unless the extractor
can establish that relationship explicitly.


## v1.6 additions

The reasoning contract now requires conservative atomic-claim alignment for:

- subject and object anchors
- causal versus associational relations
- temporal scope
- contraindication semantics
- drug-drug interaction semantics
- atomic polarity

An answer that can only be assembled by combining unrelated source sentences is not
treated as supported. Missing or mismatched temporal/safety relationships remain
uncertain and require review.

The contract still does not imply clinical validation or calibrated probability.


## v1.8 additions

The benchmark contract now supports:

- versioned benchmark records with explicit expected verdict labels
- deterministic benchmark snapshot manifests with parent-hash lineage
- exact duplicate and train/test duplicate detection
- descriptive subgroup and abstention summaries
- supplied-confidence bin diagnostics without a calibration claim
- conservative explicit date normalization requiring a reference date for relative windows
- explicit alias-only entity normalization with unknown/near-spelling entities left unresolved
- an API integrity-validation lane that returns the snapshot digest and deterministic findings

## v1.8 additions

The benchmark/provenance boundary now supports:

- snapshot schema enforcement with deterministic JSON import/export
- manifest case-ID binding and recomputed digest verification
- duplicate case-ID detection
- optional train/test separation enforcement
- train/test source-family, study-family, and canonical-ID overlap detection
- provenance-bound benchmark mode requiring source family, canonical source ID, source snapshot hash, and passage hash
- optional provenance-bound production verification that refuses evidence lacking those bindings
- conservative explicit-date entailment: a dated claim requires matching dated evidence
- explicit alias-only entity equivalence in atomic subject/object alignment

These controls remain deterministic engineering checks. They do not establish clinical validity,
representativeness, independence, calibration, or regulatory compliance.
