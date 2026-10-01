import re
from api.schemas.claim import NormalizedClaim

KNOWN_CONCEPTS = {
    "metformin", "hypoglycemia", "diabetes", "bleeding",
    "anticoagulant", "pregnancy", "pregnant", "insulin",
    "chemotherapy", "dose", "contraindication", "interaction",
    "diagnosis", "myocardial infarction", "heart attack",
    "warfarin", "heparin", "aspirin", "ibuprofen",
    "acetaminophen", "amoxicillin", "prednisone",
    "levothyroxine", "lisinopril",
}

def normalize_claim(text: str) -> NormalizedClaim:
    normalized = re.sub(r"\s+", " ", text.strip())
    lower = normalized.lower()
    concepts = sorted(x for x in KNOWN_CONCEPTS if x in lower)
    claim_type = (
        "drug_safety"
        if any(
            x in lower
            for x in (
                "drug", "medicine", "medication",
                "dose", "interaction", "contraindication",
            )
        )
        else "general_medical_claim"
    )
    return NormalizedClaim(
        original=text,
        normalized=normalized,
        concepts=concepts,
        claim_type=claim_type,
    )
