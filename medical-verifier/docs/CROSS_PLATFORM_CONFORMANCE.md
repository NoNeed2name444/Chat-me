# Cross-platform conformance

Python and iOS share a single adversarial curriculum-verification corpus.

The corpus lives at:

`ios/MedicalVerifierCore/Tests/MedicalVerifierCoreTests/Resources/conformance_vectors.json`

Python tests load that exact file from the repository. Swift tests load the same
file as a Swift Package test resource.

## Purpose

The goal is semantic compatibility, not implementation identity.

Both platforms must agree on:
- supported curriculum answers
- unsupported answers
- safety escalation
- source integrity failure
- missing-source abstention
- numeric/unit mismatches
- population mismatches
- predicate/property mismatches
- double-negation handling
- certainty escalation
- prompt-injection-as-data behavior

When a shared vector changes, both test targets exercise the changed case.

## Release gate

A verifier release should not proceed when Python and iOS disagree on the
status or review requirement for a shared safety/conformance case.

The corpus is not a substitute for clinician-reviewed medical ground truth.
It is a platform-consistency guard.
