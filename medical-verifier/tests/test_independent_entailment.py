from app.verification.independent_entailment import verify

def test_causal_vs_association_is_unknown():
    result = verify(
        "Drug X causes lower risk.",
        "Drug X was associated with lower risk in an observational study.",
    )
    assert result.label == "UNKNOWN"

def test_population_mismatch_is_unknown():
    result = verify(
        "Drug X is safe in pregnancy.",
        "Studies in adults found Drug X safe.",
    )
    assert result.label == "UNKNOWN"

def test_measurement_type_mismatch_is_unknown():
    result = verify(
        "Risk increased by 10 percentage points.",
        "Risk increased by 10 percent.",
    )
    assert result.label == "UNKNOWN"

def test_matching_claim_can_support():
    result = verify(
        "Drug X increases bleeding.",
        "Drug X increases bleeding in adults.",
    )
    assert result.label == "SUPPORTS"
