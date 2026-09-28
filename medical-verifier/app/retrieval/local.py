import sqlite3
from pathlib import Path

from app.config import settings
from app.models.evidence import EvidenceItem
from app.retrieval.sources import EvidenceProvider

class LocalEvidenceProvider(EvidenceProvider):
    name = "local"

    def search(self, claim: str, concepts: list[str], limit: int) -> list[EvidenceItem]:
        db = Path(settings.database_path)
        if not db.exists():
            return []

        conn = sqlite3.connect(db)
        try:
            rows = conn.execute(
                "SELECT id,title,source_type,publisher,url,passage,"
                "source_family,canonical_id,source_snapshot_sha256,"
                "passage_sha256,document_version,study_family_id "
                "FROM evidence ORDER BY created_at DESC LIMIT ?",
                (limit,),
            ).fetchall()
        finally:
            conn.close()

        terms = {x.lower() for x in claim.split() if len(x) > 4}
        results = []
        for row in rows:
            text = f"{row[1]} {row[5]}".lower()
            overlap = sum(1 for term in terms if term in text)
            if not overlap:
                continue

            results.append(
                EvidenceItem(
                    id=row[0],
                    canonical_id=row[7],
                    title=row[1],
                    source_type=row[2],
                    publisher=row[3],
                    url=row[4],
                    source_locator=row[4],
                    passage=row[5],
                    source_family=row[6] or "",
                    source_snapshot_sha256=row[8],
                    passage_sha256=row[9],
                    document_version=row[10],
                    study_family_id=row[11],
                    independence_group=row[11] or row[7] or "",
                    source_authority=0.40,
                    retrieval_score=float(overlap),
                )
            )
        return results
