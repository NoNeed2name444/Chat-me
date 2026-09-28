import sqlite3
from pathlib import Path
from datetime import date

from app.config import settings
from app.models.evidence import EvidenceItem
from app.retrieval.sources import EvidenceProvider

class LocalEvidenceProvider(EvidenceProvider):
    name = "local"

    def search(
        self,
        claim: str,
        concepts: list[str],
        limit: int,
        source_ids: list[str] | None = None,
        curriculum_snapshot_id: str | None = None,
    ) -> list[EvidenceItem]:
        db = Path(settings.database_path)
        if not db.exists():
            return []

        conn = sqlite3.connect(db)

        try:
            query = (
                "SELECT id,title,source_type,publisher,url,source_locator,passage,"
                "source_family,canonical_id,source_snapshot_sha256,"
                "passage_sha256,document_version,study_family_id,"
                "source_date,curriculum_snapshot_id,source_authority "
                "FROM evidence"
            )

            where = []
            params = []

            if source_ids:
                placeholders = ",".join("?" for _ in source_ids)
                where.append(f"id IN ({placeholders})")
                params.extend(source_ids)

            if curriculum_snapshot_id:
                where.append("curriculum_snapshot_id = ?")
                params.append(curriculum_snapshot_id)

            if where:
                query += " WHERE " + " AND ".join(where)

            query += " ORDER BY created_at DESC LIMIT ?"
            params.append(limit)

            rows = conn.execute(query, tuple(params)).fetchall()

        finally:
            conn.close()

        terms = {
            x.lower()
            for x in claim.split()
            if len(x) > 4
        }

        results = []

        for row in rows:
            text = f"{row[1]} {row[6]}".lower()
            overlap = sum(
                1
                for term in terms
                if term in text
            )

            if source_ids is None and overlap == 0:
                continue

            source_date = None
            if row[12]:
                try:
                    source_date = date.fromisoformat(row[13])
                except ValueError:
                    pass

            results.append(
                EvidenceItem(
                    id=row[0],
                    canonical_id=row[7],
                    title=row[1],
                    source_type=row[2],
                    publisher=row[3],
                    url=row[4],
                    source_locator=row[5],
                    passage=row[6],
                    source_family=row[7] or "",
                    source_snapshot_sha256=row[9],
                    passage_sha256=row[10],
                    document_version=row[11],
                    study_family_id=row[12],
                    source_date=source_date,
                    curriculum_snapshot_id=row[14],
                    independence_group=row[12] or row[8] or row[0],
                    source_authority=row[15] or 0.40,
                    retrieval_score=float(overlap),
                )
            )

        return results
