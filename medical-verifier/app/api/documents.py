from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.audit.store import store_evidence

router = APIRouter(tags=["documents"])

class DocumentIngestRequest(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    text: str = Field(min_length=20, max_length=200000)
    source_type: str = "reference"
    publisher: str = "internal"
    url: str | None = None
    canonical_id: str | None = None
    document_version: str | None = None
    study_family_id: str | None = None
    source_authority: float = Field(default=0.40, ge=0, le=1)

@router.post("/documents/ingest")
def ingest_document(request: DocumentIngestRequest):
    evidence_id = store_evidence(
        title=request.title,
        passage=request.text,
        source_type=request.source_type,
        publisher=request.publisher,
        url=request.url,
        canonical_id=request.canonical_id,
        document_version=request.document_version,
        study_family_id=request.study_family_id,
        source_authority=request.source_authority,
    )
    return {
        "status": "stored",
        "evidence_id": evidence_id,
        "warning": "Stored evidence remains untrusted until source provenance and clinical validation policy are satisfied.",
    }
