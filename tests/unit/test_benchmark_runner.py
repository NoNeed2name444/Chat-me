from dataclasses import dataclass

from evals.suites.validation.benchmark import BenchmarkCase
from evals.suites.validation.runner import run_benchmark

@dataclass
class Result:
    verdict: str
    confidence: float

def test_benchmark_runner_collects_metrics():
    cases = [
        BenchmarkCase(
            case_id="1",
            claim="claim one",
            expected_verdict="SUPPORTED",
            expected_support_probability=0.9,
            risk_level="low",
            clinician_reviewed=True,
        ),
        BenchmarkCase(
            case_id="2",
            claim="claim two",
            expected_verdict="CONTRADICTED",
            expected_support_probability=0.1,
            risk_level="moderate",
            clinician_reviewed=True,
        ),
    ]

    answers = {
        "claim one": Result("SUPPORTED", 0.9),
        "claim two": Result("CONTRADICTED", 0.1),
    }

    run = run_benchmark(
        cases,
        lambda claim: answers[claim],
    )

    assert run.cases_valid == 2
    assert run.metrics["tp"] == 1
    assert run.metrics["tn"] == 1
    assert run.metrics["brier_score"] < 0.02
