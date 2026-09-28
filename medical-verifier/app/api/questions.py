from uuid import uuid4
from datetime import datetime, timezone

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.config import settings
from app.models.question import QuestionArtifact
from app.retrieval.local import LocalEvidenceProvider

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

    return QuestionArtifact(
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
        generated_at=datetime.now(timezone.utc).isoformat(),
        generator_version=settings.verifier_version,
    )
