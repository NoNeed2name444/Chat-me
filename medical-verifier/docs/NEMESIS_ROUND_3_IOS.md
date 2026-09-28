# iOS Nemesis Round 3

The online-first iOS architecture was attacked as an adversary trying to make
the app produce a false or unsafe result.

## Findings and fixes

1. **Tampered passage with unchanged stored hash**
   - Fixed: the local verifier now performs source-integrity validation at the
     verifier boundary, not only in a separate helper.

2. **Unsafe action hidden in the answer**
   - Fixed: risk classification now examines both prompt and answer.
   - Example: an innocent prompt paired with "stop warfarin immediately" escalates.

3. **Reversed medication/action wording**
   - Fixed: the action detector scans around the medication term in both
     directions rather than only after the action verb.

4. **Safety vs effectiveness property confusion**
   - Fixed: local relation classes distinguish safety from effectiveness.

5. **Double negation**
   - Fixed: common forms such as "not uncommon" normalize before semantic
     polarity comparison.

6. **Numeric/unit substitution**
   - Fixed: exact measurement tokens are compared, not merely bare numbers.
   - Example: 500 mg vs 500 mcg is rejected.

7. **Certainty escalation**
   - Fixed: stronger certainty words must be present in the source before they
     can be asserted by a curriculum answer.

8. **Client/server semantic drift**
   - Fixed structurally: iOS requests and server responses carry contract
     version 1.0, and mismatches fail closed.

9. **Unsafe transport**
   - Fixed: the reference iOS network client rejects non-HTTPS endpoints.

10. **Network outage confusion**
    - Fixed structurally: online verification is optional only for the current
      medical lane. A network failure cannot be relabeled as current medical
      support.

## Remaining gaps

- Swift and Python still need true cross-platform conformance vectors executed
  in CI.
- The local semantic engine is heuristic and can miss complex cross-sentence
  scope, tables, figures, arithmetic, and clinical context.
- Current medical truth still depends on network evidence and source freshness.
- No claim of clinical validation is made until the clinician-labeled
  benchmark is populated and measured.
