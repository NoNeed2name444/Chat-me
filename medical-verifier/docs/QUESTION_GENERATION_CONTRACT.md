## Curriculum question-generation contract

When a learner supplies a source such as a PDF, textbook, lecture note, or institutional handout:

1. Preserve the original file hash when available.
2. Store an immutable source snapshot ID.
3. Store extraction-level passage hashes.
4. Store page, section, or equivalent source locators when available.
5. Generate questions only from that curriculum snapshot in curriculum mode.
6. Validate each generated question against the snapshot before automatic release.
7. Store question provenance including source IDs, versions, locators, passage hashes, and source-file hashes.
8. Verify the learner answer against the same curriculum snapshot.
9. Independently run the current-medical evidence lane.
10. Present curriculum correctness and current-evidence status separately.
11. Never silently rewrite the curriculum answer.

### Question validation states

VALIDATED means the prompt is aligned to the curriculum material and the proposed answer is supported by the source.

SOURCE_UNCERTAIN means some evidence is relevant, but semantic support is incomplete; keep the artifact traceable and route it to review.

SOURCE_UNSUPPORTED means the answer cannot be supported by the supplied source; do not treat the question as curriculum-grounded without review.

SOURCE_UNAVAILABLE means no curriculum snapshot was available; do not auto-release the question as source-grounded.

### Current-evidence layer

After curriculum validation, the medical verifier may separately report:
- current and aligned;
- potentially outdated;
- material disagreement;
- current evidence unavailable.

A current-evidence result must never silently change the expected curriculum answer.

For high-risk conflicts, require human review.

### Example

Curriculum result:
Correct according to your supplied source.

Current medical status:
Newer relevant evidence may differ.

Hint:
Review the cited newer evidence; the curriculum material is retained as the study/exam reference.

### Safety boundary

Curriculum correctness is an educational property.

It does not authorize diagnosis, prescribing, dosage changes, or other clinical action.
