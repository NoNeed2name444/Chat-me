from app.models.evidence import EvidenceItem
from app.verification.correlation import collapse_correlated_groups

def test_same_study_family_is_correlated():
    a = EvidenceItem(
        id="trial-1", title="Trial", source_type="trial",
        publisher="A", passage="evidence", study_family_id="F1",
        independence_group="", source_authority=0.8,
    )
    b = EvidenceItem(
        id="review-1", title="Review", source_type="systematic_review",
        publisher="B", passage="evidence", study_family_id="F1",
        independence_group="", source_authority=0.9,
    )
    collapse_correlated_groups([a, b])
    assert a.independence_group.startswith("correlated:")
    assert b.independence_group.startswith("correlated:")
