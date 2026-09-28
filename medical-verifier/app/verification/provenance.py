from app.verification.citation_integrity import verify_citation

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
