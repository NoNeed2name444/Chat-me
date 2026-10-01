from agents.specialists.verification_agent.assertions import decompose_claim

def test_claim_decomposition():
    assertions = decompose_claim(
        "Drug X increases bleeding risk and is safe in pregnancy."
    )
    assert len(assertions) >= 2
    assert any(x.modality == "safety" for x in assertions)
    assert any(x.required_context for x in assertions)
