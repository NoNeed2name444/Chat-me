# Nemesis Round 8 — PDF ingestion and structural provenance

Attacks:
- invalid base64 PDF payload
- oversized PDF input
- non-PDF payload with a PDF endpoint
- PDF with no extractable text
- malformed page extraction
- caption falsely treated as native figure/table truth
- multiple captions leaking into unrelated blocks
- table delimiter heuristic presented as exact table structure
- parser-local block IDs not matching persisted evidence IDs
- manifest child accepted without stored parent
- source manifest mutation after creation
- missing commercial dependency attribution

Fixes:
- bounded and validated PDF ingestion
- extraction quality and explicit warnings
- caption block typing with heuristic disclosure
- immediate-next-block-only linkage
- persisted evidence-ID relationship mapping
- parent-manifest existence requirement
- deterministic manifest verification
- pypdf third-party notice and licensing documentation
- Python regression coverage

Remaining frontier:
- native PDF object/table geometry
- OCR fallback and scanned PDFs
- figure image semantics
- richer table cell extraction
- signed manifests and external trust roots
- clinician-reviewed evaluation on real-world PDFs