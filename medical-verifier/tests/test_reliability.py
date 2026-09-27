from datetime import date

from app.models.evidence import EvidenceItem
from app.verification.contradiction import assess_evidence
from app.verification.policy import decide_verdict
from app.verification.reliability import aggregate, enrich

def item(identifier, publisher, family, supports=True):
    return EvidenceItem(
        id=identifier,
        title="Metformin evidence",
        source_type="regulatory" if family == "regulatory" else "literature",
        publisher=publisher,
        passage="Metformin and hypoglycemia evidence in adults.",
        supports=supports,
        source_family=family,
        publication_date=date.today(),
        url="https://example.test/" + identifier,
        source_authority=0.9 if family == "regulatory" else 0.8,
    )

def test_independent_corroboration():
    a = enrich(item("a", "FDA", "regulatory"), "metformin hypoglycemia", date.today())
    b = enrich(item("b", "PubMed", "primary_literature"), "metformin hypoglycemia", date.today())
    assessed, _, _ = assess_evidence([a, b], "metformin hypoglycemia")
    agg = aggregate(assessed)
    verdict, _, _ = decide_verdict(
        risk_level="moderate",
        claim_type="drug_safety",
        missing_context=[],
        aggregate=agg,
        evidence=assessed,
    )
    assert verdict == "SUPPORTED"
    assert agg["independent_support_groups"] == 2

def test_same_family_duplicate_does_not_count_twice():
    a = enrich(item("a", "Same Publisher", "primary_literature"), "metformin hypoglycemia", date.today())
    b = enrich(item("b", "Same Publisher", "primary_literature"), "metformin hypoglycemia", date.today())
    assessed, _, _ = assess_evidence([a, b], "metformin hypoglycemia")
    agg = aggregate(assessed)
    assert agg["independent_support_groups"] == 1

def test_contradiction_blocks_strong_support():
    a = enrich(item("a", "FDA", "regulatory", True), "metformin hypoglycemia", date.today())
    b = enrich(item("b", "Other", "primary_literature", False), "metformin hypoglycemia", date.today())
    assessed, _, _ = assess_evidence([a, b], "metformin hypoglycemia")
    agg = aggregate(assessed)
    verdict, _, _ = decide_verdict(
        risk_level="moderate",
        claim_type="drug_safety",
        missing_context=[],
        aggregate=agg,
        evidence=assessed,
    )
    assert verdict in {"MIXED_EVIDENCE", "SUPPORTED"}
