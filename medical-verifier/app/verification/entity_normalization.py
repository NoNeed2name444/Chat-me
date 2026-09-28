"""Conservative, explicit entity alias normalization.

Only aliases explicitly configured here are normalized. No fuzzy matching or learned
medical synonym inference is performed.
"""

from dataclasses import dataclass


DEFAULT_ALIASES = {
    "acetaminophen": "acetaminophen",
    "paracetamol": "acetaminophen",
    "ibuprofen": "ibuprofen",
}


@dataclass(frozen=True)
class NormalizedEntity:
    original: str
    canonical: str | None
    recognized: bool


def normalize_entity(name: str, aliases: dict[str, str] | None = None) -> NormalizedEntity:
    table = aliases or DEFAULT_ALIASES
    original = " ".join(name.lower().split())
    canonical = table.get(original)
    return NormalizedEntity(original, canonical, canonical is not None)


def entities_equivalent(left: str, right: str, aliases: dict[str, str] | None = None) -> bool:
    a = normalize_entity(left, aliases)
    b = normalize_entity(right, aliases)
    return a.recognized and b.recognized and a.canonical == b.canonical
