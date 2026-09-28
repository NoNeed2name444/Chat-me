# Medical Verifier v0.1

Safety-first medical verification framework.

## Included

- medical claim verification API
- drug-label evidence endpoint
- local evidence ingestion
- PubMed metadata retrieval
- openFDA drug-label retrieval
- risk gates and abstention
- contradiction handling
- audit/provenance
- Docker and CI tests

This is an engineering foundation, **not a clinically validated medical device**. It must not be used as an autonomous diagnostic, prescribing, or treatment system.

## Quick start

```bash
cd medical-verifier
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app --reload
```

Open http://127.0.0.1:8000/docs

## Commercial use

The project code is MIT licensed. Runtime dependencies are permissively licensed. External medical data/services have separate terms; see `docs/COMMERCIAL_USE.md` before redistributing source content or using it for model training.
