from typing import Literal

from pydantic import BaseModel, Field, ConfigDict


BenchmarkSplit = Literal["train", "test", "unspecified"]


class BenchmarkManifestRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    manifest_version: str = "1.6"
    dataset_id: str = Field(min_length=1, max_length=200)
    snapshot_id: str = Field(min_length=1, max_length=200)
    case_ids: list[str] = Field(default_factory=list)
    parent_snapshot_sha256: str | None = None
    snapshot_sha256: str | None = None


class BenchmarkCaseRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: str = Field(min_length=1, max_length=200)
    claim: str = Field(min_length=3, max_length=5000)
    evidence: str = Field(min_length=1, max_length=20000)
    expected: str
    subgroup: str = "unspecified"
    risk_level: str = "unspecified"
    source_family: str = "unspecified"
    study_family_id: str | None = None
    canonical_id: str | None = None
    independence_group: str = ""
    source_snapshot_sha256: str | None = None
    passage_sha256: str | None = None
    split: BenchmarkSplit = "unspecified"


class BenchmarkIntegrityRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    dataset_id: str = Field(min_length=1, max_length=200)
    snapshot_id: str = Field(min_length=1, max_length=200)
    cases: list[BenchmarkCaseRequest] = Field(default_factory=list)
    train_cases: list[BenchmarkCaseRequest] = Field(default_factory=list)
    test_cases: list[BenchmarkCaseRequest] = Field(default_factory=list)
    parent_snapshot_sha256: str | None = None
    manifest: BenchmarkManifestRequest | None = None
    expected_snapshot_sha256: str | None = None
    enforce_split_separation: bool = False
    provenance_bound: bool = False


class BenchmarkIntegrityResponse(BaseModel):
    dataset_id: str
    snapshot_id: str
    manifest_schema_version: str
    snapshot_sha256: str
    valid: bool
    case_count: int
    integrity_findings: dict
    limitations: list[str] = Field(default_factory=list)


class BenchmarkSnapshotVerifyRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    snapshot: dict
    expected_snapshot_sha256: str | None = None
    enforce_split_separation: bool = False
    provenance_bound: bool = False


class BenchmarkSnapshotVerifyResponse(BaseModel):
    valid: bool
    snapshot_sha256: str
    dataset_id: str
    snapshot_id: str
    case_count: int
    integrity_findings: dict = {}
    limitations: list[str] = Field(default_factory=list)
