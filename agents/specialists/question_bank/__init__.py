"""Question bank: the evidence loop that turns licensed source paragraphs into
reviewed questions.

It sits beside the verifier's question validation
(agents/specialists/verification_agent/question_validation.py) and calls it,
with independent entailment and citation integrity, before any AI review.

- licence_gate: which sources may be used at all, and the attribution notice.
- source_list: the checked source list and the refused register.
- evidence_loop: writer draft as cited claim units, mechanical checks, the
  verifier's deterministic checks, blind support/challenge reviews as file
  packets, bounded revision, quarantine.
- reviewed_store: export of an accepted item, with provenance and SOURCE status.

No network access, model call or API key is used anywhere in this package.
See docs/architecture/verifier/QUESTION_BANK_PIPELINE.md.
"""
