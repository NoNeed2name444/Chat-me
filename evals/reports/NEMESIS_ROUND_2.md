# Nemesis round 2 findings

This round attacked the edited verifier itself.

## Findings

1. **Dead defense problem**: semantic/provenance modules existed but were not
   guaranteed to execute in the main pipeline.
2. **Runtime dependency holes**: pipeline referenced local retrieval that was not
   present; the API referenced document ingestion/storage that was not present.
3. **Regex escape defect**: semantic/contradiction regexes used escaped word
   boundaries incorrectly, preventing intended matches.
4. **Citation illusion**: PubMed metadata could be confused with clinical
   evidence.
5. **Independence weakness**: publisher/source-family grouping was too coarse,
   while review/trial correlation needed an explicit relationship field.
6. **Source tampering risk**: no cryptographic binding between evidence passage
   and the source snapshot.

## Defender changes

- restored all runtime modules;
- corrected regex semantics;
- added source/passage SHA-256 binding;
- added citation-to-source verification;
- added explicit study-family/derived-from relationships;
- added a correlation-collapsing layer;
- integrated semantic/provenance/temporal gates into the main path;
- added regression tests for every discovered exploit.

## Remaining accuracy frontier

The next adversarial frontier is not more keyword rules. It is learned,
independent entailment and calibration against clinician-labeled cases.
