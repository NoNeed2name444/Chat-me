# iOS online-first verifier

The iOS target is an online-first client for the medical verifier.

The server is authoritative for current-medical evidence. The iOS core remains
responsible for immediate source-integrity checks, curriculum fidelity, safety
gates, and question provenance.

## Local responsibilities

- source and passage SHA-256 validation
- immutable curriculum snapshot handling
- source-bound question validation
- deterministic negation, numeric, relation, population, and certainty checks
- high-risk/clinical-action escalation
- question provenance construction
- fail-closed behavior when a curriculum source is tampered with

## Network responsibilities

- current PubMed/FDA evidence retrieval
- source revalidation
- multi-source evidence aggregation
- current-medical verdicts
- adversarial and policy gates
- calibrated/benchmark-backed future model outputs

## Online-first flow

1. iOS validates the supplied source snapshot locally.
2. Unsafe or tampered inputs stop before network verification.
3. iOS sends the claim/answer and curriculum identifiers to the server.
4. The server returns the current-medical result under the same verification
   contract version.
5. The app displays curriculum correctness and current-medical status as
   separate fields.
6. A network failure may leave the curriculum result available, but the
   current-medical status must be shown as unavailable.

The app must never convert "supported by the supplied curriculum" into
"medically current."

## Transport and contract safety

The reference API client requires HTTPS and supports bearer authentication.

The server and client exchange an explicit verification contract version.
A mismatch produces `CONTRACT_MISMATCH` and no medical verification decision.

## ML boundary

A future Core ML/NLI provider can be used locally for speed or offline
assistance, but it must remain subordinate to the same verification contract.
Provider disagreement should resolve to uncertainty rather than a forced answer.
