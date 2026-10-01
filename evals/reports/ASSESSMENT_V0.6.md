# Assessment v0.6

## Cross-platform conformance milestone

Python and iOS now consume the same adversarial curriculum-verification corpus.

The shared corpus covers:
- ordinary supported curriculum answers
- negation flips
- numeric scope changes
- medication unit mismatches
- equivalent unit spellings
- population widening
- safety/effectiveness predicate mismatch
- double negation
- treatment actions in the question
- treatment actions in the answer
- certainty escalation
- prompt injection embedded in source text
- missing curriculum sources
- tampered source passages

Both platforms are expected to agree on verdict class and review requirement.

## CI

The repository CI now has separate Python and Swift test jobs. The Swift job runs the Swift Package test suite, including the shared conformance corpus.

## Nemesis findings fixed

The conformance pass exposed and fixed:
1. Python could accept a changed stored curriculum passage while Swift rejected it.
2. Python and Swift differed on medication unit semantics.
3. Recursive Swift JSON needed an explicit indirect enum.
4. Swift substring matching was made explicit for action detection.

## Current limitation

The latest branch has not yet produced a GitHub Actions run for the current head, so CI execution remains unverified from this environment.

The conformance corpus protects platform consistency; it does not constitute clinical validation.