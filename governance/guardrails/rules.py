def deterministic_checks(claim):
    t = claim.lower()
    flags = []

    injection_markers = (
        "ignore previous instructions",
        "ignore all previous",
        "disregard the system",
        "override the rules",
        "pretend there is no safety policy",
    )
    if any(marker in t for marker in injection_markers):
        flags.append("prompt_injection_text_detected")

    if any(token in t for token in (
        "definitively diagnose", "guarantee", "100% accurate",
        "certain diagnosis", "no chance of error",
    )):
        flags.append("overclaim_or_certainty_request")

    if any(token in t for token in (
        "take this dose", "change my dose", "stop my medication",
        "start this medication", "double the dose",
    )):
        flags.append("direct_treatment_action_request")

    if (
        any(term in t for term in (
            "overdose", "poisoning", "poisoned",
            "severe bleeding", "chest pain",
            "difficulty breathing", "anaphylaxis",
        ))
        and any(
            phrase in t
            for phrase in (
                "what should i do", "what do i do",
                "should i", "can i", "may i",
            )
        )
    ):
        flags.append("urgent_safety_action_request")

    return flags

