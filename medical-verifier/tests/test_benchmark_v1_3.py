from app.verification.benchmark import BenchmarkCase, evaluate


def test_benchmark_reports_abstention_without_scoring_clinical_quality():
    result = evaluate(
        [
            BenchmarkCase(
                "supported",
                "Drug A increases bleeding.",
                "Drug A increases bleeding.",
                "SUPPORTS",
            ),
            BenchmarkCase(
                "causal_attack",
                "Drug A causes bleeding.",
                "Drug A is associated with bleeding.",
                "UNKNOWN",
            ),
        ]
    )

    assert result["benchmark_version"] == "1.3"
    assert result["case_count"] == 2
    assert result["abstention_count"] == 1
    assert "score" not in result
    assert "clinical_validity" not in result
