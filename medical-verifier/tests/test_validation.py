from app.validation.benchmark import (
    BenchmarkCase,
    evaluate_binary,
    expected_calibration_error,
    validate_case,
)
from app.validation.calibration import IsotonicCalibrator

def test_benchmark_case_requires_clinician_review():
    case = BenchmarkCase(
        case_id="x",
        claim="Medical claim",
        expected_verdict="SUPPORTED",
        expected_support_probability=0.8,
        risk_level="low",
        clinician_reviewed=False,
    )
    assert "case_not_clinician_reviewed" in validate_case(case)

def test_binary_metrics():
    rows = [
        (0.9, "SUPPORTED", "SUPPORTED"),
        (0.2, "CONTRADICTED", "CONTRADICTED"),
        (0.8, "SUPPORTED", "CONTRADICTED"),
        (0.1, "INSUFFICIENT_EVIDENCE", "SUPPORTED"),
    ]
    metrics = evaluate_binary(rows)
    assert metrics["tp"] == 1
    assert metrics["tn"] == 1
    assert metrics["fp"] == 1
    assert metrics["abstain"] == 1

def test_isotonic_calibrator_is_monotonic():
    calibrator = IsotonicCalibrator().fit(
        [0.2, 0.4, 0.6, 0.8],
        [0, 0, 1, 1],
    )
    values = [
        calibrator.predict(x)
        for x in [0.2, 0.4, 0.6, 0.8]
    ]
    assert values == sorted(values)

def test_ece_exists():
    value = expected_calibration_error(
        [0.1, 0.9],
        [0, 1],
        bins=2,
    )
    assert value is not None
