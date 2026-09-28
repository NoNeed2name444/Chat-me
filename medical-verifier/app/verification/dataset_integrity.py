"""Deterministic benchmark integrity checks.

These checks detect common evaluation leakage hazards. They do not prove that a dataset
is independent, unbiased, clinically representative, or otherwise valid.
"""

from dataclasses import dataclass
import hashlib
import re
from typing import Iterable


@dataclass(frozen=True)
class IntegrityFinding:
    case_id: str
    kind: str
    detail: str


def canonical_text(text: str) -> str:
    return " ".join(re.findall(r"[a-z0-9]+", text.lower()))


def case_fingerprint(claim: str, evidence: str) -> str:
    payload = canonical_text(claim) + "\n" + canonical_text(evidence)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def find_duplicate_cases(cases: Iterable[object]) -> list[IntegrityFinding]:
    seen: dict[str, str] = {}
    findings = []
    for case in cases:
        case_id = str(getattr(case, "case_id"))
        fingerprint = case_fingerprint(
            str(getattr(case, "claim")),
            str(getattr(case, "evidence")),
        )
        prior = seen.get(fingerprint)
        if prior is not None:
            findings.append(
                IntegrityFinding(case_id, "duplicate_case", f"same_as:{prior}")
            )
        else:
            seen[fingerprint] = case_id
    return findings


def find_cross_split_duplicates(
    train: Iterable[object],
    test: Iterable[object],
) -> list[IntegrityFinding]:
    train_map = {}
    for case in train:
        train_map[case_fingerprint(str(case.claim), str(case.evidence))] = case.case_id

    findings = []
    for case in test:
        fp = case_fingerprint(str(case.claim), str(case.evidence))
        if fp in train_map:
            findings.append(
                IntegrityFinding(
                    case.case_id,
                    "train_test_duplicate",
                    f"train_case:{train_map[fp]}",
                )
            )
    return findings


def summarize_integrity(findings: Iterable[IntegrityFinding]) -> dict[str, object]:
    rows = list(findings)
    counts = {}
    for finding in rows:
        counts[finding.kind] = counts.get(finding.kind, 0) + 1
    return {
        "finding_count": len(rows),
        "by_kind": dict(sorted(counts.items())),
        "findings": [
            {"case_id": f.case_id, "kind": f.kind, "detail": f.detail}
            for f in rows
        ],
    }
