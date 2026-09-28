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

DOUBLE_NEGATION_EQUIVALENTS = {
    "not uncommon": "common",
    "not unlikely": "likely",
    "not impossible": "possible",
}

@dataclass(frozen=True)
class IndependentEntailment:
    label: str
    reasons: tuple[str, ...]

def _normalize_double_negation(text):
    result = text.lower()
    for source, replacement in DOUBLE_NEGATION_EQUIVALENTS.items():
        result = result.replace(source, replacement)
    return result

def _tokens(text):
    return {
        token
        for token in re.findall(r"[a-z0-9'-]+", text.lower())
        if len(token) >= 5
    }

def _relation_class(text):
    lower = _normalize_double_negation(text)
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

def _normalize_measurement(value, unit):
    unit = unit.lower()

    if unit in {"mcg", "ug"}:
        return (round(float(value) * 0.001, 9), "mg")
    if unit == "g":
        return (round(float(value) * 1000.0, 9), "mg")
    if unit == "kg":
        return (round(float(value) * 1000000.0, 9), "mg")
    if unit == "l":
        return (round(float(value) * 1000.0, 9), "ml")
    if unit in {"%", "percent"}:
        return (round(float(value), 9), "percent")

    return (round(float(value), 9), unit)

def _measurements(text):
    matches = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg|ml|l|mmol|mmhg|%|percent)\b",
        text.lower(),
    )

    return {
        _normalize_measurement(value, unit)
        for value, unit in matches
    }

def _frequency_multiplier(text):
    lower = text.lower()

    if re.search(r"\b(twice|2\s+times)(?:\s+a)?\s+(?:day|daily)\b|\bbid\b", lower):
        return 2.0
    if re.search(r"\bthree\s+times(?:\s+a)?\s+(?:day|daily)\b|\btid\b", lower):
        return 3.0
    if re.search(r"\bfour\s+times(?:\s+a)?\s+(?:day|daily)\b|\bqid\b", lower):
        return 4.0
    if re.search(r"\bonce(?:\s+a)?\s+(?:day|daily)\b|\bdaily\b|\bqd\b", lower):
        return 1.0

    every_hours = re.search(
        r"\bevery\s+(\d+)\s*(?:hours?|h)\b|\bq(\d+)h\b",
        lower,
    )
    if every_hours:
        hours = int(next(
            value for value in every_hours.groups()
            if value is not None
        ))
        if 0 < hours <= 24:
            return 24.0 / hours

    if re.search(r"\b(?:once\s+a\s+week|weekly)\b", lower):
        return 1.0 / 7.0
    if re.search(r"\btwice\s+(?:a\s+)?week\b", lower):
        return 2.0 / 7.0

    return None

def _mass_values(text):
    matches = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg)\b",
        text.lower(),
    )
    return [
        _normalize_measurement(value, unit)[0]
        for value, unit in matches
    ]

def _daily_mass_dose(text):
    values = _mass_values(text)
    multiplier = _frequency_multiplier(text)

    if len(values) == 1 and multiplier is not None:
        return round(values[0] * multiplier, 9)

    return None

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
    return {
        term
        for term in POPULATION_TERMS
        if term in lower
    }

def _scope_strength(text):
    lower = text.lower()

    universal = any(
        marker in lower
        for marker in (
            "all patients", "all people", "everyone",
            "every patient", "always", "never",
            "regardless of",
        )
    )

    exclusive = any(
        marker in lower
        for marker in (
            "only", "exclusively", "only if",
        )
    )

    conditional = any(
        marker in lower
        for marker in (
            "if ", "unless ", "when ", "provided that",
            "in patients with", "for patients with",
        )
    )

    return {
        "universal": universal,
        "exclusive": exclusive,
        "conditional": conditional,
    }

def verify(claim, evidence):
    claim_for_logic = _normalize_double_negation(claim)
    evidence_for_logic = _normalize_double_negation(evidence)

    claim_tokens = _tokens(claim_for_logic)
    evidence_tokens = _tokens(evidence_for_logic)

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

    claim_relation = _relation_class(claim_for_logic)
    evidence_relation = _relation_class(evidence_for_logic)

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

    claim_scope = _scope_strength(claim_for_logic)
    evidence_scope = _scope_strength(evidence_for_logic)

    if claim_scope["universal"] and not evidence_scope["universal"]:
        return IndependentEntailment(
            "UNKNOWN",
            ("universal_scope_not_entrailed",),
        )

    if claim_scope["exclusive"] and not evidence_scope["exclusive"]:
        return IndependentEntailment(
            "UNKNOWN",
            ("exclusive_scope_not_entrailed",),
        )

    claim_measure = _measurement_kind(claim_for_logic)
    evidence_measure = _measurement_kind(evidence_for_logic)

    claim_measurements = _measurements(claim_for_logic)
    evidence_measurements = _measurements(evidence_for_logic)

    claim_daily_dose = _daily_mass_dose(claim_for_logic)
    evidence_daily_dose = _daily_mass_dose(evidence_for_logic)
    daily_dose_equivalent = (
        claim_daily_dose is not None
        and evidence_daily_dose is not None
        and abs(claim_daily_dose - evidence_daily_dose) < 1e-9
    )

    if (
        claim_measurements
        and not claim_measurements.issubset(evidence_measurements)
        and not daily_dose_equivalent
    ):
        return IndependentEntailment(
            "UNKNOWN",
            ("measurement_unit_or_value_mismatch",),
        )

    claim_frequency = _frequency_multiplier(claim_for_logic)
    evidence_frequency = _frequency_multiplier(evidence_for_logic)

    if (
        claim_frequency is not None
        and evidence_frequency is not None
        and abs(claim_frequency - evidence_frequency) > 1e-9
        and not daily_dose_equivalent
    ):
        return IndependentEntailment(
            "UNKNOWN",
            ("dose_frequency_mismatch",),
        )

    if (
        claim_measure
        and evidence_measure
        and claim_measure != evidence_measure
    ):
        return IndependentEntailment(
            "UNKNOWN",
            ("risk_measurement_type_mismatch",),
        )

    claim_numbers = _numbers(claim_for_logic)

    if (
        claim_numbers
        and not claim_numbers.issubset(_numbers(evidence_for_logic))
        and not daily_dose_equivalent
    ):
        return IndependentEntailment(
            "UNKNOWN",
            ("numeric_values_not_entrailed",),
        )

    claim_pop = _populations(claim_for_logic)
    evidence_pop = _populations(evidence_for_logic)

    if claim_pop and not claim_pop.issubset(evidence_pop):
        return IndependentEntailment(
            "UNKNOWN",
            ("population_not_entrailed",),
        )

    claim_neg = bool(NEGATION.search(claim_for_logic))
    evidence_neg = bool(NEGATION.search(evidence_for_logic))

    if claim_neg != evidence_neg:
        return IndependentEntailment(
            "CONTRADICTS",
            ("claim_evidence_polarity_mismatch",),
        )

    return IndependentEntailment(
        "SUPPORTS",
        ("independent_structured_checks_passed",),
    )
