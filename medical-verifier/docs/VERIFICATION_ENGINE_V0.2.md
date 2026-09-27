# Verification engine v0.2

The verifier now uses multiple independent dimensions rather than accepting a
single retrieved source as sufficient.

## Reliability axes

1. Atomic claim decomposition
2. Source authority
3. Lexical relevance
4. Temporal freshness
5. Provenance completeness
6. Source-family diversity
7. Independence groups
8. Contradiction weighting
9. Numeric consistency
10. Population-qualifier consistency
11. Absolute-language consistency
12. Patient-context gating
13. Risk-tier thresholds
14. Explicit abstention

Moderate/high-risk claims require stronger corroboration. Same-publisher
records do not automatically count as independent confirmation. Compound
claims are evaluated clause-by-clause.

## Current external-source policy

openFDA labeling is a living data source and its documentation warns that the
API should not be relied upon alone for medical-care decisions. PubMed is a
literature index/discovery source, not an automatic truth oracle. The verifier
therefore treats both as evidence inputs, not final clinical authority.

## Next accuracy jump

The biggest expected gains should come from:
- clinician-reviewed ground truth;
- calibrated evidence-entailment models;
- learned source-quality models;
- temporal/supersession graphs;
- prospective/shadow-mode evaluation;
- red-team and subgroup testing.

Confidence in this project remains a policy score until calibrated against
held-out clinical ground truth.
