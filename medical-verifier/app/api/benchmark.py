from fastapi import APIRouter, HTTPException

from app.models.benchmark import (
    BenchmarkCaseRequest,
    BenchmarkIntegrityRequest,
    BenchmarkIntegrityResponse,
)
from app.verification.benchmark_dataset import ALLOWED_LABELS, BenchmarkRecord
from app.verification.benchmark_manifest import (
    EXPECTED_MANIFEST_VERSION,
    BenchmarkManifest,
    case_digest,
)
from app.verification.dataset_integrity import (
    IntegrityFinding,
    find_cross_split_duplicates,
    find_cross_split_provenance_leakage,
    find_duplicate_case_ids,
    find_duplicate_cases,
    find_invalid_provenance,
    find_missing_provenance,
    summarize_integrity,
)

router = APIRouter(tags=["benchmark"])


def _record(case: BenchmarkCaseRequest) -> BenchmarkRecord:
    if case.expected not in ALLOWED_LABELS:
        raise HTTPException(status_code=422, detail="unsupported_expected_label")
    try:
        return BenchmarkRecord(
            case_id=case.case_id,
            claim=case.claim,
            evidence=case.evidence,
            expected=case.expected,
            subgroup=case.subgroup,
            risk_level=case.risk_level,
            source_family=case.source_family,
            study_family_id=case.study_family_id,
            canonical_id=case.canonical_id,
            independence_group=case.independence_group,
            source_snapshot_sha256=case.source_snapshot_sha256,
            passage_sha256=case.passage_sha256,
            split=case.split,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@router.post("/benchmark/integrity", response_model=BenchmarkIntegrityResponse)
def benchmark_integrity(request: BenchmarkIntegrityRequest):
    train_records = tuple(_record(case) for case in request.train_cases)
    test_records = tuple(_record(case) for case in request.test_cases)
    inline_records = tuple(_record(case) for case in request.cases)
    records = inline_records + train_records + test_records

    inline_train = tuple(case for case in inline_records if case.split == "train")
    inline_test = tuple(case for case in inline_records if case.split == "test")
    train_records += inline_train
    test_records += inline_test

    findings = []
    findings.extend(find_duplicate_case_ids(records))
    findings.extend(find_duplicate_cases(records))

    if train_records or test_records:
        findings.extend(find_cross_split_duplicates(train_records, test_records))
        findings.extend(find_cross_split_provenance_leakage(train_records, test_records))

    if request.provenance_bound:
        findings.extend(find_missing_provenance(records))
        findings.extend(find_invalid_provenance(records))

    ordered_records = tuple(sorted(records, key=lambda case: case.case_id))
    case_ids = tuple(case.case_id for case in ordered_records)
    case_digests = tuple(case_digest(case) for case in ordered_records)

    manifest = BenchmarkManifest(
        manifest_version=(request.manifest.manifest_version if request.manifest else EXPECTED_MANIFEST_VERSION),
        dataset_id=request.dataset_id,
        snapshot_id=request.snapshot_id,
        case_ids=case_ids,
        parent_snapshot_sha256=(
            request.manifest.parent_snapshot_sha256
            if request.manifest
            else request.parent_snapshot_sha256
        ),
        case_digests=case_digests,
    )

    for error in manifest.validate():
        findings.append({"case_id": manifest.snapshot_id, "kind": error, "detail": "manifest_validation"})

    if request.manifest:
        if request.manifest.dataset_id != request.dataset_id:
            findings.append({"case_id": manifest.snapshot_id, "kind": "manifest_dataset_id_mismatch", "detail": "manifest_dataset_id_differs_from_request"})
        if request.manifest.snapshot_id != request.snapshot_id:
            findings.append({"case_id": manifest.snapshot_id, "kind": "manifest_snapshot_id_mismatch", "detail": "manifest_snapshot_id_differs_from_request"})
        if tuple(sorted(request.manifest.case_ids)) != case_ids:
            findings.append({"case_id": manifest.snapshot_id, "kind": "manifest_case_ids_mismatch", "detail": "manifest_case_ids_differs_from_records"})
        if request.manifest.snapshot_sha256 and request.manifest.snapshot_sha256 != manifest.digest():
            findings.append({"case_id": manifest.snapshot_id, "kind": "manifest_hash_mismatch", "detail": "supplied_manifest_hash_differs_from_recomputed"})

    if request.expected_snapshot_sha256 and request.expected_snapshot_sha256 != manifest.digest():
        findings.append({"case_id": manifest.snapshot_id, "kind": "manifest_hash_mismatch", "detail": "expected_snapshot_hash_differs_from_recomputed"})

    if request.enforce_split_separation:
        enforcement_kinds = {
            "train_test_duplicate",
            "train_test_source_family_overlap",
            "train_test_study_family_overlap",
            "train_test_canonical_id_overlap",
        }
        if any((f.kind if isinstance(f, IntegrityFinding) else f.get("kind")) in enforcement_kinds for f in findings):
            raise HTTPException(status_code=422, detail="train_test_separation_violation")

    if request.provenance_bound:
        provenance_errors = {
            "missing_provenance",
            "invalid_source_snapshot_sha256",
            "invalid_passage_sha256",
            "passage_sha256_mismatch",
        }
        if any((f.kind if isinstance(f, IntegrityFinding) else f.get("kind")) in provenance_errors for f in findings):
            raise HTTPException(status_code=422, detail="provenance_bound_requires_complete_case_provenance")

    normalized_findings = []
    for finding in findings:
        if isinstance(finding, IntegrityFinding):
            normalized_findings.append(finding)
        else:
            normalized_findings.append(IntegrityFinding(str(finding["case_id"]), str(finding["kind"]), str(finding["detail"])))

    finding_summary = summarize_integrity(normalized_findings)
    return BenchmarkIntegrityResponse(
        dataset_id=request.dataset_id,
        snapshot_id=request.snapshot_id,
        manifest_schema_version=manifest.manifest_version,
        snapshot_sha256=manifest.digest(),
        valid=not finding_summary["finding_count"],
        case_count=len(records),
        integrity_findings=finding_summary,
        limitations=[
            "Integrity checks detect only the implemented deterministic conditions.",
            "A clean result does not establish clinical validity or dataset independence.",
            "The snapshot digest binds manifest metadata and canonicalized case content; it is not a digital signature.",
            "Source-family/study-family overlap is a leakage warning and is not a clinical quality measure.",
        ],
    )
