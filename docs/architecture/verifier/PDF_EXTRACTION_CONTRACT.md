# PDF Extraction Contract v1.0

## Purpose

The PDF ingestion lane converts a bounded PDF byte stream into page-scoped evidence blocks while preserving the original PDF SHA-256 and an immutable source manifest.

It is an extraction/provenance layer, not a guarantee that the PDF's visual semantics were reconstructed correctly.

## Extraction behavior

The current parser uses pypdf to extract page text.

Each extracted line becomes a block with stable page number and block index, block type, source locator, passage hash, raw-PDF SHA-256, extraction warnings, language metadata, and related-block IDs when a caption relationship can be established conservatively.

Caption lines are classified as caption.
Text with explicit pipe/tab delimiters may be classified as table, but the system records a heuristic warning. It does not claim native PDF object topology.

## Quality gating

Extraction quality is derived from page coverage and extracted character signal.
Heuristic structural warnings cap automatic quality below the curriculum auto-validation threshold.
A PDF with no extractable text remains review-required.

## Manifest

Every PDF ingestion creates a deterministic source manifest containing source and passage hashes plus structural provenance.
The manifest can reference a parent manifest hash. A parent must already be stored before the child manifest is accepted.
The system verifies manifest integrity with SHA-256. It does not claim a detached digital signature or external trust root.

## Security limits

PDF input is bounded by max_pdf_bytes.
Invalid base64, invalid PDF headers, oversized files, and extraction failures are rejected.

## Commercial dependency

The parser uses pypdf. The upstream project documents pypdf as BSD-3-Clause licensed. Commercial distributions must preserve the upstream license and attribution requirements.

Upstream: https://github.com/py-pdf/pypdf

## Clinical boundary

Extracted content is never considered clinically true merely because it came from a PDF. Source integrity, extraction quality, semantic support, risk gates, and current-evidence checks remain separate verification layers.