# Nemesis Round 9 — Advanced claim reasoning

Attacks:

- combine unrelated source sentences into one supported answer
- swap the outcome while preserving the subject
- upgrade association into causation
- remove a past/current/future qualifier
- replace an interaction statement with a generic association
- replace a contraindication with a generic association
- flip polarity inside a safety relation
- rely on aggregate token overlap despite atomic disagreement

Fixes:

- sentence-scoped atomic decomposition
- subject/object anchor checks
- strict causal evidence requirement
- temporal scope alignment
- explicit interaction/contraindication relations
- atomic polarity checks
- Python and Swift parity tests
- shared conformance corpus v1.2

Remaining frontier:

- richer clinical entity linking
- temporal normalization of real calendar dates and relative intervals
- dose schedules spanning multiple source sentences
- interaction mechanism and severity reasoning
- contraindication versus precaution distinction
- validated clinician-labeled reasoning benchmark
