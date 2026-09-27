import re

POPULATION_TERMS = (
    "adult", "adults", "child", "children", "pediatric", "elderly",
    "pregnancy", "pregnant", "breastfeeding", "renal", "kidney",
    "hepatic", "liver", "male", "female",
)

STRONG_QUALIFIERS = (
    "always", "never", "only", "all", "none", "must", "guaranteed",
    "100%", "definitively",
)

def _numbers(text: str) -> set[str]:
    return set(re.findall(r"\b\d+(?:\.\d+)?\b", text.lower()))

def guard_specificity(item, claim: str):
    claim_l = claim.lower()
    evidence_l = f"{item.title} {item.passage}".lower()
    warnings = []

    claim_numbers = _numbers(claim_l)
    evidence_numbers = _numbers(evidence_l)
    if claim_numbers and not claim_numbers.issubset(evidence_numbers):
        warnings.append("numeric_claim_not_supported_verbatim")
        item.supports = None

    claim_populations = {x for x in POPULATION_TERMS if x in claim_l}
    if claim_populations:
        missing = [x for x in claim_populations if x not in evidence_l]
        if missing:
            warnings.append("population_qualifier_not_matched")
            item.supports = None

    claim_strong = {x for x in STRONG_QUALIFIERS if x in claim_l}
    if claim_strong:
        missing = [x for x in claim_strong if x not in evidence_l]
        if missing:
            warnings.append("absolute_claim_not_matched")
            item.supports = None

    if warnings:
        item.quality_score = round(item.quality_score * 0.65, 4)

    return item, warnings
