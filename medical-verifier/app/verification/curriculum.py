from dataclasses import dataclass

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

    matched = []
    dates = []

    for item in source_items:
        text = " ".join(
            f"{item.title} {item.passage}".lower().split()
        )

        overlap = sum(
            1
            for term in terms
            if term in text
        ) / max(1, len(terms))

        if overlap >= 0.35:
            matched.append(item.id)
            observed = (
                item.source_date
                or item.effective_date
                or item.publication_date
            )
            if observed:
                dates.append(observed.isoformat())

    if matched:
        return CurriculumAssessment(
            status="ALIGNED",
            matched_source_ids=tuple(matched),
            reasons=(
                "Claim is traceably aligned with the supplied curriculum source snapshot.",
            ),
            source_dates=tuple(sorted(set(dates))),
        )

    return CurriculumAssessment(
        status="NOT_ALIGNED",
        matched_source_ids=(),
        reasons=(
            "Claim could not be sufficiently matched to the supplied curriculum source.",
        ),
        source_dates=tuple(sorted(set(dates))),
    )

def determine_divergence(
    curriculum_status: str,
    current_verdict: str,
    has_newer_evidence: bool,
):
    if curriculum_status == "SOURCE_NOT_AVAILABLE":
        return "unknown"

    if curriculum_status == "ALIGNED" and has_newer_evidence:
        if current_verdict in {
            "CONTRADICTED",
            "MIXED_EVIDENCE",
        }:
            return "curriculum_vs_current_conflict"

        return "curriculum_may_be_outdated"

    if curriculum_status == "ALIGNED":
        return "none"

    return "curriculum_alignment_uncertain"

def build_study_hint(divergence, current_evidence):
    if divergence == "curriculum_vs_current_conflict":
        return (
            "Curriculum note: this answer is judged against your supplied "
            "source. A newer-evidence check found disagreement. Review the "
            "cited current evidence before treating the curriculum statement "
            "as current medical knowledge."
        )

    if divergence == "curriculum_may_be_outdated":
        return (
            "Curriculum note: the supplied source may be older than available "
            "current evidence. The question remains source-faithful, but the "
            "material should not automatically be treated as current clinical guidance."
        )

    return None
