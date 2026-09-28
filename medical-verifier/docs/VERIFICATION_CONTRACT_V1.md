# Verification contract v1.0

The Python server and iOS client share a versioned verification contract.

## Contract identifier

Current contract: `1.0`

A client sends `verification_contract_version`.

The server echoes its supported contract version.

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
