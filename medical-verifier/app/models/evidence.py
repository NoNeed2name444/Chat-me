from datetime import date, datetime
from pydantic import BaseModel, Field

class EvidenceItem(BaseModel):
    id: str
    title: str
    source_type: str
    publisher: str
    publication_date: date | None = None
    effective_date: date | None = None
    url: str | None = None
    source_locator: str | None = None
    passage: str = ""

    # Provenance / independence
    source_family: str = ""
    canonical_id: str | None = None
    independence_group: str = ""
    study_family_id: str | None = None
    derived_from_ids: list[str] = Field(default_factory=list)
    document_version: str | None = None
    retrieved_at: datetime | None = None
    source_snapshot_sha256: str | None = None
    passage_sha256: str | None = None

    # Verification signals
    source_authority: float = 0.0
    relevance_score: float = 0.0
    temporal_score: float = 0.0
    quality_score: float = 0.0
    supports: bool | None = None
