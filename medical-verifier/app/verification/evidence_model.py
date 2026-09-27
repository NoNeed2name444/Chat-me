import re


CAUSAL_PATTERNS = (
    r"\bcauses?\b", r"\bcaused by\b", r"\bleads? to\b", r"\bresults? in\b",
    r"\bprevents?\b", r"\breduces?\b", r"\bincreases?\b", r"\bdecreases?\b",
    r"\bimproves?\b", r"\bworsens?\b",
)
ASSOCIATIVE_PATTERNS = (
    r"\bassociated with\b", r"\bassociation\b", r"\bcorrelated with\b",
    r"\bcorrelation\b", r"\blinked to\b", r"\bobservational\b",
)

MODALITY = {
    "necessary": (r"\bmust\b", r"\brequires?\b", r"\bshould\b"),
    "possible": (r"\bmay\b", r"\bmight\b", r"\bcan\b", r"\bcould\b", r"\bpossible\b"),
    "probable": (r"\blikely\b", r"\bprobable\b", r"\bprobably\b"),
    "certain": (r"\bdefinitely\b", r"\balways\b", r"\bclearly\b"),
}

DIRECTION = {
    "increase": ("increase", "increases", "increased", "increasing", "higher", "greater", "more", "raises", "raise", "elevates", "elevated"),
    "decrease": ("decrease", "decreases", "decreased", "decreasing", "lower", "less", "reduced", "reduces", "reducing", "lowers", "lowered"),
    "neutral": ("no change", "unchanged", "neutral", "noninferior"),
}

POPULATION_PATTERNS = (
    r"\b(adults?|children|pediatric|paediatric|adolescents?|elderly|older adults?)\b",
    r"\b(pregnan(?:t|cy)|lactating|breastfeeding)\b",
    r"\b(healthy volunteers?|healthy adults?)\b",
    r"\b(patients? with [a-z0-9 /-]{2,50})\b",
    r"\b(subjects? with [a-z0-9 /-]{2,50})\b",
)

OUTCOME_PATTERNS = (\n    r"\\b(?:risk|incidence|rate|odds|probability) of ([a-z0-9][a-z0-9 /-]{2,60})\\b",\n    r"\\b(?:mortality|morbidity|hypoglycemia|hyperglycemia|hospitalization|hospitalisation|death|infection|bleeding|stroke|diabetes)\\b",\n)\n\nTIME_PATTERNS = (
    r"\b(within|over|for|after|before|during)\\s+(\\d+(?:\\.\\d+)?)\\s*(hours?|days?|weeks?|months?|years?)\b",
    r"\b(short[- ]term|long[- ]term|acute|chronic)\b",
)

DOSE_RE = re.compile(r"\b(\\d+(?:\\.\\d+)?)\\s*(mg|mcg|ug|g|ml|l)\b", re.I)
PERCENT_RE = re.compile(r"\b(\\d+(?:\\.\\d+)?)\\s*(?:%|percent)\b", re.I)


def _matches(patterns, text):
    return [m.group(0).lower() for p in patterns for m in re.finditer(p, text, re.I)]


def _modality(text):
    found = []
    for label, patterns in MODALITY.items():
        if any(re.search(p, text, re.I) for p in patterns):
            found.append(label)
    return found


def _directions(text):
    found = []
    for label, words in DIRECTION.items():
        if any(re.search(r"\b" + re.escape(w) + r"\b", text, re.I) for w in words):
            found.append(label)
    return found


def _populations(text):
    return _matches(POPULATION_PATTERNS, text)


def _times(text):
    return _matches(TIME_PATTERNS, text)


def _same_quantity(claim, evidence):
    cq = [(float(n), u.lower()) for n, u in DOSE_RE.findall(claim)]
    eq = [(float(n), u.lower()) for n, u in DOSE_RE.findall(evidence)]
    if not cq:
        return True
    scale = {"mg": 1.0, "g": 1000.0, "mcg": 0.001, "ug": 0.001, "ml": 1.0, "l": 1000.0}
    return any(abs(n * scale[u] - en * scale[eu]) < 1e-9 for n, u in cq for en, eu in eq)


def _same_percent(claim, evidence):
    cp = [float(x) for x in PERCENT_RE.findall(claim)]
    ep = [float(x) for x in PERCENT_RE.findall(evidence)]
    return not cp or any(abs(x - y) < 1e-9 for x in cp for y in ep)


def _causal_kind(text):
    causal = any(re.search(p, text, re.I) for p in CAUSAL_PATTERNS)
    associative = any(re.search(p, text, re.I) for p in ASSOCIATIVE_PATTERNS)
    if causal and not associative:
        return "causal"
    if associative and not causal:
        return "associational"
    if causal and associative:
        return "mixed"
    return "unspecified"


def check_entailment(claim: str, evidence: str):
    claim = claim.lower()
    evidence = evidence.lower()
    axes = {}
    mismatches = []

    cd, ed = _directions(claim), _directions(evidence)
    axes["direction"] = {"claim": cd, "evidence": ed}
    if cd and ed and not set(cd) & set(ed):
        mismatches.append("direction")

    cm, em = _modality(claim), _modality(evidence)
    axes["modality"] = {"claim": cm, "evidence": em}
    if cm and em and set(cm) == {"certain"} and set(em) & {"possible", "probable"}:
        mismatches.append("modality")
    elif cm and em and set(cm) == {"necessary"} and set(em) & {"possible", "probable"}:
        mismatches.append("modality")

    cp, ep = _populations(claim), _populations(evidence)
    axes["population"] = {"claim": cp, "evidence": ep}
    if cp and ep and not any(c in e or e in c for c in cp for e in ep):
        mismatches.append("population")

    axes["dose_unit"] = {"matched": _same_quantity(claim, evidence), "claim": DOSE_RE.findall(claim), "evidence": DOSE_RE.findall(evidence)}
    if not axes["dose_unit"]["matched"]:
        mismatches.append("dose_unit")

    axes["percentage"] = {"matched": _same_percent(claim, evidence)}
    if not axes["percentage"]["matched"]:
        mismatches.append("percentage")

    ct, et = _times(claim), _times(evidence)
    axes["time_window"] = {"claim": ct, "evidence": et}
    if ct and et and not set(ct) & set(et):
        mismatches.append("time_window")

    ck, ek = _causal_kind(claim), _causal_kind(evidence)
    axes["causal_meaning"] = {"claim": ck, "evidence": ek}
    if ck == "causal" and ek == "associational":
        mismatches.append("causal_meaning")

    status = "mismatch" if mismatches else "compatible"
    return {"status": status, "axes": axes, "mismatches": mismatches}
