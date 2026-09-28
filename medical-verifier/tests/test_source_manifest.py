from app.models.evidence import EvidenceItem
from app.verification.source_manifest import (
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
