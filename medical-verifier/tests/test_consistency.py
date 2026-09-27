from datetime import date

from app.models.evidence import EvidenceItem
from app.verification.consistency import guard_specificity

def make_item(passage):
    return EvidenceItem(
        id="x",
        title="Evidence",
        source_type="literature",
        publisher="test",
        publication_date=date.today(),
        passage=passage,
        source_authority=0.8,
    )

def test_numeric_mismatch_is_not_supported():
    item = make_item("Adults had a rate around 5 percent.")
    item.supports = True
    item.quality_score = 0.8
    item, warnings = guard_specificity(item, "The rate is 15 percent.")
    assert item.supports is None
    assert "numeric_claim_not_supported_verbatim" in warnings

def test_population_mismatch_is_not_supported():
    item = make_item("Studies in adults found an association.")
    item.supports = True
    item.quality_score = 0.8
    item, warnings = guard_specificity(item, "This is safe in pregnancy.")
    assert item.supports is None
    assert "population_qualifier_not_matched" in warnings
