from fastapi import FastAPI

from app.api.verify import router as verify_router
from app.api.drug import router as drug_router
from app.api.documents import router as documents_router
from app.config import settings

app = FastAPI(
    title="Medical Verifier",
    version=settings.verifier_version,
    description="Safety-first medical verification framework.",
)

app.include_router(verify_router, prefix="/v1")
app.include_router(drug_router, prefix="/v1")
app.include_router(documents_router, prefix="/v1")

@app.get("/health")
def health():
    return {
        "status": "ok",
        "version": settings.verifier_version,
        "knowledge_snapshot": settings.knowledge_snapshot,
    }
