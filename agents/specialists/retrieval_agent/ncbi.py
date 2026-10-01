import time
import xml.etree.ElementTree as ET
from datetime import date

import httpx

from api.config import settings
from api.schemas.evidence import EvidenceItem

_LAST_REQUEST = 0.0
_MIN_INTERVAL = 0.34

def _throttle():
    global _LAST_REQUEST
    now = time.monotonic()
    wait = _MIN_INTERVAL - (now - _LAST_REQUEST)
    if wait > 0:
        time.sleep(wait)
    _LAST_REQUEST = time.monotonic()

class PubMedProvider:
    source_family = "primary_literature"

    def search(self, claim, limit=5):
        params = {
            "db": "pubmed",
            "term": claim,
            "retmax": min(limit, 20),
            "retmode": "xml",
            "tool": settings.ncbi_tool,
            "email": settings.ncbi_email,
        }
        if settings.ncbi_api_key:
            params["api_key"] = settings.ncbi_api_key

        with httpx.Client(timeout=settings.evidence_timeout_seconds) as client:
            _throttle()
            response = client.get(
                "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi",
                params=params,
            )
            response.raise_for_status()

            root = ET.fromstring(response.text)
            ids = [
                x.text for x in root.findall(".//IdList/Id")
                if x.text
            ]
            if not ids:
                return []

            _throttle()
            response = client.get(
                "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi",
                params={**params, "id": ",".join(ids)},
            )
            response.raise_for_status()

        out = []
        for docsum in ET.fromstring(response.text).findall(".//DocumentSummary"):
            pmid = docsum.attrib.get("uid", "")
            pubdate_text = docsum.findtext("PubDate") or ""
            publication_date = None

            for token in pubdate_text.replace("/", " ").split():
                if token.isdigit() and len(token) == 4:
                    try:
                        publication_date = date(int(token), 1, 1)
                        break
                    except ValueError:
                        pass

            out.append(EvidenceItem(
                id=f"pubmed:{pmid}",
                canonical_id=f"pubmed:{pmid}",
                title=docsum.findtext("Title") or f"PubMed {pmid}",
                source_type="literature",
                publisher="NCBI/PubMed",
                publication_date=publication_date,
                url=f"https://pubmed.ncbi.nlm.nih.gov/{pmid}/",
                passage="PubMed metadata record; consult source URL for the publication.",
                source_family=self.source_family,
                independence_group=f"pubmed:{pmid}",
                source_authority=0.75,
                retrieval_score=1.0,
            ))

        return out
