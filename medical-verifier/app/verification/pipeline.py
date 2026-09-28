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
from app.verification.curriculum import (
    assess_curriculum_fidelity,
    build_study_hint,
    determine_divergence,
)
from app.verification.entailment import assess_entailment
from app.verification.policy import POLICY_VERSION, decide_verdict
from app.verification.provenance import apply_temporal_supersession
from app.verification.reliability import aggregate, enrich
from app.verification.reliability_report import build_report
from app.verification.revalidation import revalidate
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

def _retrieve_current_for_assertion(assertion_text, request):
    evidence = []
    flags = []

    retrieval_query = assertion_text

    question_context = request.question_context
    if question_context:
        retrieval_query = (
            f"{question_context} {assertion_text}"
        )

    current_sources = [
        source
        for source in request.sources
        if source != "local"
    ]

    if request.verification_mode != "current_medical" and not current_sources:
        current_sources = ["pubmed"]

    if "pubmed" in current_sources:
        try:
            evidence.extend(
                PubMedProvider().search(
                    retrieval_query,
                    limit=6,
                )
            )
        except Exception as exc:
            flags.append(
                f"pubmed_provider_error:{type(exc).__name__}"
            )

    if "openfda" in current_sources:
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

    return evidence, flags

def _retrieve_curriculum_sources(assertion_text, request):
    if not request.curriculum_source_ids and not request.curriculum_snapshot:
        return [], []

    try:
        items = LocalEvidenceProvider().search(
            assertion_text,
            [],
            max(8, len(request.curriculum_source_ids)),
            source_ids=request.curriculum_source_ids or None,
            curriculum_snapshot_id=request.curriculum_snapshot,
        )
        return items, []
    except Exception as exc:
        return [], [
            f"curriculum_provider_error:{type(exc).__name__}"
        ]

