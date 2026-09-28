from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_benchmark_integrity_endpoint_flags_duplicates():
    payload = {
        "dataset_id": "demo",
        "snapshot_id": "snapshot-1",
        "cases": [
            {
                "case_id": "a",
                "claim": "Drug A increases bleeding.",
                "evidence": "Drug A increases bleeding.",
                "expected": "SUPPORTS",
            },
            {
                "case_id": "b",
                "claim": "Drug A increases bleeding.",
                "evidence": "Drug A increases bleeding.",
                "expected": "SUPPORTS",
            },
        ],
    }
    response = client.post("/v1/benchmark/integrity", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["case_count"] == 2
    assert body["integrity_findings"]["finding_count"] == 1


def test_benchmark_integrity_endpoint_rejects_bad_label():
    payload = {
        "dataset_id": "demo",
        "snapshot_id": "snapshot-1",
        "cases": [{
            "case_id": "a",
            "claim": "Drug A increases bleeding.",
            "evidence": "Drug A increases bleeding.",
            "expected": "VALID",
        }],
    }
    response = client.post("/v1/benchmark/integrity", json=payload)
    assert response.status_code == 422
