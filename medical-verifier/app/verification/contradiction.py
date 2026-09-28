import re

RELATION_WORDS = (
    "causes", "caused", "leads to", "results in",
    "prevents", "reduces", "increases", "decreases",
    "associated with", "correlated with", "linked to",
)

def _tokens(text):
    return {
        x
        for x in re.findall(r"[a-z0-9'-]+", text.lower())
        if len(x) >= 5
    }

def _relation_present(text, relation):
    return relation in text.lower()

def _relation_polarity(text, relation):
    lower = text.lower()
    for match in re.finditer(re.escape(relation), lower):
        window = lower[max(0, match.start() - 35):match.start()]
        if re.search(
            r"\b(no|not|never|without|does not|doesn't|isn't|cannot|can't)\b",
            window,
        ):
            return False
        return True
    return None

def assess_evidence(items, claim):
    claim_tokens = _tokens(claim)
    classified = []

    claim_relations = [
        relation
        for relation in RELATION_WORDS
        if _relation_present(claim, relation)
    ]

    for item in items:
        text = f"{item.title} {item.passage}"
        evidence_tokens = _tokens(text)
        overlap = (
            len(claim_tokens & evidence_tokens) / len(claim_tokens)
            if claim_tokens
            else 0.0
        )

        item.relevance_score = max(
            item.relevance_score,
            round(overlap, 4),
        )

        if overlap < 0.25 or not item.passage.strip():
            item.supports = None
            classified.append(item)
            continue

        polarity_mismatch = False
        for relation in claim_relations:
            claim_polarity = _relation_polarity(claim, relation)
            evidence_polarity = _relation_polarity(text, relation)
            if (
                claim_polarity is not None
                and evidence_polarity is not None
                and claim_polarity != evidence_polarity
            ):
                polarity_mismatch = True
                break

        if polarity_mismatch:
            item.supports = False
        else:
            item.supports = True

        classified.append(item)

    contradictions = [
        item for item in classified
        if item.supports is False
    ]
    return classified, contradictions, []
