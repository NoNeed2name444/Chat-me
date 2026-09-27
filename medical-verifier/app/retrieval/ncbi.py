import httpx,xml.etree.ElementTree as ET
from app.config import settings
from app.models.evidence import EvidenceItem

class PubMedProvider:
    def search(self,claim,limit=5):
        p={'db':'pubmed','term':claim,'retmax':min(limit,20),'retmode':'xml','tool':settings.ncbi_tool,'email':settings.ncbi_email}
        if settings.ncbi_api_key:p['api_key']=settings.ncbi_api_key
        with httpx.Client(timeout=settings.evidence_timeout_seconds) as c:
            r=c.get('https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi',params=p); r.raise_for_status(); ids=[x.text for x in ET.fromstring(r.text).findall('.//IdList/Id') if x.text]
            if not ids:return []
            r=c.get('https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi',params={**p,'id':','.join(ids)}); r.raise_for_status()
        out=[]
        for d in ET.fromstring(r.text).findall('.//DocumentSummary'):
            pmid=d.attrib.get('uid',''); out.append(EvidenceItem(id=f'pubmed:{pmid}',title=d.findtext('Title') or f'PubMed {pmid}',source_type='literature',publisher='NCBI/PubMed',url=f'https://pubmed.ncbi.nlm.nih.gov/{pmid}/',passage='PubMed metadata record; consult source URL.',source_authority=.75))
        return out
