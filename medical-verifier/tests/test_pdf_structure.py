from base64 import b64encode
from types import SimpleNamespace

from app.api import documents
from app.verification import pdf_structure
from app.verification.source_manifest import verify_manifest

def test_pdf_structure_detects_caption_and_links_following_block(monkeypatch):
    class FakePage:
        def __init__(self, text):
            self._text = text

        def extract_text(self):
            return self._text

    class FakeReader:
        def __init__(self, _stream):
            self.pages = [
                FakePage(
                    "Figure 1: Dose-response curve\n"
                    "The curve shows reduced risk.\n"
                    "A | B | C"
                )
            ]

    monkeypatch.setattr(
        pdf_structure,
        "PdfReader",
        FakeReader,
    )

    result = pdf_structure.extract_pdf(b"%PDF-fake")

    assert result.pages == 1
    assert result.raw_sha256
    assert result.blocks[0].block_type == "caption"
    assert result.blocks[1].related_block_ids == (
        result.blocks[0].block_id,
    )
    assert result.blocks[2].block_type == "table"
    assert "caption_linkage_is_heuristic" in result.blocks[0].warnings
    assert result.extraction_quality <= 0.84

def test_pdf_endpoint_persists_typed_manifest(monkeypatch):
    extraction = pdf_structure.PDFExtractionResult(
        raw_sha256="pdf-sha",
        pages=1,
        blocks=(
            pdf_structure.ExtractedBlock(
                block_id="block-1",
                page_number=1,
                block_index=0,
                block_type="caption",
                text="Table 1: Dosing",
                related_block_ids=(),
                warnings=("caption_linkage_is_heuristic",),
            ),
            pdf_structure.ExtractedBlock(
                block_id="block-2",
                page_number=1,
                block_index=1,
                block_type="table",
                text="Dose | Frequency",
                related_block_ids=("block-1",),
                warnings=(),
            ),
        ),
        extraction_quality=0.82,
        warnings=("caption_relationships_are_not_native_pdf_object_links",),
    )

    stored = []
    manifests = []

    monkeypatch.setattr(
        documents,
        "extract_pdf",
        lambda _: extraction,
    )
    monkeypatch.setattr(
        documents,
        "store_evidence",
        lambda **kwargs: (
            stored.append(kwargs)
            or f"local:{len(stored)}"
        ),
    )
    monkeypatch.setattr(
        documents,
        "store_manifest",
        lambda manifest: manifests.append(manifest),
    )

    request = documents.PDFDocumentIngestRequest(
        title="Course PDF",
        pdf_base64=b64encode(b"%PDF-fake").decode(),
        publisher="course",
        language="en",
        curriculum_snapshot_id="snapshot:1",
        manifest_id="manifest:1",
    )

    response = documents.ingest_pdf_document(request)

    assert response["manifest_id"] == "manifest:1"
    assert response["pdf_sha256"] == "pdf-sha"
    assert len(response["evidence_ids"]) == 2
    assert len(stored) == 2
    assert stored[0]["source_snapshot_sha256"] == "pdf-sha"
    assert stored[0]["block_type"] == "caption"
    assert stored[1]["related_block_ids"] == [stored[0]["evidence_id"] if "evidence_id" in stored[0] else "local:1"]

    assert len(manifests) == 1
    assert verify_manifest(manifests[0]) is True
    assert sorted(
        entry.evidence_id
        for entry in manifests[0].entries
    ) == sorted(response["evidence_ids"])

def test_pdf_endpoint_rejects_non_pdf_payload():
    request = documents.PDFDocumentIngestRequest(
        title="Bad file",
        pdf_base64=b64encode(b"not-a-pdf").decode(),
    )

    try:
        documents.ingest_pdf_document(request)
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 400
        assert getattr(exc, "detail", None) == "invalid_pdf_header"
    else:
        raise AssertionError("expected invalid_pdf_header")


def test_multiple_captions_do_not_cross_link(monkeypatch):
    class FakePage:
        def extract_text(self):
            return (
                "Figure 1: First\n"
                "First figure text\n"
                "Figure 2: Second\n"
                "Second figure text"
            )

    class FakeReader:
        def __init__(self, _stream):
            self.pages = [FakePage()]

    monkeypatch.setattr(
        pdf_structure,
        "PdfReader",
        FakeReader,
    )

    result = pdf_structure.extract_pdf(b"%PDF-fake")

    assert result.blocks[1].related_block_ids == (
        result.blocks[0].block_id,
    )
    assert result.blocks[3].related_block_ids == (
        result.blocks[2].block_id,
    )
    assert result.blocks[1].related_block_ids != (
        result.blocks[2].block_id,
    )
