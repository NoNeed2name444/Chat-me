from app.verification.independent_entailment import verify

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
