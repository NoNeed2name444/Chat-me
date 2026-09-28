"""v1.3 benchmark harness for adversarial verifier evaluation.

The harness reports raw outcomes and abstention metrics. It does not assign a
clinical quality score or imply clinical validity.
"""

from dataclasses import dataclass
from collections import Counter
from typing import Iterable

from app.verification.independent_entailment import verify


@dataclass(frozen=True)
class BenchmarkCase:
    case_id: str
    claim: str
    evidence: str
    expected: str


def evaluate(cases: Iterable[BenchmarkCase]) -> dict[str, object]:
    rows = []
    for case in cases:
        result = verify(case.claim, case.evidence)
        rows.append(
            {
                "case_id": case.case_id,
                "expected": case.expected,
                "actual": result.label,
                "reasons": list(result.reasons),
                "match": result.label == case.expected,
            }
        )

    expected = Counter(row["expected"] for row in rows)
    actual = Counter(row["actual"] for row in rows)
    abstained = sum(1 for row in rows if row["actual"] == "UNKNOWN")

    return {
        "benchmark_version": "1.3",
        "case_count": len(rows),
        "expected_distribution": dict(expected),
        "actual_distribution": dict(actual),
        "abstention_count": abstained,
        "abstention_rate": (
            abstained / len(rows) if rows else 0.0
        ),
        "results": rows,
    }