def _evaluate_evidence(assertion_text, evidence, risk, context_missing):
    for item in evidence:
        enrich(
            item,
            assertion_text,
            date.today(),
        )

    evidence, temporal_warnings = apply_temporal_supersession(
        evidence
    )

    classified, _, _ = assess_evidence(
        evidence,
        assertion_text,
    )

    entailment_warnings = []

    for item in classified:
        item, entailment = assess_entailment(
            item,
            assertion_text,
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

    agg = aggregate(classified)

    verdict, confidence, reasons = decide_verdict(
        risk_level=risk,
        claim_type="medical_claim",
        missing_context=context_missing,
        aggregate=agg,
        evidence=classified,
    )

    return {
        "evidence": classified,
        "temporal_warnings": temporal_warnings,
        "entailment_warnings": entailment_warnings,
        "aggregate": agg,
        "verdict": verdict,
        "confidence": confidence,
        "reasons": reasons,
    }

def verify(request):
    if request.verification_contract_version != settings.verification_contract_version:
        result = VerificationResponse(
            verdict="CONTRACT_MISMATCH",
            confidence=0.0,
            confidence_semantics="Contract mismatch; no verification decision was made.",
            claim=request.claim,
            normalized_claim=request.claim,
            claim_type="contract_mismatch",
            risk_level="high",
            verification_mode=request.verification_mode,
            evidence=[],
            contradictions=[],
            curriculum_assessment={},
            current_evidence_assessment={},
            knowledge_divergence="unknown",
            study_hint=None,
            source_revalidation={},
            reliability={
                "policy_version": POLICY_VERSION,
                "verification_contract_version": settings.verification_contract_version,
                "client_contract_version": request.verification_contract_version,
            },
            adversarial_findings=[],
            limitations=["client_server_verification_contract_mismatch"],
            decision_reasons=[
                "Client and server verification contracts differ."
            ],
            missing_context=[],
            requires_human_review=True,
            verifier_version=settings.verifier_version,
            verification_contract_version=settings.verification_contract_version,
            knowledge_snapshot=settings.knowledge_snapshot,
            verification_id=str(uuid4()),
        )
        store_verification(result)
        return result

    normalized = normalize_claim(request.claim)
    assertions = decompose_claim(normalized.normalized)

    verification_context = " ".join(
        item
        for item in [
            request.question_context,
            request.claim,
        ]
        if item
    )

    risk = classify_risk(
        verification_context,
        request.context,
    )

    context_missing = set(
        missing_context(
            request.claim,
            request.context,
        )
    )

    flags = deterministic_checks(verification_context)
    adversarial = inspect_claim(verification_context)
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
            verification_mode=request.verification_mode,
            atomic_assertions=[
                a.__dict__
                for a in assertions
            ],
            evidence=[],
            contradictions=[],
            curriculum_assessment={},
            current_evidence_assessment={},
            knowledge_divergence="unknown",
            study_hint=None,
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
            verification_contract_version=settings.verification_contract_version,
            knowledge_snapshot=settings.knowledge_snapshot,
            verification_id=str(uuid4()),
        )
        store_verification(result)
        return result

    all_current_evidence = []
    assertion_results = []
    all_flags = list(flags)

    for assertion in assertions[:4]:
        current_evidence, retrieval_flags = _retrieve_current_for_assertion(
            assertion.text,
            request,
        )
        all_flags.extend(retrieval_flags)

        evaluated = _evaluate_evidence(
            assertion.text,
            current_evidence,
            risk,
            sorted(context_missing),
        )

        all_flags.extend(
            f"{assertion.id}:{warning}"
            for warning in evaluated["entailment_warnings"]
        )

        all_flags.extend(
            f"{assertion.id}:{item_id}:{warning}"
            for item_id, warning in evaluated["temporal_warnings"]
        )

        all_current_evidence.extend(
            evaluated["evidence"]
        )

        assertion_results.append({
            "id": assertion.id,
            "text": assertion.text,
            "verdict": evaluated["verdict"],
            "confidence": evaluated["confidence"],
            "reliability": evaluated["aggregate"],
            "reasons": evaluated["reasons"],
        })

    supported = [
        x for x in assertion_results
        if x["verdict"] == "SUPPORTED"
    ]
    contradicted = [
        x for x in assertion_results
        if x["verdict"] == "CONTRADICTED"
    ]
    mixed = [
        x for x in assertion_results
        if x["verdict"] == "MIXED_EVIDENCE"
    ]
    insufficient = [
        x for x in assertion_results
        if x["verdict"] in {
            "INSUFFICIENT_EVIDENCE",
            "CONTEXT_REQUIRED",
        }
    ]

    if supported and contradicted:
        current_verdict = "MIXED_EVIDENCE"
    elif mixed:
        current_verdict = "MIXED_EVIDENCE"
    elif contradicted and not supported and not insufficient:
        current_verdict = "CONTRADICTED"
    elif insufficient:
        current_verdict = "INSUFFICIENT_EVIDENCE"
    elif supported and len(supported) == len(assertion_results):
        current_verdict = "SUPPORTED"
    else:
        current_verdict = "INSUFFICIENT_EVIDENCE"

    final_evidence = []
    seen = set()

    for item in all_current_evidence:
        if item.id in seen:
            continue
        seen.add(item.id)
        final_evidence.append(item)

    current_agg = aggregate(final_evidence)

    if request.requested_evidence_level in {
        "authoritative",
        "highest_available",
    }:
        has_authoritative_support = any(
            item.supports is True
            and item.source_authority >= 0.85
            for item in final_evidence
        )

        if (
            current_verdict == "SUPPORTED"
            and not has_authoritative_support
        ):
            current_verdict = "INSUFFICIENT_EVIDENCE"

    if attack_severity == "high" and current_verdict == "SUPPORTED":
        current_verdict = "INSUFFICIENT_EVIDENCE"

    curriculum_items, curriculum_flags = _retrieve_curriculum_sources(
        normalized.normalized,
        request,
    )
    all_flags.extend(curriculum_flags)

    curriculum_assessment = assess_curriculum_fidelity(
        normalized.normalized,
        curriculum_items,
    )

    curriculum_source_dates = []

    for item in curriculum_items:
        observed = (
            item.source_date
            or item.effective_date
            or item.publication_date
        )
        if observed:
            curriculum_source_dates.append(observed)

    newest_curriculum_date = (
        max(curriculum_source_dates)
        if curriculum_source_dates
        else None
    )

    current_dates = [
        item.effective_date
        or item.publication_date
        for item in final_evidence
        if (
            item.effective_date
            or item.publication_date
        )
    ]

    relevant_current_items = [
        item
        for item in final_evidence
        if (
            item.supports is not None
            and item.relevance_score >= 0.35
            and (
                item.effective_date
                or item.publication_date
            )
        )
    ]

    relevant_current_dates = [
        item.effective_date
        or item.publication_date
        for item in relevant_current_items
    ]

    has_relevant_newer_evidence = bool(
        newest_curriculum_date
        and relevant_current_dates
        and max(relevant_current_dates) > newest_curriculum_date
    )

    divergence = determine_divergence(
        curriculum_status=curriculum_assessment.status,
        current_verdict=current_verdict,
        has_relevant_newer_evidence=has_relevant_newer_evidence,
    )

    if request.verification_mode == "current_medical":
        final_verdict = current_verdict
    elif request.verification_mode == "curriculum_faithful":
        if curriculum_assessment.status == "SOURCE_INTEGRITY_FAILED":
            final_verdict = "CURRICULUM_SOURCE_INTEGRITY_FAILED"
        elif curriculum_assessment.status == "ALIGNED":
            final_verdict = "CURRICULUM_ALIGNED"
        else:
            final_verdict = "CURRICULUM_NOT_ALIGNED"
    else:
        if curriculum_assessment.status == "SOURCE_INTEGRITY_FAILED":
            final_verdict = "CURRICULUM_SOURCE_INTEGRITY_FAILED"
        elif (
            curriculum_assessment.status == "ALIGNED"
            and divergence == "curriculum_vs_current_conflict"
        ):
            final_verdict = "CURRICULUM_ALIGNED_CURRENT_CONFLICT"
        elif curriculum_assessment.status == "ALIGNED":
            final_verdict = "CURRICULUM_ALIGNED"
        else:
            final_verdict = "CURRICULUM_NOT_ALIGNED"

    study_hint = build_study_hint(
        divergence,
        final_evidence,
    )

    confidence = 0.0

    if final_verdict in {
        "SUPPORTED",
        "CURRICULUM_ALIGNED",
    }:
        confidence = min(
            0.96,
            round(
                0.55
                + 0.41 * (
                    current_agg["support_ratio"]
                    if final_verdict == "SUPPORTED"
                    else 0.90
                ),
                3,
            ),
        )
    elif final_verdict == "MIXED_EVIDENCE":
        confidence = 0.40
    elif final_verdict == "CONTRADICTED":
        confidence = 0.72
    elif final_verdict == "CURRICULUM_ALIGNED_CURRENT_CONFLICT":
        confidence = 0.85

    source_revalidation = {}

    should_revalidate = (
        risk in {"high", "critical"}
        or (
            request.verification_mode != "current_medical"
            and divergence == "curriculum_vs_current_conflict"
        )
    )

    if should_revalidate:
        for item in final_evidence:
            check = revalidate(item)
            source_revalidation[item.id] = {
                "status": check.status,
                "checked_at": check.checked_at,
                "warnings": check.warnings,
                "remote_snapshot_sha256": check.remote_snapshot_sha256,
            }
            all_flags.extend(
                f"{item.id}:{warning}"
                for warning in check.warnings
            )

    revalidation_blocked = any(
        details["status"] in {"CHANGED", "NOT_FOUND", "ERROR", "UNVERIFIABLE"}
        for details in source_revalidation.values()
    )

    if revalidation_blocked:
        current_verdict = "INSUFFICIENT_EVIDENCE"

        if request.verification_mode == "current_medical":
            final_verdict = "INSUFFICIENT_EVIDENCE"

        all_flags.append(
            "source_revalidation_blocked_automatic_support"
        )

    report_reasons = []

    for result in assertion_results:
        report_reasons.extend(
            result["reasons"]
        )

    if request.verification_mode != "current_medical":
        report_reasons.extend(
            curriculum_assessment.reasons
        )

    reliability = build_report(
        current_agg,
        final_evidence,
        sorted(set(report_reasons)),
    )

    reliability["policy_version"] = POLICY_VERSION
    reliability["atomic_assertions"] = assertion_results
    reliability["adversarial_severity"] = attack_severity
    reliability["has_relevant_newer_evidence"] = has_relevant_newer_evidence

    limitations = sorted(set(all_flags))

    if request.verification_mode != "current_medical":
        limitations.append(
            "Curriculum mode preserves source-faithful correctness separately "
            "from current medical evidence."
        )

    if divergence == "curriculum_vs_current_conflict":
        limitations.append(
            "Supplied curriculum and current evidence are in material disagreement."
        )

    if not final_evidence:
        limitations.append("No current external evidence was retrieved.")

    if request.verification_mode != "current_medical" and not curriculum_items:
        limitations.append(
            "No curriculum source snapshot was available for source-faithful verification."
        )

    if current_verdict == "INSUFFICIENT_EVIDENCE":
        limitations.append(
            "Current evidence did not meet the multi-axis verification threshold."
        )

    if current_verdict == "MIXED_EVIDENCE":
        limitations.append(
            "Material current-evidence disagreement remains."
        )

    if curriculum_assessment.status == "SOURCE_INTEGRITY_FAILED":
        limitations.append(
            "Stored curriculum evidence failed integrity validation."
        )

    limitations.append(
        "Curriculum material does not authorize clinical action."
    )

    requires_review = (
        risk in {"moderate", "high"}
        or attack_severity in {"moderate", "high"}
        or current_verdict in {
            "MIXED_EVIDENCE",
            "CONTEXT_REQUIRED",
            "CONTRADICTED",
        }
        or divergence == "curriculum_vs_current_conflict"
        or curriculum_assessment.status == "SOURCE_INTEGRITY_FAILED"
        or bool(context_missing)
        or revalidation_blocked
    )

    result = VerificationResponse(
        verdict=final_verdict,
        confidence=confidence,
        confidence_semantics=(
            "Policy score only; not a calibrated probability of clinical truth."
        ),
        claim=request.claim,
        normalized_claim=normalized.normalized,
        claim_type=normalized.claim_type,
        risk_level=risk,
        verification_mode=request.verification_mode,
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
        curriculum_assessment={
            "status": curriculum_assessment.status,
            "matched_source_ids": curriculum_assessment.matched_source_ids,
            "reasons": curriculum_assessment.reasons,
            "source_dates": curriculum_assessment.source_dates,
        },
        current_evidence_assessment={
            "verdict": current_verdict,
            "reliability": current_agg,
            "evidence_ids": [
                item.id
                for item in final_evidence
            ],
            "has_relevant_newer_evidence": has_relevant_newer_evidence,
        "source_revalidation_blocked": revalidation_blocked,
        },
        knowledge_divergence=divergence,
        study_hint=study_hint,
        source_revalidation=source_revalidation,
        reliability=reliability,
        adversarial_findings=adversarial_data,
        limitations=limitations,
        decision_reasons=sorted(set(report_reasons)),
        missing_context=sorted(context_missing),
        requires_human_review=requires_review,
        verifier_version=settings.verifier_version,
        verification_contract_version=settings.verification_contract_version,
        knowledge_snapshot=settings.knowledge_snapshot,
        verification_id=str(uuid4()),
    )

    store_verification(result)
    return result
