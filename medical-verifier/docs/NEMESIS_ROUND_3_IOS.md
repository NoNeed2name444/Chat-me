# iOS Nemesis Round

Adversarial cases exercised against the initial iOS core:

1. Tampered passage with unchanged stored hash.
   Defense: SourceIntegrityChecker recomputes the passage hash.

2. Tampered original file with unchanged extracted passage.
   Defense: source file SHA-256 is stored separately and verified separately.

3. Negation flip.
   Example: source says "reduces"; answer says "does not reduce".
   Defense: polarity/relation guards abstain.

4. Numeric escalation.
   Example: source says 20 percent; answer says 50 percent.
   Defense: claim numbers must occur in the source.

5. Population widening/narrowing.
   Example: source says adults; answer says children.
   Defense: population qualifiers must be supported.

6. Clinical-action bypass.
   Example: "Should I stop warfarin?"
   Defense: critical safety escalation, never curriculum auto-validation.

7. Prompt injection inside uploaded source.
   Defense: source text is treated as data only; no instruction execution path
   exists in the local verifier.

8. Missing curriculum.
   Defense: SOURCE_UNAVAILABLE and human review.

9. Provenance loss.
   Defense: QuestionArtifactFactory stores snapshot, file hash, passage hash,
   locator, and version.

10. Server/device semantic drift.
    Remaining gap: the Swift core and Python verifier are separate
    implementations and need conformance-vector testing.

11. Linguistic edge cases.
    Remaining gap: the deterministic local semantic guard can still miss
    complex scope, cross-sentence negation, tables, figures, and arithmetic.

12. Medical-currentness gap.
    Remaining by design: offline curriculum verification cannot prove current
    medical knowledge.

The next hardening milestone should be cross-platform conformance vectors plus
a signed/versioned verifier contract so iOS and server cannot silently disagree
on verdict semantics.
