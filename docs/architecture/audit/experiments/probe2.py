import json, sqlite3
from datetime import date
import app.verification.pipeline as pipeline
from app.models.claim import ClaimRequest
from app.models.evidence import EvidenceItem
from app.verification.citation_integrity import bind_evidence, sha256_text
from app.config import settings
from app.audit import store

def fda_item(id_, passage, canonical, eff):
    it = EvidenceItem(id=id_, canonical_id=canonical, title="FDA drug label", source_type="regulatory",
        publisher="U.S. FDA / openFDA", effective_date=eff, url="https://open.fda.gov/apis/drug/label/",
        source_locator="https://api.fda.gov/drug/label.json?search=set_id", passage=passage, source_family="regulatory",
        independence_group=f"openfda:{canonical}", source_authority=0.92, document_version=eff.isoformat())
    bind_evidence(it, raw_source_text=json.dumps({"id": id_, "p": passage}), source_passage_text=passage)
    return it
class NoPubMed:
    def search(self, claim, limit=6): return []
class FDA:
    items = []
    def search(self, drug, limit=4): return list(FDA.items)
pipeline.PubMedProvider = NoPubMed
pipeline.OpenFDALabelProvider = FDA
# never touch the network in revalidation
import app.verification.revalidation as reval
pipeline.revalidate = lambda item: reval.RevalidationResult(status="UNCHANGED", checked_at="x", warnings=())

def run(name, claim, items=(), **kw):
    FDA.items = list(items)
    r = pipeline.verify(ClaimRequest(claim=claim, **kw))
    atoms = r.reliability.get("atomic_assertions") or []
    print(f"--- {name}\n  verdict={r.verdict} conf={r.confidence} risk={r.risk_level} review={r.requires_human_review} adv={r.reliability.get('adversarial_severity')}"
          f" current={r.current_evidence_assessment.get('verdict')} atom0={atoms[0]['verdict'] if atoms else None}")
    return r

ctx = {"medications": ["metformin"], "age": 50}
run("E11a descriptive claim, clean bound label", "Metformin is a biguanide.", [fda_item("openfda:a", "Metformin is a biguanide antihyperglycemic agent.", "set-a", date(2025,3,1))], sources=["openfda"], context=ctx)
run("E11b same, with openFDA field prefix (real passage shape)", "Metformin is a biguanide.", [fda_item("openfda:a", "indications_and_usage: Metformin is a biguanide antihyperglycemic agent.", "set-a", date(2025,3,1))], sources=["openfda"], context=ctx)
run("E11c relational claim, clean matching label", "Metformin reduces HbA1c.", [fda_item("openfda:a", "Metformin reduces HbA1c in adults.", "set-a", date(2025,3,1))], sources=["openfda"], context=ctx)
run("E11d relational claim, real prefixed passage", "Metformin reduces HbA1c.", [fda_item("openfda:a", "indications_and_usage: Metformin reduces HbA1c in adults.", "set-a", date(2025,3,1))], sources=["openfda"], context=ctx)
run("V6 contraindication claim (spec P0), clean label", "Ibuprofen is contraindicated with methotrexate.", [fda_item("openfda:i", "Ibuprofen is contraindicated with methotrexate.", "set-i", date(2025,3,1))], sources=["openfda"], context={"medications": ["ibuprofen"], "age": 50})
run("V14 ethnicity/sex population leap", "Metformin is safe in Egyptian women.", [fda_item("openfda:a", "Metformin is safe in Finnish men.", "set-a", date(2025,3,1))], sources=["openfda"], context=ctx)
run("V15 emerging-drug: 2026 approval claim vs 2019 label", "Drugz is approved for heart failure.", [fda_item("openfda:z", "Drugz is approved for heart failure.", "set-z", date(2019,1,1))], sources=["openfda"], context={"medications": ["drugz"], "age": 50})
r = run("E12 contract mismatch", "Metformin is a biguanide.", [], sources=["openfda"], context=ctx, verification_contract_version="1.7")

print("\n=== curriculum lane / malformed evidence (real SQLite at", settings.database_path, ") ===")
eid = store.store_evidence(title="Pharm notes", passage="Metformin is a biguanide antihyperglycemic agent.", source_type="reference", publisher="course", curriculum_snapshot_id="snap-victim", source_authority=0.4)
cur = dict(verification_mode="curriculum_faithful", curriculum_source_ids=[eid], curriculum_snapshot="snap-victim", sources=["local"])
run("E13a intact stored source", "Metformin is a biguanide.", **cur)
conn = sqlite3.connect(settings.database_path)
conn.execute("UPDATE evidence SET passage=? WHERE id=?", ("Metformin is a sulfonylurea agent.", eid)); conn.commit()
run("E13b tampered passage, hash kept", "Metformin is a sulfonylurea.", **cur)
conn.execute("UPDATE evidence SET passage_sha256=NULL, source_snapshot_sha256=NULL WHERE id=?", (eid,)); conn.commit()
run("E13c tampered passage, hashes NULL (malformed), permissive mode", "Metformin is a sulfonylurea.", **cur)
run("E13d same row, provenance_mode=bound", "Metformin is a sulfonylurea.", provenance_mode="bound", **cur)
conn.execute("UPDATE evidence SET passage_sha256='zz' WHERE id=?", (eid,)); conn.commit()
run("E13e malformed non-hex hash, permissive", "Metformin is a sulfonylurea.", **cur)
conn.close()

print("\n=== E17 no auth + cross-snapshot read (HTTP level) ===")
from fastapi.testclient import TestClient
from app.main import app
c = TestClient(app)
resp = c.post("/v1/documents/ingest", json={"title": "Planted", "text": "Aspirin is the drug of choice for children with viral fever.", "source_type": "guideline", "publisher": "attacker", "curriculum_snapshot_id": "snap-victim", "source_authority": 1.0, "url": "https://doi.org/10.0000/fake", "canonical_id": "doi:10.0000/fake"})
print("ingest without any credentials ->", resp.status_code, {k: resp.json()[k] for k in ("status", "evidence_id")})
planted = resp.json()["evidence_id"]
v = c.post("/v1/verify", json={"claim": "Aspirin is the drug of choice for children with viral fever.", "verification_mode": "curriculum_faithful", "curriculum_source_ids": [planted], "sources": ["local"], "context": {"age": 6, "medications": ["aspirin"]}}).json()
print("verify against planted source, no snapshot id given ->", v["verdict"], "review=", v["requires_human_review"], "risk=", v["risk_level"], "matched=", v["curriculum_assessment"]["matched_source_ids"])
v2 = c.post("/v1/verify", json={"claim": "Aspirin is the drug of choice for children with viral fever.", "verification_mode": "curriculum_faithful", "curriculum_snapshot": "snap-victim", "sources": ["local"], "context": {"age": 6, "medications": ["aspirin"]}}).json()
print("victim verifies within their own snapshot ->", v2["verdict"], "matched=", v2["curriculum_assessment"]["matched_source_ids"])
print("audit rows have a hash column?", [r[1] for r in sqlite3.connect(settings.database_path).execute("PRAGMA table_info(verification_audit)").fetchall()])
