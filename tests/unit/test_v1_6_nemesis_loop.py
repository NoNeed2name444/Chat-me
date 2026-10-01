import json
from dataclasses import replace

import pytest

from evals.suites.benchmark_dataset import BenchmarkRecord
from evals.suites.benchmark_manifest import case_digest
from evals.suites.benchmark_snapshot import BenchmarkSnapshot, verify_snapshot_manifest
from evals.suites.dataset_integrity import (
    find_cross_split_provenance_leakage,
    find_duplicate_case_ids,
)
from agents.specialists.retrieval_agent.provenance import evidence_provenance_warnings


def record(case_id="c1", **overrides):
    values = {
        "case_id": case_id,
        "claim": "Drug A reduces pain.",
        "evidence": "Drug A reduces pain.",
        "expected": "SUPPORTS",
        "source_family": "guideline-family-a",
        "canonical_id": "guideline-123",
        "source_snapshot_sha256": "a" * 64,
        "passage_sha256": None,
        "split": "test",
    }
    values.update(overrides)
    return BenchmarkRecord(**values)


def test_snapshot_digest_changes_when_case_content_changes():
    original = record()
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="nemesis",
        snapshot_id="s1",
        cases=[original],
    )
    exported = snapshot.export_dict()

    tampered = json.loads(snapshot.export_json())
    tampered["cases"][0]["evidence"] = "Drug A increases bleeding."

    with pytest.raises(ValueError, match="case_digest_mismatch"):
        BenchmarkSnapshot.from_dict(tampered)

    assert snapshot.manifest.case_digests == (case_digest(original),)
    assert verify_snapshot_manifest(snapshot)[0] is True


def test_duplicate_case_ids_are_never_hidden_by_sorting():
    findings = find_duplicate_case_ids([record("b"), record("a"), record("b")])
    assert [(item.case_id, item.kind) for item in findings] == [("b", "duplicate_case_id")]


def test_train_test_provenance_overlap_is_flagged_even_without_text_duplicate():
    train = [record("train", claim="Drug A reduces pain.", evidence="Source says benefit.", split="train")]
    test = [record("test", claim="Drug A increases pain.", evidence="Different passage.", split="test")]
    findings = find_cross_split_provenance_leakage(train, test)
    kinds = {item.kind for item in findings}
    assert "train_test_source_family_overlap" in kinds
    assert "train_test_canonical_id_overlap" in kinds


def test_provenance_bound_rejects_missing_hashes():
    item = record(passage_sha256=None)
    warnings = evidence_provenance_warnings(item, mode="bound")
    assert "missing_passage_sha256" in warnings


def test_provenance_bound_rejects_wrong_passage_hash():
    item = record(passage_sha256="b" * 64)
    warnings = evidence_provenance_warnings(item, mode="bound")
    assert "passage_hash_mismatch" in warnings


def test_near_spelling_source_change_is_not_silently_equivalent():
    original = record(claim="Paracetamol reduces pain.", evidence="Paracetamol reduces pain.")
    near = replace(original, evidence="Paracetamol reduces pains.")
    assert case_digest(original) != case_digest(near)
