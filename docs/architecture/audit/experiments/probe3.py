import json
from datetime import date
import app.verification.pipeline as pipeline
from app.models.claim import ClaimRequest
from app.models.evidence import EvidenceItem
from app.verification.citation_integrity import bind_evidence
import app.verification.revalidation as reval
def fda_item(id_, passage, canonical, eff):
    it = EvidenceItem(id=id_, canonical_id=canonical, title="FDA drug label", source_type="regulatory", publisher="U.S. FDA / openFDA",
        effective_date=eff, url="https://open.fda.gov/apis/drug/label/", source_locator="x", passage=passage, source_family="regulatory",
        independence_group=f"openfda:{canonical}", source_authority=0.92, document_version=eff.isoformat())
    bind_evidence(it, raw_source_text=json.dumps({"id": id_, "p": passage}), source_passage_text=passage); return it
class NoPubMed:
    def search(self, claim, limit=6): return []
class FDA:
    items = []
    def search(self, drug, limit=4): return list(FDA.items)
pipeline.PubMedProvider = NoPubMed; pipeline.OpenFDALabelProvider = FDA
pipeline.revalidate = lambda item: reval.RevalidationResult(status="UNCHANGED", checked_at="x", warnings=())
def run(name, claim, items, **kw):
    FDA.items = list(items); r = pipeline.verify(ClaimRequest(claim=claim, **kw))
    print(f"--- {name}\n  verdict={r.verdict} risk={r.risk_level} review={r.requires_human_review} adv={r.reliability.get('adversarial_severity')} missing={r.missing_context}\n  flags={[l for l in r.limitations if ':' in l]}\n  reasons={r.decision_reasons}")
ev = [fda_item("openfda:i", "Ibuprofen is contraindicated with methotrexate.", "set-i", date(2025,3,1))]
run("V6 medications alias", "Ibuprofen is contraindicated with methotrexate.", ev, sources=["openfda"], context={"medications": ["ibuprofen"], "age": 50})
run("V6 current_medications key", "Ibuprofen is contraindicated with methotrexate.", ev, sources=["openfda"], context={"current_medications": ["ibuprofen"], "age": 50})
run("V6 no context at all", "Ibuprofen is contraindicated with methotrexate.", ev, sources=["openfda"])
ev2 = [fda_item("openfda:w", "Warfarin should not be used with aspirin.", "set-w", date(2025,3,1))]
run("V6b avoid-combination wording", "Warfarin should not be used with aspirin.", ev2, sources=["openfda"], context={"current_medications": ["warfarin"], "age": 50})
