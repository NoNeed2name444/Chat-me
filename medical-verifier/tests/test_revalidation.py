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


def test_openfda_revalidation_detects_remote_change(monkeypatch):
    import app.verification.revalidation as module
    from app.verification.citation_integrity import sha256_text

    record = {"set_id": "set-1", "effective_time": "20260101"}
    item = EvidenceItem(
        id="openfda:1",
        title="FDA label",
        source_type="regulatory",
        publisher="U.S. FDA / openFDA",
        canonical_id="set-1",
        passage="warning: test",
        source_snapshot_sha256=sha256_text('{"set_id": "set-1", "effective_time": "20250101"}'),
    )

    class Response:
        status_code = 200
        def raise_for_status(self):
            return None
        def json(self):
            return {"results": [record]}

    class Client:
        def __init__(self, *args, **kwargs):
            pass
        def __enter__(self):
            return self
        def __exit__(self, *args):
            return False
        def get(self, *args, **kwargs):
            return Response()

    monkeypatch.setattr(module.httpx, "Client", Client)

    result = module.revalidate_openfda(item)

    assert result.status == "CHANGED"
    assert "source_snapshot_changed" in result.warnings
