from datetime import date

CURRENT_TERMS = (
    "currently",
    "today",
    "now",
    "as of",
    "current",
    "latest",
    "presently",
)

def guard_temporal_specificity(item, claim, today: date):
    lower = claim.lower()
    warnings = []

    if not any(term in lower for term in CURRENT_TERMS):
        return item, warnings

    observed = item.effective_date or item.publication_date
    if observed is None:
        warnings.append("current_claim_has_no_evidence_date")
    else:
        age_days = max(0, (today - observed).days)
        if age_days > 365:
            warnings.append("current_claim_relies_on_old_evidence")

    if warnings:
        item.supports = None
        item.quality_score = round(item.quality_score * 0.55, 4)

    return item, warnings
