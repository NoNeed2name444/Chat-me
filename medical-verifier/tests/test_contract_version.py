from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

def test_contract_mismatch_fails_closed():
    response = client.post(
        "/v1/verify",
        json={
            "claim": "Insulin lowers blood glucose.",
            "sources": ["local"],
            "question_context": "Should I stop warfarin?",
            "verification_contract_version": "999.0",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["verdict"] == "CONTRACT_MISMATCH"
    assert body["requires_human_review"] is True
    assert body["verification_contract_version"] == "1.3
