from types import SimpleNamespace
from app.verification.contradiction import NEGATION
from app.verification.semantic_guard import semantic_guard
from app.verification.lineage import canonical_url, identifiers, evidence_identity

def item(title="", passage="", score=1.0):
    return SimpleNamespace(title=title, passage=passage, quality_score=score, supports=True)

def test_negation_regex_uses_word_boundaries():
    assert NEGATION.search("There is no benefit")
    assert not NEGATION.search("innovation")

def test_polarity_uses_phrase_boundaries():
    x,w=semantic_guard(item(passage="The dose was higher."),"The dose was lower.")
    assert "directional_polarity_mismatch" in w and x.supports is False

def test_causal_vs_association_is_not_entailment():
    x,w=semantic_guard(item(passage="The exposure was associated with bleeding."),"The exposure causes bleeding.")
    assert "causal_claim_only_associative_evidence" in w and x.supports is None

def test_numeric_mismatch_abstains():
    x,w=semantic_guard(item(passage="Dose 5 mg."),"Dose 0.5 mg.")
    assert "dose_or_unit_not_matched" in w and x.supports is None

def test_lineage_strips_tracking_parameters():
    assert canonical_url("HTTPS://Example.org/paper/?utm_source=x&id=7")=="https://example.org/paper?id=7"

def test_identifier_extraction():
    assert identifiers("DOI: 10.1000/ABC-123 PMID: 12345678 NCT01234567")=={"doi":"10.1000/abc-123","pmid":"12345678","nct":"nct01234567"}

def test_identity_prefers_canonical_id():
    assert evidence_identity(canonical_id="PMID:123",url="https://example.org/x")=="pmid:123"
