from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

def test_treatment_question_context_cannot_be_hidden_by_benign_answer():
    response = client.post(
        "/v1/verify",
        json={
            "claim": "Yes.",
            "question_context": "Should I stop warfarin immediately?",
            "sources": ["pubmed"],
            "verification_contract_version": "1.1",
        },
    )

    assert response.status_code == 200
    body = response.json()

    assert body["verdict"] == "SAFETY_ESCALATION"
    assert body["requires_human_review"] is True
