def classify_risk(claim, context):
    t = (claim + " " + str(context)).lower()

    if any(x in t for x in (
        "suicide", "self-harm", "overdose", "poisoning",
        "intentional overdose", "drug overdose",
    )):
        return "critical"

    if any(x in t for x in (
        "pregnancy", "pregnant", "infant", "newborn",
        "anaphylaxis", "severe allergic reaction", "emergency",
        "insulin", "chemotherapy", "anticoagulant",
        "chest pain", "stroke", "seizure", "unconscious",
        "severe bleeding", "stop my medication", "change my medication",
        "double my dose", "increase my dose", "decrease my dose",
    )):
        return "high"

    if any(x in t for x in (
        "dose", "interaction", "contraindication", "diagnosis",
        "child", "pediatric", "renal", "kidney", "liver",
        "pregnancy", "breastfeeding", "allergy",
        "interpret this lab", "lab result",
    )):
        return "moderate"

    return "low"

def missing_context(claim, context):
    t = claim.lower()
    out = []

    def need(key, *aliases):
        if key not in context and not any(alias in context for alias in aliases):
            out.append(key)

    if any(x in t for x in (
        "dose", "safe", "interaction", "contraindication",
        "stop", "start", "change", "double", "halve", "increase", "decrease",
    )):
        need("current_medications", "medications")
        need("age")

    if "pregnan" in t or "breastfeed" in t:
        need("pregnancy_status")
    if "pregnan" in t:
        need("gestational_age")
    if any(x in t for x in ("renal", "kidney")):
        need("renal_function")
    if any(x in t for x in ("liver", "hepatic")):
        need("hepatic_function")
    if any(x in t for x in ("allergy", "allergic")):
        need("allergies")

    return sorted(set(out))
