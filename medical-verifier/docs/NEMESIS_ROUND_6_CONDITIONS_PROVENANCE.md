# Nemesis Round 6 — Conditions, arithmetic, provenance, and version precedence

Attacks:
- condition appears in one source sentence and the constrained statement in another
- answer drops a severe renal impairment condition
- answer substitutes a different condition
- concentration text is mistaken for a standalone dose
- weight-based dose is treated as direct dose
- patient weight is assumed when absent
- two document versions disagree and storage order decides the winner
- equal-precedence conflicting versions are silently resolved
- table/figure provenance is lost when a question is generated

Fixes:
- cross-sentence conditional scope checks
- concentration × volume × frequency arithmetic
- weight-based dose × explicit weight arithmetic
- direct-dose exclusions for concentration and weight-based expressions
- explicit precedence groups/ranks with tie-to-conflict behavior
- page/section/block provenance through evidence and question artifacts
- Python and Swift regression coverage

Remaining frontier:
- multilingual semantic normalization
- richer table/figure relationships and caption linkage
- unit arithmetic beyond the conservative supported patterns
- document lineage graphs and signed source manifests
- clinician-grounded benchmark performance on real extracted PDFs