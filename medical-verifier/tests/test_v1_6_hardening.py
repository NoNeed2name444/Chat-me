from fastapi.testclient import TestClient
import pytest

from app.main import app
from app.models.evidence import EvidenceItem
from app.verification.benchmark_dataset import BenchmarkRecord
from app.verification.benchmark_manifest import BenchmarkManifest
from app.verification.benchmark_snapshot import (
    BenchmarkSnapshot,
    SNAPSHOT_SCHEMA_VERSION,
)
from app.verification.dataset_integrity import (
    find_cross_split_provenance_leakage,
    find_duplicate_case_ids,
)
from app.verification.provenance import evidence_provenance_warnings
from app.verification.claim_reasoning import decompose_claim, temporal_entailed


client = TestClient(app)


def _record(case_id="a", split="unspecified", source_family="family-a"):
    return BenchmarkRecord(
        case_id=case_id,
        claim="Paracetamol reduces pain.",
        evidence="Acetaminophen reduces pain.",
        expected="SUPPORTS",
        source_family=source_family,
        study_family_id="study-1",
        canonical_id="source-1",
        source_snapshot_sha256="0" * 64,
        passage_sha256="1" * 64,
        split=split,
    )


def test_snapshot_round_trip_and_hash_binding():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-1",
        cases=(_record(),),
    )
    payload = snapshot.export_dict()
    loaded = BenchmarkSnapshot.from_dict(payload)

    assert loaded.export_json() == snapshot.export_json()
    assert loaded.manifest.digest() == snapshot.manifest.digest()


def test_tampered_manifest_hash_is_rejected():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-1",
        cases=(_record(),),
    )
    payload = snapshot.export_dict()
    payload["manifest"]["snapshot_sha256"] = "f" * 64

    with pytest.raises(ValueError, match="manifest_hash_mismatch"):
        BenchmarkSnapshot.from_dict(payload)


def test_snapshot_rejects_unsorted_manifest_case_ids():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-1",
        cases=(_record("a"), _record("b")),
    )
    payload = snapshot.export_dict()
    payload["manifest"]["case_ids"] = ["b", "a"]

    with pytest.raises(ValueError, match="manifest_case_ids_must_be_sorted"):
        BenchmarkSnapshot.from_dict(payload)


def test_snapshot_rejects_missing_manifest_hash():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-1",
        cases=(_record(),),
    )
    payload = snapshot.export_dict()
    payload["manifest"].pop("snapshot_sha256")

    with pytest.raises(ValueError, match="missing_or_invalid_manifest_hash"):
        BenchmarkSnapshot.from_dict(payload)



def test_duplicate_case_ids_are_explicit():
    findings = find_duplicate_case_ids(
        [_record("a"), _record("a", source_family="family-b")]
    )
    assert [(row.kind, row.case_id) for row in findings] == [
        ("duplicate_case_id", "a")
    ]


def test_cross_split_provenance_overlap_is_detected():
    findings = find_cross_split_provenance_leakage(
        [_record("train", split="train")],
        [_record("test", split="test")],
    )
    kinds = {row.kind for row in findings}
    assert "train_test_source_family_overlap" in kinds
    assert "train_test_study_family_overlap" in kinds
    assert "train_test_canonical_id_overlap" in kinds


def test_inline_split_separation_can_be_enforced():
    payload = {
        "dataset_id": "demo",
        "snapshot_id": "snapshot-1",
        "enforce_split_separation": True,
        "cases": [
            {
                **snapshot_case(_record("train", split="train")),
            },
            {
                **snapshot_case(_record("test", split="test")),
            },
        ],
    }
    response = client.post("/v1/benchmark/integrity", json=payload)
    assert response.status_code == 422
    assert response.json()["detail"] == "train_test_separation_violation"


def test_provenance_bound_mode_rejects_missing_case_fields():
    payload = {
        "dataset_id": "demo",
        "snapshot_id": "snapshot-1",
        "provenance_bound": True,
        "cases": [{
            "case_id": "a",
            "claim": "Drug A increases bleeding.",
            "evidence": "Drug A increases bleeding.",
            "expected": "SUPPORTS",
        }],
    }
    response = client.post("/v1/benchmark/integrity", json=payload)
    assert response.status_code == 422
    assert response.json()["detail"] == "provenance_bound_requires_complete_case_provenance"


