import json
from datetime import datetime

import httpx

from app.config import settings
from app.models.evidence import EvidenceItem
from app.verification.citation_integrity import bind_evidence

class OpenFDALabelProvider:
    source_family = "regulatory"

    def search(self, drug, limit=5):
        drug = drug.strip()
        if not drug:
            return []

        params = {
            "search": f'openfda.generic_name:"{drug}"',
            "limit": min(limit, 20),
        }

        if settings.openfda_api_key:
            params["api_key"] = settings.openfda_api_key

        with httpx.Client(timeout=settings.evidence_timeout_seconds) as client:
            response = client.get(
                "https://api.fda.gov/drug/label.json",
                params=params,
            )
            if response.status_code == 404:
                return []

            response.raise_for_status()
            payload = response.json()
            source_locator = str(response.url)

        out = []

        for index, record in enumerate(payload.get("results", [])):
            of = record.get("openfda", {})
            generic = ", ".join(of.get("generic_name", [])[:3])
            brand = ", ".join(of.get("brand_name", [])[:3])

            chunks = []
            for field in (
                "boxed_warning",
                "warnings",
                "contraindications",
                "drug_interactions",
                "adverse_reactions",
                "indications_and_usage",
                "dosage_and_administration",
                "pregnancy",
                "use_in_specific_populations",
                "recent_major_changes",
            ):
                value = record.get(field)
                if isinstance(value, list):
                    chunks.append(f"{field}: {' '.join(value)}")
                elif isinstance(value, str):
                    chunks.append(f"{field}: {value}")

            passage = "\n".join(chunks)[:30000]

            effective_date = None
            raw_effective = record.get("effective_time")

            if raw_effective:
                try:
                    effective_date = datetime.strptime(
                        str(raw_effective)[:8],
                        "%Y%m%d",
                    ).date()
                except ValueError:
                    pass

            spl_id = (
                record.get("id")
                or record.get("set_id")
                or (of.get("spl_id") or [None])[0]
                or str(index)
            )

            set_id = (
                record.get("set_id")
                or (of.get("spl_set_id") or [None])[0]
            )

            item = EvidenceItem(
                id=f"openfda:{spl_id}",
                canonical_id=set_id or str(spl_id),
                title=f"FDA drug label: {generic or brand or drug}",
                source_type="regulatory",
                publisher="U.S. FDA / openFDA",
                effective_date=effective_date,
                url="https://open.fda.gov/apis/drug/label/",
                source_locator=source_locator,
                passage=passage,
                source_family=self.source_family,
                independence_group=f"openfda:{set_id or spl_id}",
                source_authority=0.92,
                retrieval_score=1.0,
                document_version=(
                    effective_date.isoformat()
                    if effective_date
                    else None
                ),
            )

            bind_evidence(
                item,
                raw_source_text=json.dumps(
                    record,
                    sort_keys=True,
                    ensure_ascii=False,
                ),
                source_passage_text=passage,
                source_locator=source_locator,
                document_version=item.document_version,
            )

            out.append(item)

        return out
