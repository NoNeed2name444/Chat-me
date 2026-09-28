# Nemesis Round 5 — Semantic scope and PDF extraction

Attacks:
- 500 mg twice daily vs 500 mg once daily
- 500 mg twice daily vs 1000 mg daily
- universal claim invented from selected-patient source
- exclusive claim invented from non-exclusive source
- OCR-uncertain source presented as authoritative curriculum
- two curriculum chunks giving opposite answers
- corrupted local evidence column mapping

Fixes:
- dose-frequency and daily-dose semantics
- universal/exclusive scope guards
- extraction-quality gating
- explicit curriculum conflict verdict
- corrected local evidence index mapping
- regression coverage on Python and Swift

Remaining frontier:
- cross-sentence conditionals and negation
- table/figure extraction semantics
- dose arithmetic involving weight, concentration, and units
- multilingual source extraction
- conflicting versions with explicit document precedence
- real clinician-reviewed benchmark performance