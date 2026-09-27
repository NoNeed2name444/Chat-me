from app.models.evidence import EvidenceItem
from app.verification.contradiction import assess_evidence
from app.verification.semantic_guard import semantic_guard


def evidence(passage):
    return EvidenceItem(id="regex", title="Study", source_type="trial", publisher="P", passage=passage, url="https://example.test")


def test_word_boundaries_do_not_match_substrings():
    item = evidence("The study reports a notable improvement with 500 mg daily.")
    item.supports = True
    item.quality_score = 0.9
    _, warnings = semantic_guard(item, "The study reports a notable improvement with 500 mg daily.")
    assert "negation_scope_mismatch" not in warnings
    assert "dose_or_unit_not_matched" not in warnings


def test_quantity_regex_matches_real_units():
    item = evidence("The dose was 500 mg daily.")
    item.supports = True
    item.quality_score = 0.9
    _, warnings = semantic_guard(item, "The dose was 500 mg daily.")
    assert "dose_or_unit_not_matched" not in warnings


def test_quantity_regex_rejects_wrong_unit():
    item = evidence("The dose was 500 mg daily.")
    item.supports = True
    item.quality_score = 0.9
    _, warnings = semantic_guard(item, "The dose was 500 mcg daily.")
    assert "dose_or_unit_not_matched" in warnings


def test_percentage_regex_rejects_wrong_value():
    item = evidence("Risk increased by 4 percent.")
    item.supports = True
    item.quality_score = 0.9
    _, warnings = semantic_guard(item, "Risk increased by 40 percent.")
    assert "percentage_not_matched" in warnings


def test_contradiction_regex_detects_negation():
    item = evidence("Drug X increases bleeding.")
    classified, contradictions, _ = assess_evidence([item], "Drug X does not increase bleeding.")
    assert classified[0].supports is False
    assert contradictions == classified
