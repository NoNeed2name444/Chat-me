from api.schemas.evidence import EvidenceItem
from agents.specialists.retrieval_agent.provenance import apply_temporal_supersession, validate_provenance
from agents.specialists.verification_agent.semantic_guard import semantic_guard


def evidence(passage, source_type="trial", **kwargs):
    return EvidenceItem(id=kwargs.get("id", "e1"), title="Study", source_type=source_type, publisher="P", passage=passage, url="https://example.test", **{k:v for k,v in kwargs.items() if k != "id"})


def test_causal_claim_rejects_association_only():
    item = evidence("Drug X was associated with lower risk in an observational study.")
    item.supports = True
    item.quality_score = 0.9
    item, warnings = semantic_guard(item, "Drug X causes lower risk.")
    assert "causal_claim_only_associative_evidence" in warnings
    assert item.supports is None


def test_negation_mismatch_is_not_support():
    item = evidence("Drug X increases bleeding.")
    item.supports = True
    item.quality_score = 0.9
    item, warnings = semantic_guard(item, "Drug X does not increase bleeding.")
    assert "negation_scope_mismatch" in warnings
    assert item.supports is None


def test_unit_mismatch_is_not_support():
    item = evidence("The dose was 500 mg daily.")
    item.supports = True
    item.quality_score = 0.9
    item, warnings = semantic_guard(item, "The dose was 500 mcg daily.")
    assert "dose_or_unit_not_matched" in warnings
    assert item.supports is None


def test_percentage_mismatch_is_not_support():
    item = evidence("Risk increased by 4 percent.")
    item.supports = True
    item.quality_score = 0.9
    item, warnings = semantic_guard(item, "Risk increased by 40 percent.")
    assert "percentage_not_matched" in warnings
    assert item.supports is None


def test_metadata_only_literature_is_not_entailment():
    item = evidence("PubMed metadata record; consult source URL for the publication.", source_type="literature")
    item, warnings = validate_provenance(item)
    assert "metadata_record_not_clinical_entailment" in warnings
    assert item.supports is None


def test_missing_provenance_is_downgraded():
    item = evidence("Evidence", id="")
    item.url = None
    item, warnings = validate_provenance(item)
    assert "missing_evidence_id" in warnings
    assert "missing_source_url" in warnings
    assert item.supports is None


def test_newer_regulatory_record_penalizes_older_record():
    from datetime import date
    old = evidence("old label", source_type="regulatory", id="old", effective_date=date(2024,1,1))
    new = evidence("new label", source_type="regulatory", id="new", effective_date=date(2026,1,1))
    old.canonical_id = "drug-x-label"
    new.canonical_id = "drug-x-label"
    old.quality_score = new.quality_score = 0.9
    items, warnings = apply_temporal_supersession([old, new])
    assert any(x[0] == "old" for x in warnings)
    assert old.quality_score < new.quality_score
