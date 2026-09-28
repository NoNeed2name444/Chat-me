from dataclasses import dataclass

from app.verification.consistency import guard_specificity
from app.verification.citation_integrity import verify_citation
from app.verification.semantic_guard import semantic_guard

@dataclass(frozen=True)
class EntailmentResult:
    label: str
    warnings: tuple[str, ...]

def assess_entailment(item, claim):
    warnings = []

    item, citation_warnings = verify_citation(item)
    warnings.extend(citation_warnings)

    item, semantic_warnings = semantic_guard(item, claim)
    warnings.extend(semantic_warnings)

    item, specificity_warnings = guard_specificity(item, claim)
    warnings.extend(specificity_warnings)

    if item.supports is False:
        label = "CONTRADICTS"
    elif warnings or item.supports is not True:
        label = "UNKNOWN"
    else:
        label = "SUPPORTS"

    return item, EntailmentResult(
        label=label,
        warnings=tuple(sorted(set(warnings))),
    )
