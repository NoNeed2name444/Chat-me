"""The question-bank source list and the refused register.

ALLOWED_SOURCES is the checked list brought from the question-bank research
(research/amboss-uworld/model/sources.json, checked 2026-10-19): PMC Open Access
articles whose own licence is CC BY 4.0. Authors and the article's licence
notice are filled in at ingestion from the article XML; the licence gate refuses
a CC BY paragraph whose attribution is still missing.

ALLOWED_FAMILIES and REFUSED_REGISTER restate the licence follow-up of October
2026 as data, so the doc, the gate and the tests read one list.
"""
from agents.specialists.question_bank.licence_gate import (
    OPENFDA,
    PMC_OPEN_ACCESS,
    REFUSED_FAMILIES,
)

CHECKED_ON = "2026-10-19"

_SCOPE = (
    "Original body paragraphs only; exclude assets, tables, quotations, "
    "supplements and separately credited material"
)

ALLOWED_SOURCES = (
    {
        "source_id": "PMC11343900",
        "family": PMC_OPEN_ACCESS,
        "version": "PMC11343900.1",
        "doi": "10.1007/s00125-024-06183-8",
        "title": "Hyperglycaemic crises in adults with diabetes: a consensus report",
        "license": "CC-BY-4.0",
        "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "evidence_url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC11343900/",
        "split": "train",
        "group": "hyperglycemic-crises-consensus-2024",
        "scope": _SCOPE,
    },
    {
        "source_id": "PMC11907869",
        "family": PMC_OPEN_ACCESS,
        "version": "PMC11907869.1",
        "doi": "10.1186/s40560-025-00776-0",
        "title": (
            "The Japanese Clinical Practice Guidelines for Management of "
            "Sepsis and Septic Shock 2024"
        ),
        "license": "CC-BY-4.0",
        "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "evidence_url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC11907869/",
        "split": "train",
        "group": "japanese-sepsis-guideline-2024",
        "scope": _SCOPE,
    },
    {
        "source_id": "PMC11893709",
        "family": PMC_OPEN_ACCESS,
        "version": "PMC11893709.1",
        "doi": "10.1007/s10157-024-02606-3",
        "title": "Treatment of hyponatremia: comprehension and best clinical practice",
        "license": "CC-BY-4.0",
        "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "evidence_url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC11893709/",
        # Held out: never evidence for a writer, never exported to training.
        "split": "validation",
        "group": "electrolyte-winter-seminar-hyponatremia",
        "scope": _SCOPE + ", self-assessment cases",
    },
)

ALLOWED_FAMILIES = {
    OPENFDA: "openFDA drug labels: CC0, except records flagged otherwise",
    PMC_OPEN_ACCESS: (
        "PMC Open Access subset: per article, only when that article's own "
        "licence is CC0 or CC BY"
    ),
}

REFUSED_LICENCES = {
    "CC BY-NC (all versions and combinations)": "non-commercial",
    "CC BY-ND": "no derivatives",
    "CC BY-SA": "ShareAlike; refused unless explicitly allowed later",
    "UNKNOWN": "no readable licence",
}

REFUSED_REGISTER = dict(REFUSED_FAMILIES)
