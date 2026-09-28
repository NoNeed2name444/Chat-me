# Assessment v1.1

## Advanced medical reasoning milestone

v1.1 adds a conservative atomic reasoning layer on top of the existing provenance,
extraction, source integrity, and safety gates.

### Atomic-claim decomposition

Claims are decomposed into sentence-scoped atomic assertions. When multiple recognized
relations occur, conjunctions are separated only when both sides carry an explicit
relation.

The verifier tracks relation, polarity, temporal scope, safety relation, and simple
subject/object anchors.

### Anti-Franken-claim protection

A claim cannot combine a subject from one source sentence with an unrelated outcome from
another sentence merely because the aggregate token overlap is high.

Subject and object anchors must align when both sides expose them.

### Causal versus associative reasoning

A causal claim requires explicit causal evidence. An associational statement is not silently
upgraded into causation.

### Temporal reasoning

Past, current, future, before-event, after-event, and explicit duration markers are treated
as scope. A temporal qualifier in the claim that is absent or mismatched in evidence produces
uncertainty rather than support.

### Contraindications and interactions

Contraindication and drug-interaction statements are separate semantic relations. Evidence
about general association does not satisfy either relation.

### Cross-platform parity

The Python verifier and Swift core implement the same v1.1 reasoning categories, with new
shared conformance vectors and dedicated Swift/Python regression tests.

### Boundary

These are deterministic reasoning guards, not a clinically validated medical reasoning
engine. They reduce specific false-support modes and increase abstention when semantics do
not align.
