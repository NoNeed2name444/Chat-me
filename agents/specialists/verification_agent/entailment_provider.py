from dataclasses import dataclass
from typing import Protocol

from agents.specialists.verification_agent.independent_entailment import verify as structured_verify

@dataclass(frozen=True)
class EntailmentDecision:
    label: str
    reasons: tuple[str, ...]

class EntailmentProvider(Protocol):
    name: str

    def assess(self, claim: str, evidence: str) -> EntailmentDecision:
        ...

class StructuredProvider:
    name = "structured-heuristic-v1"

    def assess(self, claim: str, evidence: str):
        result = structured_verify(
            claim,
            evidence,
        )
        return EntailmentDecision(
            label=result.label,
            reasons=result.reasons,
        )

class AgreementGate:
    def __init__(self, providers):
        self.providers = tuple(providers)

    def assess(self, claim: str, evidence: str):
        decisions = [
            provider.assess(
                claim,
                evidence,
            )
            for provider in self.providers
        ]

        labels = {
            decision.label
            for decision in decisions
        }

        reasons = []

        for provider, decision in zip(
            self.providers,
            decisions,
        ):
            reasons.extend(
                f"{provider.name}:{reason}"
                for reason in decision.reasons
            )

        if len(labels) == 1:
            return EntailmentDecision(
                label=next(iter(labels)),
                reasons=tuple(sorted(set(reasons))),
            )

        return EntailmentDecision(
            label="UNKNOWN",
            reasons=tuple(
                sorted(
                    set(
                        reasons
                        + ["entailment_provider_disagreement"]
                    )
                )
            ),
        )
