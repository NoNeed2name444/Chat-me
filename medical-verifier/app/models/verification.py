from pydantic import BaseModel, Field

class VerificationResponse(BaseModel):
    verdict: str
    confidence: float = Field(ge=0, le=1)
    confidence_semantics: str

    claim: str
    normalized_claim: str = ""
    claim_type: str = "general_medical_claim"
    risk_level: str

    atomic_assertions: list[dict] = []
    evidence: list[dict] = []
    contradictions: list[dict] = []

    verification_mode: str = "current_medical"

    curriculum_assessment: dict = {}
    current_evidence_assessment: dict = {}
    knowledge_divergence: str = "none"
    study_hint: str | None = None
    source_revalidation: dict = {}

    reliability: dict = {}
    adversarial_findings: list[dict] = []

    limitations: list[str] = []
    decision_reasons: list[str] = []
    missing_context: list[str] = []

    requires_human_review: bool
    verifier_version: str = "1.5.0"
    verification_contract_version: str = "1.7"
    knowledge_snapshot: str = "LIVE-API"
    verification_id: str
