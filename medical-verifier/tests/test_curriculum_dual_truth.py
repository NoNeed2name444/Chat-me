from datetime import date

from app.models.evidence import EvidenceItem
from app.verification.curriculum import (
    assess_curriculum_fidelity,
    determine_divergence,
)

def make_source(text, source_id="curriculum-1", source_date=date(2022,1,1)):
    return EvidenceItem(
        id=source_id,
        title="Curriculum",
        source_type="reference",
        publisher="course",
        passage=text,
        source_date=source_date,
        source_snapshot_sha256="bound",
        passage_sha256="bound",
    )

def test_curriculum_source_can_define_fidelity():
    result = assess_curriculum_fidelity(
        "Drug X increases bleeding.",
        [make_source("Drug X increases bleeding.")],
    )
    assert result.status == "ALIGNED"

def test_curriculum_opposite_claim_is_not_aligned():
    result = assess_curriculum_fidelity(
        "Drug X does not increase bleeding.",
        [make_source("Drug X increases bleeding.")],
    )
    assert result.status != "ALIGNED"

def test_irrelevant_newer_evidence_does_not_trigger_divergence():
    divergence = determine_divergence(
        curriculum_status="ALIGNED",
        current_verdict="SUPPORTED",
        has_relevant_newer_evidence=False,
    )
    assert divergence == "none"

def test_newer_conflicting_relevant_evidence_triggers_conflict():
    divergence = determine_divergence(
        curriculum_status="ALIGNED",
        current_verdict="CONTRADICTED",
        has_relevant_newer_evidence=True,
    )
    assert divergence == "curriculum_vs_current_conflict"


def test_curriculum_fidelity_rejects_tampered_stored_passage():
    from app.verification.citation_integrity import sha256_text
    from app.verification.curriculum import assess_curriculum_fidelity

    item = make_source("Insulin lowers blood glucose.")
    item.passage = "Insulin raises blood pressure."

    result = assess_curriculum_fidelity(
        "What does insulin do?",
        [item],
    )

    assert result.status == "SOURCE_INTEGRITY_FAILED"
