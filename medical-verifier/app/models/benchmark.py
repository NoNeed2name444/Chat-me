from pydantic import BaseModel, Field


class BenchmarkCaseRequest(BaseModel):
    case_id: str = Field(min_length=1, max_length=200)
    claim: str = Field(min_length=3, max_length=5000)
    evidence: str = Field(min_length=1, max_length=20000)
    expected: str
    subgroup: str = "unspecified"
    risk_level: str = "unspecified"
    source_family: str = "unspecified"


class BenchmarkIntegrityRequest(BaseModel):
    dataset_id: str = Field(min_length=1, max_length=200)
    snapshot_id: str = Field(min_length=1, max_length=200)
    cases: list[BenchmarkCaseRequest] = Field(default_factory=list)
    parent_snapshot_sha256: str | None = None


class BenchmarkIntegrityResponse(BaseModel):
    dataset_id: str
    snapshot_id: str
    snapshot_sha256: str
    case_count: int
    integrity_findings: dict
    limitations: list[str] = Field(default_factory=list)
