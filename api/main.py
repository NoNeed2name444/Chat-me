from fastapi import FastAPI

from api.routes.verify import router as verify_router
from api.routes.drug import router as drug_router
from api.routes.documents import router as documents_router
from api.routes.questions import router as questions_router
from api.routes.benchmark import router as benchmark_router
from api.config import settings

app = FastAPI(
    title="Medical Verifier",
    version=settings.verifier_version,
    description="Safety-first medical verification framework.",
)

app.include_router(verify_router, prefix="/v1")
app.include_router(drug_router, prefix="/v1")
app.include_router(documents_router, prefix="/v1")
app.include_router(questions_router, prefix="/v1")
app.include_router(benchmark_router, prefix="/v1")

@app.get("/health")
def health():
    return {
        "status": "ok",
        "version": settings.verifier_version,
        "knowledge_snapshot": settings.knowledge_snapshot,
    }
