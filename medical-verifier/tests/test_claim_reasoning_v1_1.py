from app.verification.independent_entailment import verify
from app.verification.claim_reasoning import decompose_claim

def test_atomic_claim_decomposition_preserves_two_relations():
    atoms = decompose_claim(
        "Drug A increases bleeding and Drug A reduces blood pressure."
    )
    assert len(atoms) == 2
    assert {atom.relation for atom in atoms} == {
        "risk_increase",
        "risk_decrease",
    }

def test_franken_claim_cannot_mix_subjects_across_sentences():
    result = verify(
        "Drug A increases bleeding. Drug A reduces blood pressure.",
        "Drug A increases bleeding. Drug B reduces blood pressure.",
    )
    assert result.label == "UNKNOWN"
    assert "atomic_subject_mismatch" in result.reasons

def test_atomic_claim_cannot_mix_objects():
    result = verify(
        "Drug A increases bleeding.",
        "Drug A increases blood pressure.",
    )
    assert result.label == "UNKNOWN"
    assert "atomic_object_mismatch" in result.reasons

def test_causal_claim_requires_causal_evidence():
    result = verify(
        "Drug A causes bleeding.",
        "Drug A is associated with bleeding.",
    )
    assert result.label == "UNKNOWN"
    assert "causal_claim_requires_causal_evidence" in result.reasons

def test_temporal_scope_must_be_preserved():
    result = verify(
        "Drug A currently increases bleeding.",
        "Drug A increases bleeding.",
    )
    assert result.label == "UNKNOWN"
    assert "temporal_scope_missing" in result.reasons

def test_temporal_scope_mismatch_is_unknown():
    result = verify(
        "Drug A previously increased bleeding.",
        "Drug A currently increases bleeding.",
    )
    assert result.label == "UNKNOWN"
    assert "temporal_scope_mismatch" in result.reasons

def test_interaction_claim_requires_interaction_evidence():
    result = verify(
        "Drug A interacts with Drug B.",
        "Drug A is associated with Drug B.",
    )
    assert result.label == "UNKNOWN"
    assert "interaction_claim_requires_interaction_evidence" in result.reasons

def test_contraindication_claim_requires_contraindication_evidence():
    result = verify(
        "Drug A is contraindicated in pregnancy.",
        "Drug A is associated with pregnancy.",
    )
    assert result.label == "UNKNOWN"
    assert "contraindication_claim_requires_contraindication_evidence" in result.reasons

def test_matching_interaction_is_supported():
    result = verify(
        "Drug A interacts with Drug B.",
        "Drug A has an interaction with Drug B.",
    )
    assert result.label == "SUPPORTS"

def test_matching_contraindication_is_supported():
    result = verify(
        "Drug A is contraindicated in pregnancy.",
        "Drug A is contraindicated in pregnancy.",
    )
    assert result.label == "SUPPORTS"
