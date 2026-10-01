"""v1.4 adversarial entity and temporal attacks."""

from agents.specialists.verification_agent.independent_entailment import verify


def test_date_like_temporal_mismatch_abstains():
    result = verify(
        "Drug A currently increases bleeding.",
        "In 2020, Drug A increased bleeding.",
    )
    assert result.label == "UNKNOWN"


def test_entity_swap_abstains_even_with_shared_outcome():
    result = verify(
        "Warfarin increases bleeding.",
        "Aspirin increases bleeding.",
    )
    assert result.label == "UNKNOWN"


def test_drug_interaction_pair_swap_abstains():
    result = verify(
        "Drug A interacts with Drug B.",
        "Drug A interacts with Drug C.",
    )
    assert result.label == "UNKNOWN"


def test_contraindication_population_swap_abstains():
    result = verify(
        "Drug A is contraindicated in pregnancy.",
        "Drug A is contraindicated in children.",
    )
    assert result.label == "UNKNOWN"
