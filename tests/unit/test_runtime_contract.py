from pathlib import Path

def test_runtime_modules_exist():
    root = Path(__file__).resolve().parents[2]
    required = [
        root / "api" / "main.py",
        root / "api" / "config.py",
        root / "api" / "schemas" / "claim.py",
        root / "api" / "routes" / "documents.py",
        root / "agents" / "specialists" / "retrieval_agent" / "local.py",
        root / "tools" / "definitions" / "normalize.py",
    ]
    assert all(path.exists() for path in required)
