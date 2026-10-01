# Assessment v1.0

## Real PDF document intelligence milestone

Added the first bounded PDF ingestion path instead of requiring pre-flattened text.

### PDF ingestion

The API accepts bounded base64 PDF input, validates the PDF header, extracts page text with pypdf, and stores page-scoped evidence blocks.

Each block preserves the raw PDF SHA-256, passage hash, page number, block index, block type, extraction warnings, language, and related evidence IDs.

### Structural extraction

Caption lines are explicitly typed as caption.
Simple tab/pipe-delimited text may be typed as table, but receives a heuristic warning.
Caption linkage is limited to the immediately following block so unrelated content cannot inherit provenance.

### Source manifests

Every PDF ingestion produces a deterministic manifest. Manifest hashes are verifiable and parent links require an existing parent manifest.

Manifest lineage is cryptographic hashing, not a digital-signature trust system.

### Commercial dependency

The project uses pypdf for extraction. The upstream project documents BSD-3-Clause licensing; attribution is recorded in the repository NOTICE file.

### Safety boundary

PDF extraction does not increase medical trust by itself. Poor extraction, missing text, heuristic table detection, or uncertain provenance remain separate review signals.