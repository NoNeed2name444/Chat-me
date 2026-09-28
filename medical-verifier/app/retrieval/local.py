import json
import sqlite3
from datetime import date
from pathlib import Path

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
                "source_date,curriculum_snapshot_id,source_authority,"
                "page_number,section,block_type,block_index,related_block_ids,"
                "language,precedence_group,precedence_rank,extraction_quality,"
                "extraction_warnings "
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
            if row[13]:
                try:
                    source_date = date.fromisoformat(row[13])
                except ValueError:
                    pass

            try:
                extraction_warnings = json.loads(
                    row[25] or "[]"
                )
            except (TypeError, json.JSONDecodeError):
                extraction_warnings = [
                    "stored_extraction_warnings_invalid"
                ]

            try:
                related_block_ids = json.loads(
                    row[20] or "[]"
                )
                if not isinstance(related_block_ids, list):
                    raise ValueError("related_block_ids_not_list")
            except (TypeError, json.JSONDecodeError, ValueError):
                related_block_ids = []
                extraction_warnings.append(
                    "stored_related_block_ids_invalid"
                )

            results.append(
                EvidenceItem(
                    id=row[0],
                    title=row[1],
                    source_type=row[2],
                    publisher=row[3],
                    url=row[4],
                    source_locator=row[5],
                    passage=row[6],
                    source_family=row[7] or "",
                    canonical_id=row[8],
                    source_snapshot_sha256=row[9],
                    passage_sha256=row[10],
                    document_version=row[11],
                    study_family_id=row[12],
                    source_date=source_date,
                    curriculum_snapshot_id=row[14],
                    independence_group=row[12] or row[8] or row[0],
                    source_authority=row[15] or 0.40,
                    page_number=row[16],
                    section=row[17],
                    block_type=row[18] or "text",
                    block_index=row[19],
                    related_block_ids=related_block_ids,
                    language=row[21] or "auto",
                    precedence_group=row[22],
                    precedence_rank=row[23] or 0,
                    extraction_quality=(
                        row[24]
                        if row[24] is not None
                        else 1.0
                    ),
                    extraction_warnings=extraction_warnings,
                    retrieval_score=float(overlap),
                )
            )

        return results
