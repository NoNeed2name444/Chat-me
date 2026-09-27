from datetime import date
from pydantic import BaseModel

class EvidenceItem(BaseModel):
    id: str
    title: str
    source_type: str
    publisher: str
    publication_date: date | None = None
    effective_date: date | None = None
    url: str | None = None
    passage: str = ""

    # Provenance / independence
    source_family: str = ""
    canonical_id: str | None = None
    independence_group: str = ""

    # Verification signals
    source_authority: float = 0.0
    relevance_score: float = 0.0
    temporal_score: float = 0.0
    quality_score: float = 0.0
    supports: bool | None = None
    structured_entailment: dict = {}
