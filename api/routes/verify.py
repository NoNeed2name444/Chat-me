from fastapi import APIRouter
from api.schemas.claim import ClaimRequest
from api.schemas.verification import VerificationResponse
from orchestration.graph import verify

router=APIRouter(tags=["verification"])

@router.post("/verify",response_model=VerificationResponse)
def verify_claim(request: ClaimRequest):
    return verify(request)
