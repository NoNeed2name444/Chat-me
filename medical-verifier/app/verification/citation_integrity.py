import hashlib
import re
from datetime import datetime, timezone

def normalize_source_text(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()

def sha256_text(text: str) -> str:
    return hashlib.sha256(
        normalize_source_text(text).encode("utf-8")
    ).hexdigest()

def bind_evidence(
    item,
    *,
    raw_source_text: str | None,
    source_passage_text: str | None = None,
    source_locator: str | None = None,
    document_version: str | None = None,
):
    if source_passage_text is None:
        source_passage_text = item.passage

    if raw_source_text is not None:
        item.source_snapshot_sha256 = sha256_text(raw_source_text)

    if source_passage_text:
        item.passage_sha256 = sha256_text(source_passage_text)

    if source_locator:
        item.source_locator = source_locator

    if document_version:
        item.document_version = document_version

    item.retrieved_at = datetime.now(timezone.utc)
    return item

def verify_citation(item, source_text: str | None = None):
    warnings = []

    if not item.id:
        warnings.append("missing_evidence_id")

    if not item.url:
        warnings.append("missing_source_url")

    if not item.url and not item.source_locator:
        warnings.append("missing_source_locator")

    if not item.passage.strip():
        warnings.append("missing_source_passage")

    if item.source_type == "literature" and item.passage.lower().startswith(
        "pubmed metadata record"
    ):
        warnings.append("metadata_record_not_clinical_entailment")

    if item.passage_sha256 and item.passage_sha256 != sha256_text(item.passage):
        warnings.append("passage_hash_mismatch")

    if item.source_snapshot_sha256 is None:
        warnings.append("source_snapshot_unverified")

    if source_text is not None and item.passage.strip():
        source_norm = normalize_source_text(source_text)
        passage_norm = normalize_source_text(item.passage)

        if passage_norm not in source_norm:
            warnings.append("citation_passage_not_found_in_source")

        if (
            item.source_snapshot_sha256
            and item.source_snapshot_sha256 != sha256_text(source_text)
        ):
            warnings.append("source_snapshot_hash_mismatch")

    if warnings:
        item.supports = None
        item.quality_score = round(item.quality_score * 0.40, 4)

    return item, warnings
