import re

NEGATION = re.compile(r"\\b(no|not|never|without|does not|doesn't|isn't|cannot|can't)\\b", re.I)


def _tokens(text):
    return {x for x in re.findall(r"[a-z0-9'-]+", text.lower()) if len(x) >= 5}


def assess_evidence(items, claim):
    claim_tokens = _tokens(claim)
    claim_neg = bool(NEGATION.search(claim))
    classified = []
    for item in items:
        text = f"{item.title} {item.passage}"
        overlap = len(claim_tokens & _tokens(text)) / len(claim_tokens) if claim_tokens else 0.0
        evidence_neg = bool(NEGATION.search(text))
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
