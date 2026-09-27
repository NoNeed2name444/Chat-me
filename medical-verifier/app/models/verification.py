from pydantic import BaseModel,Field

class VerificationResponse(BaseModel):
    verdict:str
    confidence:float=Field(ge=0,le=1)
    confidence_semantics:str
    claim:str
    risk_level:str
    evidence:list[dict]=[]
    limitations:list[str]=[]
    requires_human_review:bool
    verification_id:str