def test_explicit_date_mismatch_is_not_supported():
    claim = decompose_claim(
        "Drug A increases bleeding on 2026-09-28."
    )[0]
    evidence = decompose_claim(
        "Drug A increases bleeding on 2026-09-27."
    )[0]
    ok, reason = temporal_entailed(claim, evidence)
    assert ok is False
    assert reason == "temporal_date_mismatch"


def test_provenance_bound_evidence_requires_hashes_and_ids():
    item = EvidenceItem(
        id="e1",
        title="Source",
        source_type="reference",
        publisher="Publisher",
        passage="Drug A increases bleeding.",
    )
    warnings = evidence_provenance_warnings(item, mode="bound")
    assert "missing_source_family" in warnings
    assert "missing_canonical_id" in warnings
    assert "missing_source_snapshot_sha256" in warnings
    assert "missing_passage_sha256" in warnings


def test_explicit_valid_provenance_is_accepted():
    item = EvidenceItem(
        id="e1",
        title="Source",
        source_type="reference",
        publisher="Publisher",
        passage="Drug A increases bleeding.",
        source_family="family-a",
        canonical_id="source-1",
        source_snapshot_sha256="0" * 64,
        passage_sha256=(
            "08d3bac20cd27c8c0e7a71b8a7e55fe485f47ba5aabaac2cd21c898ec372a2bd"
        ),
    )
    warnings = evidence_provenance_warnings(item, mode="bound")
    assert "missing_source_family" not in warnings
    assert "missing_canonical_id" not in warnings
    assert "missing_source_snapshot_sha256" not in warnings
    assert "missing_passage_sha256" not in warnings


def test_snapshot_rejects_unsorted_manifest_case_ids():
    manifest = BenchmarkManifest("1.6", "demo", "snapshot-1", ("a", "b"))
    payload = {
        "schema_version": "1.6",
        "manifest": {**manifest.to_dict(), "case_ids": ["b", "a"]},
        "cases": [snapshot_case(_record("a")), snapshot_case(_record("b"))],
    }
    import pytest
    with pytest.raises(ValueError, match="manifest_case_ids_must_be_sorted"):
        BenchmarkSnapshot.from_dict(payload)


def test_snapshot_rejects_missing_manifest_hash():
    manifest = BenchmarkManifest("1.6", "demo", "snapshot-1", ("a",))
    payload = {
        "schema_version": "1.6",
        "manifest": {k: v for k, v in manifest.to_dict().items() if k != "snapshot_sha256"},
        "cases": [snapshot_case(_record())],
    }
    import pytest
    with pytest.raises(ValueError, match="missing_or_invalid_manifest_hash"):
        BenchmarkSnapshot.from_dict(payload)


def test_custom_empty_alias_map_does_not_fall_back_to_defaults():
    from app.verification.entity_normalization import entities_equivalent
    assert not entities_equivalent(
        "paracetamol",
        "acetaminophen",
        aliases={},
    )


def test_provenance_bound_mode_rejects_malformed_hash():
    payload = {
        "dataset_id": "demo",
        "snapshot_id": "snapshot-1",
        "provenance_bound": True,
        "cases": [{
            "case_id": "a",
            "claim": "Drug A increases bleeding.",
            "evidence": "Drug A increases bleeding.",
            "expected": "SUPPORTS",
            "source_family": "family-a",
            "canonical_id": "source-1",
            "source_snapshot_sha256": "not-a-hash",
            "passage_sha256": "0" * 64,
        }],
    }
    response = client.post("/v1/benchmark/integrity", json=payload)
    assert response.status_code == 422
    assert response.json()["detail"] == "provenance_bound_requires_complete_case_provenance"


def test_generic_drug_anchor_cannot_cross_subjects():
    from app.verification.independent_entailment import verify
    result = verify(
        "Drug A increases bleeding.",
        "Drug B increases bleeding.",
    )
    assert result.label == "UNKNOWN"
    assert "atomic_subject_mismatch" in result.reasons
