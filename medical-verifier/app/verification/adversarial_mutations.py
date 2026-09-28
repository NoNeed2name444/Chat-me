"""Deterministic adversarial mutation runner.

This tool measures abstention on known semantic mutations. It is a regression
harness, not a clinical validation benchmark.
"""

from dataclasses import dataclass
from typing import Callable

from app.verification.independent_entailment import verify


@dataclass(frozen=True)
class MutationCase:
    case_id: str
    claim: str
    evidence: str
    expected: str = "UNKNOWN"


MUTATORS: dict[str, Callable[[str], str]] = {
    "negate": lambda s: s.replace(" increases ", " does not increase ", 1),
    "causal_to_association": lambda s: s.replace(
        " causes ", " is associated with ", 1
    ),
    "swap_drug": lambda s: s.replace("Drug A", "Drug B", 1),
    "swap_outcome": lambda s: s.replace(
        "bleeding", "blood pressure", 1
    ),
    "past_to_current": lambda s: s.replace(
        "previously", "currently", 1
    ),
    "current_to_past": lambda s: s.replace(
        "currently", "previously", 1
    ),
}


def generate_mutations(claim: str, evidence: str) -> tuple[MutationCase, ...]:
    cases = []
    for name, mutate in MUTATORS.items():
        mutated = mutate(evidence)
        cases.append(
            MutationCase(
                case_id=name,
                claim=claim,
                evidence=mutated,
            )
        )
    return tuple(cases)


def run_mutation_suite(
    claim: str,
    evidence: str,
) -> dict[str, object]:
    cases = generate_mutations(claim, evidence)
    results = []

    for case in cases:
        result = verify(case.claim, case.evidence)
        results.append(
            {
                "case_id": case.case_id,
                "actual": result.label,
                "expected": case.expected,
                "passed": result.label == case.expected,
                "reasons": list(result.reasons),
            }
        )

    passed = sum(1 for item in results if item["passed"])
    return {
        "suite_version": "1.2",
        "case_count": len(results),
        "passed": passed,
        "failed": len(results) - passed,
        "all_passed": passed == len(results),
        "results": results,
    }
