from app.models.evidence import EvidenceItem
from app.verification.independent_entailment import verify
from app.verification.question_validation import validate_question
from app.verification.curriculum import assess_curriculum_fidelity

def make_item(passage, *, quality=1.0, warnings=None, id="s1"):
    return EvidenceItem(
        id=id,
        title="Course",
        source_type="reference",
        publisher="course",
        passage=passage,
        source_snapshot_sha256="bound",
        passage_sha256=(
            __import__("app.verification.citation_integrity", fromlist=["sha256_text"])
            .sha256_text(passage)
        ),
        extraction_quality=quality,
        extraction_warnings=warnings or [],
    )

def test_daily_dose_equivalence_is_supported():
    result = verify(
        "Take 500 mg twice daily.",
        "Take 1000 mg daily.",
    )
    assert result.label == "SUPPORTS"

def test_dose_frequency_mismatch_is_unknown():
    result = verify(
        "Take 500 mg twice daily.",
        "Take 500 mg once daily.",
    )
    assert result.label == "UNKNOWN"
    assert "dose_frequency_mismatch" in result.reasons

def test_universal_scope_cannot_be_invented():
    result = verify(
        "Treatment A works in all patients.",
        "Treatment A works in selected patients.",
    )
    assert result.label == "UNKNOWN"
    assert "universal_scope_not_entrailed" in result.reasons

def test_exclusive_scope_cannot_be_invented():
    result = verify(
        "Treatment A works only in adults.",
        "Treatment A works in adults.",
    )
    assert result.label == "UNKNOWN"
    assert "exclusive_scope_not_entrailed" in result.reasons

def test_low_extraction_quality_prevents_auto_validation():
    result = assess_curriculum_fidelity(
        "Insulin lowers blood glucose.",
        [make_item(
            "Insulin lowers blood glucose.",
            quality=0.60,
            warnings=["ocr_uncertain"],
        )],
    )
    assert result.status == "SOURCE_EXTRACTION_UNCERTAIN"

def test_conflicting_curriculum_sources_require_review():
    support = make_item(
        "Drug X increases bleeding.",
        id="support",
    )
    contradiction = make_item(
        "Drug X does not increase bleeding.",
        id="contradiction",
    )

    result = assess_curriculum_fidelity(
        "Drug X increases bleeding.",
        [support, contradiction],
    )

    assert result.status == "CONFLICTING_CURRICULUM_SOURCES"

def test_question_validation_does_not_grade_conflicting_sources_as_correct():
    result = validate_question(
        "Does Drug X increase bleeding?",
        "Drug X increases bleeding.",
        [
            make_item("Drug X increases bleeding.", id="support"),
            make_item("Drug X does not increase bleeding.", id="contradiction"),
        ],
    )
    assert result.status == "SOURCE_CONFLICT"
    assert result.requires_review is True


def test_cross_sentence_condition_cannot_be_dropped():
    result = verify(
        "The dose is 5 mg.",
        "For patients with severe renal impairment, use 5 mg. The dose should be monitored.",
    )
    assert result.label == "UNKNOWN"
    assert "conditional_scope_missing" in result.reasons

def test_cross_sentence_condition_can_be_preserved():
    result = verify(
        "For patients with severe renal impairment, use 5 mg.",
        "For patients with severe renal impairment, use 5 mg. The dose should be monitored.",
    )
    assert result.label == "SUPPORTS"

def test_concentration_arithmetic_is_conservative_and_equivalent():
    result = verify(
        "The daily dose is 200 mg.",
        "The concentration is 10 mg/mL. Take 10 mL twice daily.",
    )
    assert result.label == "SUPPORTS"

def test_weight_based_arithmetic_requires_explicit_weight():
    result = verify(
        "The daily dose is 100 mg for a 20 kg patient.",
        "Use 5 mg/kg/day for a 20 kg patient.",
    )
    assert result.label == "SUPPORTS"

def test_document_precedence_selects_only_explicit_higher_rank():
    support = make_item(
        "Drug X increases bleeding.",
        id="old",
    )
    support = support.model_copy(
        update={
            "precedence_group": "drug-x-guideline",
            "precedence_rank": 1,
        }
    )
    newer = make_item(
        "Drug X does not increase bleeding.",
        id="new",
    ).model_copy(
        update={
            "precedence_group": "drug-x-guideline",
            "precedence_rank": 2,
        }
    )

    result = assess_curriculum_fidelity(
        "Drug X increases bleeding.",
        [support, newer],
    )

    assert result.status == "UNCERTAIN"
    assert "old" not in result.matched_source_ids
    assert "new" in result.matched_source_ids
    assert any(
        "explicit precedence excluded" in reason.lower()
        for reason in result.reasons
    )

def test_question_validation_uses_the_same_precedence_rule():
    old = make_item(
        "Drug X increases bleeding.",
        id="old",
    ).model_copy(
        update={
            "precedence_group": "drug-x-guideline",
            "precedence_rank": 1,
        }
    )
    newer = make_item(
        "Drug X does not increase bleeding.",
        id="new",
    ).model_copy(
        update={
            "precedence_group": "drug-x-guideline",
            "precedence_rank": 2,
        }
    )

    result = validate_question(
        "Does Drug X increase bleeding?",
        "Drug X does not increase bleeding.",
        [old, newer],
    )

    assert result.status == "VALIDATED"
    assert result.requires_review is False


def test_equal_precedence_tie_remains_a_conflict():
    support = make_item(
        "Drug X increases bleeding.",
        id="tie-support",
    ).model_copy(
        update={
            "precedence_group": "drug-x-guideline",
            "precedence_rank": 2,
        }
    )
    contradiction = make_item(
        "Drug X does not increase bleeding.",
        id="tie-contradiction",
    ).model_copy(
        update={
            "precedence_group": "drug-x-guideline",
            "precedence_rank": 2,
        }
    )

    result = assess_curriculum_fidelity(
        "Drug X increases bleeding.",
        [support, contradiction],
    )

    assert result.status == "CONFLICTING_CURRICULUM_SOURCES"

def test_concentration_arithmetic_mismatch_is_not_supported():
    result = verify(
        "The daily dose is 200 mg.",
        "The concentration is 10 mg/mL. Take 10 mL once daily.",
    )
    assert result.label == "UNKNOWN"

def test_weight_based_arithmetic_without_patient_weight_is_unknown():
    result = verify(
        "The daily dose is 100 mg.",
        "Use 5 mg/kg/day.",
    )
    assert result.label == "UNKNOWN"


def test_spanish_semantic_normalization_is_conservative():
    result = verify(
        "La insulina aumenta la glucosa.",
        "La insulina aumenta la glucosa.",
    )
    assert result.label == "SUPPORTS"

def test_french_semantic_normalization_is_conservative():
    result = verify(
        "Le traitement est efficace chez les adultes.",
        "Le traitement est efficace chez les adultes.",
    )
    assert result.label == "SUPPORTS"
