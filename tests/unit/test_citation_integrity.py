from api.schemas.evidence import EvidenceItem
from agents.specialists.retrieval_agent.citation_integrity import bind_evidence, verify_citation

def make_item():
    return EvidenceItem(
        id="x",
        title="Evidence",
        source_type="reference",
        publisher="test",
        passage="Drug X reduces risk.",
        url="https://example.test/source",
    )

def test_exact_passage_binding():
    item = make_item()
    bind_evidence(
        item,
        raw_source_text="Header\nDrug X reduces risk.\nFooter",
        source_passage_text="Drug X reduces risk.",
    )
    item.supports = True
    item, warnings = verify_citation(
        item,
        "Header Drug X reduces risk. Footer",
    )
    assert "citation_passage_not_found_in_source" not in warnings

def test_tampered_passage_is_detected():
    item = make_item()
    bind_evidence(
        item,
        raw_source_text="Drug X reduces risk.",
        source_passage_text="Drug X reduces risk.",
    )
    item.passage = "Drug X eliminates risk."
    item.supports = True
    item, warnings = verify_citation(
        item,
        "Drug X reduces risk.",
    )
    assert "passage_hash_mismatch" in warnings or "citation_passage_not_found_in_source" in warnings

def test_unbound_external_evidence_cannot_support():
    item = make_item()
    item.supports = True
    item, warnings = verify_citation(item)
    assert "source_snapshot_unverified" in warnings
    assert item.supports is None
