import httpx
from app.config import settings
from app.models.evidence import EvidenceItem

class OpenFDALabelProvider:
    def search(self,drug,limit=5):
        p={'search':f'openfda.generic_name:"{drug}"','limit':min(limit,20)}
        if settings.openfda_api_key:p['api_key']=settings.openfda_api_key
        with httpx.Client(timeout=settings.evidence_timeout_seconds) as c:
            r=c.get('https://api.fda.gov/drug/label.json',params=p)
            if r.status_code==404:return []
            r.raise_for_status(); payload=r.json()
        out=[]
        for i,record in enumerate(payload.get('results',[])):
            of=record.get('openfda',{}); generic=', '.join(of.get('generic_name',[])[:3]); brand=', '.join(of.get('brand_name',[])[:3]); chunks=[]
            for f in ('boxed_warning','warnings','contraindications','drug_interactions','adverse_reactions','indications_and_usage','dosage_and_administration'):
                v=record.get(f)
                if isinstance(v,list):chunks.append(f'{f}: {" ".join(v)}')
                elif isinstance(v,str):chunks.append(f'{f}: {v}')
            sid=record.get('id') or record.get('set_id') or str(i)
            out.append(EvidenceItem(id=f'openfda:{sid}',title=f'FDA drug label: {generic or brand or drug}',source_type='regulatory',publisher='U.S. FDA / openFDA',url='https://open.fda.gov/apis/drug/label/',passage='\n'.join(chunks)[:20000],source_authority=.92))
        return out
