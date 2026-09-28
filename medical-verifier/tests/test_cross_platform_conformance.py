import json
from hashlib import sha256
from pathlib import Path

from app.models.evidence import EvidenceItem
from app.verification.question_validation import validate_question

VECTORS = (
    Path(__file__).parents[1]
    / "ios"
    / "MedicalVerifierCore"
    / "Tests"
    / "MedicalVerifierCoreTests"
    / "Resources"
    / "conformance_vectors.json"
)

def normalized_hash(text):
    normalized = " ".join((text or "").split())
    return sha256(normalized.encode("utf-8")).hexdigest()

def file_hash(seed):
    return sha256(seed.encode("utf-8")).hexdigest()

def load_vectors():
    return json.loads(VECTORS.read_text())["cases"]

def make_source(case):
    if case.get("source_present", True) is False:
        return []

    passage = case["tampered_passage"] if case.get("tampered_passage") else case["source_passage"]

    return [
        EvidenceItem(
            id=case["id"],
            title=case["source_title"],
            source_type="reference",
            publisher="course",
            passage=passage,
            source_snapshot_sha256=file_hash(case["source_file_seed"]),
            passage_sha256=normalized_hash(case["source_passage"]),
            source_locator="page:1",
            document_version="2022",
            extraction_quality=case.get("extraction_quality", 1.0),
            extraction_warnings=case.get("extraction_warnings", []),
        )
    ]

def test_shared_conformance_vectors():
    for case in load_vectors():
        result = validate_question(
            case["prompt"],
            case["answer"],
            make_source(case),
        )

        assert result.status == case["expected_status"], case["id"]
        assert result.requires_review is case["requires_review"], case["id"]
