import re
from dataclasses import dataclass

RELATION_PHRASES = {
    "causal": (
        "causes", "caused", "lead to", "leads to",
        "result in", "results in", "prevents",
    ),
    "risk_increase": (
        "increases", "increased", "raises", "elevates",
    ),
    "risk_decrease": (
        "reduces", "reduced", "lowers", "decreases",
    ),
    "association": (
        "associated with", "correlated with",
        "linked to", "association between",
    ),
    "effectiveness": (
        "effective", "efficacy", "works",
    ),
    "contraindication": (
        "contraindicated", "contraindication",
        "should not use", "do not use", "avoid",
    ),
    "interaction": (
        "interacts with", "interaction", "interactions",
        "do not combine", "should not be combined",
        "concomitant use",
    ),
}

NEGATION_RE = re.compile(
    r"\b(no|not|never|without|does not|doesn't|cannot|can't)\b",
    re.I,
)

TEMPORAL_PATTERNS = (
    ("past", re.compile(
        r"\b(previously|previous|prior|history of|in the past|was|were)\b",
        re.I,
    )),
    ("current", re.compile(
        r"\b(currently|at present|now|is taking|are taking|currently taking)\b",
        re.I,
    )),
    ("future", re.compile(
        r"\b(will|planned|plan to|expected to|intends to|future)\b",
        re.I,
    )),
    ("before_event", re.compile(
        r"\b(before|prior to)\b",
        re.I,
    )),
    ("after_event", re.compile(
        r"\b(after|following)\b",
        re.I,
    )),
    ("duration", re.compile(
        r"\b(?:for|within)\s+\d+(?:\.\d+)?\s*(?:hours?|days?|weeks?|months?|years?)\b",
        re.I,
    )),
)

@dataclass(frozen=True)
class AtomicClaim:
    text: str
    relation: str
    polarity: str
    temporal: tuple[str, ...]
    safety: str | None
    subject_terms: tuple[str, ...]
    object_terms: tuple[str, ...]



_STOPWORDS = {
    "a", "an", "the", "and", "or", "but", "for", "with",
    "in", "on", "to", "of", "is", "are", "was", "were",
    "that", "this", "these", "those", "patients", "patient",
}

def _anchor_tokens(text: str, relation: str, *, before: bool) -> tuple[str, ...]:
    if relation == "unclassified" or relation == "mixed":
        return ()

    lower = text.lower()
    phrases = RELATION_PHRASES.get(relation, ())
    matches = [
        (lower.find(phrase), phrase)
        for phrase in phrases
        if lower.find(phrase) >= 0
    ]
    if not matches:
        return ()

    start, phrase = min(matches, key=lambda item: item[0])
    if before:
        fragment = lower[:start]
    else:
        fragment = lower[start + len(phrase):]

    raw_tokens = re.findall(r"[a-z0-9'-]+", fragment)
    tokens = [
        token
        for token in raw_tokens
        if token not in _STOPWORDS and len(token) >= 2
    ]

    compounds = []
    for index in range(len(raw_tokens) - 1):
        left, right = raw_tokens[index:index + 2]
        if left in _STOPWORDS and right in _STOPWORDS:
            continue
        if len(left) >= 2 or len(right) >= 1:
            compounds.append(f"{left} {right}")

    selected = (
        tokens[-3:] + compounds[-2:]
        if before
        else tokens[:3] + compounds[:2]
    )
    return tuple(dict.fromkeys(selected))


def _relation_types(text: str) -> tuple[str, ...]:
    lower = text.lower()
    return tuple(
        relation
        for relation, phrases in RELATION_PHRASES.items()
        if any(phrase in lower for phrase in phrases)
    )

def _polarity(text: str) -> str:
    return "negative" if NEGATION_RE.search(text) else "positive"

def temporal_signature(text: str) -> tuple[str, ...]:
    return tuple(
        label
        for label, pattern in TEMPORAL_PATTERNS
        if pattern.search(text)
    )

