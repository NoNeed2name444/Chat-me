from api.schemas.evidence import EvidenceItem
from agents.specialists.verification_agent.semantic_guard import semantic_guard

def item(passage):
    return EvidenceItem(
        id="x",
        title="Study",
        source_type="trial",
        publisher="test",
        passage=passage,
        source_authority=0.8,
        quality_score=0.8,
    )

def test_word_boundary_regex_now_matches():
    evidence = item("Drug X increases bleeding.")
    evidence.supports = True
    evidence, warnings = semantic_guard(
        evidence,
        "Drug X does not increase bleeding.",
    )
    assert "negation_scope_mismatch" in warnings
    assert evidence.supports is None

def test_same_daily_total_of_another_drug_is_not_excused():
    evidence = item("Aspirin 500 mg is given twice daily.")
    evidence.supports = True
    evidence, warnings = semantic_guard(
        evidence,
        "Warfarin 1000 mg is given daily.",
    )
    assert "dose_or_unit_not_matched" in warnings
    assert evidence.supports is None

def test_same_daily_total_of_the_same_drug_is_excused():
    evidence = item("Aspirin 500 mg is given twice daily.")
    evidence, warnings = semantic_guard(
        evidence,
        "Aspirin 1000 mg is given daily.",
    )
    assert "dose_or_unit_not_matched" not in warnings
    assert "dose_frequency_mismatch" not in warnings
