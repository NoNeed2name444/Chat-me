"""Conservative normalization of explicit date/time expressions.

This module only normalizes expressions it can interpret deterministically. It does
not infer unstated clinical timing or convert relative expressions without a supplied
reference date.
"""

import re
from datetime import date, timedelta


def normalize_date(value: str) -> date | None:
    value = value.strip()
    for pattern, fmt in (
        (r"^\d{4}-\d{2}-\d{2}$", "%Y-%m-%d"),
        (r"^\d{4}/\d{2}/\d{2}$", "%Y/%m/%d"),
    ):
        if re.fullmatch(pattern, value):
            try:
                from datetime import datetime
                return datetime.strptime(value, fmt).date()
            except ValueError:
                return None
    return None


def extract_explicit_dates(text: str) -> tuple[date, ...]:
    dates = []
    for match in re.findall(r"\b\d{4}(?:-|/)\d{2}(?:-|/)\d{2}\b", text):
        parsed = normalize_date(match)
        if parsed is not None:
            dates.append(parsed)
    return tuple(sorted(set(dates)))


def resolve_relative(expression: str, reference: date) -> tuple[date, date] | None:
    lower = " ".join(expression.lower().split())
    match = re.fullmatch(r"last (\d+) days?", lower)
    if match:
        days = int(match.group(1))
        return reference - timedelta(days=days), reference
    match = re.fullmatch(r"next (\d+) days?", lower)
    if match:
        days = int(match.group(1))
        return reference, reference + timedelta(days=days)
    return None


def temporal_signature(text: str) -> dict[str, object]:
    lower = " ".join(text.lower().split())
    dates = extract_explicit_dates(text)
    markers = []
    for marker in (
        "currently", "currently active", "previously", "historically",
        "before", "after", "within", "during", "until", "since",
    ):
        if re.search(r"\b" + re.escape(marker) + r"\b", lower):
            markers.append(marker)
    return {"dates": tuple(d.isoformat() for d in dates), "markers": tuple(markers)}
