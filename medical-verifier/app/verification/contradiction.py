import re

NEGATION = re.compile(r"\b(?:no|not|never|without|does\s+not|doesn't|isn't|cannot|can't)\b", re.I)

def _tokens(text: str) -> set[str]:
    return {x for x in re.findall(r"[a-z0-9'-]+", text.lower()) if len(x) >= 5}

def _negation(text: str) -> bool:
    return bool(NEGATION.search(text))

def assess_evidence(items, claim):
    claim_tokens = _tokens(claim)
    claim_neg = _negation(claim)
    classified = []
    for item in items:
        text = f"{item.title} {item.passage}".strip()
        overlap = len(claim_tokens & _tokens(text)) / len(claim_tokens) if claim_tokens else 0.0
        evidence_neg = _negation(text)
        item.relevance_score = max(item.relevance_score, round(overlap, 4))
        if overlap < 0.25 or not item.passage.strip():
            item.supports = None
        elif claim_neg != evidence_neg:
            item.supports = False
        else:
            item.supports = True
        classified.append(item)
    contradictions = [x for x in classified if x.supports is False]
    return classified, contradictions, []
