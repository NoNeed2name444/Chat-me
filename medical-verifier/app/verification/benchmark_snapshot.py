"""Versioned benchmark snapshots with content-bound manifest verification."""

from dataclasses import dataclass
import json
from typing import Any, Iterable

from app.verification.benchmark_dataset import BenchmarkRecord
from app.verification.benchmark_manifest import BenchmarkManifest, case_digest

SNAPSHOT_SCHEMA_VERSION = "1.6"


def _record_to_dict(record: BenchmarkRecord) -> dict[str, Any]:
    return {
        "case_id": record.case_id,
        "claim": record.claim,
        "evidence": record.evidence,
        "expected": record.expected,
        "subgroup": record.subgroup,
        "risk_level": record.risk_level,
        "source_family": record.source_family,
        "study_family_id": record.study_family_id,
        "canonical_id": record.canonical_id,
        "independence_group": record.independence_group,
        "source_snapshot_sha256": record.source_snapshot_sha256,
        "passage_sha256": record.passage_sha256,
        "split": record.split,
    }


def _records_from_payload(rows: Iterable[dict[str, Any]]) -> tuple[BenchmarkRecord, ...]:
    return tuple(
        BenchmarkRecord(
            case_id=str(row["case_id"]),
            claim=str(row["claim"]),
            evidence=str(row["evidence"]),
            expected=str(row["expected"]),
            subgroup=str(row.get("subgroup", "unspecified")),
            risk_level=str(row.get("risk_level", "unspecified")),
            source_family=str(row.get("source_family", "unspecified")),
            study_family_id=row.get("study_family_id"),
            canonical_id=row.get("canonical_id"),
            independence_group=str(row.get("independence_group", "")),
            source_snapshot_sha256=row.get("source_snapshot_sha256"),
            passage_sha256=row.get("passage_sha256"),
            split=str(row.get("split", "unspecified")),
        )
        for row in rows
    )


def _manifest_for_cases(raw_manifest: dict[str, Any], cases: tuple[BenchmarkRecord, ...]) -> BenchmarkManifest:
    case_ids = tuple(case.case_id for case in cases)

    raw_case_ids = raw_manifest.get("case_ids")
    if not isinstance(raw_case_ids, list):
        raise ValueError("missing_manifest_case_ids")
    supplied_case_ids = tuple(str(value) for value in raw_case_ids)
    if supplied_case_ids != tuple(sorted(supplied_case_ids)):
        raise ValueError("manifest_case_ids_must_be_sorted")
    if supplied_case_ids != case_ids:
        raise ValueError("manifest_case_ids_mismatch")

    expected_digests = tuple(case_digest(case) for case in cases)
    supplied_digests = raw_manifest.get("case_digests")
    if not isinstance(supplied_digests, list):
        raise ValueError("missing_case_digests")
    if tuple(supplied_digests) != expected_digests:
        raise ValueError("case_digest_mismatch")

    return BenchmarkManifest(
        manifest_version=str(raw_manifest.get("manifest_version", "")),
        dataset_id=str(raw_manifest.get("dataset_id", "")),
        snapshot_id=str(raw_manifest.get("snapshot_id", "")),
        case_ids=case_ids,
        parent_snapshot_sha256=raw_manifest.get("parent_snapshot_sha256"),
        case_digests=expected_digests,
    )


@dataclass(frozen=True)
class BenchmarkSnapshot:
    schema_version: str
    manifest: BenchmarkManifest
    cases: tuple[BenchmarkRecord, ...]

    @classmethod
    def from_cases(
        cls,
        *,
        dataset_id: str,
        snapshot_id: str,
        cases: Iterable[BenchmarkRecord],
        parent_snapshot_sha256: str | None = None,
    ) -> "BenchmarkSnapshot":
        ordered = tuple(sorted(cases, key=lambda item: item.case_id))
        manifest = BenchmarkManifest(
            manifest_version=SNAPSHOT_SCHEMA_VERSION,
            dataset_id=dataset_id,
            snapshot_id=snapshot_id,
            case_ids=tuple(case.case_id for case in ordered),
            parent_snapshot_sha256=parent_snapshot_sha256,
            case_digests=tuple(case_digest(case) for case in ordered),
        )
        return cls(SNAPSHOT_SCHEMA_VERSION, manifest, ordered)

    def export_dict(self) -> dict[str, Any]:
        return {
            "schema_version": self.schema_version,
            "manifest": self.manifest.to_dict(),
            "cases": [_record_to_dict(case) for case in self.cases],
        }

    def export_json(self) -> str:
        return json.dumps(self.export_dict(), sort_keys=True, separators=(",", ":"))

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "BenchmarkSnapshot":
        if not isinstance(payload, dict):
            raise ValueError("snapshot_payload_must_be_object")
        if str(payload.get("schema_version", "")) != SNAPSHOT_SCHEMA_VERSION:
            raise ValueError("unsupported_snapshot_schema_version")

        raw_manifest = payload.get("manifest")
        raw_cases = payload.get("cases")
        if not isinstance(raw_manifest, dict):
            raise ValueError("missing_snapshot_manifest")
        if not isinstance(raw_cases, list):
            raise ValueError("snapshot_cases_must_be_array")

        cases = tuple(sorted(_records_from_payload(raw_cases), key=lambda item: item.case_id))
        manifest = _manifest_for_cases(raw_manifest, cases)
        supplied_hash = raw_manifest.get("snapshot_sha256")
        if not isinstance(supplied_hash, str) or len(supplied_hash) != 64:
            raise ValueError("missing_or_invalid_manifest_hash")
        if supplied_hash != manifest.digest():
            raise ValueError("manifest_hash_mismatch")
        if manifest.validate():
            raise ValueError("invalid_manifest")

        return cls(SNAPSHOT_SCHEMA_VERSION, manifest, cases)


def verify_snapshot_manifest(
    snapshot: BenchmarkSnapshot,
    *,
    expected_snapshot_sha256: str | None = None,
) -> tuple[bool, tuple[str, ...]]:
    problems: list[str] = []
    expected_digests = tuple(case_digest(case) for case in snapshot.cases)

    if snapshot.schema_version != SNAPSHOT_SCHEMA_VERSION:
        problems.append("unsupported_snapshot_schema_version")
    if snapshot.manifest.manifest_version != SNAPSHOT_SCHEMA_VERSION:
        problems.append("unsupported_manifest_version")
    if snapshot.manifest.case_digests != expected_digests:
        problems.append("case_digest_mismatch")
    if snapshot.manifest.case_ids != tuple(case.case_id for case in snapshot.cases):
        problems.append("manifest_case_ids_mismatch")
    if expected_snapshot_sha256 is not None and expected_snapshot_sha256 != snapshot.manifest.digest():
        problems.append("manifest_hash_mismatch")

    return not problems, tuple(sorted(set(problems)))
