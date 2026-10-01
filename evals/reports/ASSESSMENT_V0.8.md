# Assessment v0.8

## Conditional scope, structured provenance, and dose arithmetic

This milestone hardens the verifier against semantic widening that can occur when PDF extraction separates conditions from the statements they constrain.

### Cross-sentence conditions

The verifier now treats conditions such as if, unless, when, provided that, in patients with, and for patients with as evidence constraints.
A source condition cannot silently disappear from the answer. A different condition is not treated as equivalent.

### Clinical-unit arithmetic

The verifier conservatively recognizes only explicit, reversible arithmetic patterns:
- direct dose + frequency
- concentration × volume × frequency
- weight-based dose × explicit patient weight

It does not infer a patient weight that is not present, and concentration or weight-based quantities cannot masquerade as direct doses.
This is semantic equivalence checking, not dosing advice.

### Structured PDF provenance

Evidence can now retain page number, section, block type, and block index.
Block types are constrained to text, table, figure, caption, footnote, header, or unknown.
Question artifacts carry structural provenance forward.

### Explicit document precedence

Conflicting curriculum versions are never silently resolved by date or storage order.
A source must declare a precedence_group and precedence_rank to take part in deterministic precedence resolution.
Higher rank excludes lower-ranked versions in the same group. Equal-rank versions remain a conflict and require review.

### Cross-platform parity

Python and Swift share the same conformance corpus and carry the same conditional, arithmetic, provenance, and precedence expectations.

## Validation boundary

These safeguards remain heuristic and intentionally conservative. They reduce unsafe false support and preserve provenance; they do not establish clinical truth or clinical validity.