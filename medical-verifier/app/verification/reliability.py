from collections import defaultdict
from datetime import date

from app.models.evidence import EvidenceItem

SOURCE_FAMILY_DEFAULTS = {
    "regulatory": ("regulatory", 0.92),
    "guideline": ("guideline", 0.95),
    "systematic_review": ("literature_review", 0.90),
    "trial": ("primary_literature", 0.82),
    "observational": ("primary_literature", 0.70),
    "literature": ("primary_literature", 0.75),
    "reference": ("reference", 0.45),
}

def _tokens(text: str) -> set[str]:
    return {x for x in text.lower().split() if len(x) >= 5}

def token_relevance(claim: str, evidence_text: str) -> float:
    claim_tokens = _tokens(claim)
    if not claim_tokens:
        return 0.0
    evidence_tokens = _tokens(evidence_text)
    return len(claim_tokens & evidence_tokens) / len(claim_tokens)

def recency_score(item: EvidenceItem, today: date, half_life_days: int) -> float:
    observed = item.publication_date or item.effective_date
    if observed is None:
        return 0.60
    age = max(0, (today - observed).days)
    return max(0.15, 1.0 / (1.0 + age / half_life_days))

def enrich(item: EvidenceItem, claim: str, today: date) -> EvidenceItem:
    family, default_authority = SOURCE_FAMILY_DEFAULTS.get(
        item.source_type,
        ("other", 0.35),
    )
    item.source_family = item.source_family or family
    if item.source_authority <= 0:
        item.source_authority = default_authority

    relevance = token_relevance(claim, f"{item.title} {item.passage}")
    item.relevance_score = round(relevance, 4)
    item.temporal_score = round(recency_score(item, today, 365 * 3), 4)

    item.quality_score = round(
        0.35 * item.source_authority
        + 0.25 * item.relevance_score
        + 0.20 * item.temporal_score
        + 0.10 * (1.0 if item.passage else 0.0)
        + 0.10 * (1.0 if item.url else 0.0),
        4,
    )
    item.independence_group = item.independence_group or (
        f"{item.source_family}:{item.publisher.lower().strip()}"
    )
    return item

def deduplicate(items: list[EvidenceItem]) -> list[EvidenceItem]:
    seen = set()
    output = []
    for item in items:
        key = (
            item.source_family,
            item.publisher.lower().strip(),
            item.canonical_id or item.id,
            item.title.strip().lower(),
        )
        if key in seen:
            continue
        seen.add(key)
        output.append(item)
    return output

def aggregate(items: list[EvidenceItem]):
    usable = [x for x in deduplicate(items) if x.passage and not x.id.startswith("error:")]

    support_by_group = defaultdict(float)
    contradiction_by_group = defaultdict(float)
    for item in usable:
        weight = item.quality_score
        if item.supports is True:
            support_by_group[item.independence_group] += weight
        elif item.supports is False:
            contradiction_by_group[item.independence_group] += weight

    support = sum(support_by_group.values())
    contradiction = sum(contradiction_by_group.values())
    total = support + contradiction

    support_ratio = support / total if total else 0.0
    independent_support = sum(1 for x in support_by_group.values() if x > 0.35)
    independent_contradiction = sum(1 for x in contradiction_by_group.values() if x > 0.35)

    families_support = len({
        x.source_family for x in usable if x.supports is True
    })
    families_contra = len({
        x.source_family for x in usable if x.supports is False
    })

    return {
        "support_weight": round(support, 4),
        "contradiction_weight": round(contradiction, 4),
        "support_ratio": round(support_ratio, 4),
        "independent_support_groups": independent_support,
        "independent_contradiction_groups": independent_contradiction,
        "support_source_families": families_support,
        "contradiction_source_families": families_contra,
        "usable_evidence_count": len(usable),
    }
