# iOS on-device verifier

The iOS target is an offline-first safety kernel for curriculum fidelity and
source provenance.

It intentionally does not claim current medical truth.

## Local responsibilities

- source and passage SHA-256 validation
- immutable curriculum snapshot handling
- source-bound question validation
- deterministic negation, numeric, relation, and population checks
- high-risk/clinical-action escalation
- question provenance construction
- abstention when evidence is absent or ambiguous

## Network responsibilities

A separate network layer may retrieve current evidence and perform revalidation.
That layer should feed results into the same versioned contract as the local
core.

## Important boundary

The local core must never convert an uploaded curriculum into authorization for
diagnosis, prescribing, dosage changes, or treatment.

The device can say:
"supported by the supplied curriculum."

It should not infer:
"medically current."

## Deployment recommendation

Keep the local core small and deterministic.

A future Core ML/NLI provider can be added behind the same provider contract,
but model disagreement must result in uncertainty rather than a forced answer.
