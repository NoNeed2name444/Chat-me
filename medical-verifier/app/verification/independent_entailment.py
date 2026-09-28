import re
from dataclasses import dataclass

RELATION_CLASSES = {
    "causal": (
        "causes", "caused", "leads to", "results in", "prevents",
    ),
    "risk_increase": (
        "increases", "increased", "raises", "elevates", "higher",
    ),
    "risk_decrease": (
        "reduces", "reduced", "lowers", "decreases", "decreased",
    ),
    "association": (
        "associated with", "association", "correlated with", "linked to",
    ),
    "safety": (
        "safe", "safely", "dangerous", "harmful", "harm", "adverse",
        "contraindicated", "contraindication",
    ),
    "effectiveness": (
        "effective", "effectiveness", "efficacy", "works",
    ),
}

POPULATION_TERMS = (
    "adult", "adults", "child", "children", "pediatric",
    "elderly", "pregnancy", "pregnant", "breastfeeding",
    "renal", "kidney", "hepatic", "liver",
)

NEGATION = re.compile(
    r"\b(no|not|never|without|does not|doesn't|cannot|can't)\b",
    re.I,
)

@dataclass(frozen=True)
class IndependentEntailment:
    label: str
    reasons: tuple[str, ...]

def _tokens(text):
    return {
        token
        for token in re.findall(r"[a-z0-9'-]+", text.lower())
        if len(token) >= 5
    }

def _relation_class(text):
    lower = text.lower()
    matches = [
        category
        for category, words in RELATION_CLASSES.items()
        if any(word in lower for word in words)
    ]
    return matches[0] if len(matches) == 1 else "mixed"

def _numbers(text):
    return set(
        re.findall(
            r"\b\d+(?:\.\d+)?\b",
            text.lower(),
        )
    )

def _measurement_kind(text):
    lower = text.lower()
    if "percentage point" in lower:
        return "percentage_points"
    if "%" in lower or "percent" in lower:
        return "percent"
    if "odds ratio" in lower:
        return "odds_ratio"
    if "hazard ratio" in lower:
        return "hazard_ratio"
    if "relative risk" in lower:
        return "relative_risk"
    if "absolute risk" in lower:
        return "absolute_risk"
    return None

def _populations(text):
    lower = text.lower()
    return {term for term in POPULATION_TERMS if term in lower}

def verify(claim, evidence):
    claim_tokens = _tokens(claim)
    evidence_tokens = _tokens(evidence)

    if not claim_tokens:
        return IndependentEntailment(
            "UNKNOWN",
            ("empty_claim_tokens",),
        )

    overlap = len(claim_tokens & evidence_tokens) / len(claim_tokens)
    if overlap < 0.50:
        return IndependentEntailment(
            "UNKNOWN",
            ("insufficient_semantic_overlap",),
        )

    claim_relation = _relation_class(claim)
    evidence_relation = _relation_class(evidence)

    if claim_relation == "causal" and evidence_relation == "association":
        return IndependentEntailment(
            "UNKNOWN",
            ("causal_vs_associative_mismatch",),
        )

    if claim_relation != "mixed" and evidence_relation not in {
        claim_relation,
        "mixed",
    }:
        return IndependentEntailment(
            "UNKNOWN",
            ("relation_class_mismatch",),
        )

    claim_measure = _measurement_kind(claim)
    evidence_measure = _measurement_kind(evidence)

    if (
        claim_measure
        and evidence_measure
        and claim_measure != evidence_measure
    ):
        return IndependentEntailment(
            "UNKNOWN",
            ("risk_measurement_type_mismatch",),
        )

    claim_numbers = _numbers(claim)
    if claim_numbers and not claim_numbers.issubset(_numbers(evidence)):
        return IndependentEntailment(
            "UNKNOWN",
            ("numeric_values_not_entrailed",),
        )

    claim_pop = _populations(claim)
    evidence_pop = _populations(evidence)
    if claim_pop and not claim_pop.issubset(evidence_pop):
        return IndependentEntailment(
            "UNKNOWN",
            ("population_not_entrailed",),
        )

    claim_neg = bool(NEGATION.search(claim))
    evidence_neg = bool(NEGATION.search(evidence))

    if claim_neg != evidence_neg:
        return IndependentEntailment(
            "CONTRADICTS",
            ("claim_evidence_polarity_mismatch",),
        )

    return IndependentEntailment(
        "SUPPORTS",
        ("independent_structured_checks_passed",),
    )
