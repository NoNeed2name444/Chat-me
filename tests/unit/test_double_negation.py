from agents.specialists.verification_agent.independent_entailment import verify
from agents.specialists.verification_agent.independent_entailment_base import (
    _normalize_double_negation,
)

def test_common_double_negation_is_not_misclassified():
    result = verify(
        "Drug X is not uncommon.",
        "Drug X is common.",
    )
    assert result.label == "SUPPORTS"

def test_unrelated_negation_is_not_normalized():
    result = verify(
        "Drug X does not increase bleeding.",
        "Drug X increases bleeding.",
    )
    assert result.label == "CONTRADICTS"

def test_translated_terms_are_swapped_as_whole_words():
    assert _normalize_double_negation(
        "La insulina reduce la glucosa."
    ) == "la insulina reduces la glucose."
    # English words that only start like a Spanish one stay as they are
    assert _normalize_double_negation(
        "A reduced risk with no causal link."
    ) == "a reduced risk with no causal link."
