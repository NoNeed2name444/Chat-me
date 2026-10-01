from evals.suites.benchmark_manifest import BenchmarkManifest
from agents.specialists.verification_agent.entity_normalization import entities_equivalent, normalize_entity


def test_explicit_alias_is_equivalent():
    assert entities_equivalent("paracetamol", "acetaminophen")


def test_unknown_entity_is_not_fuzzily_equivalent():
    assert not entities_equivalent("acetaminophen", "acetaminophenn")


def test_manifest_digest_is_deterministic():
    manifest = BenchmarkManifest("1.6", "demo", "snapshot-1", ("a", "b"))
    assert manifest.digest() == manifest.digest()
    assert manifest.to_dict()["snapshot_sha256"] == manifest.digest()


def test_manifest_parent_hash_is_bound():
    first = BenchmarkManifest("1.6", "demo", "snapshot-1", ("a",))
    second = BenchmarkManifest(
        "1.6", "demo", "snapshot-2", ("b",), first.digest()
    )
    assert second.to_dict()["parent_snapshot_sha256"] == first.digest()
    assert second.digest() != first.digest()


def test_unrecognized_entity_remains_unknown():
    entity = normalize_entity("some unknown drug")
    assert entity.recognized is False
    assert entity.canonical is None
