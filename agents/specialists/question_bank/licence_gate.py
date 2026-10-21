"""Licence gate for question-bank sources.

Follows docs/architecture/research/22d-question-bank-brief.md (§2, §4 step 1,
§9) as narrowed by the verified licence follow-up of October 2026:

- allowed: CC0 and CC BY only;
- openFDA is CC0 except records flagged otherwise;
- PMC Open Access is decided per article, and only a CC0 or CC BY article passes;
- refused, with a reason: CC BY-NC*, CC BY-ND, CC BY-SA (refused unless it is
  explicitly allowed later), OpenStax Anatomy & Physiology 2e, NICE, WHO,
  MedlinePlus, StatPearls, MedMCQA, MedQA, MMLU, and anything UNKNOWN.

A licence label never overrides a refused source family: a MedQA row relabelled
"CC BY 4.0" is still refused, because the label cannot grant rights the dataset
authors never held (brief §1, datasets table).
"""
import re
from dataclasses import dataclass

ALLOWED_LICENCES = {
    "CC0-1.0": (
        "CC0 1.0 Universal (public-domain dedication)",
        "https://creativecommons.org/publicdomain/zero/1.0/",
    ),
    "CC-BY-4.0": (
        "Creative Commons Attribution 4.0 International",
        "https://creativecommons.org/licenses/by/4.0/",
    ),
    "CC-BY-3.0": (
        "Creative Commons Attribution 3.0",
        "https://creativecommons.org/licenses/by/3.0/",
    ),
    "CC-BY-2.5": (
        "Creative Commons Attribution 2.5",
        "https://creativecommons.org/licenses/by/2.5/",
    ),
    "CC-BY-2.0": (
        "Creative Commons Attribution 2.0",
        "https://creativecommons.org/licenses/by/2.0/",
    ),
}

# Source families refused whatever licence label a record carries.
REFUSED_FAMILIES = {
    "openstax-anatomy-physiology-2e": (
        "OpenStax Anatomy & Physiology 2e is CC BY-NC-SA 4.0: non-commercial "
        "and ShareAlike"
    ),
    "nice": "NICE content is not CC0 or CC BY; reuse needs NICE's own licence",
    "who": "WHO content is not CC0 or CC BY (CC BY-NC-SA 3.0 IGO or own terms)",
    "medlineplus": (
        "MedlinePlus is not CC0 or CC BY and mixes licensed third-party "
        "content (A.D.A.M., ASHP)"
    ),
    "statpearls": "StatPearls is CC BY-NC-ND 4.0: non-commercial, no derivatives",
    "medmcqa": (
        "MedMCQA: licence unclear for the underlying items, which are "
        "exam-derived (AIIMS/NEET PG); usable for screening only"
    ),
    "medqa": (
        "MedQA: licence unknown; items scraped from commercial banks and "
        "exam-derived; usable for screening only"
    ),
    "mmlu": (
        "MMLU (medical subsets): licence of the underlying items unclear and "
        "exam-derived (USMLE practice items); usable for screening only"
    ),
}

PMC_OPEN_ACCESS = "pmc-oa"
OPENFDA = "openfda"


class LicenceRefused(ValueError):
    """A source failed the licence gate; the message lists every reason."""


@dataclass(frozen=True)
class LicenceDecision:
    source_id: str
    allowed: bool
    licence: str
    reason: str


def normalize_licence(value) -> str:
    if not isinstance(value, str) or not value.strip():
        return "UNKNOWN"
    text = value.strip().upper()
    text = re.sub(r"[\s_/]+", "-", text)
    text = text.replace("CC-ZERO", "CC0")
    if text in {"CC0", "CC0-1", "CC-0", "CC-0-1.0"}:
        return "CC0-1.0"
    if not text.startswith("CC") and text.startswith("BY"):
        text = "CC-" + text
    text = re.sub(r"^CC(?=BY)", "CC-", text)
    text = re.sub(r"-INTERNATIONAL$", "", text)
    return text


