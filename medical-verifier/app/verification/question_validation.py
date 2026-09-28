from dataclasses import dataclass

from app.verification.independent_entailment import verify

@dataclass(frozen=True)
class QuestionValidation:
    status: str
    warnings: tuple[str, ...]
    supporting_source_ids: tuple[str, ...]
    requires_review: bool

def validate_question(prompt: str, answer: str, source_items):
    if not source_items:
        return QuestionValidation(
            status="SOURCE_UNAVAILABLE",
            warnings=("no_curriculum_sources",),
            supporting_source_ids=(),
            requires_review=True,
        )

    answer_support = []
    prompt_overlap = False
    warnings = []

    prompt_terms = {
        token.lower()
        for token in prompt.split()
        if len(token) >= 5
    }

    for item in source_items:
        source_text = f"{item.title} {item.passage}"

        source_terms = {
            token.lower()
            for token in source_text.split()
            if len(token) >= 5
        }

        if prompt_terms & source_terms:
            prompt_overlap = True

        entailment = verify(
            answer,
            source_text,
        )

        if entailment.label == "SUPPORTS":
            answer_support.append(item.id)
        else:
            warnings.extend(entailment.reasons)

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
