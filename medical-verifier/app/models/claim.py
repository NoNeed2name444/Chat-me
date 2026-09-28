from typing import Any, Literal
from pydantic import BaseModel, Field

VerificationMode = Literal[
    "current_medical",
    "curriculum_faithful",
    "curriculum_update_aware",
]

ProvenanceMode = Literal[
    "permissive",
    "bound",
]

class ClaimRequest(BaseModel):
    claim: str = Field(min_length=3, max_length=5000)
    context: dict[str, Any] = Field(default_factory=dict)
    sources: list[Literal["pubmed", "openfda", "local"]] = Field(
        default_factory=lambda: ["pubmed", "openfda", "local"]
    )
    requested_evidence_level: Literal[
        "any", "authoritative", "highest_available"
    ] = "authoritative"

    verification_mode: VerificationMode = "current_medical"
    curriculum_source_ids: list[str] = Field(default_factory=list)
    curriculum_snapshot: str | None = None
    question_context: str | None = None
    provenance_mode: ProvenanceMode = "permissive"
    verification_contract_version: str = "1.8"

class NormalizedClaim(BaseModel):
    original: str
    normalized: str
    concepts: list[str] = Field(default_factory=list)
    claim_type: str = "general_medical_claim"
