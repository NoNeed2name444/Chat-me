# Nemesis Round 11 — integrity and normalization

The adversarial suite now targets:

1. exact duplicate benchmark cases
2. train/test leakage by duplicate claim/evidence
3. invalid calendar dates
4. relative dates without a reference date
5. near-spelling entity substitutions
6. interaction-pair substitutions
7. contraindication population substitutions
8. benchmark snapshot mutation through parent-hash changes

Expected behavior is deterministic detection or fail-closed uncertainty. No fuzzy medical
entity inference is permitted.
