# Commercial Accuracy Stack

Updated: 2026-09-27

## Goal

Improve transcription verification for Egyptian Arabic lectures containing English medical terminology without turning the verifier into a clinical decision system.

## Recommended architecture

1. **Audio ASR** — benchmark Qwen3-ASR and QwenCleo-ASR as interchangeable backends.
2. **Forced alignment** — retain word/segment timestamps so every correction can point back to audio.
3. **Code-switch detector** — classify spans as Egyptian Arabic, medical English, general English, or uncertain.
4. **Candidate lattice** — keep the baseline ASR hypothesis plus acoustic alternatives instead of forcing one spelling.
5. **Medical lexicon** — normalize medication, laboratory, anatomy and procedure terms using commercially usable sources where the exact release permits it.
6. **Structured verification** — compare candidates on subject, polarity, negation, causality, modality, population, quantity, units, time and outcome.
7. **Evidence lineage** — deduplicate by DOI/PMID/NCT/other canonical identifiers before publisher fallback.
8. **Conservative decision** — accept only when audio evidence and semantic/medical constraints agree; otherwise abstain.

## Important distinction

Medical knowledge should resolve terminology ambiguity, not overwrite what the speaker acoustically said. For example, if two medical terms are acoustically plausible, the verifier should retain both candidates until audio alignment and context provide enough evidence.

## Commercial-use candidates

- Qwen3-ASR: Apache-2.0 repository code; audit the exact model-weight revision before distribution.
- QwenCleo-ASR: Apache-2.0 claim in its repository; audit exact weights and inherited terms before distribution.
- Common Voice: Mozilla states the datasets are provided under CC0, subject to its current distribution/access terms.
- MASC Arabic: current Hugging Face dataset card states CC BY 4.0.
- LOINC: official license states commercial and non-commercial use is permitted, subject to license conditions.
- RxNorm: NLM distinguishes releases; the current prescribable content is marked no-license-required, while other releases require checking the applicable terms.

## Accuracy controls

Every production run should record:
- ASR backend and exact revision;
- aligner revision;
- lexicon snapshot;
- terminology source revisions;
- audio segment timestamps;
- original ASR text;
- candidate alternatives;
- verification decision;
- evidence identifiers;
- reasons for acceptance or abstention.

The verifier should fail closed when a required component returns malformed evidence or an uncalibrated confidence value.
