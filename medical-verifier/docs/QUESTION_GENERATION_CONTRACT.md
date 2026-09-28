# Curriculum question-generation contract

When a learner supplies a source such as a PDF, textbook, lecture note, or
institutional handout:

1. Create an immutable source snapshot.
2. Assign a stable curriculum snapshot ID.
3. Generate questions only from that snapshot in curriculum mode.
4. Store question provenance to the source snapshot and, when available,
   page/section/chunk locations.
5. Verify the learner answer against the same curriculum snapshot.
6. Independently run the current-medical evidence lane.
7. Present the curriculum result and current-evidence status separately.
8. Never silently rewrite the curriculum answer.

### Example

Curriculum result:
"Correct according to your supplied source."

Current-evidence status:
"Newer evidence may differ."

Hint:
"Review the cited newer evidence; the curriculum material is retained as the
exam/study reference."

For high-risk medical topics, a current-vs-curriculum conflict should trigger
an explicit warning and, where appropriate, human review.

This design lets an educational product remain faithful to the learner's actual
curriculum while avoiding the unsafe implication that an outdated educational
source is current clinical guidance.
