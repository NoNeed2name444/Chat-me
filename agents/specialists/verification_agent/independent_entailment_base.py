import re
from dataclasses import dataclass

from agents.specialists.verification_agent.claim_reasoning import (
    decompose_claim,
    relation_entailed,
    safety_relation_entailed,
    temporal_entailed,
)
from agents.specialists.verification_agent.direction import direction_entailed

RELATION_CLASSES = {
    "causal": (
        "cause", "causes", "caused", "leads to", "result in", "results in", "prevent", "prevents",
    ),
    "risk_increase": (
        "increase", "increases", "increased", "raises", "elevates", "higher",
    ),
    "risk_decrease": (
        "reduce", "reduces", "reduced", "lowers", "decrease", "decreases", "decreased",
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

def _normalize_multilingual_terms(text):
    result = text.lower()
    replacements = {
        "no aumenta": "does not increase",
        "ne augmente pas": "does not increase",
        "n'augmente pas": "does not increase",
        "no causa": "does not cause",
        "ne cause pas": "does not cause",
        "n'est pas sûr": "is not safe",
        "no es seguro": "is not safe",
        "aumenta": "increases",
        "augmente": "increases",
        "reduce": "reduces",
        "réduit": "reduces",
        "causa": "causes",
        "causado": "caused",
        "asociado con": "associated with",
        "associé à": "associated with",
        "asociado a": "associated with",
        "seguro": "safe",
        "sûr": "safe",
        "efectivo": "effective",
        "efficace": "effective",
        "pacientes": "patients",
        "patients": "patients",
        "niños": "children",
        "enfants": "children",
        "adultos": "adults",
        "adultes": "adults",
        "glucosa": "glucose",
        "glucose": "glucose",
    }

    for source, replacement in replacements.items():
        # whole words only: Spanish "reduce" must not turn English "reduced"
        # into "reducesd", nor "causa" turn "causal" into "causesl"
        result = re.sub(
            r"(?<!\w)" + re.escape(source) + r"(?!\w)",
            replacement,
            result,
        )

    return result

def _normalize_double_negation(text):
    result = _normalize_multilingual_terms(text)
    for source, replacement in DOUBLE_NEGATION_EQUIVALENTS.items():
        result = result.replace(source, replacement)
    return result

def _tokens(text):
    return {
        token
        for token in re.findall(r"[a-z0-9'-]+", text.lower())
        if len(token) >= 4
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
        return (float(value) * 0.001, "mg")
    if unit == "g":
        return (float(value) * 1000.0, "mg")
    if unit == "kg":
        return (float(value) * 1000000.0, "mg")
    if unit == "l":
        return (float(value) * 1000.0, "ml")
    if unit in {"%", "percent"}:
        return (float(value), "percent")

    return (float(value), unit)

def _measurements(text):
    matches = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug|kg|ml|l|mmol|mmhg|%|percent)\b",
        text.lower(),
    )
    return {
        (
            round(value, 9),
            unit,
        )
        for value, unit in (
            _normalize_measurement(number, unit)
            for number, unit in matches
        )
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

def _daily_mass_dose(text):
    if re.search(
        r"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug)\s*(?:/|per)\s*(?:ml|l)\b",
        text.lower(),
    ):
        return None

    if re.search(
        r"\b\d+(?:\.\d+)?\s*(?:mg|g|mcg|ug)\s*/\s*kg\b",
        text.lower(),
    ):
        return None

    values = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\b",
        text.lower(),
    )
    multiplier = _frequency_multiplier(text)

    if len(values) != 1 or multiplier is None:
        return None

    value, unit = _normalize_measurement(*values[0])
    if unit != "mg":
        return None

    return round(value * multiplier, 9)

def _concentration_daily_dose(text):
    concentration = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\s*(?:/|per)\s*(ml|l)\b",
        text.lower(),
    )
    volume = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(ml|l)\b",
        text.lower(),
    )
    multiplier = _frequency_multiplier(text)

    if len(concentration) != 1 or len(volume) != 1 or multiplier is None:
        return None

    mass_value, mass_unit = _normalize_measurement(
        concentration[0][0],
        concentration[0][1],
    )
    volume_value, volume_unit = _normalize_measurement(
        volume[0][0],
        volume[0][1],
    )

    if mass_unit != "mg" or volume_unit != "ml":
        return None

    return round(
        mass_value * volume_value * multiplier,
        9,
    )

def _weight_based_daily_dose(text):
    dose = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|ug)\s*/\s*kg(\s*/\s*day)?\b",
        text.lower(),
    )
    weights = re.findall(
        r"\b(\d+(?:\.\d+)?)\s*kg\b",
        text.lower(),
    )

    if len(dose) != 1 or len(weights) != 1:
        return None

    dose_value, dose_unit = _normalize_measurement(
        dose[0][0],
        dose[0][1],
    )
    weight = float(weights[0])

    if dose_unit != "mg":
        return None

    per_day = (
        1.0
        if dose[0][2]
        else _frequency_multiplier(text)
    )
    if per_day is None:
        return None

    return round(dose_value * weight * per_day, 9)

