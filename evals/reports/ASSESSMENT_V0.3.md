# Verification layer assessment — v0.3

## What was checked

The layer was reviewed as an adversary rather than as a normal unit-test
consumer.

### Critical implementation findings that were fixed
- missing application entrypoint/configuration
- missing terminology module
- missing claim model
- missing local evidence provider
- missing evidence-provider base class
- missing document-ingestion route
- missing evidence persistence function
- semantic/contradiction regex escape defect
- semantic/provenance defenses not fully wired into the main path

### Reliability findings that were fixed
- metadata-only PubMed records could be mistaken for clinical entailment
- source/passages lacked cryptographic binding
- evidence independence was too coarse
- review/trial correlation lacked an explicit relationship
- older regulatory versions were not sufficiently penalized
- causal vs associative evidence could be confused
- negation scope could be mismatched
- numeric/unit mismatches could pass
- population mismatches could pass
- stale "current" claims could pass
- property mismatch could create false support
- named medication action requests could bypass a generic action pattern

## Current architecture

Claim
→ adversarial inspection
→ atomic assertions
→ context/risk gate
→ source retrieval
→ provenance binding
→ temporal supersession
→ evidence-direction analysis
→ deterministic semantic guard
→ specificity guard
→ independent structured entailment
→ correlated-evidence aggregation
→ risk-dependent policy
→ abstain/escalate/support

## Current conclusion

The implementation is materially more defensive than the initial version, but it
is still **not clinically validated** and should not be represented as near-100%
accurate.

The biggest remaining reliability gap is empirical validation:
- expert-labeled claim/evidence pairs
- calibrated confidence
- source-level revalidation
- clinical population testing
- prospective/shadow-mode evaluation
- continuous adversarial regression
