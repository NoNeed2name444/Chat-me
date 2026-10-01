import json
from dataclasses import dataclass
from datetime import date

import httpx

from api.config import settings
from api.schemas.evidence import EvidenceItem
from agents.specialists.retrieval_agent.citation_integrity import sha256_text

@dataclass(frozen=True)
class RevalidationResult:
    status: str
    checked_at: str
    warnings: tuple[str, ...]
    remote_snapshot_sha256: str | None = None

def _today():
    return date.today().isoformat()

def revalidate_openfda(item: EvidenceItem) -> RevalidationResult:
    if item.source_type != "regulatory":
        return RevalidationResult(
            status="NOT_APPLICABLE",
            checked_at=_today(),
            warnings=("not_openfda_record",),
        )

    if not item.canonical_id:
        return RevalidationResult(
            status="UNVERIFIABLE",
            checked_at=_today(),
            warnings=("missing_openfda_set_id",),
        )

    params = {
        "search": f'set_id:"{item.canonical_id}"',
        "limit": 1,
    }

    if settings.openfda_api_key:
        params["api_key"] = settings.openfda_api_key

    try:
        with httpx.Client(timeout=settings.evidence_timeout_seconds) as client:
            response = client.get(
                "https://api.fda.gov/drug/label.json",
                params=params,
            )

        if response.status_code == 404:
            return RevalidationResult(
                status="NOT_FOUND",
                checked_at=_today(),
                warnings=("openfda_record_not_found",),
            )

        response.raise_for_status()
        payload = response.json()
        records = payload.get("results", [])

        if not records:
            return RevalidationResult(
                status="NOT_FOUND",
                checked_at=_today(),
                warnings=("openfda_record_not_found",),
            )

        raw = json.dumps(
            records[0],
            sort_keys=True,
            ensure_ascii=False,
        )
        remote_hash = sha256_text(raw)

        warnings = []
        if (
            item.source_snapshot_sha256
            and item.source_snapshot_sha256 != remote_hash
        ):
            warnings.append("source_snapshot_changed")

        return RevalidationResult(
            status="UNCHANGED" if not warnings else "CHANGED",
            checked_at=_today(),
            warnings=tuple(warnings),
            remote_snapshot_sha256=remote_hash,
        )

    except Exception as exc:
        return RevalidationResult(
            status="ERROR",
            checked_at=_today(),
            warnings=(f"revalidation_error:{type(exc).__name__}",),
        )

def revalidate_local_snapshot(item: EvidenceItem) -> RevalidationResult:
    if not item.passage_sha256:
        return RevalidationResult(
            status="UNVERIFIABLE",
            checked_at=_today(),
            warnings=("missing_passage_hash",),
        )

    current_hash = sha256_text(item.passage)

    if current_hash != item.passage_sha256:
        return RevalidationResult(
            status="CHANGED",
            checked_at=_today(),
            warnings=("stored_curriculum_passage_changed",),
            remote_snapshot_sha256=current_hash,
        )

    return RevalidationResult(
        status="UNCHANGED",
        checked_at=_today(),
        warnings=(),
        remote_snapshot_sha256=current_hash,
    )

def revalidate(item: EvidenceItem) -> RevalidationResult:
    if item.source_type == "regulatory":
        return revalidate_openfda(item)

    if item.source_type in {"reference", "curriculum", "local"}:
        return revalidate_local_snapshot(item)

    return RevalidationResult(
        status="NOT_APPLICABLE",
        checked_at=_today(),
        warnings=("source_family_has_no_revalidator",),
    )
