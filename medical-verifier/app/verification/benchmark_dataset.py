"""Versioned benchmark dataset and descriptive evaluation utilities."""

from dataclasses import dataclass
from collections import Counter
from typing import Iterable, Mapping


ALLOWED_LABELS = frozenset({"SUPPORTS", "CONTRADICTS", "UNKNOWN"})


@dataclass(frozen=True)
class BenchmarkRecord:
    case_id: str
    claim: str
    evidence: str
    expected: str
    subgroup: str = "unspecified"
    risk_level: str = "unspecified"
    source_family: str = "unspecified"

    def __post_init__(self):
        if self.expected not in ALLOWED_LABELS:
            raise ValueError("unsupported_expected_label")
        if not self.case_id.strip():
            raise ValueError("empty_case_id")


def summarize_by_group(
    rows: Iterable[Mapping[str, object]],
    group_key: str,
) -> dict[str, dict[str, object]]:
    grouped: dict[str, list[Mapping[str, object]]] = {}
    for row in rows:
        group = str(row.get(group_key, "unspecified"))
        grouped.setdefault(group, []).append(row)

    result = {}
    for group, items in sorted(grouped.items()):
        abstained = sum(1 for item in items if item.get("actual") == "UNKNOWN")
        matched = sum(1 for item in items if item.get("match") is True)
        result[group] = {
            "case_count": len(items),
            "abstention_count": abstained,
            "abstention_rate": abstained / len(items) if items else 0.0,
            "exact_match_count": matched,
            "exact_match_rate": matched / len(items) if items else 0.0,
        }
    return result


def descriptive_reliability_bins(
    rows: Iterable[Mapping[str, object]],
    *,
    bin_count: int = 10,
) -> list[dict[str, float | int]]:
    """Bin supplied confidence values without asserting calibration quality."""
    if bin_count < 1:
        raise ValueError("bin_count_must_be_positive")

    bins = [
        {"lower": i / bin_count, "upper": (i + 1) / bin_count,
         "count": 0, "mean_confidence": 0.0, "match_rate": 0.0}
        for i in range(bin_count)
    ]

    grouped: list[list[Mapping[str, object]]] = [[] for _ in range(bin_count)]
    for row in rows:
        confidence = row.get("confidence")
        if not isinstance(confidence, (int, float)):
            continue
        value = min(1.0, max(0.0, float(confidence)))
        index = min(bin_count - 1, int(value * bin_count))
        grouped[index].append(row)

    for index, items in enumerate(grouped):
        if not items:
            continue
        confidences = [
            float(item["confidence"])
            for item in items
            if isinstance(item.get("confidence"), (int, float))
        ]
        matches = sum(1 for item in items if item.get("match") is True)
        bins[index]["count"] = len(items)
        bins[index]["mean_confidence"] = (
            sum(confidences) / len(confidences)
        )
        bins[index]["match_rate"] = matches / len(items)

    return bins
