from fastapi import APIRouter
from app.models.claim import ClaimRequest
from app.models.verification import VerificationResponse
from app.verification.pipeline import verify

router=APIRouter(tags=["verification"])

@router.post("/verify",response_model=VerificationResponse)
def verify_claim(request: ClaimRequest):
    return verify(request)
