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


"""Nemesis mutation tests for v1.2/v1.3."""

from copy import deepcopy

from app.verification.independent_entailment import verify


def _assert_unknown(claim, evidence):
    result = verify(claim, evidence)
    assert result.label == "UNKNOWN"


def test_negation_mutation():
    _assert_unknown(
        "Drug A increases bleeding.",
        "Drug A does not increase bleeding.",
    )


def test_numeric_mutation():
    _assert_unknown(
        "Drug A increases bleeding by 10 percent.",
        "Drug A increases bleeding by 20 percent.",
    )


def test_population_mutation():
    _assert_unknown(
        "Drug A is effective in children.",
        "Drug A is effective in adults.",
    )


def test_temporal_mutation():
    _assert_unknown(
        "Drug A currently increases bleeding.",
        "Drug A previously increased bleeding.",
    )


def test_causal_mutation():
    _assert_unknown(
        "Drug A causes bleeding.",
        "Drug A is associated with bleeding.",
    )


def test_safety_relation_mutation():
    _assert_unknown(
        "Drug A is contraindicated in pregnancy.",
        "Drug A is associated with pregnancy.",
    )


def test_interaction_mutation():
    _assert_unknown(
        "Drug A interacts with Drug B.",
        "Drug A is associated with Drug B.",
    )


def test_subject_swap_mutation():
    _assert_unknown(
        "Drug A increases bleeding.",
        "Drug B increases bleeding.",
    )


def test_object_swap_mutation():
    _assert_unknown(
        "Drug A increases bleeding.",
        "Drug A increases blood pressure.",
    )


def test_mutation_does_not_modify_original_strings():
    claim = "Drug A causes bleeding."
    evidence = "Drug A is associated with bleeding."
    before = deepcopy((claim, evidence))
    _assert_unknown(claim, evidence)
    assert (claim, evidence) == before
