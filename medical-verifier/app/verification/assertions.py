import re
from dataclasses import dataclass

@dataclass(frozen=True)
class AtomicAssertion:
    id: str
    text: str
    modality: str
    polarity: str
    subject_terms: tuple[str, ...]
    required_context: tuple[str, ...]

def _split_clauses(text: str) -> list[str]:
    chunks = re.split(r"\s+(?:and|but|while|although|because|however)\s+|[;\n]+", text)
    return [re.sub(r"\s+", " ", x).strip(" .") for x in chunks if x.strip()]

def decompose_claim(claim: str) -> list[AtomicAssertion]:
    assertions = []
    for index, clause in enumerate(_split_clauses(claim)):
        lower = clause.lower()
        polarity = "negative" if re.search(
            r"\b(no|not|never|without|does not|doesn't|isn't|cannot|can't)\b",
            lower,
        ) else "positive"
        modality = "safety" if any(
            x in lower for x in ("safe", "dangerous", "risk", "harm", "contraindicated")
        ) else "causal_or_associative" if any(
            x in lower for x in ("causes", "cause", "increases", "decreases", "reduces", "associated")
        ) else "descriptive"

        required = []
        if any(x in lower for x in ("dose", "safe", "interaction", "contraindicated")):
            required += ["age", "current_medications"]
        if "pregnan" in lower:
            required.append("gestational_age")
        if any(x in lower for x in ("kidney", "renal")):
            required.append("renal_function")
        if any(x in lower for x in ("child", "pediatric", "infant")):
            required.append("age")

        terms = tuple(sorted(set(
            w for w in re.findall(r"[a-z0-9'-]+", lower)
            if len(w) >= 5
        )))

        assertions.append(
            AtomicAssertion(
                id=f"A{index + 1}",
                text=clause,
                modality=modality,
                polarity=polarity,
                subject_terms=terms,
                required_context=tuple(sorted(set(required))),
            )
        )
    return assertions
