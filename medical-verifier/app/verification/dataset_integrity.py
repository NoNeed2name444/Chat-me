"""Deterministic benchmark integrity and leakage checks.

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


def find_duplicate_case_ids(cases: Iterable[object]) -> list[IntegrityFinding]:
    seen: dict[str, str] = {}
    findings = []
    for case in cases:
        case_id = str(getattr(case, "case_id"))
        if case_id in seen:
            findings.append(
                IntegrityFinding(case_id, "duplicate_case_id", f"same_as:{seen[case_id]}")
            )
        else:
            seen[case_id] = case_id
    return findings


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


def find_cross_split_provenance_leakage(
    train: Iterable[object],
    test: Iterable[object],
) -> list[IntegrityFinding]:
    train_cases = list(train)
    test_cases = list(test)
    findings: list[IntegrityFinding] = []

    train_source_families = {
        str(case.source_family)
        for case in train_cases
        if getattr(case, "source_family", None)
        and str(case.source_family) != "unspecified"
    }
    train_study_families = {
        str(case.study_family_id)
        for case in train_cases
        if getattr(case, "study_family_id", None)
    }
    train_canonical_ids = {
        str(case.canonical_id)
        for case in train_cases
        if getattr(case, "canonical_id", None)
    }

    for case in test_cases:
        source_family = getattr(case, "source_family", None)
        if source_family and str(source_family) in train_source_families:
            findings.append(
                IntegrityFinding(
                    str(case.case_id),
                    "train_test_source_family_overlap",
                    f"source_family:{source_family}",
                )
            )

        study_family = getattr(case, "study_family_id", None)
        if study_family and str(study_family) in train_study_families:
            findings.append(
                IntegrityFinding(
                    str(case.case_id),
                    "train_test_study_family_overlap",
                    f"study_family_id:{study_family}",
                )
            )

        canonical_id = getattr(case, "canonical_id", None)
        if canonical_id and str(canonical_id) in train_canonical_ids:
            findings.append(
                IntegrityFinding(
                    str(case.case_id),
                    "train_test_canonical_id_overlap",
                    f"canonical_id:{canonical_id}",
                )
            )

    return findings


def find_missing_provenance(
    cases: Iterable[object],
) -> list[IntegrityFinding]:
    findings = []
    for case in cases:
        missing = []
        if not getattr(case, "source_family", None) or getattr(case, "source_family") == "unspecified":
            missing.append("source_family")
        if not getattr(case, "canonical_id", None):
            missing.append("canonical_id")
        if not getattr(case, "source_snapshot_sha256", None):
            missing.append("source_snapshot_sha256")
        if not getattr(case, "passage_sha256", None):
            missing.append("passage_sha256")
        if missing:
            findings.append(
                IntegrityFinding(
                    str(case.case_id),
                    "missing_provenance",
                    ",".join(missing),
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
