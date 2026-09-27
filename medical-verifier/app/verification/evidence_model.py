import re
from typing import Any

STRONG_CAUSAL_PATTERNS = (
    r"\bcauses?\b", r"\bcaused by\b", r"\bleads? to\b", r"\bresults? in\b",
    r"\bprevents?\b",
)
CAUSAL_PATTERNS = (
    r"\bcauses?\b", r"\bcaused by\b", r"\bleads? to\b", r"\bresults? in\b",
    r"\bprevents?\b", r"\breduces?\b", r"\bincreases?\b", r"\bdecreases?\b",
    r"\bimproves?\b", r"\bworsens?\b", r"\blowers?\b", r"\braises?\b",
)
ASSOCIATIVE_PATTERNS = (
    r"\bassociated with\b", r"\bassociation\b", r"\bcorrelated with\b",
    r"\bcorrelation\b", r"\blinked to\b", r"\bobservational\b",
)
MODALITY = {
    "necessary": (r"\bmust\b", r"\brequires?\b", r"\bshould\b", r"\bneed[s]? to\b"),
    "possible": (r"\bmay\b", r"\bmight\b", r"\bcan\b", r"\bcould\b", r"\bpossible\b"),
    "probable": (r"\blikely\b", r"\bprobable\b", r"\bprobably\b"),
    "certain": (r"\bdefinitely\b", r"\balways\b", r"\bclearly\b", r"\bwill\b"),
}
DIRECTION = {
    "increase": ("increase", "increases", "increased", "increasing", "higher", "greater", "more", "raises", "raise", "elevates", "elevated"),
    "decrease": ("decrease", "decreases", "decreased", "decreasing", "lower", "less", "reduced", "reduces", "reducing", "lowers", "lowered"),
    "neutral": ("no change", "unchanged", "neutral", "noninferior", "no difference"),
}
NEGATION_PATTERNS = (
    r"\bno\b", r"\bnot\b", r"\bnever\b", r"\bwithout\b",
    r"\bdoes not\b", r"\bdoesn't\b", r"\bisn't\b", r"\bcannot\b", r"\bcan't\b",
)
POPULATION_PATTERNS = (
    r"\b(adults?|children|pediatric|paediatric|adolescents?|elderly|older adults?)\b",
    r"\b(pregnan(?:t|cy)|lactating|breastfeeding)\b",
    r"\b(healthy volunteers?|healthy adults?)\b",
    r"\bpatients? with [a-z0-9 /-]{2,60}\b",
    r"\bsubjects? with [a-z0-9 /-]{2,60}\b",
)
OUTCOME_PATTERNS = (
    r"\b(?:risk|incidence|rate|odds|probability) of ([a-z0-9][a-z0-9 /-]{2,60})\b",
    r"\b(mortality|morbidity|hypoglycemia|hyperglycemia|hospitalization|hospitalisation|death|infection|bleeding|stroke|diabetes|pain|blood pressure|weight|symptoms?)\b",
)
TIME_PATTERNS = (
    r"\b(within|over|for|after|before|during)\s+(\d+(?:\.\d+)?)\s*(hours?|days?|weeks?|months?|years?)\b",
    r"\b(short[- ]term|long[- ]term|acute|chronic)\b",
)
DOSE_RE = re.compile(r"\b(\d+(?:\.\d+)?)\s*(mg|mcg|ug|g|ml|l)\b", re.I)
PERCENT_RE = re.compile(r"\b(\d+(?:\.\d+)?)\s*(?:%|percent)\b", re.I)
WORD_RE = re.compile(r"[a-z0-9][a-z0-9'-]*", re.I)

def _matches(patterns, text):
    return [m.group(0).lower() for p in patterns for m in re.finditer(p, text, re.I)]

def _modality(text):
    return [label for label, patterns in MODALITY.items() if any(re.search(p, text, re.I) for p in patterns)]

