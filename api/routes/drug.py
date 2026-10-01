from fastapi import APIRouter
from pydantic import BaseModel,Field
from agents.specialists.retrieval_agent.openfda import OpenFDALabelProvider

router=APIRouter(tags=["drug"])
class DrugCheckRequest(BaseModel):
    drug:str=Field(min_length=2,max_length=200)
    search_terms:list[str]=Field(default_factory=list,max_length=12)

@router.post("/drug/check")
def drug_check(request:DrugCheckRequest):
    evidence=OpenFDALabelProvider().search(request.drug,6)
    return {"drug":request.drug,"evidence":[x.model_dump(mode="json") for x in evidence],"warning":"Label retrieval is not a validated prescribing or interaction engine."}
