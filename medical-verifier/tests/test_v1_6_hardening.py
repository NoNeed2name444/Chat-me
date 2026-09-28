[object Object]

def test_snapshot_api_accepts_valid_content_bound_snapshot():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-api",
        cases=(_record(),),
    )
    response = client.post(
        "/v1/benchmark/snapshot/verify",
        json={"snapshot": snapshot.export_dict()},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["valid"] is True
    assert body["snapshot_sha256"] == snapshot.manifest.digest()


def test_snapshot_api_fails_closed_on_tampered_hash():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-api",
        cases=(_record(),),
    )
    payload = snapshot.export_dict()
    payload["manifest"]["snapshot_sha256"] = "f" * 64
    response = client.post(
        "/v1/benchmark/snapshot/verify",
        json={"snapshot": payload},
    )
    assert response.status_code == 422
    assert "manifest_hash_mismatch" in response.json()["detail"]


def test_snapshot_api_can_enforce_split_separation():
    snapshot = BenchmarkSnapshot.from_cases(
        dataset_id="demo",
        snapshot_id="snapshot-api-split",
        cases=(
            _record("train", split="train"),
            _record("test", split="test"),
        ),
    )
    response = client.post(
        "/v1/benchmark/snapshot/verify",
        json={
            "snapshot": snapshot.export_dict(),
            "enforce_split_separation": True,
        },
    )
    assert response.status_code == 422
    assert response.json()["detail"] == "train_test_separation_violation"
