"""Immutable, hash-addressed benchmark manifest helpers."""

from dataclasses import dataclass
import hashlib
import json


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
        encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
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
