from app.models.evidence import EvidenceItem
from app.verification.evidence_model import check_entailment
from app.verification.reliability import aggregate


def _item(item_id, publisher, family="trial"):
    item = EvidenceItem(
        id=item_id,
        title="Statins increase diabetes risk",
        source_type=family,
        publisher=publisher,
        passage="Statins increase diabetes risk in adults.",
        source_family="primary_literature",
        independence_group=f"primary_literature:{publisher}",
        quality_score=0.9,
    )
    item.structured_entailment = check_entailment(
        "Statins increase diabetes risk in adults.",
        item.passage,
    )
    item.supports = item.structured_entailment["relation"] == "entailment"
    return item


def test_same_publisher_does_not_create_independent_support():
    a = _item("1", "Publisher A")
    b = _item("2", "Publisher A")
    agg = aggregate([a, b])
    assert agg["independent_support_groups"] == 1


def test_independent_publishers_create_independent_support():
    a = _item("1", "Publisher A")
    b = _item("2", "Publisher B")
    agg = aggregate([a, b])
    assert agg["independent_support_groups"] == 2


def test_structured_conflict_is_visible_to_aggregator():
    support = _item("1", "Publisher A")
    contradiction = EvidenceItem(
        id="2",
        title="Statins decrease diabetes risk",
        source_type="trial",
        publisher="Publisher B",
        passage="Statins decrease diabetes risk in adults.",
        source_family="primary_literature",
        independence_group="primary_literature:Publisher B",
        quality_score=0.9,
    )
    contradiction.structured_entailment = check_entailment(
        "Statins increase diabetes risk in adults.",
        contradiction.passage,
    )
    contradiction.supports = False
    agg = aggregate([support, contradiction])
    assert agg["conflict"] is True
    assert agg["independent_contradiction_groups"] == 1
