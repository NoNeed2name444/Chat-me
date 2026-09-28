import re
from dataclasses import dataclass

from app.verification.entity_normalization import entities_equivalent
from app.verification.temporal_normalization import extract_explicit_dates

RELATION_PHRASES = {
    "causal": (
        "cause", "causes", "caused", "lead to", "leads to",
        "result in", "results in", "prevent", "prevents",
    ),
    "risk_increase": (
        "increase", "increases", "increased", "raises", "elevates",
    ),
    "risk_decrease": (
        "reduce", "reduces", "reduced", "lowers", "decrease", "decreases",
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
    "all", "selected", "every", "everyone", "regardless", "only",
    "exclusively", "previously", "previous", "prior", "currently",
    "current", "now", "at", "present", "will", "planned", "plan",
    "expected", "future",
    "no", "not", "never", "without", "does", "doesn't",
    "cannot", "can't", "has", "have", "had",
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
    matches.sort(key=lambda item: (item[0], -len(item[1])))
    if not matches:
        return ()

    start, phrase = min(matches, key=lambda item: item[0])
    if before:
        fragment = lower[:start]
    else:
        fragment = lower[start + len(phrase):]

    raw_tokens = re.findall(r"[a-z0-9'-]+", fragment)

    if before:
        candidates = reversed(raw_tokens)
    else:
        candidates = iter(raw_tokens)

    selected = []
    seen_substantive = False

    for token in candidates:
        is_stopword = token in _STOPWORDS and len(token) != 1
        if is_stopword:
            if seen_substantive:
                break
            continue

        selected.append(token)
        seen_substantive = True

        if len(selected) >= 2:
            break

    if not selected:
        return ()

    if before:
        selected.reverse()

    return (" ".join(selected),)


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
    claim_dates = extract_explicit_dates(claim.text)
    evidence_dates = extract_explicit_dates(evidence.text)

    if claim_dates:
        if not evidence_dates:
            return False, "temporal_date_missing"
        if not set(claim_dates).issubset(set(evidence_dates)):
            return False, "temporal_date_mismatch"

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

def _term_sets_overlap(
    left: tuple[str, ...],
    right: tuple[str, ...],
) -> bool:
    if set(left) & set(right):
        return True

    for left_term in left:
        for right_term in right:
            if entities_equivalent(left_term, right_term):
                return True

    return False


def relation_entailed(
    claim: AtomicClaim,
    evidence: AtomicClaim,
) -> tuple[bool, str | None]:
    if claim.subject_terms and evidence.subject_terms:
        if not _term_sets_overlap(claim.subject_terms, evidence.subject_terms):
            return False, "atomic_subject_mismatch"

    if claim.object_terms and evidence.object_terms:
        if not _term_sets_overlap(claim.object_terms, evidence.object_terms):
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


def opposite_polarity_entailed(claim: str, evidence: str) -> bool:
    """Detect a structurally matching atomic statement with opposite polarity."""
    claim_atoms = decompose_claim(claim)
    evidence_atoms = decompose_claim(evidence)

    for claim_atom in claim_atoms:
        claim_tokens = {
            token
            for token in re.findall(r"[a-z0-9'-]+", claim_atom.text.lower())
            if len(token) >= 4
        }
        if not claim_tokens:
            continue

        for evidence_atom in evidence_atoms:
            evidence_tokens = {
                token
                for token in re.findall(r"[a-z0-9'-]+", evidence_atom.text.lower())
                if len(token) >= 4
            }
            overlap = len(claim_tokens & evidence_tokens) / max(1, len(claim_tokens))
            if overlap < 0.45:
                continue

            relation_ok, _ = relation_entailed(claim_atom, evidence_atom)
            if not relation_ok:
                continue

            temporal_ok, _ = temporal_entailed(claim_atom, evidence_atom)
            if not temporal_ok:
                continue

            if claim_atom.safety != evidence_atom.safety:
                continue

            if claim_atom.polarity != evidence_atom.polarity:
                return True

    return False
