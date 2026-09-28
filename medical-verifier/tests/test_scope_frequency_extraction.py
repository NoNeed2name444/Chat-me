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
