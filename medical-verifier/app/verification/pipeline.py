from datetime import date
from uuid import uuid4

from app.audit.store import store_verification
from app.config import settings
from app.models.verification import VerificationResponse
from app.retrieval.local import LocalEvidenceProvider
from app.retrieval.ncbi import PubMedProvider
from app.retrieval.openfda import OpenFDALabelProvider
from app.terminology.normalize import normalize_claim
from app.verification.adversarial import highest_severity, inspect_claim
from app.verification.assertions import decompose_claim
from app.verification.contradiction import assess_evidence
from app.verification.entailment import assess_entailment
from app.verification.policy import POLICY_VERSION, decide_verdict
from app.verification.provenance import apply_temporal_supersession
from app.verification.reliability import aggregate, enrich
from app.verification.reliability_report import build_report
from app.verification.risk import classify_risk, missing_context
from app.verification.rules import deterministic_checks

RISK_ORDER = {
    "low": 0,
    "moderate": 1,
    "high": 2,
    "critical": 3,
}

def _explicit_medications(context):
    meds = context.get(
        "current_medications",
        context.get("medications", []),
    )

    if isinstance(meds, str):
        meds = [meds]

    if not isinstance(meds, list):
        return []

    return [
        str(x).strip()
        for x in meds
        if str(x).strip()
    ][:2]

def _retrieve_for_assertion(assertion_text, request):
    evidence = []
    flags = []

    if "pubmed" in request.sources:
        try:
            evidence.extend(
                PubMedProvider().search(
                    assertion_text,
                    limit=6,
                )
            )
        except Exception as exc:
            flags.append(
                f"pubmed_provider_error:{type(exc).__name__}"
            )

    if "openfda" in request.sources:
        for drug in _explicit_medications(request.context):
            try:
                evidence.extend(
                    OpenFDALabelProvider().search(
                        drug,
                        limit=4,
                    )
                )
            except Exception as exc:
                flags.append(
                    f"openfda_provider_error:{type(exc).__name__}"
                )

    if "local" in request.sources:
        try:
            evidence.extend(
                LocalEvidenceProvider().search(
                    assertion_text,
                    [],
                    6,
                )
            )
        except Exception as exc:
            flags.append(
                f"local_provider_error:{type(exc).__name__}"
            )

    return evidence, flags

