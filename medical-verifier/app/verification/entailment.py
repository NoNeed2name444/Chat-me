from dataclasses import dataclass
from datetime import date

from app.verification.citation_integrity import verify_citation
from app.verification.consistency import guard_specificity
from app.verification.entailment_provider import AgreementGate, StructuredProvider
from app.verification.semantic_guard import semantic_guard
from app.verification.temporal_guard import guard_temporal_specificity

@dataclass(frozen=True)
class EntailmentResult:
    label: str
    warnings: tuple[str, ...]

def assess_entailment(item, claim):
    warnings = []

    item, citation_warnings = verify_citation(item)
    warnings.extend(citation_warnings)

    independent = AgreementGate(
        [StructuredProvider()]
    ).assess(
        claim,
        f"{item.title} {item.passage}",
    )
    warnings.extend(independent.reasons)

    item, semantic_warnings = semantic_guard(
        item,
        claim,
    )
    warnings.extend(semantic_warnings)

    item, specificity_warnings = guard_specificity(
        item,
        claim,
    )
    warnings.extend(specificity_warnings)

    item, temporal_warnings = guard_temporal_specificity(
        item,
        claim,
        date.today(),
    )
    warnings.extend(temporal_warnings)

    semantic_support = (
        item.supports is True
        and not semantic_warnings
        and not specificity_warnings
        and not temporal_warnings
    )
    citation_verified = not citation_warnings
    independent_support = independent.label == "SUPPORTS"
    independent_contradiction = independent.label == "CONTRADICTS"

    if (
        independent_contradiction
        and item.supports is False
        and citation_verified
        and not temporal_warnings
    ):
        label = "CONTRADICTS"
    elif (
        citation_verified
        and semantic_support
        and independent_support
    ):
        label = "SUPPORTS"
    else:
        label = "UNKNOWN"

    return item, EntailmentResult(
        label=label,
        warnings=tuple(sorted(set(warnings))),
    )
