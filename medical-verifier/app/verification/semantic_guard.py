import re

CAUSAL_WORDS = (
    "causes", "caused", "leads to", "results in",
    "prevents", "reduces", "increases", "decreases",
)
ASSOCIATION_WORDS = (
    "associated with", "association", "correlated with",
    "correlation", "linked to", "observational",
)
UNIT_SCALE = {
    "mg": 1.0,
    "g": 1000.0,
    "mcg": 0.001,
    "ug": 0.001,
    "ml": 1.0,
    "l": 1000.0,
}

def _quantities(text):
    pattern = r"\b(\d+(?:\.\d+)?)\s*(mg|mcg|ug|g|ml|l)\b"
    return [
        (float(number), unit.lower())
        for number, unit in re.findall(pattern, text, re.I)
    ]

def _percents(text):
    return [
        float(x)
        for x in re.findall(
            r"\b(\d+(?:\.\d+)?)\s*(?:%|percent)\b",
            text,
            re.I,
        )
    ]

def _frequency_multiplier(text):
    lower = text.lower()

    if re.search(
        r"\b(twice|2\s+times)(?:\s+a)?\s+(?:day|daily)\b|\bbid\b",
        lower,
    ):
        return 2.0
    if re.search(
        r"\bthree\s+times(?:\s+a)?\s+(?:day|daily)\b|\btid\b",
        lower,
    ):
        return 3.0
    if re.search(
        r"\bfour\s+times(?:\s+a)?\s+(?:day|daily)\b|\bqid\b",
        lower,
    ):
        return 4.0
    if re.search(
        r"\bonce(?:\s+a)?\s+(?:day|daily)\b|\bdaily\b|\bqd\b",
        lower,
    ):
        return 1.0

    every_hours = re.search(
        r"\bevery\s+(\d+)\s*(?:hours?|h)\b|\bq(\d+)h\b",
        lower,
    )
    if every_hours:
        hours = int(next(
            value
            for value in every_hours.groups()
            if value is not None
        ))
        if 0 < hours <= 24:
            return 24.0 / hours

    if re.search(r"\b(?:once\s+a\s+week|weekly)\b", lower):
        return 1.0 / 7.0

    return None

def _daily_mass_dose(text):
    quantities = _quantities(text)
    multiplier = _frequency_multiplier(text)

    if len(quantities) != 1 or multiplier is None:
        return None

    value, unit = quantities[0]
    return round(value * UNIT_SCALE[unit] * multiplier, 9)

def _scope_strength(text):
    lower = text.lower()

    return {
        "universal": any(
            x in lower
            for x in (
                "all patients", "all people", "everyone",
                "every patient", "always", "never",
                "regardless of",
            )
        ),
        "exclusive": any(
            x in lower
            for x in ("only", "exclusively", "only if")
        ),
    }

def _has_negation_near_relation(text, relation_words):
    lower = text.lower()

    for relation in relation_words:
        for match in re.finditer(re.escape(relation), lower):
            window = lower[max(0, match.start() - 35):match.start()]
            if re.search(
                r"\b(no|not|never|without|doesn't|does not|cannot|can't)\b",
                window,
            ):
                return True

    return False

def semantic_guard(item, claim):
    evidence = f"{item.title} {item.passage}".lower()
    claim_l = claim.lower()
    warnings = []

    claim_has_causal = any(
        x in claim_l
        for x in CAUSAL_WORDS
    )
    evidence_has_association = any(
        x in evidence
        for x in ASSOCIATION_WORDS
    )
    evidence_has_causal = any(
        x in evidence
        for x in CAUSAL_WORDS
    )

    if claim_has_causal and evidence_has_association and not evidence_has_causal:
        warnings.append(
            "causal_claim_only_associative_evidence"
        )
        item.supports = None

    claim_neg = _has_negation_near_relation(
        claim_l,
        CAUSAL_WORDS + ASSOCIATION_WORDS,
    )
    evidence_neg = _has_negation_near_relation(
        evidence,
        CAUSAL_WORDS + ASSOCIATION_WORDS,
    )

    if claim_has_causal and claim_neg != evidence_neg:
        warnings.append("negation_scope_mismatch")
        item.supports = None

    claim_q = _quantities(claim_l)
    evidence_q = _quantities(evidence)

    claim_daily = _daily_mass_dose(claim_l)
    evidence_daily = _daily_mass_dose(evidence)

    daily_equivalent = (
        claim_daily is not None
        and evidence_daily is not None
        and abs(claim_daily - evidence_daily) < 1e-9
    )

    if not daily_equivalent:
        for value, unit in claim_q:
            if not any(
                abs(
                    value * UNIT_SCALE[unit]
                    - ev * UNIT_SCALE[eu]
                ) < 1e-9
                for ev, eu in evidence_q
            ):
                warnings.append("dose_or_unit_not_matched")
                item.supports = None
                break

        claim_frequency = _frequency_multiplier(claim_l)
        evidence_frequency = _frequency_multiplier(evidence)

        if (
            claim_frequency is not None
            and evidence_frequency is not None
            and abs(claim_frequency - evidence_frequency) > 1e-9
        ):
            warnings.append("dose_frequency_mismatch")
            item.supports = None

    claim_p = _percents(claim_l)
    evidence_p = _percents(evidence)

    if claim_p and not any(
        abs(x - y) < 1e-9
        for x in claim_p
        for y in evidence_p
    ):
        warnings.append("percentage_not_matched")
        item.supports = None

    claim_scope = _scope_strength(claim_l)
    evidence_scope = _scope_strength(evidence)

    if claim_scope["universal"] and not evidence_scope["universal"]:
        warnings.append("universal_scope_not_supported")
        item.supports = None

    if claim_scope["exclusive"] and not evidence_scope["exclusive"]:
        warnings.append("exclusive_scope_not_supported")
        item.supports = None

    if warnings:
        item.quality_score = round(
            item.quality_score * 0.55,
            4,
        )

    return item, warnings
