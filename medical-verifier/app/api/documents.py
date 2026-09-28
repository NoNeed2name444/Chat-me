from fastapi import APIRouter
from pydantic import BaseModel, Field
from typing import Literal

from app.audit.store import store_evidence

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
