from pydantic import BaseModel, Field

class QuestionArtifact(BaseModel):
    question_id: str
    prompt: str
    answer: str
    curriculum_snapshot_id: str
    source_evidence_ids: list[str] = Field(default_factory=list)
    source_locators: list[str] = Field(default_factory=list)
    source_versions: list[str] = Field(default_factory=list)
    generated_at: str
    generator_version: str
    verification_mode: str = "curriculum_faithful"
