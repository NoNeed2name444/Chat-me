from dataclasses import dataclass

from app.verification.citation_integrity import sha256_text
from app.verification.independent_entailment import verify as independent_verify

EXTRACTION_THRESHOLD = 0.85

@dataclass(frozen=True)
class CurriculumAssessment:
    status: str
    matched_source_ids: tuple[str, ...]
    reasons: tuple[str, ...]
    source_dates: tuple[str, ...]

def _apply_explicit_precedence(source_items):
    grouped = {}
    ungrouped = []

    for item in source_items:
        if item.precedence_group:
            grouped.setdefault(item.precedence_group, []).append(item)
        else:
            ungrouped.append(item)

    selected = list(ungrouped)
    excluded = []

    for group, items in grouped.items():
        highest = max(item.precedence_rank for item in items)
        winners = [
            item for item in items
            if item.precedence_rank == highest
        ]

        selected.extend(winners)

        excluded.extend(
            item.id
            for item in items
            if item.precedence_rank < highest
        )

    return selected, tuple(sorted(excluded))

def assess_curriculum_fidelity(claim: str, source_items):
    if not source_items:
        return CurriculumAssessment(
            status="SOURCE_NOT_AVAILABLE",
            matched_source_ids=(),
            reasons=("No supplied curriculum source was available.",),
            source_dates=(),
        )

    source_items, precedence_excluded = _apply_explicit_precedence(
        source_items
    )

    normalized = " ".join(claim.lower().split())
    terms = [
        word
        for word in normalized.split()
        if len(word) >= 5
    ]

    aligned = []
    contradicted = []
    uncertain = []
    integrity_failures = []
    extraction_uncertain = []
    dates = []

    for item in source_items:
        if (
            item.passage_sha256
            and item.passage_sha256 != sha256_text(item.passage)
        ):
            integrity_failures.append(item.id)
            continue

        if (
            item.extraction_quality < EXTRACTION_THRESHOLD
            or item.extraction_warnings
        ):
            extraction_uncertain.append(item.id)
            continue

        source_text = " ".join(
            f"{item.title} {item.passage}".lower().split()
        )

        overlap = sum(
            1 for term in terms if term in source_text
        ) / max(1, len(terms))

        if overlap < 0.35:
            continue

        entailment = independent_verify(
            normalized,
            source_text,
        )

        observed = (
            item.source_date
            or item.effective_date
            or item.publication_date
        )

        if observed:
            dates.append(observed.isoformat())

        if entailment.label == "SUPPORTS":
            aligned.append(item.id)
        elif entailment.label == "CONTRADICTS":
            contradicted.append(item.id)
        else:
            uncertain.append(item.id)

    precedence_reason = (
        "Explicit precedence excluded lower-ranked curriculum versions: "
        + ", ".join(precedence_excluded)
        if precedence_excluded
        else None
    )

    if integrity_failures:
        return CurriculumAssessment(
            status="SOURCE_INTEGRITY_FAILED",
            matched_source_ids=(),
            reasons=tuple(
                [
                    "Stored curriculum passage failed its cryptographic integrity check."
                ]
                + ([precedence_reason] if precedence_reason else [])
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    if aligned and contradicted:
        return CurriculumAssessment(
            status="CONFLICTING_CURRICULUM_SOURCES",
            matched_source_ids=tuple(aligned),
            reasons=tuple(
                [
                    "Supplied curriculum sources contain materially conflicting answers."
                ]
                + ([precedence_reason] if precedence_reason else [])
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    if extraction_uncertain and aligned:
        return CurriculumAssessment(
            status="SOURCE_EXTRACTION_UNCERTAIN",
            matched_source_ids=tuple(aligned),
            reasons=(
                "Relevant curriculum material has insufficient extraction quality for fully automatic validation.",
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    if extraction_uncertain and not aligned:
        return CurriculumAssessment(
            status="SOURCE_EXTRACTION_UNCERTAIN",
            matched_source_ids=(),
            reasons=(
                "Relevant curriculum material has insufficient extraction quality for automatic validation.",
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    if aligned:
        reasons = [
            "Claim is traceably aligned with the supplied curriculum source snapshot."
        ]

        if precedence_reason:
            reasons.append(precedence_reason)

        if uncertain:
            reasons.append(
                "Some matching curriculum passages were semantically uncertain."
            )

        return CurriculumAssessment(
            status="ALIGNED",
            matched_source_ids=tuple(aligned),
            reasons=tuple(reasons),
            source_dates=tuple(sorted(set(dates))),
        )

    if uncertain or contradicted:
        return CurriculumAssessment(
            status="UNCERTAIN",
            matched_source_ids=tuple(
                uncertain + contradicted
            ),
            reasons=tuple(
                [
                    "The claim matches curriculum material, but exact semantic support could not be established."
                ]
                + ([precedence_reason] if precedence_reason else [])
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    return CurriculumAssessment(
        status="NOT_ALIGNED",
        matched_source_ids=(),
        reasons=(
            "Claim could not be sufficiently matched to supplied curriculum material.",
        ),
        source_dates=tuple(sorted(set(dates))),
    )

def determine_divergence(
    curriculum_status,
    current_verdict,
    has_relevant_newer_evidence,
):
    if curriculum_status in {
        "SOURCE_NOT_AVAILABLE",
        "SOURCE_INTEGRITY_FAILED",
        "SOURCE_EXTRACTION_UNCERTAIN",
        "CONFLICTING_CURRICULUM_SOURCES",
    }:
        return "unknown"

    if (
        curriculum_status == "ALIGNED"
        and has_relevant_newer_evidence
        and current_verdict in {
            "CONTRADICTED",
            "MIXED_EVIDENCE",
        }
    ):
        return "curriculum_vs_current_conflict"

    if curriculum_status == "ALIGNED" and has_relevant_newer_evidence:
        return "curriculum_may_be_outdated"

    if curriculum_status == "ALIGNED":
        return "none"

    return "curriculum_alignment_uncertain"

def build_study_hint(divergence, current_evidence):
    if divergence == "curriculum_vs_current_conflict":
        return (
            "Curriculum note: this answer is judged against your supplied "
            "source. A newer relevant-evidence check found disagreement. "
            "Review the cited current evidence before treating the curriculum "
            "statement as current medical knowledge."
        )

    if divergence == "curriculum_may_be_outdated":
        return (
            "Curriculum note: the supplied source may be older than newer "
            "relevant evidence. The question remains source-faithful, but the "
            "material should not automatically be treated as current clinical guidance."
        )

    return None
