def build_report(aggregate: dict, evidence: list, reasons: list):
    source_families = sorted({
        item.source_family
        for item in evidence
        if getattr(item, "source_family", None)
    })
    unresolved = [
        item.id for item in evidence
        if item.supports is None
    ]
    low_relevance = [
        item.id for item in evidence
        if getattr(item, "relevance_score", 0.0) < 0.20
    ]

    return {
        "evidence_count": len(evidence),
        "usable_evidence_count": aggregate["usable_evidence_count"],
        "independent_support_groups": aggregate["independent_support_groups"],
        "independent_contradiction_groups": aggregate["independent_contradiction_groups"],
        "support_source_families": aggregate["support_source_families"],
        "contradiction_source_families": aggregate["contradiction_source_families"],
        "support_ratio": aggregate["support_ratio"],
        "source_families": source_families,
        "unresolved_evidence_ids": unresolved,
        "low_relevance_evidence_ids": low_relevance,
        "decision_reasons": reasons,
    }
