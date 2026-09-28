"""Explicit provenance validation and temporal supersession helpers."""

import re

from app.verification.citation_integrity import sha256_text, verify_citation


SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def evidence_provenance_warnings(item, *, mode: str = "permissive") -> tuple[str, ...]:
    if mode != "bound":
        return ()

    warnings: list[str] = []
    source_family = str(getattr(item, "source_family", "") or "")
    canonical_id = getattr(item, "canonical_id", None)
    source_snapshot_sha256 = getattr(item, "source_snapshot_sha256", None)
    passage_sha256 = getattr(item, "passage_sha256", None)
    passage = getattr(item, "passage", None)
    if passage is None:
        passage = getattr(item, "evidence", "")

    if not source_family.strip():
        warnings.append("missing_source_family")

    if not canonical_id or not str(canonical_id).strip():
        warnings.append("missing_canonical_id")

    if not source_snapshot_sha256:
        warnings.append("missing_source_snapshot_sha256")
    elif not SHA256_RE.fullmatch(str(source_snapshot_sha256).lower()):
        warnings.append("invalid_source_snapshot_sha256")

    if not passage_sha256:
        warnings.append("missing_passage_sha256")
    elif not SHA256_RE.fullmatch(str(passage_sha256).lower()):
        warnings.append("invalid_passage_sha256")
    elif str(passage_sha256).lower() != sha256_text(str(passage)):
        warnings.append("passage_hash_mismatch")

    return tuple(sorted(set(warnings)))


def provenance_is_sufficient(item, *, mode: str = "permissive") -> bool:
    return not evidence_provenance_warnings(item, mode=mode)


def validate_provenance(item):
    item, warnings = verify_citation(item)
    return item, warnings


def apply_temporal_supersession(items):
    newest_by_key = {}

    for item in items:
        key = item.canonical_id or f"{item.source_family}:{item.publisher}"
        observed = item.effective_date or item.publication_date
        if observed is None:
            continue

        current = newest_by_key.get(key)
        if current is None or observed > current:
            newest_by_key[key] = observed

    warnings = []

    for item in items:
        key = item.canonical_id or f"{item.source_family}:{item.publisher}"
        newest = newest_by_key.get(key)
        observed = item.effective_date or item.publication_date

        if (
            newest
            and observed
            and observed < newest
            and item.source_type in {"regulatory", "guideline"}
        ):
            item.quality_score = round(item.quality_score * 0.35, 4)
            warnings.append(
                (item.id, "superseded_or_older_version_penalty")
            )

    return items, warnings
