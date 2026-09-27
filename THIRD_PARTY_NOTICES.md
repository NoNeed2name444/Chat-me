# Third-Party Licensing Inventory

This file records licensing findings for external projects and resources considered during development of the verification layer.

## Status legend

- **CLEAR (code)** — permissive repository license permits commercial use, subject to its license conditions.
- **CLEAR (data/ontology)** — identified data/ontology license permits commercial use.
- **RESTRICTED** — referenced dataset/resource has a non-commercial or otherwise restrictive license.
- **UNKNOWN / DO NOT SHIP** — exact license for the relevant asset has not been verified.

> This is an engineering inventory, not legal advice. Code, model weights, datasets, papers, APIs, and retrieved content can have different terms.

## Investigated projects

| Component | Source | License verified | Commercial use | Attribution / obligations | Chat-me integration status |
|---|---|---|---|---|---|
| Scientific Claim Verifier | https://github.com/melofy-vibes/scientific-claim-verifier | MIT | **Yes** for repository code, subject to MIT notice/disclaimer requirements | Preserve copyright + MIT license notice in redistributed copies/substantial portions | **Inspiration only**; no wholesale code/data copied |
| Scientific Claim Verification Engine | https://github.com/VivienP/scientific-claim-verification-engine | Apache-2.0 | **Yes** for repository code, subject to Apache-2.0 conditions | Include license; preserve notices; mark modified files; comply with patent/trademark terms | **Inspiration only**; no wholesale code/data copied |
| Scientific Claim Verification Engine evaluation dataset (`eval/e2e/`) | https://github.com/VivienP/scientific-claim-verification-engine | CC BY-NC | **No / restricted for commercial use** | Non-commercial restriction; do not bundle or reuse commercially without permission | **Not used** |
| Evidence & Conclusion Ontology (ECO) | https://github.com/evidenceontology/evidenceontology | CC0 1.0 Universal | **Yes** | Attribution/citation is requested but not a CC0 condition | **Conceptual/schema inspiration only** |

## Previously investigated repositories — not yet cleared

The following repositories were reviewed as potential implementation references, but their exact licensing and all bundled assets were not fully verified at the time of this inventory. They are therefore **not approved dependencies or copied sources**:

- `VishruthiAnandh/Medical-AI-Fact-Checking`
- `kx0809/Automated-Climate-Fact-Checking`
- `OMAR-IMAD/scientific-claim-verifier`
- `ThongVo-GitHub/FactCheck_AI`
- `PasupuletiSindhu/Evidence-Bound-Ontology-State-Engine-for-Clinical-Text`
- `Hero-Legend/evidence-first-bottleneck-architecture`

Do not copy code, model weights, datasets, benchmark files, prompts, or other repository assets from these projects into a commercial Chat-me distribution until their individual licenses and asset terms have been verified.

## Data and API sources

A source being publicly accessible or having a free API does **not** by itself establish unrestricted commercial rights to the returned content. For any production retrieval layer, audit the terms for each provider and, where applicable, the license of the underlying paper/full text.

This includes sources considered by related projects such as PubMed/PMC, Europe PMC, OpenAlex, arXiv, Semantic Scholar, CORE, Crossref, and Unpaywall. The verifier should prefer metadata and openly licensed full text where the applicable terms permit the intended use.

## Models and model weights

Model licenses must be audited separately from repository licenses. A permissively licensed GitHub repository can still depend on model weights with different restrictions.

Before shipping any external model as part of Chat-me, record:

1. model name and exact revision;
2. model-card/license URL;
3. weight license;
4. tokenizer/license where separately licensed;
5. training-data restrictions if stated;
6. commercial-use restrictions;
7. redistribution requirements.

## Chat-me project license status

No root `LICENSE` or `LICENCE` file was found on branch verification-layer-adversarial-50 during this audit. Until Chat-me adds an explicit project license, do **not** describe the repository as open source or assume third parties have permission to commercially reuse its code.

## Shipping rule

If an external component is marked **UNKNOWN / DO NOT SHIP** or **RESTRICTED**, it should not be incorporated into a commercial distribution until its terms are verified and recorded here.

## Paid-license / commercial-service status

No paid-license runtime dependency, proprietary SDK, commercial-only model, or paid API integration was identified in the current `medical-verifier` dependency set or the audited verifier code. The runtime dependencies declared in `medical-verifier/pyproject.toml` are permissively licensed open-source packages; no paid license is required merely to install or use them. The restricted CC BY-NC evaluation dataset listed above is **not used or shipped**.

If a future retrieval provider, model, database, or hosted API is added, it must be audited before inclusion; a free tier does not by itself establish unrestricted commercial rights.

Last audited: 2026-09-27.


## New 2026 candidates for the audio + medical terminology stack

| Component | Source | License / commercial status | Recommended use |
|---|---|---|---|
| Qwen3-ASR | https://github.com/QwenLM/Qwen3-ASR | Apache-2.0 repository code; verify exact model-weight terms before shipping a model revision | Primary multilingual ASR candidate; use forced alignment for word/segment timestamps |
| QwenCleo-ASR | https://github.com/MohammedAly22/qwencleo-asr | Apache-2.0 repository/model claim; verify exact model revision and inherited weight terms before shipping | Egyptian Arabic + Arabic/English code-switching candidate; benchmark against Qwen3-ASR |
| Common Voice | https://commonvoice.mozilla.org/ | CC0 for the dataset under Mozilla's current terms | General speech robustness and Egyptian Arabic coverage where the target locale is available |
| MASC Arabic | https://huggingface.co/datasets/pain/MASC | CC BY 4.0 dataset card | Arabic dialect/acoustic diversity; retain attribution and audit source provenance |
| LOINC | https://loinc.org/license | Free for commercial and non-commercial use under its open license, with conditions | Clinical observation/test terminology and spelling/alias normalization |
| RxNorm | https://www.nlm.nih.gov/research/umls/licensedcontent/rxnormfiles.html | Release-dependent NLM terms; current prescribable content is explicitly marked no-license-required; audit the exact release before bundling | Drug names, ingredients, strengths, dose forms and normalized medication aliases |

Do not automatically bundle every candidate. Model weights, datasets, repository code, and retrieved medical content can have different terms. Record the exact revision and license in the build manifest before commercial distribution.
