import hashlib

def test_file_and_passage_hashes_survive_storage(monkeypatch, tmp_path):
    from governance.audit import store as module

    db = tmp_path / "test.db"
    monkeypatch.setattr(
        module.settings,
        "database_path",
        str(db),
    )

    original_file = b"curriculum PDF bytes"
    file_hash = hashlib.sha256(original_file).hexdigest()

    evidence_id = module.store_evidence(
        title="Course PDF",
        passage="Insulin lowers blood glucose.",
        source_type="reference",
        publisher="course",
        source_locator="page:12",
        source_snapshot_sha256=file_hash,
        curriculum_snapshot_id="snapshot:1",
    )

    from agents.specialists.retrieval_agent.local import LocalEvidenceProvider

    results = LocalEvidenceProvider().search(
        "Insulin lowers blood glucose",
        [],
        5,
        source_ids=[evidence_id],
        curriculum_snapshot_id="snapshot:1",
    )

    assert len(results) == 1
    assert results[0].source_snapshot_sha256 == file_hash
    assert results[0].source_locator == "page:12"
    assert results[0].passage_sha256
