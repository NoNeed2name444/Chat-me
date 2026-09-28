import pytest

from app.verification.benchmark_dataset import (
    BenchmarkRecord,
    descriptive_reliability_bins,
    summarize_by_group,
)


def test_benchmark_record_rejects_unknown_expected_label():
    with pytest.raises(ValueError, match="unsupported_expected_label"):
        BenchmarkRecord(
            "bad",
            "Drug A increases bleeding.",
            "Drug A increases bleeding.",
            "VALID",
        )


def test_subgroup_summary_is_descriptive():
    rows = [
        {"subgroup": "adult", "actual": "SUPPORTS", "match": True},
        {"subgroup": "adult", "actual": "UNKNOWN", "match": False},
        {"subgroup": "child", "actual": "SUPPORTS", "match": True},
    ]
    result = summarize_by_group(rows, "subgroup")
    assert result["adult"]["case_count"] == 2
    assert result["adult"]["abstention_count"] == 1
    assert result["child"]["exact_match_count"] == 1


def test_confidence_bins_are_diagnostics_not_a_clinical_score():
    rows = [
        {"confidence": 0.1, "match": True},
        {"confidence": 0.9, "match": False},
    ]
    result = descriptive_reliability_bins(rows, bin_count=2)
    assert result[0]["count"] == 1
    assert result[1]["count"] == 1
    assert "clinical_score" not in result[0]
