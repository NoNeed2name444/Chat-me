import pytest

from evals.suites.adversarial_50 import BENCHMARK, run_benchmark


@pytest.fixture(scope="module")
def benchmark():
    return run_benchmark()


def test_benchmark_shape():
    assert len(BENCHMARK) == 50
    assert sum(1 for case in BENCHMARK if case[1]) == 40
    assert len({case[0] for case in BENCHMARK}) == 50


def test_no_false_claim_is_supported(benchmark):
    assert benchmark["case_count"] == 50
    assert benchmark["false_supported"] == []


def test_true_claims_keep_their_support(benchmark):
    # where personal's verifier stands today: a new check that turns away a
    # true claim it used to support shows here
    assert len(benchmark["true_supported"]) >= 15
