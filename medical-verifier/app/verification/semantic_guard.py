import re

CAUSAL_WORDS = ("causes", "caused", "leads to", "results in", "prevents", "reduces", "increases", "decreases")
ASSOCIATION_WORDS = ("associated with", "association", "correlated with", "correlation", "linked to", "observational")
NEGATION_WORDS = ("not", "no", "never", "without", "doesn't", "does not", "cannot", "can't")
POLARITY_PAIRS = (("high","low"),("higher","lower"),("increased","decreased"),("increase","decrease"),("increases","decreases"),("increasing","decreasing"),("greater","less"),("more","less"),("stronger","weaker"),("rare","common"),("uncommon","common"),("reduced","increased"))
UNIT_SCALE = {"mg":1.0,"g":1000.0,"mcg":0.001,"ug":0.001,"ml":1.0,"l":1000.0}

def _contains(text: str, phrase: str) -> bool:
    return bool(re.search(r"\b" + re.escape(phrase) + r"\b", text, re.I))

def _quantities(text):
    return [(float(n),u.lower()) for n,u in re.findall(r"\b(\d+(?:\.\d+)?)\s*(mg|mcg|ug|g|ml|l)\b",text,re.I)]

def _percents(text):
    return [float(x) for x in re.findall(r"\b(\d+(?:\.\d+)?)\s*(?:%|percent)\b",text,re.I)]

def _polarity_mismatch(claim: str, evidence: str) -> bool:
    return any((_contains(claim,l) and _contains(evidence,r)) or (_contains(claim,r) and _contains(evidence,l)) for l,r in POLARITY_PAIRS)

def semantic_guard(item, claim):
    evidence=f"{item.title} {item.passage}".lower()
    claim_l=claim.lower()
    warnings=[]
    if _polarity_mismatch(claim_l,evidence):
        warnings.append("directional_polarity_mismatch"); item.supports=False
    if any(_contains(claim_l,x) for x in CAUSAL_WORDS) and any(_contains(evidence,x) for x in ASSOCIATION_WORDS) and not any(_contains(evidence,x) for x in CAUSAL_WORDS):
        warnings.append("causal_claim_only_associative_evidence"); item.supports=None
    if any(_contains(claim_l,x) for x in NEGATION_WORDS) != any(_contains(evidence,x) for x in NEGATION_WORDS):
        warnings.append("negation_scope_mismatch"); item.supports=None
    claim_q,evidence_q=_quantities(claim_l),_quantities(evidence)
    for value,unit in claim_q:
        if not any(abs(value*UNIT_SCALE[unit]-ev*UNIT_SCALE[eu])<1e-9 for ev,eu in evidence_q):
            warnings.append("dose_or_unit_not_matched"); item.supports=None; break
    claim_p,evidence_p=_percents(claim_l),_percents(evidence)
    if claim_p and not any(abs(x-y)<1e-9 for x in claim_p for y in evidence_p):
        warnings.append("percentage_not_matched"); item.supports=None
    if warnings: item.quality_score=round(item.quality_score*0.55,4)
    return item,warnings
