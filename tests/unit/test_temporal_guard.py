from datetime import date

from api.schemas.evidence import EvidenceItem
from agents.specialists.verification_agent.temporal_guard import guard_temporal_specificity

def test_current_claim_requires_recent_evidence():
    item = EvidenceItem(
        id="x",
        title="Study",
        source_type="trial",
        publisher="test",
        publication_date=date(2022, 1, 1),
        passage="Drug X reduces risk.",
        source_authority=0.8,
        quality_score=0.8,
    )
    item.supports = True
    item, warnings = guard_temporal_specificity(
        item,
        "Drug X currently reduces risk.",
        date(2026, 9, 28),
    )
    assert "current_claim_relies_on_old_evidence" in warnings
    assert item.supports is None
