# Nemesis Round 7 — Lineage, linkage, and multilingual scope

Attacks:
- mutate one manifest entry while preserving the manifest identifier
- break the parent manifest hash chain
- drop a table-caption relationship during retrieval
- lose language metadata during question generation
- Spanish negation translated into a positive claim
- French relation terms treated as unrelated text
- unsupported language silently treated as equivalent

Fixes:
- deterministic manifest digest verification
- parent-manifest hash chaining
- explicit related-block provenance
- language persistence through evidence and question artifacts
- narrow Spanish/French normalization with polarity checks
- no claim of universal multilingual support

Remaining frontier:
- real PDF parser integration for table/figure topology
- richer multilingual medical terminology and locale-aware units
- detached signatures and external trust roots
- signed document lineage across distributed stores
- clinician-reviewed multilingual benchmark performance