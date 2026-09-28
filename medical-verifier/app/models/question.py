from pydantic import BaseModel, Field

class QuestionArtifact(BaseModel):
    question_id: str
    prompt: str
    answer: str
    curriculum_snapshot_id: str
    source_evidence_ids: list[str] = Field(default_factory=list)
    source_locators: list[str] = Field(default_factory=list)
    source_versions: list[str] = Field(default_factory=list)
    source_passage_hashes: dict[str, str] = Field(default_factory=dict)
    source_snapshot_hashes: dict[str, str] = Field(default_factory=dict)
    source_pages: dict[str, int] = Field(default_factory=dict)
    source_sections: dict[str, str] = Field(default_factory=dict)
    source_block_types: dict[str, str] = Field(default_factory=dict)
    source_related_block_ids: dict[str, list[str]] = Field(default_factory=dict)
    source_languages: dict[str, str] = Field(default_factory=dict)

    validation_status: str = "SOURCE_UNCERTAIN"
    validation_warnings: list[str] = Field(default_factory=list)
    supporting_source_ids: list[str] = Field(default_factory=list)
    requires_human_review: bool = True
    generated_at: str
    generator_version: str
    verification_mode: str = "curriculum_faithful"
