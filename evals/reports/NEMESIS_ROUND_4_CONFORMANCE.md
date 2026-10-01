# Nemesis Round 4 — Cross-platform conformance

The adversary attempted to make Python and iOS disagree while preserving apparently plausible answers.

## Attack matrix

- tampered stored curriculum passage
- medication dose unit substitution
- equivalent unit spelling
- treatment action hidden in answer
- treatment action in question context
- negation flip
- property/predicate substitution
- certainty escalation
- source prompt injection
- missing source
- contract version drift

## Results

The shared JSON corpus now drives both test targets.

The most important discovered divergence was that Python could accept a modified stored curriculum passage while Swift raised a source-integrity failure. This was fixed by adding passage-hash validation to the Python curriculum fidelity lane and a distinct SOURCE_INTEGRITY_FAILED outcome.

Another divergence involved measurement semantics. Both implementations now reject mismatched units and agree on equivalent spellings such as mcg and ug when the numeric value is unchanged.

The iOS API protocol now carries explicit question context, so a dangerous question cannot be hidden behind a benign answer.

## Remaining adversarial frontier

- cross-sentence scope and negation
- tables/figures extracted from PDFs
- arithmetic and dose conversions with contextual units
- multilingual source material
- conflicting curriculum snapshots
- source OCR/extraction errors
- clinician-ground-truth benchmark performance