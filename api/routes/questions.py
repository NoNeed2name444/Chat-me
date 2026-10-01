from uuid import uuid4
from datetime import datetime, timezone

from fastapi import APIRouter
from pydantic import BaseModel, Field

from governance.audit.store import fetch_question, store_question
from api.config import settings
from api.schemas.question import QuestionArtifact
from agents.specialists.retrieval_agent.local import LocalEvidenceProvider
from agents.specialists.verification_agent.question_validation import validate_question

router = APIRouter(tags=["questions"])

class QuestionCreateRequest(BaseModel):
    prompt: str = Field(min_length=3, max_length=5000)
    answer: str = Field(min_length=1, max_length=5000)
    curriculum_snapshot_id: str = Field(min_length=1, max_length=200)
    source_evidence_ids: list[str] = Field(default_factory=list)

@router.post("/questions", response_model=QuestionArtifact)
def create_question(request: QuestionCreateRequest):
    evidence = LocalEvidenceProvider().search(
        request.prompt,
        [],
        max(10, len(request.source_evidence_ids)),
        source_ids=request.source_evidence_ids or None,
        curriculum_snapshot_id=request.curriculum_snapshot_id,
    )

    validation = validate_question(
        request.prompt,
        request.answer,
        evidence,
    )

    artifact = QuestionArtifact(
        question_id=f"question:{uuid4()}",
        prompt=request.prompt,
        answer=request.answer,
        curriculum_snapshot_id=request.curriculum_snapshot_id,
        source_evidence_ids=[item.id for item in evidence],
        source_locators=[
            item.source_locator
            for item in evidence
            if item.source_locator
        ],
        source_versions=[
            item.document_version
            for item in evidence
            if item.document_version
        ],
        source_passage_hashes={
            item.id: item.passage_sha256
            for item in evidence
            if item.passage_sha256
        },
        source_snapshot_hashes={
            item.id: item.source_snapshot_sha256
            for item in evidence
            if item.source_snapshot_sha256
        },
        source_pages={
            item.id: item.page_number
            for item in evidence
            if item.page_number is not None
        },
        source_sections={
            item.id: item.section
            for item in evidence
            if item.section
        },
        source_block_types={
            item.id: item.block_type
            for item in evidence
            if item.block_type
        },
        source_related_block_ids={
            item.id: item.related_block_ids
            for item in evidence
            if item.related_block_ids
        },
        source_languages={
            item.id: item.language
            for item in evidence
            if item.language
        },
        validation_status=validation.status,
        validation_warnings=list(validation.warnings),
        supporting_source_ids=list(validation.supporting_source_ids),
        requires_human_review=validation.requires_review,
        generated_at=datetime.now(timezone.utc).isoformat(),
        generator_version=settings.verifier_version,
    )

    store_question(artifact)
    return artifact

@router.get("/questions/{question_id}", response_model=QuestionArtifact)
def get_question(question_id: str):
    data = fetch_question(question_id)
    if data is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="question_not_found")
    return QuestionArtifact.model_validate(data)
