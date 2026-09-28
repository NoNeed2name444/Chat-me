from pathlib import Path

def test_runtime_modules_exist():
    root = Path(__file__).resolve().parents[1] / "app"
    required = [
        root / "main.py",
        root / "config.py",
        root / "models" / "claim.py",
        root / "api" / "documents.py",
        root / "retrieval" / "local.py",
        root / "terminology" / "normalize.py",
    ]
    assert all(path.exists() for path in required)
