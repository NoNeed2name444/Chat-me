import hashlib
import json
import sqlite3
from pathlib import Path
from uuid import uuid4

from app.config import settings

def _connect():
    path = Path(settings.database_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)

    conn.execute("""
        CREATE TABLE IF NOT EXISTS verification_audit (
            verification_id TEXT PRIMARY KEY,
            verdict TEXT,
            risk_level TEXT,
            result_json TEXT,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
    """)

    conn.execute("""
        CREATE TABLE IF NOT EXISTS evidence (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            source_type TEXT NOT NULL,
            publisher TEXT NOT NULL,
            url TEXT,
            passage TEXT NOT NULL,
            source_family TEXT,
            canonical_id TEXT,
            source_snapshot_sha256 TEXT,
            passage_sha256 TEXT,
            document_version TEXT,
            study_family_id TEXT,
            source_authority REAL DEFAULT 0.4,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
    """)

    conn.commit()
    return conn

def _sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def store_evidence(
    *,
    title,
    passage,
    source_type,
    publisher,
    url=None,
    canonical_id=None,
    document_version=None,
    study_family_id=None,
    source_authority=0.40,
):
    evidence_id = f"local:{uuid4()}"
    passage_hash = _sha256(passage)

    conn = _connect()
    try:
        conn.execute(
            "INSERT INTO evidence "
            "(id,title,source_type,publisher,url,passage,source_family,"
            "canonical_id,source_snapshot_sha256,passage_sha256,"
            "document_version,study_family_id,source_authority) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                evidence_id,
                title,
                source_type,
                publisher,
                url,
                passage,
                source_type,
                canonical_id,
                passage_hash,
                passage_hash,
                document_version,
                study_family_id,
                source_authority,
            ),
        )
        conn.commit()
    finally:
        conn.close()

    return evidence_id

def store_verification(result):
    conn = _connect()
    try:
        conn.execute(
            "INSERT OR REPLACE INTO verification_audit "
            "(verification_id,verdict,risk_level,result_json) VALUES (?,?,?,?)",
            (
                result.verification_id,
                result.verdict,
                result.risk_level,
                json.dumps(result.model_dump(mode="json")),
            ),
        )
        conn.commit()
    finally:
        conn.close()
