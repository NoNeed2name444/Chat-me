from dataclasses import dataclass
from collections.abc import Callable

from app.validation.benchmark import (
    BenchmarkCase,
    binary_truth,
    brier_score,
    evaluate_binary,
    expected_calibration_error,
    validate_case,
)

@dataclass(frozen=True)
class BenchmarkRun:
    cases_seen: int
    cases_valid: int
    validation_errors: dict[str, tuple[str, ...]]
    metrics: dict

def run_benchmark(
    cases: list[BenchmarkCase],
    verifier: Callable[[str], object],
):
    predictions = []
    probabilities = []
    truths = []
    validation_errors = {}

    for case in cases:
        errors = validate_case(case)
        if errors:
            validation_errors[case.case_id] = tuple(errors)
            continue

        result = verifier(case.claim)
        verdict = getattr(result, "verdict", None)
        confidence = float(getattr(result, "confidence", 0.0))

        predictions.append(
            (confidence, verdict, case.expected_verdict)
        )

        truth = binary_truth(case.expected_verdict)
        if truth is not None:
            probabilities.append(confidence)
            truths.append(truth)

    metrics = evaluate_binary(predictions)
    metrics["brier_score"] = brier_score(
        probabilities,
        truths,
    )
    metrics["expected_calibration_error"] = expected_calibration_error(
        probabilities,
        truths,
    )

    return BenchmarkRun(
        cases_seen=len(cases),
        cases_valid=len(cases) - len(validation_errors),
        validation_errors=validation_errors,
        metrics=metrics,
    )
