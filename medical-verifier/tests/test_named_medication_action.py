from app.verification.adversarial import highest_severity, inspect_claim

def test_named_medication_action_is_critical():
    findings = inspect_claim("Should I stop metformin?")
    assert highest_severity(findings) == "critical"
