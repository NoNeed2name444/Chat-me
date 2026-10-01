from evals.suites.metamorphic import (
    duplicate_evidence,
    equivalent_whitespace_variants,
    normalize_for_metamorphic,
    paraphrase_like_variants,
)

def test_whitespace_variants_normalize_equally():
    base = normalize_for_metamorphic("Drug X increases bleeding")
    for variant in equivalent_whitespace_variants("Drug X increases bleeding"):
        assert normalize_for_metamorphic(variant) == base

def test_duplicate_evidence_is_explicitly_detectable():
    items = [{"id": "same"}]
    assert duplicate_evidence(items)[0]["id"] == duplicate_evidence(items)[1]["id"]

def test_paraphrase_variants_are_generated():
    variants = paraphrase_like_variants("Drug X does not increase risk")
    assert len(variants) >= 2
