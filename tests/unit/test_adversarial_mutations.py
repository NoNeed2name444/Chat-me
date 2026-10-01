from evals.suites.adversarial_mutations import (
    generate_mutations,
    run_mutation_suite,
)


def test_mutation_generation_is_deterministic():
    first = generate_mutations(
        "Drug A increases bleeding.",
        "Drug A increases bleeding.",
    )
    second = generate_mutations(
        "Drug A increases bleeding.",
        "Drug A increases bleeding.",
    )
    assert first == second
    assert len(first) == 6


def test_mutation_suite_is_machine_readable():
    result = run_mutation_suite(
        "Drug A causes bleeding.",
        "Drug A causes bleeding.",
    )
    assert result["suite_version"] == "1.2"
    assert result["case_count"] == 6
    assert len(result["results"]) == 6
    assert all("reasons" in item for item in result["results"])
