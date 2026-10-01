import re

from dataclasses import dataclass

from governance.guardrails.adversarial import highest_severity, inspect_claim
from agents.specialists.retrieval_agent.citation_integrity import sha256_text
from agents.specialists.verification_agent.independent_entailment import verify
from agents.specialists.verification_agent.claim_reasoning import opposite_polarity_entailed
from agents.specialists.verification_agent.curriculum import _apply_explicit_precedence

EXTRACTION_THRESHOLD = 0.85

_PROMPT_INTENTS = {
    "compatible": "interaction",
    "compatibility": "interaction",
    "interact": "interaction",
    "interactions": "interaction",
    "interaction": "interaction",
    "contraindicated": "contraindication",
    "contraindication": "contraindication",
    "contraindications": "contraindication",
    "safe": "safety",
    "safety": "safety",
    "dangerous": "safety",
    "effective": "effectiveness",
    "efficacy": "effectiveness",
    "works": "effectiveness",
    "cause": "causal",
    "causes": "causal",
    "caused": "causal",
    "reduce": "risk_decrease",
    "reduces": "risk_decrease",
    "decrease": "risk_decrease",
    "decreases": "risk_decrease",
    "increase": "risk_increase",
    "increases": "risk_increase",
    "dose": "dose",
    "dosage": "dose",
}

@dataclass(frozen=True)
class QuestionValidation:
    status: str
    warnings: tuple[str, ...]
    supporting_source_ids: tuple[str, ...]
    requires_review: bool

def validate_question(prompt: str, answer: str, source_items):
    attack = inspect_claim(
        f"{prompt} {answer}"
    )

    if highest_severity(attack) == "critical":
        return QuestionValidation(
            status="SAFETY_ESCALATION",
            warnings=tuple(
                sorted(
                    finding.code
                    for finding in attack
                )
            ),
            supporting_source_ids=(),
            requires_review=True,
        )

    if not source_items:
        return QuestionValidation(
            status="SOURCE_UNAVAILABLE",
            warnings=("no_curriculum_sources",),
            supporting_source_ids=(),
            requires_review=True,
        )

    source_items, precedence_excluded = _apply_explicit_precedence(
        source_items
    )

    answer_support = []
    answer_contradictions = []
    prompt_overlap = False
    warnings = (
        [
            "explicit_precedence_excluded:" + ",".join(precedence_excluded)
        ]
        if precedence_excluded
        else []
    )
    extraction_uncertain = False

    prompt_stopwords = {
        "what", "does", "do", "is", "the", "a", "an", "how",
        "who", "which", "are", "can", "may", "should", "from",
        "for", "in", "on", "to", "about", "and", "or",
    }
    prompt_terms = {
        token
        for token in re.findall(r"[a-z0-9'-]+", prompt.lower())
        if len(token) >= 4 and token not in prompt_stopwords
    }
    answer_terms = {
        token
        for token in re.findall(r"[a-z0-9'-]+", answer.lower())
        if len(token) >= 4 and token not in prompt_stopwords
    }
    prompt_intents = {
        _PROMPT_INTENTS[token]
        for token in prompt_terms
        if token in _PROMPT_INTENTS
    }

    for item in source_items:
        if (
            item.extraction_quality < EXTRACTION_THRESHOLD
            or item.extraction_warnings
        ):
            extraction_uncertain = True
            warnings.append(
                f"source_extraction_uncertain:{item.id}"
            )
            continue

        if (
            item.passage_sha256
            and item.passage_sha256 != sha256_text(item.passage)
        ):
            warnings.append(
                f"source_integrity_failed:{item.id}"
            )
            continue

        source_text = item.passage

        source_terms = {
            token
            for token in re.findall(r"[a-z0-9'-]+", source_text.lower())
            if len(token) >= 5
        }

        if prompt_terms & (source_terms | answer_terms):
            prompt_overlap = True

        answer_atoms = __import__(
            "agents.specialists.verification_agent.claim_reasoning",
            fromlist=["decompose_claim"],
        ).decompose_claim(answer)
        answer_relations = {
            atom.relation
            for atom in answer_atoms
            if atom.relation not in {"mixed", "unclassified"}
        }
        if prompt_intents & answer_relations:
            prompt_overlap = True
        if "dose" in prompt_intents and any(
            re.search(r"\b(?:mg|g|mcg|ug|ml|l|kg)\b", token)
            for token in answer_terms
        ):
            prompt_overlap = True

        entailment = verify(
            answer,
            source_text,
        )
        opposite_polarity = opposite_polarity_entailed(
            answer,
            source_text,
        )

        if entailment.label == "SUPPORTS":
            answer_support.append(item.id)
        elif entailment.label == "CONTRADICTS" or opposite_polarity:
            answer_contradictions.append(item.id)
            warnings.extend(entailment.reasons)
        else:
            warnings.extend(entailment.reasons)

    if any(
        warning.startswith("source_integrity_failed:")
        for warning in warnings
    ):
        return QuestionValidation(
            status="SOURCE_INTEGRITY_FAILED",
            warnings=tuple(sorted(set(warnings))),
            supporting_source_ids=(),
            requires_review=True,
        )

    if answer_support and answer_contradictions:
        return QuestionValidation(
            status="SOURCE_CONFLICT",
            warnings=tuple(sorted(set(
                warnings + ["conflicting_curriculum_sources"]
            ))),
            supporting_source_ids=tuple(answer_support),
            requires_review=True,
        )

    if extraction_uncertain:
        return QuestionValidation(
            status="SOURCE_EXTRACTION_UNCERTAIN",
            warnings=tuple(sorted(set(warnings))),
            supporting_source_ids=tuple(answer_support),
            requires_review=True,
        )

    if not prompt_overlap:
        warnings.append("question_prompt_not_aligned_to_curriculum")

    if answer_support and prompt_overlap:
        return QuestionValidation(
            status="VALIDATED",
            warnings=tuple(sorted(set(warnings))),
            supporting_source_ids=tuple(answer_support),
            requires_review=False,
        )

    if answer_support:
        return QuestionValidation(
            status="SOURCE_UNCERTAIN",
            warnings=tuple(sorted(set(warnings))),
            supporting_source_ids=tuple(answer_support),
            requires_review=True,
        )

    return QuestionValidation(
        status="SOURCE_UNSUPPORTED",
        warnings=tuple(sorted(set(
            warnings + ["answer_not_supported_by_curriculum"]
        ))),
        supporting_source_ids=(),
        requires_review=True,
    )
