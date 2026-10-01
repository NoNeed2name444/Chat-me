from api.schemas.evidence import EvidenceItem
from agents.specialists.verification_agent.entailment import assess_entailment

def test_unproven_citation_becomes_unknown():
    item = EvidenceItem(
        id="x",
        title="Study",
        source_type="reference",
        publisher="test",
        passage="Drug X reduces risk.",
        url="https://example.test",
        supports=True,
        source_authority=0.8,
    )
    item, result = assess_entailment(item, "Drug X reduces risk.")
    assert result.label == "UNKNOWN"
    assert "source_snapshot_unverified" in result.warnings
