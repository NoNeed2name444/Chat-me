from agents.specialists.verification_agent.entailment_provider import (
    AgreementGate,
    EntailmentDecision,
    StructuredProvider,
)

class FixedProvider:
    def __init__(self, name, label):
        self.name = name
        self.label = label

    def assess(self, claim, evidence):
        return EntailmentDecision(
            label=self.label,
            reasons=("fixed",),
        )

def test_structured_provider_supports_matching_claim():
    provider = StructuredProvider()
    result = provider.assess(
        "Insulin lowers blood glucose.",
        "Insulin lowers blood glucose.",
    )
    assert result.label == "SUPPORTS"

def test_agreement_gate_abstains_on_provider_disagreement():
    gate = AgreementGate([
        FixedProvider("a", "SUPPORTS"),
        FixedProvider("b", "UNKNOWN"),
    ])
    result = gate.assess(
        "claim",
        "evidence",
    )
    assert result.label == "UNKNOWN"
    assert "entailment_provider_disagreement" in result.reasons
