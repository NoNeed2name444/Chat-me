from dataclasses import dataclass

from app.verification.citation_integrity import sha256_text
from app.verification.independent_entailment import verify as independent_verify

@dataclass(frozen=True)
class CurriculumAssessment:
    status: str
    matched_source_ids: tuple[str, ...]
    reasons: tuple[str, ...]
    source_dates: tuple[str, ...]

def assess_curriculum_fidelity(claim: str, source_items):
    if not source_items:
        return CurriculumAssessment(
            status="SOURCE_NOT_AVAILABLE",
            matched_source_ids=(),
            reasons=("No supplied curriculum source was available.",),
            source_dates=(),
        )

    normalized = " ".join(claim.lower().split())
    terms = [
        word for word in normalized.split()
        if len(word) >= 5
    ]

    aligned = []
    uncertain = []
    integrity_failures = []
    dates = []

    for item in source_items:
        if (
            item.passage_sha256
            and item.passage_sha256 != sha256_text(item.passage)
        ):
            integrity_failures.append(item.id)
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
        elif entailment.label == "UNKNOWN":
            uncertain.append(item.id)

    if integrity_failures:
        return CurriculumAssessment(
            status="SOURCE_INTEGRITY_FAILED",
            matched_source_ids=(),
            reasons=(
                "Stored curriculum passage failed its cryptographic integrity check.",
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    if aligned:
        reasons = [
            "Claim is traceably aligned with the supplied curriculum source snapshot."
        ]
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

    if uncertain:
        return CurriculumAssessment(
            status="UNCERTAIN",
            matched_source_ids=tuple(uncertain),
            reasons=(
                "The claim matches the curriculum topic, but exact semantic support could not be established.",
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
    if curriculum_status == "SOURCE_NOT_AVAILABLE":
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
