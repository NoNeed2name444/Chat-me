# Assessment v0.9

## Verifiable lineage, structural linkage, and multilingual normalization

Added a deterministic provenance layer for extracted medical curriculum material.

### Source manifests

Evidence can be collected into a canonical manifest containing source-file hashes, passage hashes, structural locators, language, block linkage, document version, and precedence metadata.

Manifests are hash-verifiable and can be parent-linked into a tamper-evident lineage chain.
No detached digital signature is claimed unless an external signing and trust system is configured.

### Table and figure linkage

Evidence can retain related block IDs so a table or figure can explicitly reference its caption or associated extracted block.
This relationship is preserved into generated question artifacts.

### Language metadata

Source blocks now carry explicit language metadata. The semantic layer adds a deliberately narrow Spanish/French normalization set for common negation, relation, safety, effectiveness, population, and glucose terminology.

Unsupported languages are not silently treated as fully normalized.

### Cross-platform parity

Python and Swift both implement manifest hashing, parent-link verification, structural provenance, and the tested Spanish/French normalization cases.

## Validation boundary

This remains an integrity and semantic-conformance layer, not a clinically validated multilingual medical reasoning system.