def safety_relation(text: str) -> str | None:
    relations = set(_relation_types(text))
    if "interaction" in relations:
        return "interaction"
    if "contraindication" in relations:
        return "contraindication"
    return None

def _substantive_relation_count(text: str) -> int:
    return len(_relation_types(text))

def decompose_claim(text: str) -> tuple[AtomicClaim, ...]:
    normalized = " ".join(text.split())
    if not normalized:
        return ()

    fragments = [
        fragment.strip(" ,")
        for fragment in re.split(r"[.;!?]+", normalized)
        if fragment.strip(" ,")
    ]

    expanded = []
    for fragment in fragments:
        relation_count = _substantive_relation_count(fragment)
        if relation_count <= 1:
            expanded.append(fragment)
            continue

        pieces = re.split(r"\s+(?:and|but|while)\s+", fragment, flags=re.I)
        if len(pieces) == 1:
            expanded.append(fragment)
            continue

        # Only split a conjunction when both sides contain a recognized
        # relation. This avoids turning "increases bleeding and mortality"
        # into a relation-less second fragment.
        relationful = [
            piece.strip(" ,")
            for piece in pieces
            if _substantive_relation_count(piece) > 0
        ]
        if len(relationful) == len(pieces):
            expanded.extend(relationful)
        else:
            expanded.append(fragment)

    claims = []
    for fragment in expanded:
        relations = _relation_types(fragment)
        if not relations:
            relation = "unclassified"
        elif len(relations) == 1:
            relation = relations[0]
        else:
            relation = "mixed"

        claims.append(
            AtomicClaim(
                text=fragment,
                relation=relation,
                polarity=_polarity(fragment),
                temporal=temporal_signature(fragment),
                safety=safety_relation(fragment),
                subject_terms=_anchor_tokens(
                    fragment,
                    relation,
                    before=True,
                ),
                object_terms=_anchor_tokens(
                    fragment,
                    relation,
                    before=False,
                ),
            )
        )

    return tuple(claims)

def temporal_entailed(claim: AtomicClaim, evidence: AtomicClaim) -> tuple[bool, str | None]:
    if not claim.temporal:
        return True, None

    if not evidence.temporal:
        return False, "temporal_scope_missing"

    if set(claim.temporal) != set(evidence.temporal):
        return False, "temporal_scope_mismatch"

    return True, None

def safety_relation_entailed(
    claim: AtomicClaim,
    evidence: AtomicClaim,
) -> tuple[bool, str | None]:
    if claim.safety is None:
        return True, None

    if evidence.safety != claim.safety:
        return False, "safety_relation_mismatch"

    if claim.polarity != evidence.polarity:
        return False, "safety_polarity_mismatch"

    return True, None

def relation_entailed(
    claim: AtomicClaim,
    evidence: AtomicClaim,
) -> tuple[bool, str | None]:
    if claim.subject_terms and evidence.subject_terms:
        subject_overlap = (
            set(claim.subject_terms)
            & set(evidence.subject_terms)
        )
        if not subject_overlap:
            return False, "atomic_subject_mismatch"

    if claim.object_terms and evidence.object_terms:
        object_overlap = (
            set(claim.object_terms)
            & set(evidence.object_terms)
        )
        if not object_overlap:
            return False, "atomic_object_mismatch"

    if claim.relation == "causal":
        if evidence.relation != "causal":
            return False, "causal_claim_requires_causal_evidence"

    elif claim.relation == "interaction":
        if evidence.relation != "interaction":
            return False, "interaction_claim_requires_interaction_evidence"

    elif claim.relation == "contraindication":
        if evidence.relation != "contraindication":
            return False, "contraindication_claim_requires_contraindication_evidence"

    elif claim.relation not in {"mixed", "unclassified"}:
        if evidence.relation not in {claim.relation, "mixed"}:
            return False, "atomic_relation_mismatch"

    return True, None
