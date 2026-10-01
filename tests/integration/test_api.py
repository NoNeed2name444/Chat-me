from fastapi.testclient import TestClient
from api.main import app

c=TestClient(app)

def test_health():
    r=c.get('/health'); assert r.status_code==200

def test_critical():
    r=c.post('/v1/verify',json={'claim':'What should I do after an overdose?','sources':['pubmed']})
    assert r.status_code==200 and r.json()['verdict']=='SAFETY_ESCALATION'
