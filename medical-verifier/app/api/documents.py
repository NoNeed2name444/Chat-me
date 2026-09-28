import base64
import binascii
from uuid import uuid4

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Literal

from app.audit.store import store_evidence, store_manifest
from app.config import settings
from app.verification.pdf_structure import extract_pdf
from app.verification.citation_integrity import sha256_text
from app.models.evidence import EvidenceItem
from app.verification.source_manifest import build_manifest

router = APIRouter(tags=["documents"])

class DocumentIngestRequest(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    text: str = Field(min_length=20, max_length=200000)
    source_type: str = "reference"
    publisher: str = "internal"
    url: str | None = None
    source_locator: str | None = None
    source_snapshot_sha256: str | None = None
    canonical_id: str | None = None
    document_version: str | None = None
    study_family_id: str | None = None
    source_date: str | None = None
    curriculum_snapshot_id: str | None = None
    extraction_quality: float = Field(default=1.0, ge=0, le=1)
    extraction_warnings: list[str] = Field(default_factory=list)
    page_number: int | None = Field(default=None, ge=1)
    section: str | None = Field(default=None, max_length=500)
    block_type: Literal[
        "text", "table", "figure", "caption",
        "footnote", "header", "unknown"
    ] = "text"
    block_index: int | None = Field(default=None, ge=0)
    related_block_ids: list[str] = Field(default_factory=list)
    language: str = Field(default="auto", max_length=20)
    precedence_group: str | None = Field(default=None, max_length=200)
    precedence_rank: int = Field(default=0, ge=0)
    source_authority: float = Field(default=0.40, ge=0, le=1)

@router.post("/documents/ingest")
def ingest_document(request: DocumentIngestRequest):
    evidence_id = store_evidence(
        title=request.title,
        passage=request.text,
        source_type=request.source_type,
        publisher=request.publisher,
        url=request.url,
        source_locator=request.source_locator,
        source_snapshot_sha256=request.source_snapshot_sha256,
        canonical_id=request.canonical_id,
        document_version=request.document_version,
        study_family_id=request.study_family_id,
        source_date=request.source_date,
        curriculum_snapshot_id=request.curriculum_snapshot_id,
        extraction_quality=request.extraction_quality,
        extraction_warnings=request.extraction_warnings,
        source_authority=request.source_authority,
        page_number=request.page_number,
        section=request.section,
        block_type=request.block_type,
        block_index=request.block_index,
        related_block_ids=request.related_block_ids,
        language=request.language,
        precedence_group=request.precedence_group,
        precedence_rank=request.precedence_rank,
    )
    return {
        "status": "stored",
        "evidence_id": evidence_id,
        "curriculum_snapshot_id": request.curriculum_snapshot_id,
        "extraction_quality": request.extraction_quality,
        "page_number": request.page_number,
        "section": request.section,
        "block_type": request.block_type,
        "related_block_ids": request.related_block_ids,
        "language": request.language,
        "warning": (
            "Stored evidence can define curriculum fidelity, but does not "
            "authorize clinical action."
        ),
    }


class PDFDocumentIngestRequest(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    pdf_base64: str = Field(min_length=1, max_length=27_000_000)
    source_type: str = "reference"
    publisher: str = "internal"
    url: str | None = None
    source_locator: str | None = None
    canonical_id: str | None = None
    document_version: str | None = None
    study_family_id: str | None = None
    source_date: str | None = None
    curriculum_snapshot_id: str | None = None
    language: str = Field(default="auto", max_length=20)
    precedence_group: str | None = Field(default=None, max_length=200)
    precedence_rank: int = Field(default=0, ge=0)
    source_authority: float = Field(default=0.40, ge=0, le=1)
    manifest_id: str | None = Field(default=None, max_length=200)
    parent_manifest_sha256: str | None = None

@router.post("/documents/pdf/ingest")
def ingest_pdf_document(request: PDFDocumentIngestRequest):
    try:
        raw_bytes = base64.b64decode(
            request.pdf_base64,
            validate=True,
        )
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(
            status_code=400,
            detail="invalid_pdf_base64",
        ) from exc

    if len(raw_bytes) > settings.max_pdf_bytes:
        raise HTTPException(
            status_code=413,
            detail="pdf_too_large",
        )

    if not raw_bytes.startswith(b"%PDF-"):
        raise HTTPException(
            status_code=400,
            detail="invalid_pdf_header",
        )

    try:
        extraction = extract_pdf(raw_bytes)
    except Exception as exc:
        raise HTTPException(
            status_code=400,
            detail="pdf_extraction_failed",
        ) from exc

    evidence_ids = []
    manifest_items = []
    block_to_evidence_id = {
        block.block_id: f"local:{uuid4()}"
        for block in extraction.blocks
    }

    for block in extraction.blocks:
        warnings = list(extraction.warnings)
        warnings.extend(block.warnings)

        source_locator = (
            request.source_locator
            or f"page:{block.page_number}:block:{block.block_index}"
        )

        item_id = block_to_evidence_id[block.block_id]

        store_evidence(
            title=f"{request.title} — page {block.page_number}",
            passage=block.text,
            source_type=request.source_type,
            publisher=request.publisher,
            url=request.url,
            source_locator=source_locator,
            source_snapshot_sha256=extraction.raw_sha256,
            canonical_id=request.canonical_id,
            document_version=request.document_version,
            study_family_id=request.study_family_id,
            source_date=request.source_date,
            curriculum_snapshot_id=request.curriculum_snapshot_id,
            extraction_quality=extraction.extraction_quality,
            extraction_warnings=warnings,
            page_number=block.page_number,
            section=None,
            block_type=block.block_type,
            block_index=block.block_index,
            related_block_ids=[
                block_to_evidence_id[related_id]
                for related_id in block.related_block_ids
                if related_id in block_to_evidence_id
            ],
            language=request.language,
            precedence_group=request.precedence_group,
            precedence_rank=request.precedence_rank,
            source_authority=request.source_authority,
            evidence_id=item_id,
        )
        evidence_ids.append(item_id)

        manifest_items.append(
            EvidenceItem(
                id=item_id,
                title=request.title,
                source_type=request.source_type,
                publisher=request.publisher,
                source_locator=source_locator,
                source_snapshot_sha256=extraction.raw_sha256,
                passage_sha256=sha256_text(block.text),
                page_number=block.page_number,
                block_type=block.block_type,
                block_index=block.block_index,
                related_block_ids=[
                    block_to_evidence_id[related_id]
                    for related_id in block.related_block_ids
                    if related_id in block_to_evidence_id
                ],
                language=request.language,
                document_version=request.document_version,
                precedence_group=request.precedence_group,
                precedence_rank=request.precedence_rank,
            )
        )

    manifest = build_manifest(
        manifest_id=(
            request.manifest_id
            or f"manifest:{uuid4()}"
        ),
        evidence_items=manifest_items,
        parent_manifest_sha256=request.parent_manifest_sha256,
    )
    store_manifest(manifest)

    return {
        "status": "stored",
        "manifest_id": manifest.manifest_id,
        "manifest_sha256": manifest.manifest_sha256,
        "parent_manifest_sha256": manifest.parent_manifest_sha256,
        "pdf_sha256": extraction.raw_sha256,
        "pages": extraction.pages,
        "blocks": len(extraction.blocks),
        "extraction_quality": extraction.extraction_quality,
        "extraction_warnings": list(extraction.warnings),
        "evidence_ids": evidence_ids,
        "warning": (
            "PDF extraction is structural and heuristic. Table/figure "
            "relationships are not treated as native PDF object truth."
        ),
    }
