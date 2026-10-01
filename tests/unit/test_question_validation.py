from api.schemas.evidence import EvidenceItem
from agents.specialists.retrieval_agent.citation_integrity import sha256_text
from agents.specialists.verification_agent.question_validation import validate_question

def source():
    return [
        EvidenceItem(
            id="local:1",
            title="Course text",
            source_type="reference",
            publisher="course",
            passage="Insulin lowers blood glucose.",
            curriculum_snapshot_id="snapshot:1",
            source_snapshot_sha256="bound",
            passage_sha256=sha256_text("Insulin lowers blood glucose."),
        )
    ]

def test_question_answer_must_be_supported():
    result = validate_question(
        "What does insulin do?",
        "Insulin lowers blood glucose.",
        source(),
    )
    assert result.status == "VALIDATED"
    assert result.requires_review is False
    assert result.supporting_source_ids == ("local:1",)

def test_hallucinated_answer_is_not_auto_validated():
    result = validate_question(
        "What does insulin do?",
        "Insulin cures all infections.",
        source(),
    )
    assert result.status == "SOURCE_UNSUPPORTED"
    assert result.requires_review is True