def _family(source) -> str:
    value = source.get("family")
    return value.strip().lower() if isinstance(value, str) else ""


def licence_decision(source) -> LicenceDecision:
    source_id = str(source.get("source_id") or "")
    licence = normalize_licence(source.get("license"))
    family = _family(source)

    def refuse(reason):
        return LicenceDecision(source_id, False, licence, reason)

    if family in REFUSED_FAMILIES:
        return refuse(f"refused_source:{family}: {REFUSED_FAMILIES[family]}")
    if family not in (OPENFDA, PMC_OPEN_ACCESS):
        return refuse(
            f"family_not_allowed:{family or '<none>'}: only openFDA and PMC OA "
            "families are allowed"
        )
    if licence == "UNKNOWN":
        return refuse("licence_unknown: a source without a readable licence is unusable")
    if licence.startswith("CC-BY-NC"):
        return refuse(f"licence_non_commercial:{licence}: NC bars a subscription app")
    if licence.startswith("CC-BY-ND"):
        return refuse(f"licence_no_derivatives:{licence}: ND bars adapting text into questions")
    if licence.startswith("CC-BY-SA"):
        return refuse(
            f"licence_share_alike:{licence}: ShareAlike would bind every derived "
            "item; refused unless explicitly allowed later"
        )
    if licence not in ALLOWED_LICENCES:
        return refuse(f"licence_not_allowed:{licence}: only CC0 and CC BY are allowed")

    canonical_url = ALLOWED_LICENCES[licence][1]
    url = str(source.get("license_url") or "").replace("http://", "https://")
    if url != canonical_url:
        return refuse(f"licence_url_mismatch:{licence}: expected {canonical_url}")

    if family == OPENFDA:
        if licence != "CC0-1.0":
            return refuse("openfda_not_cc0: openFDA records are CC0 unless flagged otherwise")
        if source.get("licence_flags"):
            return refuse(
                "openfda_flagged: record carries a licence flag "
                f"{sorted(source['licence_flags'])}; flagged openFDA items are refused"
            )
    if family == PMC_OPEN_ACCESS:
        if not re.fullmatch(r"PMC[0-9]+", source_id):
            return refuse("pmc_article_id_missing: PMC licences are per article")
        notice = str(source.get("license_notice") or "")
        if re.search(r"non.?commercial|no.?deriv|share.?alike|BY.NC|BY.ND|BY.SA", notice, re.I):
            return refuse("pmc_conflicting_notice: article notice names a restrictive term")

    if licence.startswith("CC-BY"):
        missing = [
            key for key in ("title", "authors", "license_notice", "evidence_url")
            if not source.get(key)
        ]
        if missing:
            return refuse("attribution_unavailable: missing " + ",".join(missing))

    return LicenceDecision(source_id, True, licence, "allowed")


def require_licensed(sources):
    """Raise LicenceRefused naming every refused source, or return decisions."""
    decisions = [licence_decision(source) for source in sources]
    refused = [d for d in decisions if not d.allowed]
    if refused:
        raise LicenceRefused(
            "; ".join(f"{d.source_id or '<no id>'}: {d.reason}" for d in refused)
        )
    return decisions


def attribution_notice(source, modifications: str) -> str:
    """Per-item notice: creator, title, link, licence and its link, changes."""
    decision = licence_decision(source)
    if not decision.allowed:
        raise LicenceRefused(f"{decision.source_id}: {decision.reason}")
    name, url = ALLOWED_LICENCES[decision.licence]
    authors = source.get("authors") or []
    creator = ", ".join(authors) if authors else str(source.get("publisher") or "")
    title = str(source.get("title") or decision.source_id)
    link = str(source.get("evidence_url") or "")
    if decision.licence == "CC0-1.0":
        terms = f"dedicated to the public domain under {name} ({url}); credit given as courtesy"
    else:
        terms = f"licensed under {name} ({url})"
    parts = [f'"{title}"']
    if creator:
        parts.append(f"by {creator}")
    if link:
        parts.append(link)
    return f"{', '.join(parts)}, {terms}. Changes: {modifications}"
