"""Immutable, content-bound benchmark manifest helpers."""

from dataclasses import dataclass
import hashlib
import json


EXPECTED_MANIFEST_VERSION = "1.6"


def _canonical_case_digest(case) -> str:
    payload = {
        "case_id": case.case_id,
        "claim": case.claim,
        "evidence": case.evidence,
        "expected": case.expected,
        "subgroup": case.subgroup,
        "risk_level": case.risk_level,
        "source_family": case.source_family,
        "study_family_id": case.study_family_id,
        "canonical_id": case.canonical_id,
        "independence_group": case.independence_group,
        "source_snapshot_sha256": case.source_snapshot_sha256,
        "passage_sha256": case.passage_sha256,
        "split": case.split,
    }
    return hashlib.sha256(
        json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


@dataclass(frozen=True)
class BenchmarkManifest:
    manifest_version: str
    dataset_id: str
    snapshot_id: str
    case_ids: tuple[str, ...]
    parent_snapshot_sha256: str | None = None
    case_digests: tuple[str, ...] = ()

    def digest(self) -> str:
        payload = {
            "manifest_version": self.manifest_version,
            "dataset_id": self.dataset_id,
            "snapshot_id": self.snapshot_id,
            "case_ids": list(self.case_ids),
            "parent_snapshot_sha256": self.parent_snapshot_sha256,
            "case_digests": list(self.case_digests),
        }
        encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
        return hashlib.sha256(encoded).hexdigest()

    def to_dict(self) -> dict[str, object]:
        return {
            "manifest_version": self.manifest_version,
            "dataset_id": self.dataset_id,
            "snapshot_id": self.snapshot_id,
            "case_ids": list(self.case_ids),
            "parent_snapshot_sha256": self.parent_snapshot_sha256,
            "case_digests": list(self.case_digests),
            "snapshot_sha256": self.digest(),
        }

    def validate(self) -> tuple[str, ...]:
        errors = []
        if self.manifest_version != EXPECTED_MANIFEST_VERSION:
            errors.append("unsupported_manifest_version")
        if not self.dataset_id.strip():
            errors.append("empty_dataset_id")
        if not self.snapshot_id.strip():
            errors.append("empty_snapshot_id")
        if len(self.case_ids) != len(set(self.case_ids)):
            errors.append("duplicate_case_id")
        if self.case_digests and len(self.case_digests) != len(self.case_ids):
            errors.append("case_digest_count_mismatch")
        return tuple(sorted(set(errors)))


def case_digest(case) -> str:
    """Public deterministic digest for binding a manifest to case content."""
    return _canonical_case_digest(case)
