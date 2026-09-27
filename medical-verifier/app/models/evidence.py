from datetime import date
from pydantic import BaseModel

class EvidenceItem(BaseModel):
    id:str
    title:str
    source_type:str
    publisher:str
    publication_date:date|None=None
    url:str|None=None
    passage:str=""
    source_authority:float=0.0
    supports:bool|None=None
