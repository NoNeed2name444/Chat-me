import re
from dataclasses import dataclass

@dataclass(frozen=True)
class AttackFinding:
    code: str
    severity: str
    description: str

_PATTERNS = [
    ("negation_scope", "high", re.compile(r"\b(not|never|no|without|isn't|doesn't|cannot|can't)\b")),
    ("absolute_quantifier", "high", re.compile(r"\b(always|never|all|none|only|must|guaranteed|100%)\b")),
    ("temporal_claim", "high", re.compile(r"\b(now|currently|today|formerly|previously|used to|as of|since|before|after|recently)\b")),
    ("conditional_claim", "moderate", re.compile(r"\b(if|unless|only if|when|provided that|except)\b")),
    ("numeric_claim", "high", re.compile(r"\b\d+(?:\.\d+)?\s*(?:%|percent|mg|mcg|g|kg|ml|mL|mmol|mmHg|years?|months?|days?)(?![A-Za-z0-9])")),
    ("population_qualifier", "high", re.compile(r"\b(adults?|children|pediatric|elderly|pregnan\w*|breastfeed\w*|renal|kidney|hepatic|liver)\b")),
    ("causal_claim", "high", re.compile(r"\b(caus(?:e|es|ed|al)|leads? to|prevents?|reduces?|increases?|decreases?|results? in)\b")),
    ("diagnostic_claim", "high", re.compile(r"\b(diagnos(?:e|is|ed|tic)|rules? out|confirms?|definitively)\b")),
    ("action_request", "critical", re.compile(
        r"\b(?:should\s+i|should\s+we|can\s+i|may\s+i|what\s+should\s+i|how\s+should\s+i|do\s+i)\b"
        r".{0,100}\b(stop|start|change|double|halve|take|skip|replace|increase|decrease|reduce)\b"
        r".{0,100}\b(medication|medicine|drug|dose|insulin|anticoagulant"
        r"|metformin|warfarin|heparin|aspirin|ibuprofen|acetaminophen"
        r"|amoxicillin|prednisone|levothyroxine|lisinopril)\b",
        re.I,
    )),
    ("urgent_safety_action", "critical", re.compile(
        r"\b(overdose|poisoning|poisoned|severe\s+bleeding|chest\s+pain|difficulty\s+breathing|anaphylaxis)\b"
        r".{0,120}\b(what\s+should\s+i|what\s+do\s+i\s+do|should\s+i|can\s+i|may\s+i)\b",
        re.I,
    )),
    ("prompt_injection", "critical", re.compile(
        r"\b(ignore|disregard|override|bypass)\b.{0,100}"
        r"\b(instructions?|rules?|policy|safety|guardrails?)\b",
        re.I,
    )),
    ("authority_pressure", "moderate", re.compile(
        r"\b(my doctor|doctor said|expert said|guideline says|you must trust)\b",
        re.I,
    )),
    ("citation_pressure", "moderate", re.compile(
        r"\b(without checking|don't verify|no need to verify|assume the citation)\b",
        re.I,
    )),
]

def inspect_claim(claim: str) -> list[AttackFinding]:
    return [
        AttackFinding(
            code=code,
            severity=severity,
            description=f"Claim contains {code.replace('_', ' ')}.",
        )
        for code, severity, pattern in _PATTERNS
        if pattern.search(claim)
    ]

def highest_severity(findings: list[AttackFinding]) -> str:
    order = {"low": 0, "moderate": 1, "high": 2, "critical": 3}
    if not findings:
        return "low"
    return max(findings, key=lambda x: order[x.severity]).severity