def _directions(text):
    return [label for label, words in DIRECTION.items() if any(re.search(r"\b" + re.escape(w) + r"\b", text, re.I) for w in words)]

def _populations(text):
    return _matches(POPULATION_PATTERNS, text)

def _outcomes(text):
    values = []
    for pattern in OUTCOME_PATTERNS:
        for match in re.finditer(pattern, text, re.I):
            value = (match.group(1) if match.lastindex else match.group(0)).strip(" .;,")
            if value:
                values.append(re.sub(r"\s+", " ", value.lower()))
    return sorted(set(values))

def _times(text):
    return _matches(TIME_PATTERNS, text)

def _same_quantity(claim, evidence):
    cq = [(float(n), u.lower()) for n, u in DOSE_RE.findall(claim)]
    eq = [(float(n), u.lower()) for n, u in DOSE_RE.findall(evidence)]
    if not cq:
        return True
    if not eq:
        return False
    scale = {"mg": 1.0, "g": 1000.0, "mcg": 0.001, "ug": 0.001, "ml": 1.0, "l": 1000.0}
    return any(abs(n * scale[u] - en * scale[eu]) < 1e-9 for n, u in cq for en, eu in eq)

def _same_percent(claim, evidence):
    cp = [float(x) for x in PERCENT_RE.findall(claim)]
    ep = [float(x) for x in PERCENT_RE.findall(evidence)]
    return not cp or (bool(ep) and any(abs(x - y) < 1e-9 for x in cp for y in ep))

def _causal_kind(text):
    associative = any(re.search(p, text, re.I) for p in ASSOCIATIVE_PATTERNS)
    strong_causal = any(re.search(p, text, re.I) for p in STRONG_CAUSAL_PATTERNS)
    directional = any(re.search(p, text, re.I) for p in CAUSAL_PATTERNS if p not in STRONG_CAUSAL_PATTERNS)
    if strong_causal: return "causal"
    if associative: return "associational"
    if directional: return "causal"
    return "unspecified"

def _polarity(text):
    return "negative" if any(re.search(p, text, re.I) for p in NEGATION_PATTERNS) else "positive"

def _subject_terms(text):
    words = WORD_RE.findall(text.lower())
    stop = {"the","a","an","and","or","but","with","for","from","that","this","these","those","may","might","can","could","will","should","must","daily","within","after","before","during"}
    relation_words = {"increase","increases","increased","increasing","decrease","decreases","decreased","decreasing","reduce","reduces","reduced","reducing","cause","causes","caused","associated","association","risk","rate","odds","probability","prevents","prevent","leads","results"}
    return sorted({w for w in words if len(w) >= 4 and w not in stop and w not in relation_words})

def _overlap(left, right):
    a, b = set(left), set(right)
    if not a or not b: return 0.0
    return len(a & b) / min(len(a), len(b))

def _text_overlap(left, right):
    a, b = set(re.findall(r"[a-z0-9]+", left.lower())), set(re.findall(r"[a-z0-9]+", right.lower()))
    if not a or not b: return 0.0
    return len(a & b) / min(len(a), len(b))

def _time_equivalent(left, right):
    if left in {"short-term","long-term","acute","chronic"} or right in {"short-term","long-term","acute","chronic"}:
        return left == right
    lm = re.search(r"(\d+(?:\.\d+)?)\s*(hours?|days?|weeks?|months?|years?)", left)
    rm = re.search(r"(\d+(?:\.\d+)?)\s*(hours?|days?|weeks?|months?|years?)", right)
    if not lm or not rm: return left == right
    scale = {"hour":1/24,"hours":1/24,"day":1,"days":1,"week":7,"weeks":7,"month":30,"months":30,"year":365,"years":365}
    return abs(float(lm.group(1))*scale[lm.group(2)] - float(rm.group(1))*scale[rm.group(2)]) < 1e-9

