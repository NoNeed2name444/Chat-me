# Benchmark runner

The validation runner provides a deterministic execution harness for
clinician-reviewed benchmark cases.

The harness separates:
- case validation
- verifier execution
- binary safety and entailment metrics
- abstention measurement
- probability calibration metrics

Keep development, calibration, and locked evaluation datasets separate.

The runner does not create clinical ground truth. Ground truth must be supplied
by the benchmark owner and marked as clinician reviewed.

A strong benchmark score is evidence about the tested benchmark population. It
is not proof of safe performance in every medical setting.
