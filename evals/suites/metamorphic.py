import re

def normalize_for_metamorphic(text: str) -> str:
    text = text.lower()
    text = re.sub(r"\s+", " ", text)
    return text.strip(" .")

def equivalent_whitespace_variants(text: str) -> list[str]:
    return [
        text,
        re.sub(r"\s+", "  ", text),
        text.replace(" ", "\n"),
    ]

def paraphrase_like_variants(text: str) -> list[str]:
    replacements = (
        ("does not", "doesn't"),
        ("cannot", "can't"),
        ("increases", "raises"),
        ("decreases", "lowers"),
    )
    variants = [text]
    for a, b in replacements:
        if a in text.lower():
            variants.append(re.sub(a, b, text, flags=re.I))
    return variants

def reorder_evidence(items):
    return list(reversed(items))

def duplicate_evidence(items):
    return items + list(items)
