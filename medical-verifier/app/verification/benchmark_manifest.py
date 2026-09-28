"""Immutable, hash-addressed benchmark manifest helpers."""

from dataclasses import dataclass
import hashlib
import json


EXPECTED_MANIFEST_VERSION = "1.6"


@dataclass(frozen=True)
class BenchmarkManifest:
    manifest_version: str
    dataset_id: str
    snapshot_id: str
    case_ids: tuple[str, ...]
    parent_snapshot_sha256: str | None = None

    def digest(self) -> str:
        payload = {
            "manifest_version": self.manifest_version,
            "dataset_id": self.dataset_id,
            "snapshot_id": self.snapshot_id,
            "case_ids": list(self.case_ids),
            "parent_snapshot_sha256": self.parent_snapshot_sha256,
        }
        encoded = json.dumps(
            payload,
            sort_keys=True,
            separators=(",", ":"),
        ).encode()
        return hashlib.sha256(encoded).hexdigest()

    def to_dict(self) -> dict[str, object]:
        return {
            "manifest_version": self.manifest_version,
            "dataset_id": self.dataset_id,
            "snapshot_id": self.snapshot_id,
            "case_ids": list(self.case_ids),
            "parent_snapshot_sha256": self.parent_snapshot_sha256,
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
        return tuple(sorted(set(errors)))
