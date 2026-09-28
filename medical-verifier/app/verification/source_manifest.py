import hashlib
import json
from dataclasses import dataclass
from typing import Any

@dataclass(frozen=True)
class ManifestEntry:
    evidence_id: str
    source_snapshot_sha256: str | None
    passage_sha256: str | None
    source_locator: str | None
    page_number: int | None
    section: str | None
    block_type: str
    block_index: int | None
    related_block_ids: tuple[str, ...]
    language: str
    document_version: str | None
    precedence_group: str | None
    precedence_rank: int

    def canonical(self) -> dict[str, Any]:
        return {
            "evidence_id": self.evidence_id,
            "source_snapshot_sha256": self.source_snapshot_sha256,
            "passage_sha256": self.passage_sha256,
            "source_locator": self.source_locator,
            "page_number": self.page_number,
            "section": self.section,
            "block_type": self.block_type,
            "block_index": self.block_index,
            "related_block_ids": list(self.related_block_ids),
            "language": self.language,
            "document_version": self.document_version,
            "precedence_group": self.precedence_group,
            "precedence_rank": self.precedence_rank,
        }

@dataclass(frozen=True)
class SourceManifest:
    manifest_id: str
    manifest_version: str
    parent_manifest_sha256: str | None
    entries: tuple[ManifestEntry, ...]
    manifest_sha256: str

    def canonical_payload(self) -> dict[str, Any]:
        return {
            "manifest_id": self.manifest_id,
            "manifest_version": self.manifest_version,
            "parent_manifest_sha256": self.parent_manifest_sha256,
            "entries": [
                entry.canonical()
                for entry in sorted(
                    self.entries,
                    key=lambda item: item.evidence_id,
                )
            ],
        }

def _digest(payload: dict[str, Any]) -> str:
    encoded = json.dumps(
        payload,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()

def build_manifest(
    *,
    manifest_id: str,
    evidence_items,
    parent_manifest_sha256: str | None = None,
) -> SourceManifest:
    entries = tuple(
        ManifestEntry(
            evidence_id=item.id,
            source_snapshot_sha256=item.source_snapshot_sha256,
            passage_sha256=item.passage_sha256,
            source_locator=item.source_locator,
            page_number=item.page_number,
            section=item.section,
            block_type=item.block_type,
            block_index=item.block_index,
            related_block_ids=tuple(
                sorted(item.related_block_ids)
            ),
            language=item.language,
            document_version=item.document_version,
            precedence_group=item.precedence_group,
            precedence_rank=item.precedence_rank,
        )
        for item in evidence_items
    )

    provisional = SourceManifest(
        manifest_id=manifest_id,
        manifest_version="1",
        parent_manifest_sha256=parent_manifest_sha256,
        entries=entries,
        manifest_sha256="",
    )

    digest = _digest(provisional.canonical_payload())

    return SourceManifest(
        manifest_id=manifest_id,
        manifest_version=provisional.manifest_version,
        parent_manifest_sha256=parent_manifest_sha256,
        entries=entries,
        manifest_sha256=digest,
    )

def verify_manifest(manifest: SourceManifest) -> bool:
    return _digest(manifest.canonical_payload()) == manifest.manifest_sha256

def verify_parent_link(
    manifest: SourceManifest,
    parent_manifest: SourceManifest | None,
) -> bool:
    if manifest.parent_manifest_sha256 is None:
        return parent_manifest is None

    if parent_manifest is None:
        return False

    return (
        verify_manifest(parent_manifest)
        and manifest.parent_manifest_sha256
        == parent_manifest.manifest_sha256
    )
