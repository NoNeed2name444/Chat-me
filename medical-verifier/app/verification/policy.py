POLICY_VERSION = "0.2-multi-axis"

def required_independence(risk_level: str) -> int:
    return {
        "low": 1,
        "moderate": 2,
        "high": 2,
        "critical": 99,
    }[risk_level]

def decide_verdict(
    *,
    risk_level: str,
    claim_type: str,
    missing_context: list[str],
    aggregate: dict,
    evidence: list,
):
    if risk_level == "critical":
        return "SAFETY_ESCALATION", 0.0, [
            "Critical-risk input can never be auto-verified."
        ]

    if missing_context and risk_level in {"moderate", "high"}:
        return "CONTEXT_REQUIRED", 0.0, [
            "Required patient/clinical context is missing."
        ]

    if aggregate["usable_evidence_count"] == 0:
        return "INSUFFICIENT_EVIDENCE", 0.0, [
            "No usable evidence was retrieved."
        ]

    if aggregate["independent_contradiction_groups"] > 0:
        if aggregate["contradiction_weight"] >= aggregate["support_weight"] * 0.75:
            return "MIXED_EVIDENCE", 0.45, [
                "Material contradiction remains unresolved."
            ]

    minimum = required_independence(risk_level)
    enough_independence = aggregate["independent_support_groups"] >= minimum
    enough_families = (
        aggregate["support_source_families"] >= 2
        if risk_level in {"moderate", "high"}
        else aggregate["support_source_families"] >= 1
    )
    strong_support = (
        aggregate["support_ratio"] >= 0.78
        and enough_independence
        and enough_families
    )

    if strong_support:
        return (
            "SUPPORTED",
            round(min(0.96, 0.55 + 0.41 * aggregate["support_ratio"]), 3),
            [
                "Support threshold met.",
                f"{aggregate['independent_support_groups']} independent evidence groups support the claim.",
                f"{aggregate['support_source_families']} source families support the claim.",
            ],
        )

    if aggregate["contradiction_weight"] > aggregate["support_weight"]:
        return "CONTRADICTED", 0.72, [
            "Contradicting evidence outweighs supporting evidence under current policy."
        ]

    return "INSUFFICIENT_EVIDENCE", 0.0, [
        "Evidence did not reach the multi-source reliability threshold."
    ]
