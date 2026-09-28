from app.verification import independent_entailment_base as _base

IndependentEntailment = _base.IndependentEntailment
_condition_supported = _base._condition_supported
_daily_dose_equivalent = _base._daily_dose_equivalent
_tokens = _base._tokens


def verify(claim, evidence):
    claim_for_logic = _base._normalize_double_negation(claim)
    evidence_for_logic = _base._normalize_double_negation(evidence)
    claim_tokens = _base._tokens(claim_for_logic)
    evidence_tokens = _base._tokens(evidence_for_logic)

    if not claim_tokens:
        return IndependentEntailment("UNKNOWN", ("empty_claim_tokens",))

    claim_daily_dose = _base._daily_dose_equivalent(claim_for_logic)
    evidence_daily_dose = _base._daily_dose_equivalent(evidence_for_logic)
    daily_dose_equivalent = (
        claim_daily_dose is not None
        and evidence_daily_dose is not None
        and abs(claim_daily_dose - evidence_daily_dose) < 1e-9
    )

    overlap = len(claim_tokens & evidence_tokens) / len(claim_tokens)
    if overlap < 0.50:
        if not daily_dose_equivalent:
            return IndependentEntailment(
                "UNKNOWN", ("insufficient_semantic_overlap",)
            )
        shared_logic = (claim_tokens & evidence_tokens) - _base._numbers(claim_for_logic)
        if not shared_logic:
            return IndependentEntailment(
                "UNKNOWN", ("insufficient_semantic_overlap",)
            )

    atomic_ok, atomic_reason = _base._atomic_alignment(
        claim_for_logic, evidence_for_logic
    )
    if not atomic_ok:
        bypass_reasons = {
            "atomic_claim_not_entailed",
            "atomic_object_mismatch",
            "atomic_relation_mismatch",
        }
        if not (daily_dose_equivalent and atomic_reason in bypass_reasons):
            return IndependentEntailment("UNKNOWN", (atomic_reason,))

    claim_relation = _base._relation_class(claim_for_logic)
    evidence_relation = _base._relation_class(evidence_for_logic)
    if claim_relation == "causal" and evidence_relation == "association":
        return IndependentEntailment(
            "UNKNOWN", ("causal_vs_associative_mismatch",)
        )
    if claim_relation != "mixed" and evidence_relation not in {
        claim_relation, "mixed"
    }:
        return IndependentEntailment("UNKNOWN", ("relation_class_mismatch",))

    condition_ok, condition_reason = _base._condition_supported(
        claim_for_logic, evidence_for_logic
    )
    if not condition_ok:
        return IndependentEntailment("UNKNOWN", (condition_reason,))

    claim_scope = _base._scope_strength(claim_for_logic)
    evidence_scope = _base._scope_strength(evidence_for_logic)
    if claim_scope["universal"] and not evidence_scope["universal"]:
        return IndependentEntailment("UNKNOWN", ("universal_scope_not_entrailed",))
    if claim_scope["exclusive"] and not evidence_scope["exclusive"]:
        return IndependentEntailment("UNKNOWN", ("exclusive_scope_not_entrailed",))

    claim_measure = _base._measurement_kind(claim_for_logic)
    evidence_measure = _base._measurement_kind(evidence_for_logic)
    claim_measurements = _base._measurements(claim_for_logic)
    evidence_measurements = _base._measurements(evidence_for_logic)
    if (
        claim_measurements
        and not claim_measurements.issubset(evidence_measurements)
        and not daily_dose_equivalent
    ):
        return IndependentEntailment(
            "UNKNOWN", ("measurement_unit_or_value_mismatch",)
        )

    claim_frequency = _base._frequency_multiplier(claim_for_logic)
    evidence_frequency = _base._frequency_multiplier(evidence_for_logic)
    if (
        claim_frequency is not None
        and evidence_frequency is not None
        and abs(claim_frequency - evidence_frequency) > 1e-9
        and not daily_dose_equivalent
    ):
        return IndependentEntailment("UNKNOWN", ("dose_frequency_mismatch",))

    if (
        claim_measure
        and evidence_measure
        and claim_measure != evidence_measure
    ):
        return IndependentEntailment(
            "UNKNOWN", ("risk_measurement_type_mismatch",)
        )

    claim_numbers = _base._numbers(claim_for_logic)
    if (
        claim_numbers
        and not claim_numbers.issubset(_base._numbers(evidence_for_logic))
        and not daily_dose_equivalent
    ):
        return IndependentEntailment(
            "UNKNOWN", ("numeric_values_not_entrailed",)
        )

    claim_pop = _base._populations(claim_for_logic)
    evidence_pop = _base._populations(evidence_for_logic)
    if claim_pop and not claim_pop.issubset(evidence_pop):
        return IndependentEntailment("UNKNOWN", ("population_not_entrailed",))

    claim_neg = bool(_base.NEGATION.search(claim_for_logic))
    evidence_neg = bool(_base.NEGATION.search(evidence_for_logic))
    if claim_neg != evidence_neg:
        if claim_neg and not evidence_neg:
            return IndependentEntailment(
                "CONTRADICTS",
                ("claim_evidence_polarity_mismatch",),
            )
        return IndependentEntailment(
            "UNKNOWN",
            ("claim_evidence_polarity_mismatch",),
        )

    return IndependentEntailment(
        "SUPPORTS", ("independent_structured_checks_passed",)
    )
