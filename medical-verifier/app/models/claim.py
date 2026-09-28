from typing import Any, Literal
from pydantic import BaseModel, Field

class ClaimRequest(BaseModel):
    claim: str = Field(min_length=3, max_length=5000)
    context: dict[str, Any] = Field(default_factory=dict)
    sources: list[Literal["pubmed", "openfda", "local"]] = Field(
        default_factory=lambda: ["pubmed", "openfda", "local"]
    )
    requested_evidence_level: Literal[
        "any", "authoritative", "highest_available"
    ] = "authoritative"

class NormalizedClaim(BaseModel):
    original: str
    normalized: str
    concepts: list[str] = Field(default_factory=list)
    claim_type: str = "general_medical_claim"
