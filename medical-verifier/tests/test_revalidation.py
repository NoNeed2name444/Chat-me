from datetime import date
from app.models.evidence import EvidenceItem
from app.verification.revalidation import revalidate_local_snapshot

def test_local_snapshot_revalidation_detects_tampering():
    item = EvidenceItem(
        id="local:1",
        title="Curriculum",
        source_type="reference",
        publisher="course",
        passage="Original passage",
        passage_sha256="bad-hash",
    )

    result = revalidate_local_snapshot(item)

    assert result.status == "CHANGED"
    assert "stored_curriculum_passage_changed" in result.warnings

def test_local_snapshot_revalidation_accepts_bound_passage():
    from app.verification.citation_integrity import sha256_text

    passage = "Original passage"
    item = EvidenceItem(
        id="local:1",
        title="Curriculum",
        source_type="reference",
        publisher="course",
        passage=passage,
        passage_sha256=sha256_text(passage),
    )

    result = revalidate_local_snapshot(item)

    assert result.status == "UNCHANGED"
