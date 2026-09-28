# Assessment v0.5

## iOS online-first milestone

Added a Swift Package for iOS with curriculum/source-bound verification, provenance and SHA-256 integrity checks, safety and adversarial gates, question artifact provenance, HTTPS-only server API client, bearer authentication support, JSON context parity with the Python API, explicit client/server verification contract versioning, and online-first orchestration separating curriculum and current-medical results.

## Nemesis result

The initial iOS implementation exposed gaps around answer-side medication actions, medication/action ordering, safety vs effectiveness predicates, double negation, numeric and unit substitution, certainty escalation, tampered curriculum sources, contract drift, insecure transport, and network outage semantics. These were patched with regression coverage.

## Validation status

The Swift Package and test target are present in the repository. Full iOS execution requires an Apple/Xcode environment; the available repository CI has not yet reported a workflow run for the current branch head.

The iOS layer is an engineering component, not a clinically validated medical device.
