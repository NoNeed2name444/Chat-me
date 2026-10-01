from api.schemas.evidence import EvidenceItem
from governance.audit.source_manifest import (
    build_manifest,
    verify_manifest,
    verify_parent_link,
)

def make_item(item_id, passage):
    return EvidenceItem(
        id=item_id,
        title="Course",
        source_type="reference",
        publisher="course",
        passage=passage,
        source_snapshot_sha256=f"file-{item_id}",
        passage_sha256=f"passage-{item_id}",
        source_locator="page:4",
        page_number=4,
        section="Dosing",
        block_type="table",
        block_index=2,
        related_block_ids=["caption:1"],
        language="es",
        document_version="2025",
        precedence_group="course-guideline",
        precedence_rank=2,
    )

def test_manifest_is_deterministic_and_verifiable():
    items = [
        make_item("b", "Dose"),
        make_item("a", "Dose"),
    ]

    manifest = build_manifest(
        manifest_id="manifest:1",
        evidence_items=items,
    )

    assert verify_manifest(manifest) is True
    assert [
        entry.evidence_id
        for entry in manifest.entries
    ] == ["a", "b"]

def test_manifest_change_breaks_verification():
    manifest = build_manifest(
        manifest_id="manifest:1",
        evidence_items=[make_item("a", "Dose")],
    )

    tampered = manifest.__class__(
        manifest_id=manifest.manifest_id,
        manifest_version=manifest.manifest_version,
        parent_manifest_sha256=manifest.parent_manifest_sha256,
        entries=manifest.entries,
        manifest_sha256="tampered",
    )

    assert verify_manifest(tampered) is False

def test_manifest_parent_chain_is_verified():
    parent = build_manifest(
        manifest_id="manifest:parent",
        evidence_items=[make_item("a", "Dose")],
    )
    child = build_manifest(
        manifest_id="manifest:child",
        evidence_items=[make_item("b", "Dose")],
        parent_manifest_sha256=parent.manifest_sha256,
    )

    assert verify_parent_link(child, parent) is True
    assert verify_parent_link(child, None) is False


def test_manifest_store_requires_existing_parent(monkeypatch, tmp_path):
    from governance.audit import store

    monkeypatch.setattr(
        store.settings,
        "database_path",
        str(tmp_path / "manifest.db"),
    )

    parent = build_manifest(
        manifest_id="manifest:parent",
        evidence_items=[make_item("p", "Dose")],
    )
    child = build_manifest(
        manifest_id="manifest:child",
        evidence_items=[make_item("c", "Dose")],
        parent_manifest_sha256=parent.manifest_sha256,
    )

    try:
        store.store_manifest(child)
    except ValueError as exc:
        assert str(exc) == "manifest_parent_not_found"
    else:
        raise AssertionError("expected parent lineage rejection")

    store.store_manifest(parent)
    store.store_manifest(child)

def test_manifest_entry_contains_structural_provenance():
    manifest = build_manifest(
        manifest_id="manifest:structural",
        evidence_items=[make_item("a", "Dose")],
    )

    entry = manifest.entries[0]
    assert entry.page_number == 4
    assert entry.block_type == "table"
    assert entry.related_block_ids == ("caption:1",)
    assert entry.language == "es"
