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

def test_swapped_short_term_is_unknown():
    # the Swift twin holds the same cases (testSwappedShortTermIsRejected)
    for claim, evidence in (
        ("Statins lower LDL cholesterol.", "Statins lower HDL cholesterol."),
        ("Tenofovir treats HIV infection.", "Tenofovir treats HBV infection."),
        ("Aspirin is used after MI.", "Aspirin is used after PE."),
        ("Adrenaline 0.5 mg is given IM for anaphylaxis.",
         "Adrenaline 0.5 mg is given IV for anaphylaxis."),
        ("Warfarin is reversed with vitamin K.", "Warfarin is reversed with vitamin D."),
        ("Gout is more common in men.", "Gout is more common in women."),
    ):
        result = verify(claim, evidence)
        assert result.label == "UNKNOWN", claim
        assert "atomic_term_substituted" in result.reasons, claim

def test_short_name_of_the_same_term_is_not_a_swap():
    # a route's two names, and a short name spelled by the initials
    for claim, evidence in (
        ("Amoxicillin 500 mg PO three times a day.",
         "Amoxicillin 500 mg orally three times a day."),
        ("Rate control in AF uses beta blockers.",
         "Rate control in atrial fibrillation uses beta blockers."),
        ("Furosemide is used in heart failure.", "Furosemide is used for heart failure."),
        ("Rivaroxaban inhibits factor Xa.", "Rivaroxaban inhibits activated factor X."),
    ):
        assert verify(claim, evidence).label == "SUPPORTS", claim

def test_another_route_is_unknown_whatever_words_stand_around():
    # the Swift twin holds the same cases (testAnotherRouteIsRejected)
    for claim, evidence in (
        ("Adrenaline 0.5 mg IV for anaphylaxis in adults.",
         "Adrenaline 0.5 mg IM is given for anaphylaxis in adults."),
        ("Vincristine is given intrathecally.",
         "Vincristine must only be given intravenously."),
    ):
        result = verify(claim, evidence)
        assert result.label == "UNKNOWN", claim
        assert "atomic_term_substituted" in result.reasons, claim
