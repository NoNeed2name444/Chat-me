# Assessment v1.5 — benchmark integrity and conservative normalization

## Benchmark snapshots

v1.5 adds a hash-addressed benchmark manifest containing a dataset identifier, snapshot
identifier, ordered case IDs, and optional parent snapshot hash. The digest provides
deterministic integrity checking and lineage; it is not a digital signature.

## Leakage checks

The integrity layer detects exact duplicate cases and exact train/test duplicates after
conservative text canonicalization. These checks are warnings about evaluation design,
not proof that a dataset is independent or clinically representative.

## Temporal normalization

Explicit ISO-like dates are parsed only when valid. Relative windows require an explicit
reference date. Unsupported temporal language is left unresolved rather than guessed.

## Entity normalization

Only an explicit alias table is used. Unknown names and near-spellings are not treated as
equivalent. This deliberately favors abstention over unsafe fuzzy medical entity matching.

## Safety boundary

These utilities are benchmark infrastructure. They do not establish clinical accuracy,
clinical calibration, safety, efficacy, or regulatory compliance.
