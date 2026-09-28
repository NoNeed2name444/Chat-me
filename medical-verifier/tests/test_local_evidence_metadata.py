from app.audit import store
from app.config import settings
from app.retrieval.local import LocalEvidenceProvider

def test_local_provider_preserves_extraction_metadata(monkeypatch, tmp_path):
    monkeypatch.setattr(
        settings,
        "database_path",
        str(tmp_path / "evidence.db"),
    )

    evidence_id = store.store_evidence(
        title="OCR course",
        passage="Insulin lowers blood glucose.",
        source_type="reference",
        publisher="course",
        source_locator="page:8",
        curriculum_snapshot_id="snapshot:ocr",
        extraction_quality=0.60,
        extraction_warnings=["ocr_uncertain"],
        page_number=8,
        section="Dosing",
        block_type="table",
        block_index=2,
        precedence_group="drug-x-guideline",
        precedence_rank=3,
    )

    results = LocalEvidenceProvider().search(
        "Insulin lowers blood glucose",
        [],
        5,
        source_ids=[evidence_id],
        curriculum_snapshot_id="snapshot:ocr",
    )

    assert len(results) == 1
    assert results[0].extraction_quality == 0.60
    assert results[0].extraction_warnings == ["ocr_uncertain"]
    assert results[0].source_locator == "page:8"
    assert results[0].page_number == 8
    assert results[0].section == "Dosing"
    assert results[0].block_type == "table"
    assert results[0].block_index == 2
    assert results[0].precedence_group == "drug-x-guideline"
    assert results[0].precedence_rank == 3
    assert results[0].canonical_id is None
