import json,sqlite3
from pathlib import Path
from uuid import uuid4
from app.config import settings

def _connect():
    p=Path(settings.database_path); p.parent.mkdir(parents=True,exist_ok=True)
    c=sqlite3.connect(p)
    c.execute("CREATE TABLE IF NOT EXISTS verification_audit (verification_id TEXT PRIMARY KEY, verdict TEXT, risk_level TEXT, result_json TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP)")
    c.commit(); return c

def store_verification(result):
    c=_connect()
    try:
        c.execute("INSERT OR REPLACE INTO verification_audit (verification_id,verdict,risk_level,result_json) VALUES (?,?,?,?)",(result.verification_id,result.verdict,result.risk_level,json.dumps(result.model_dump(mode='json')))); c.commit()
    finally: c.close()
