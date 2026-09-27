# Nemesis verification program

The verifier has two deliberately opposed roles.

## Attacker

The attacker tries to make the system return the wrong verdict using realistic,
clinically plausible manipulations.

Attack families:

### Semantic
- negation scope
- double negation
- conditional statements
- causal vs associative language
- correlation vs causation
- “not proven” vs “proven false”
- absolute words
- vague terms such as “safe”, “effective”, “common”

### Numeric
- percent vs percentage points
- relative vs absolute risk
- unit swaps
- decimal shifts
- dose-frequency changes
- denominator changes
- confidence interval misreading
- NNT/NNH arithmetic
- mmol/L vs mg/dL
- micrograms vs milligrams

### Population/context
- adults vs children
- pregnancy vs general population
- renal/hepatic impairment
- dose/route mismatches
- indication/stage mismatches
- missing concurrent medications
- contraindication context hidden in another field

### Evidence
- duplicated studies
- same study republished
- review + included trial counted as independent
- preprint + final paper counted twice
- outdated guideline vs current label
- weak study vs regulatory source
- cherry-picked evidence
- citation points to a related but non-supporting source

### Temporal
- old evidence presented as current
- “currently” without a date
- policy/guideline changes
- treatment-label changes
- superseded evidence

### Adversarial input
- prompt injection
- authority pressure
- fake urgency
- “doctor already approved it”
- fake references
- fabricated PMID/DOI
- malicious Unicode/whitespace
- long irrelevant context intended to bury the claim

## Defender

Every successful attack must produce all four:

1. a code fix;
2. a regression test;
3. a documented acceptance criterion;
4. a new failure-mode entry.

The defender must not simply lower confidence. The goal is to identify why
the evidence is inadequate and either:
- retrieve better evidence,
- resolve the contradiction,
- request missing context, or
- abstain.

## Metamorphic requirements

The verdict should remain stable under:
- harmless whitespace changes;
- evidence reordering;
- duplicate evidence insertion;
- equivalent phrasing.

The verdict should change when the claim's clinical meaning changes.

## Pass criteria

No attack is considered fixed merely because the demo output looks better.
The regression test must demonstrate that the previously exploitable path is
blocked or deterministically escalated.
