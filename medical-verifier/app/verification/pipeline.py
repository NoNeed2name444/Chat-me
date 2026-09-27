from uuid import uuid4

from app.audit.store import store_verification
from app.config import settings
from app.models.verification import VerificationResponse
from app.retrieval.ncbi import PubMedProvider
from app.retrieval.openfda import OpenFDALabelProvider
from app.terminology.normalize import normalize_claim
from app.verification.assertions import decompose_claim
from app.verification.contradiction import assess_evidence
from app.verification.policy import decide_verdict, POLICY_VERSION
from app.verification.reliability import aggregate, enrich
from app.verification.reliability_report import build_report
from app.verification.risk import classify_risk, missing_context
from app.verification.rules import deterministic_checks

def _explicit_medications(context):
    meds = context.get("current_medications", context.get("medications", []))
    if isinstance(meds, str):
        meds = [meds]
    if not isinstance(meds, list):
        return []
    return [str(x).strip() for x in meds if str(x).strip()][:2]

def _retrieve_for_assertion(assertion_text, request):
    evidence = []
    flags = []

    try:
        evidence.extend(PubMedProvider().search(assertion_text, limit=6))
    except Exception as exc:
        flags.append(f"pubmed_provider_error:{type(exc).__name__}")

    # Do not guess the drug from arbitrary prose. Only query openFDA for
    # explicit medication context supplied by the caller.
    for drug in _explicit_medications(request.context):
        try:
            evidence.extend(OpenFDALabelProvider().search(drug, limit=4))
        except Exception as exc:
            flags.append(f"openfda_provider_error:{type(exc).__name__}")

    return evidence, flags

def verify(request):
    normalized = normalize_claim(request.claim)
    assertions = decompose_claim(normalized.normalized)
    risk = classify_risk(request.claim, request.context)
    context_missing = set(missing_context(request.claim, request.context))
    flags = deterministic_checks(request.claim)

    for assertion in assertions:
        context_missing.update(x for x in assertion.required_context if x not in request.context)

    if flags and "direct_treatment_action_request" in flags:
        risk = max(risk, "moderate", key=lambda x: {"low":0,"moderate":1,"high":2,"critical":3}[x])

    if risk == "critical":
        result = VerificationResponse(
            verdict="SAFETY_ESCALATION",
            confidence=0.0,
            confidence_semantics="Critical-risk safety gate; not a probability.",
            claim=request.claim,
            normalized_claim=normalized.normalized,
            claim_type=normalized.claim_type,
            risk_level=risk,
            atomic_assertions=[a.__dict__ for a in assertions],
            evidence=[],
            contradictions=[],
            reliability={"policy_version": POLICY_VERSION},
            limitations=flags + ["Critical-risk content requires human/clinical escalation."],
            decision_reasons=["Critical-risk input cannot be auto-verified."],
            missing_context=sorted(context_missing),
            requires_human_review=True,
            verifier_version=settings.verifier_version,
            knowledge_snapshot=settings.knowledge_snapshot,
            verification_id=str(uuid4()),
        )
        store_verification(result)
        return result

    all_evidence = []
    assertion_results = []
    all_flags = list(flags)

    # Verify every atomic assertion independently, then aggregate.
    for assertion in assertions[:4]:
        evidence, retrieval_flags = _retrieve_for_assertion(assertion.text, request)
        all_flags.extend(retrieval_flags)

        for item in evidence:
            enrich(item, assertion.text, __import__("datetime").date.today())

        classified, _, _ = assess_evidence(evidence, assertion.text)
        agg = aggregate(classified)
        verdict, confidence, reasons = decide_verdict(
            risk_level=risk,
            claim_type=normalized.claim_type,
            missing_context=sorted(context_missing),
            aggregate=agg,
            evidence=classified,
        )

        all_evidence.extend(classified)
        assertion_results.append({
            "id": assertion.id,
            "text": assertion.text,
            "verdict": verdict,
            "confidence": confidence,
            "reliability": agg,
            "reasons": reasons,
        })

    verified = [x for x in assertion_results if x["verdict"] == "SUPPORTED"]
    contradicted = [x for x in assertion_results if x["verdict"] == "CONTRADICTED"]
    mixed = [x for x in assertion_results if x["verdict"] == "MIXED_EVIDENCE"]
    insufficient = [x for x in assertion_results if x["verdict"] in {
        "INSUFFICIENT_EVIDENCE", "CONTEXT_REQUIRED"
    }]

    if contradicted and verified:
        final_verdict = "MIXED_EVIDENCE"
    elif mixed:
        final_verdict = "MIXED_EVIDENCE"
    elif insufficient:
        final_verdict = "INSUFFICIENT_EVIDENCE"
    elif verified and len(verified) == len(assertion_results):
        final_verdict = "SUPPORTED"
    else:
        final_verdict = "INSUFFICIENT_EVIDENCE"

    # Recompute the full evidence graph for the final reliability report.
    final_evidence = []
    seen = set()
    for item in all_evidence:
        if item.id in seen:
            continue
        seen.add(item.id)
        final_evidence.append(item)

    final_agg = aggregate(final_evidence)

    # Authoritative requested means at least one regulatory/guideline-type
    # source must support the result. PubMed metadata alone cannot satisfy it.
    if request.requested_evidence_level == "authoritative":
        has_authoritative_support = any(
            item.supports is True and item.source_authority >= 0.85
            for item in final_evidence
        )
        if final_verdict == "SUPPORTED" and not has_authoritative_support:
            final_verdict = "INSUFFICIENT_EVIDENCE"

    # Confidence is a policy score, not a medical-truth probability.
    confidence = 0.0
    if final_verdict == "SUPPORTED":
        confidence = min(
            0.96,
            round(0.55 + 0.41 * final_agg["support_ratio"], 3)
        )
    elif final_verdict == "MIXED_EVIDENCE":
        confidence = 0.40
    elif final_verdict == "CONTRADICTED":
        confidence = 0.72

    report_reasons = []
    for result in assertion_results:
        report_reasons.extend(result["reasons"])

    reliability = build_report(final_agg, final_evidence, report_reasons)
    reliability["policy_version"] = POLICY_VERSION
    reliability["atomic_assertions"] = assertion_results

    limitations = sorted(set(all_flags))
    if not final_evidence:
        limitations.append("No usable evidence was retrieved.")
    if final_verdict == "INSUFFICIENT_EVIDENCE":
        limitations.append("The evidence did not meet the multi-axis verification threshold.")
    limitations.append(
        "External evidence must be independently reviewed before clinical use."
    )

    requires_review = (
        risk in {"moderate", "high"}
        or final_verdict in {"MIXED_EVIDENCE", "CONTEXT_REQUIRED"}
        or bool(context_missing)
    )

    result = VerificationResponse(
        verdict=final_verdict,
        confidence=confidence,
        confidence_semantics="Policy score only; not a calibrated probability of clinical truth.",
        claim=request.claim,
        normalized_claim=normalized.normalized,
        claim_type=normalized.claim_type,
        risk_level=risk,
        atomic_assertions=[a.__dict__ for a in assertions],
        evidence=[item.model_dump(mode="json") for item in final_evidence],
        contradictions=[
            item.model_dump(mode="json")
            for item in final_evidence
            if item.supports is False
        ],
        reliability=reliability,
        limitations=limitations,
        decision_reasons=report_reasons,
        missing_context=sorted(context_missing),
        requires_human_review=requires_review,
        verifier_version=settings.verifier_version,
        knowledge_snapshot=settings.knowledge_snapshot,
        verification_id=str(uuid4()),
    )
    store_verification(result)
    return result
