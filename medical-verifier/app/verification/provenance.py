
def validate_provenance(item):
    warnings = []
    if not item.id:
        warnings.append("missing_evidence_id")
    if not item.url:
        warnings.append("missing_source_url")
    if not item.passage.strip():
        warnings.append("missing_source_passage")
    if not item.title.strip():
        warnings.append("missing_source_title")
    metadata_only = item.source_type == "literature" and item.passage.lower().startswith("pubmed metadata record")
    if metadata_only:
        warnings.append("metadata_record_not_clinical_entailment")
    if warnings:
        item.supports = None
        item.quality_score = round(item.quality_score * 0.40, 4)
    return item, warnings


def apply_temporal_supersession(items):
    newest_by_key = {}
    for item in items:
        key = item.canonical_id or item.source_family or item.publisher
        observed = item.effective_date or item.publication_date
        if observed is None:
            continue
        if key not in newest_by_key or observed > newest_by_key[key]:
            newest_by_key[key] = observed

    warnings = []
    for item in items:
        key = item.canonical_id or item.source_family or item.publisher
        newest = newest_by_key.get(key)
        observed = item.effective_date or item.publication_date
        if newest and observed and observed < newest and item.source_type in {"regulatory", "guideline"}:
            item.quality_score = round(item.quality_score * 0.35, 4)
            warnings.append((item.id, "superseded_or_older_version_penalty"))
    return items, warnings
