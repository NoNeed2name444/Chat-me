from datetime import date

from app.verification.benchmark_dataset import BenchmarkRecord
from app.verification.dataset_integrity import (
    case_fingerprint,
    find_cross_split_duplicates,
    find_duplicate_cases,
    summarize_integrity,
)
from app.verification.temporal_normalization import (
    extract_explicit_dates,
    resolve_relative,
    temporal_signature,
)


def record(case_id, claim, evidence):
    return BenchmarkRecord(case_id, claim, evidence, "SUPPORTS")


def test_duplicate_detection_is_deterministic():
    cases = [
        record("a", "Drug A increases bleeding.", "Drug A increases bleeding."),
        record("b", "Drug A increases bleeding.", "Drug A increases bleeding."),
    ]
    findings = find_duplicate_cases(cases)
    assert [(f.case_id, f.kind) for f in findings] == [("b", "duplicate_case")]
    assert case_fingerprint(cases[0].claim, cases[0].evidence) == case_fingerprint(
        cases[0].claim, cases[0].evidence
    )


def test_train_test_duplicate_is_flagged():
    train = [record("train-1", "Drug A causes bleeding.", "Drug A causes bleeding.")]
    test = [record("test-1", "Drug A causes bleeding.", "Drug A causes bleeding.")]
    findings = find_cross_split_duplicates(train, test)
    assert findings[0].kind == "train_test_duplicate"


def test_integrity_summary_has_no_clinical_score():
    result = summarize_integrity([])
    assert result == {"finding_count": 0, "by_kind": {}, "findings": []}
    assert "clinical_score" not in result


def test_explicit_date_normalization():
    assert extract_explicit_dates("Evidence dated 2024-03-01.") == (date(2024, 3, 1),)


def test_invalid_date_is_not_invented():
    assert extract_explicit_dates("Evidence dated 2024-02-31.") == ()


def test_relative_window_requires_reference_date():
    assert resolve_relative("last 30 days", date(2026, 9, 28)) == (
        date(2026, 8, 29),
        date(2026, 9, 28),
    )


def test_temporal_signature_is_explicit_only():
    signature = temporal_signature("Previously, evidence was dated 2024-03-01.")
    assert "previously" in signature["markers"]
    assert signature["dates"] == ("2024-03-01",)