def check_entailment(claim: str, evidence: str) -> dict[str, Any]:
    claim = re.sub(r"\s+", " ", claim.strip().lower())
    evidence = re.sub(r"\s+", " ", evidence.strip().lower())
    claim_subject, evidence_subject = _subject_terms(claim), _subject_terms(evidence)
    subject_overlap = _overlap(claim_subject, evidence_subject)
    cd, ed = _directions(claim), _directions(evidence)
    cm, em = _modality(claim), _modality(evidence)
    cp, ep = _populations(claim), _populations(evidence)
    co, eo = _outcomes(claim), _outcomes(evidence)
    ct, et = _times(claim), _times(evidence)
    ck, ek = _causal_kind(claim), _causal_kind(evidence)
    cpol, epol = _polarity(claim), _polarity(evidence)

    axes = {
        "subject": {"claim": claim_subject, "evidence": evidence_subject, "overlap": round(subject_overlap,4)},
        "direction": {"claim": cd, "evidence": ed},
        "modality": {"claim": cm, "evidence": em},
        "population": {"claim": cp, "evidence": ep},
        "dose_unit": {"matched": _same_quantity(claim,evidence), "claim": DOSE_RE.findall(claim), "evidence": DOSE_RE.findall(evidence)},
        "percentage": {"matched": _same_percent(claim,evidence), "claim": PERCENT_RE.findall(claim), "evidence": PERCENT_RE.findall(evidence)},
        "outcome": {"claim": co, "evidence": eo},
        "time_window": {"claim": ct, "evidence": et},
        "causal_meaning": {"claim": ck, "evidence": ek},
        "polarity": {"claim": cpol, "evidence": epol},
    }
    mismatches = []
    if cd and ed and not set(cd) & set(ed): mismatches.append("direction")
    if cpol != epol: mismatches.append("polarity")

    strength = {"possible":1,"probable":2,"necessary":3,"certain":4}
    if cm and em and max(strength.get(x,0) for x in em) < max(strength.get(x,0) for x in cm):
        mismatches.append("modality")

    if cp and ep and not any(_text_overlap(c,e) >= 0.5 for c in cp for e in ep):
        mismatches.append("population")
    if not axes["dose_unit"]["matched"]: mismatches.append("dose_unit")
    if not axes["percentage"]["matched"]: mismatches.append("percentage")
    if co and eo and not any(_text_overlap(c,e) >= 0.5 for c in co for e in eo):
        mismatches.append("outcome")
    if ct and et and not any(c == e or _time_equivalent(c,e) for c in ct for e in et):
        mismatches.append("time_window")

    if ck == "causal" and ek == "associational":
        mismatches.append("causal_meaning")
    elif ck == "associational" and ek == "causal":
        pass
    elif ck != "unspecified" and ek != "unspecified" and ck != ek:
        mismatches.append("causal_meaning")

    has_dose_in_both = bool(DOSE_RE.search(claim) and DOSE_RE.search(evidence))
    has_percent_in_both = bool(PERCENT_RE.search(claim) and PERCENT_RE.search(evidence))
    relevance = "insufficient" if subject_overlap < 0.34 and not (has_dose_in_both or has_percent_in_both) else "relevant"
    if relevance == "insufficient":
        relation = "insufficient"
    elif {"direction","polarity"} & set(mismatches):
        relation = "contradiction"
    else:
        relation = "neutral" if mismatches else "entailment"

    return {
        "status": relation,
        "relation": relation,
        "relevance": relevance,
        "axes": axes,
        "mismatches": sorted(set(mismatches)),
        "claim_structure": {"subject_terms": claim_subject,"direction":cd,"outcomes":co,"population":cp,"causal_meaning":ck,"modality":cm,"polarity":cpol},
        "evidence_structure": {"subject_terms": evidence_subject,"direction":ed,"outcomes":eo,"population":ep,"causal_meaning":ek,"modality":em,"polarity":epol},
    }
