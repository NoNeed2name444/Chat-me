from uuid import uuid4
from app.audit.store import store_verification
from app.models.verification import VerificationResponse
from app.retrieval.ncbi import PubMedProvider
from app.retrieval.openfda import OpenFDALabelProvider
from app.verification.risk import classify_risk,missing_context
from app.verification.rules import deterministic_checks

def verify(request):
    risk=classify_risk(request.claim,request.context); missing=missing_context(request.claim,request.context); flags=deterministic_checks(request.claim)
    if risk=="critical":
        r=VerificationResponse(verdict="SAFETY_ESCALATION",confidence=0,confidence_semantics="Critical-risk safety gate; not a probability.",claim=request.claim,risk_level=risk,evidence=[],limitations=flags+["Critical-risk content requires human/clinical escalation."],requires_human_review=True,verification_id=str(uuid4())); store_verification(r); return r
    evidence=[]
    for source in request.sources:
        try:
            evidence += PubMedProvider().search(request.claim,6) if source=="pubmed" else OpenFDALabelProvider().search(request.claim,6)
        except Exception as exc: flags.append(f"{source}_provider_error:{type(exc).__name__}")
    usable=[x for x in evidence if x.passage]
    if missing and risk in {"moderate","high"}: verdict,conf="CONTEXT_REQUIRED",0
    elif not usable: verdict,conf="INSUFFICIENT_EVIDENCE",0
    elif request.requested_evidence_level=="authoritative" and not any(x.source_authority>=.85 for x in usable): verdict,conf="INSUFFICIENT_EVIDENCE",0
    else: verdict,conf="SUPPORTED",round(min(.9,.55+max(x.source_authority for x in usable)*.35),3)
    r=VerificationResponse(verdict=verdict,confidence=conf,confidence_semantics="Policy score only; not a calibrated probability of truth.",claim=request.claim,risk_level=risk,evidence=[x.model_dump(mode="json") for x in usable],limitations=flags,requires_human_review=risk in {"moderate","high"} or verdict=="CONTEXT_REQUIRED",verification_id=str(uuid4())); store_verification(r); return r
