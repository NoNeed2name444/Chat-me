# v1.2–v1.3 Assessment

## v1.2 — adversarial mutation testing

The verifier now has a deterministic mutation harness that systematically perturbs
claims/evidence through polarity, relation, subject, object, and temporal mutations.

The harness records raw verifier outcomes and reasons. It is intended for regression
testing and adversarial engineering, not as a clinical accuracy claim.

## v1.3 — benchmark evaluation

A neutral benchmark runner now evaluates a supplied corpus and reports:

- expected and observed verdict distributions
- abstention count and rate
- per-case outcomes
- verifier reasons

It deliberately does not calculate a clinical score, clinical validity claim, or ranking.

## Safety boundary

A high abstention rate is not interpreted as clinical performance. A low mutation failure
rate is not interpreted as clinical safety. Clinical deployment would require independently
designed, clinician-adjudicated datasets, prospective validation, calibration analysis, and
appropriate regulatory/quality controls.

## Nemesis targets

The combined rounds target:

1. negation flips
2. causal/association upgrades
3. subject swaps
4. object swaps
5. temporal swaps
6. population swaps
7. numeric changes
8. interaction/contraindication relation swaps
9. unrelated sentence recombination
10. mutation reproducibility

All attacks should fail closed to UNKNOWN unless the mutated evidence independently entails
the claim.