def _daily_dose_equivalent(text):
    for calculator in (
        _daily_mass_dose,
        _concentration_daily_dose,
        _weight_based_daily_dose,
    ):
        value = calculator(text)
        if value is not None:
            return value
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

def _condition_signatures(text):
    lower = " ".join(text.lower().split())
    patterns = (
        r"\bif\s+([^,.;:]+)",
        r"\bonly if\s+([^,.;:]+)",
        r"\bunless\s+([^,.;:]+)",
        r"\bwhen\s+([^,.;:]+)",
        r"\bprovided that\s+([^,.;:]+)",
        r"\bin patients with\s+([^,.;:]+)",
        r"\bfor patients with\s+([^,.;:]+)",
    )

    results = []
    for pattern in patterns:
        for match in re.finditer(pattern, lower):
            signature = set(
                token
                for token in re.findall(r"[a-z0-9'-]+", match.group(1))
                if len(token) >= 4
            )
            if signature:
                results.append(signature)

    return results

def _scope_strength(text):
    lower = text.lower()

    return {
        "universal": any(
            marker in lower
            for marker in (
                "all patients", "all people", "everyone",
                "every patient", "always", "never",
                "regardless of",
            )
        ),
        "exclusive": any(
            marker in lower
            for marker in ("only", "exclusively", "only if")
        ),
    }

def _condition_supported(claim, evidence):
    claim_conditions = _condition_signatures(claim)
    evidence_conditions = _condition_signatures(evidence)

    if not evidence_conditions:
        return True, None

    if not claim_conditions:
        return False, "conditional_scope_missing"

    for source_condition in evidence_conditions:
        best = max(
            (
                len(source_condition & claim_condition)
                / max(1, len(source_condition))
                for claim_condition in claim_conditions
            ),
            default=0.0,
        )
        if best < 0.70:
            return False, "condition_not_entrailed"

    return True, None

def _atomic_alignment(claim: str, evidence: str):
    claim_atoms = decompose_claim(claim)
    evidence_atoms = decompose_claim(evidence)

    if not claim_atoms:
        return True, None

    if not evidence_atoms:
        return False, "no_atomic_evidence_claim"

    evidence_atoms = tuple(
        atom for atom in evidence_atoms
        if atom.text
    )

    for claim_atom in claim_atoms:
        claim_tokens = _tokens(claim_atom.text)
        matched = False
        failure_reasons = []

        for evidence_atom in evidence_atoms:
            evidence_tokens = _tokens(evidence_atom.text)
            overlap = (
                len(claim_tokens & evidence_tokens)
                / max(1, len(claim_tokens))
            )

            if overlap < 0.45:
                continue

            relation_ok, relation_reason = relation_entailed(
                claim_atom,
                evidence_atom,
            )
            if not relation_ok:
                if relation_reason:
                    failure_reasons.append(relation_reason)
                continue

            temporal_ok, temporal_reason = temporal_entailed(
                claim_atom,
                evidence_atom,
            )
            if not temporal_ok:
                if temporal_reason:
                    failure_reasons.append(temporal_reason)
                continue

            safety_ok, safety_reason = safety_relation_entailed(
                claim_atom,
                evidence_atom,
            )
            if not safety_ok:
                if safety_reason:
                    failure_reasons.append(safety_reason)
                continue

            direction_ok, direction_reason = direction_entailed(
                claim_atom.text,
                evidence_atom.text,
            )
            if not direction_ok:
                failure_reasons.append(direction_reason)
                continue

            matched = True
            break

        if not matched:
            return False, (
                failure_reasons[0]
                if failure_reasons
                else "atomic_claim_not_entailed"
            )

    return True, None

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
        if not daily_dose_equivalent:
            return IndependentEntailment(
                "UNKNOWN",
                ("insufficient_semantic_overlap",),
            )
        shared_logic = (claim_tokens & evidence_tokens) - _numbers(claim_for_logic)
        if not shared_logic:
            return IndependentEntailment(
                "UNKNOWN",
                ("insufficient_semantic_overlap",),
            )

    atomic_ok, atomic_reason = _atomic_alignment(
        claim_for_logic,
        evidence_for_logic,
    )
    if not atomic_ok:
        bypass_reasons = {
            "atomic_claim_not_entailed",
            "atomic_object_mismatch",
            "atomic_relation_mismatch",
        }
        if not (
            daily_dose_equivalent
            and atomic_reason in bypass_reasons
        ):
            return IndependentEntailment(
                "UNKNOWN",
                (atomic_reason,),
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

    condition_ok, condition_reason = _condition_supported(
        claim_for_logic,
        evidence_for_logic,
    )

    if not condition_ok:
        return IndependentEntailment(
            "UNKNOWN",
            (condition_reason,),
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

    claim_daily_dose = _daily_dose_equivalent(claim_for_logic)
    evidence_daily_dose = _daily_dose_equivalent(evidence_for_logic)
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
