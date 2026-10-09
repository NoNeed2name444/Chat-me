from agents.specialists.verification_agent.independent_entailment import verify

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

def test_property_mismatch_is_unknown():
    result = verify(
        "Drug X is safe.",
        "Drug X is effective.",
    )
    assert result.label == "UNKNOWN"

def test_matching_claim_can_support():
    result = verify(
        "Drug X increases bleeding.",
        "Drug X increases bleeding in adults.",
    )
    assert result.label == "SUPPORTS"


def test_mass_unit_mismatch_is_unknown():
    result = verify(
        "The dose is 500 mcg.",
        "The dose is 500 mg.",
    )
    assert result.label == "UNKNOWN"
    assert "measurement_unit_or_value_mismatch" in result.reasons

def test_equivalent_unit_spellings_match():
    result = verify(
        "The dose is 500 mcg.",
        "The dose is 500 ug.",
    )
    assert result.label == "SUPPORTS"

def test_swapped_drug_is_unknown():
    # the Swift twin holds the same case (testSwappedDrugIsRejected)
    result = verify(
        "Amoxicillin treats otitis media.",
        "Ibuprofen treats otitis media.",
    )
    assert result.label == "UNKNOWN"
    assert "atomic_term_substituted" in result.reasons

def test_swapped_outcome_is_unknown():
    result = verify(
        "Warfarin increases the risk of bleeding.",
        "Warfarin increases the risk of stroke.",
    )
    assert result.label == "UNKNOWN"
    assert "atomic_term_substituted" in result.reasons

def test_opposite_verb_of_the_same_relation_is_unknown():
    # "causes" and "prevents" are both causal, so the relation checks pass them
    result = verify(
        "Smoking causes lung cancer.",
        "Smoking prevents lung cancer.",
    )
    assert result.label == "UNKNOWN"

def test_swap_beside_an_article_is_unknown():
    result = verify(
        "Aspirin reduces the risk of stroke.",
        "Aspirin reduces the risk of a heart attack.",
    )
    assert result.label == "UNKNOWN"

def test_nitrate_and_nitrite_stay_apart():
    result = verify(
        "Nitrates relieve angina.",
        "Nitrites relieve angina.",
    )
    assert result.label == "UNKNOWN"

def test_known_alias_is_not_a_swap():
    result = verify(
        "Paracetamol treats fever.",
        "Acetaminophen treats fever.",
    )
    assert result.label == "SUPPORTS"

def test_another_form_of_the_same_word_is_not_a_swap():
    result = verify(
        "Clopidogrel is a prodrug requiring metabolic activation.",
        "Clopidogrel is a prodrug and requires metabolic activation.",
    )
    assert result.label == "SUPPORTS"

def test_a_sentence_naming_the_claimed_drug_still_supports():
    result = verify(
        "Amoxicillin treats otitis media.",
        "Ibuprofen treats otitis media. Amoxicillin treats otitis media.",
    )
    assert result.label == "SUPPORTS"
