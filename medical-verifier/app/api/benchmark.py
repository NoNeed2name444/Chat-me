from fastapi import APIRouter

from app.models.benchmark import (
    BenchmarkIntegrityRequest,
    BenchmarkIntegrityResponse,
)
from app.verification.benchmark_dataset import BenchmarkRecord
from app.verification.benchmark_manifest import BenchmarkManifest
from app.verification.dataset_integrity import (
    find_duplicate_cases,
    summarize_integrity,
)

router = APIRouter(tags=["benchmark"])


@router.post("/benchmark/integrity", response_model=BenchmarkIntegrityResponse)
def benchmark_integrity(request: BenchmarkIntegrityRequest):
    records = [
        BenchmarkRecord(
            case.case_id,
            case.claim,
            case.evidence,
            case.expected,
            case.subgroup,
            case.risk_level,
            case.source_family,
        )
        for case in request.cases
    ]
    findings = find_duplicate_cases(records)
    manifest = BenchmarkManifest(
        "1.5",
        request.dataset_id,
        request.snapshot_id,
        tuple(case.case_id for case in records),
        request.parent_snapshot_sha256,
    )
    return BenchmarkIntegrityResponse(
        dataset_id=request.dataset_id,
        snapshot_id=request.snapshot_id,
        snapshot_sha256=manifest.digest(),
        case_count=len(records),
        integrity_findings=summarize_integrity(findings),
        limitations=[
            "Integrity checks detect only the implemented deterministic conditions.",
            "A clean result does not establish clinical validity or dataset independence.",
            "The snapshot hash is an integrity digest, not a digital signature.",
        ],
    )