def verify(request):
    normalized = normalize_claim(request.claim)
    assertions = decompose_claim(normalized.normalized)

    risk = classify_risk(
        request.claim,
        request.context,
    )

    context_missing = set(
        missing_context(
            request.claim,
            request.context,
        )
    )

    flags = deterministic_checks(request.claim)

    adversarial = inspect_claim(request.claim)
    adversarial_data = [
        finding.__dict__
        for finding in adversarial
    ]
    attack_severity = highest_severity(adversarial)

    if len(assertions) > 4:
        flags.append(
            "compound_claim_truncated_to_four_assertions"
        )

    for assertion in assertions:
        context_missing.update(
            x
            for x in assertion.required_context
            if x not in request.context
        )

    if "direct_treatment_action_request" in flags:
        risk = max(
            risk,
            "high",
            key=lambda x: RISK_ORDER[x],
        )

    if attack_severity == "critical":
        result = VerificationResponse(
            verdict="SAFETY_ESCALATION",
            confidence=0.0,
            confidence_semantics=(
                "Critical adversarial/safety gate; not a probability."
            ),
            claim=request.claim,
            normalized_claim=normalized.normalized,
            claim_type=normalized.claim_type,
            risk_level=max(
                risk,
                "high",
                key=lambda x: RISK_ORDER[x],
            ),
            atomic_assertions=[
                a.__dict__
                for a in assertions
            ],
            evidence=[],
            contradictions=[],
            reliability={
                "policy_version": POLICY_VERSION,
                "adversarial_severity": attack_severity,
            },
            adversarial_findings=adversarial_data,
            limitations=flags + [
                "Critical adversarial pattern requires human/clinical escalation."
            ],
            decision_reasons=[
                "Adversarial safety gate blocked autonomous verification."
            ],
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

    for assertion in assertions[:4]:
        evidence, retrieval_flags = _retrieve_for_assertion(
            assertion.text,
            request,
        )
        all_flags.extend(retrieval_flags)

        for item in evidence:
            enrich(
                item,
                assertion.text,
                date.today(),
            )

        evidence, temporal_warnings = apply_temporal_supersession(
            evidence
        )
        all_flags.extend(
            f"{item_id}:{warning}"
            for item_id, warning in temporal_warnings
        )

        classified, _, _ = assess_evidence(
            evidence,
            assertion.text,
        )

        entailment_warnings = []

        for item in classified:
            item, entailment = assess_entailment(
                item,
                assertion.text,
            )

            entailment_warnings.extend(
                entailment.warnings
            )

            if entailment.label == "SUPPORTS":
                item.supports = True
            elif entailment.label == "CONTRADICTS":
                item.supports = False
            else:
                item.supports = None

        all_flags.extend(
            f"{assertion.id}:{warning}"
            for warning in sorted(set(entailment_warnings))
        )

        agg = aggregate(classified)

        verdict, confidence, reasons = decide_verdict(
            risk_level=risk,
            claim_type=normalized.claim_type,
            missing_context=sorted(context_missing),
            aggregate=agg,
            evidence=classified,
        )

        if entailment_warnings:
            reasons += [
                "Citation/semantic entailment checks downgraded "
                "one or more evidence items."
            ]

        all_evidence.extend(classified)

        assertion_results.append({
            "id": assertion.id,
            "text": assertion.text,
            "verdict": verdict,
            "confidence": confidence,
            "reliability": agg,
            "reasons": reasons,
        })

    supported = [
        x
        for x in assertion_results
        if x["verdict"] == "SUPPORTED"
    ]

    contradicted = [
        x
        for x in assertion_results
        if x["verdict"] == "CONTRADICTED"
    ]

    mixed = [
        x
        for x in assertion_results
        if x["verdict"] == "MIXED_EVIDENCE"
    ]

    insufficient = [
        x
        for x in assertion_results
        if x["verdict"] in {
            "INSUFFICIENT_EVIDENCE",
            "CONTEXT_REQUIRED",
        }
    ]

    if supported and contradicted:
        final_verdict = "MIXED_EVIDENCE"
    elif mixed:
        final_verdict = "MIXED_EVIDENCE"
    elif contradicted and not supported and not insufficient:
        final_verdict = "CONTRADICTED"
    elif insufficient:
        final_verdict = "INSUFFICIENT_EVIDENCE"
    elif supported and len(supported) == len(assertion_results):
        final_verdict = "SUPPORTED"
    else:
        final_verdict = "INSUFFICIENT_EVIDENCE"

    final_evidence = []
    seen = set()

    for item in all_evidence:
        if item.id in seen:
            continue
        seen.add(item.id)
        final_evidence.append(item)

    final_agg = aggregate(final_evidence)

    if request.requested_evidence_level in {
        "authoritative",
        "highest_available",
    }:
        has_authoritative_support = any(
            item.supports is True
            and item.source_authority >= 0.85
            for item in final_evidence
        )

        if final_verdict == "SUPPORTED" and not has_authoritative_support:
            final_verdict = "INSUFFICIENT_EVIDENCE"

    if attack_severity == "high" and final_verdict == "SUPPORTED":
        final_verdict = "INSUFFICIENT_EVIDENCE"

    confidence = 0.0

    if final_verdict == "SUPPORTED":
        confidence = min(
            0.96,
            round(
                0.55
                + 0.41 * final_agg["support_ratio"],
                3,
            ),
        )
    elif final_verdict == "MIXED_EVIDENCE":
        confidence = 0.40
    elif final_verdict == "CONTRADICTED":
        confidence = 0.72

    report_reasons = []

    for assertion_result in assertion_results:
        report_reasons.extend(
            assertion_result["reasons"]
        )

    if attack_severity == "high":
        report_reasons.append(
            "High-severity adversarial claim pattern "
            "blocked automatic support."
        )

    reliability = build_report(
        final_agg,
        final_evidence,
        sorted(set(report_reasons)),
    )

    reliability["policy_version"] = POLICY_VERSION
    reliability["atomic_assertions"] = assertion_results
    reliability["adversarial_severity"] = attack_severity

    limitations = sorted(set(all_flags))

    if adversarial_data:
        limitations.append(
            "Adversarial inspection found semantic or "
            "safety-sensitive claim patterns."
        )

    if not final_evidence:
        limitations.append(
            "No usable evidence was retrieved."
        )

    if final_verdict == "INSUFFICIENT_EVIDENCE":
        limitations.append(
            "Evidence did not meet the multi-axis "
            "verification threshold."
        )

    if final_verdict == "MIXED_EVIDENCE":
        limitations.append(
            "Material disagreement remains; automatic "
            "verification is withheld."
        )

    limitations.append(
        "External evidence must be independently reviewed "
        "before clinical use."
    )

    requires_review = (
        risk in {"moderate", "high"}
        or attack_severity in {"moderate", "high"}
        or final_verdict in {
            "MIXED_EVIDENCE",
            "CONTEXT_REQUIRED",
            "CONTRADICTED",
        }
        or bool(context_missing)
    )

    result = VerificationResponse(
        verdict=final_verdict,
        confidence=confidence,
        confidence_semantics=(
            "Policy score only; not a calibrated probability "
            "of clinical truth."
        ),
        claim=request.claim,
        normalized_claim=normalized.normalized,
        claim_type=normalized.claim_type,
        risk_level=risk,
        atomic_assertions=[
            a.__dict__
            for a in assertions
        ],
        evidence=[
            item.model_dump(mode="json")
            for item in final_evidence
        ],
        contradictions=[
            item.model_dump(mode="json")
            for item in final_evidence
            if item.supports is False
        ],
        reliability=reliability,
        adversarial_findings=adversarial_data,
        limitations=limitations,
        decision_reasons=sorted(set(report_reasons)),
        missing_context=sorted(context_missing),
        requires_human_review=requires_review,
        verifier_version=settings.verifier_version,
        knowledge_snapshot=settings.knowledge_snapshot,
        verification_id=str(uuid4()),
    )

    store_verification(result)
    return result
