from api.schemas.question import QuestionArtifact

def test_question_requires_curriculum_snapshot_id():
    question = QuestionArtifact(
        question_id="question:1",
        prompt="What does the source say?",
        answer="The source says X.",
        curriculum_snapshot_id="snapshot:1",
        generated_at="2026-09-28T00:00:00+00:00",
        generator_version="0.3.0",
    )

    assert question.curriculum_snapshot_id == "snapshot:1"
