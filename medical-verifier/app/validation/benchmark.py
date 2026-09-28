from dataclasses import dataclass
from math import sqrt

ALLOWED_GROUND_TRUTH = {
    "SUPPORTED",
    "CONTRADICTED",
    "MIXED_EVIDENCE",
    "INSUFFICIENT_EVIDENCE",
    "SAFETY_ESCALATION",
    "CURRICULUM_ALIGNED",
    "CURRICULUM_NOT_ALIGNED",
}

@dataclass(frozen=True)
class BenchmarkCase:
    case_id: str
    claim: str
    expected_verdict: str
    expected_support_probability: float | None
    risk_level: str
    clinician_reviewed: bool
    evidence_ids: tuple[str, ...] = ()
    population: str | None = None
    source_era: str | None = None

def validate_case(case: BenchmarkCase):
    errors = []

    if case.expected_verdict not in ALLOWED_GROUND_TRUTH:
        errors.append("invalid_ground_truth_verdict")

    if not case.clinician_reviewed:
        errors.append("case_not_clinician_reviewed")

    if case.expected_support_probability is not None:
        if not 0.0 <= case.expected_support_probability <= 1.0:
            errors.append("invalid_expected_probability")

    if not case.claim.strip():
        errors.append("empty_claim")

    return errors

def binary_truth(verdict: str):
    if verdict in {
        "SUPPORTED",
        "CURRICULUM_ALIGNED",
    }:
        return 1

    if verdict in {
        "CONTRADICTED",
        "SAFETY_ESCALATION",
        "CURRICULUM_NOT_ALIGNED",
    }:
        return 0

    return None

def evaluate_binary(predictions):
    # predictions = [(predicted_probability, predicted_verdict, truth_verdict), ...]
    usable = [
        row for row in predictions
        if binary_truth(row[2]) is not None
    ]

    tp = fp = tn = fn = 0
    abstain = 0

    for probability, predicted, truth in usable:
        y = binary_truth(truth)

        if predicted in {"INSUFFICIENT_EVIDENCE", "MIXED_EVIDENCE"}:
            abstain += 1
            continue

        pred = binary_truth(predicted)

        if pred == 1 and y == 1:
            tp += 1
        elif pred == 1 and y == 0:
            fp += 1
        elif pred == 0 and y == 0:
            tn += 1
        elif pred == 0 and y == 1:
            fn += 1

    total = max(1, tp + fp + tn + fn + abstain)

    sensitivity = tp / max(1, tp + fn)
    specificity = tn / max(1, tn + fp)
    ppv = tp / max(1, tp + fp)
    npv = tn / max(1, tn + fn)

    return {
        "n": len(usable),
        "tp": tp,
        "fp": fp,
        "tn": tn,
        "fn": fn,
        "abstain": abstain,
        "abstention_rate": abstain / total,
        "sensitivity": sensitivity,
        "specificity": specificity,
        "ppv": ppv,
        "npv": npv,
    }

def brier_score(probabilities, truths):
    if not probabilities:
        return None

    return sum(
        (p - y) ** 2
        for p, y in zip(probabilities, truths)
    ) / len(probabilities)

def expected_calibration_error(probabilities, truths, bins=10):
    if not probabilities:
        return None

    buckets = [
        []
        for _ in range(bins)
    ]

    for probability, truth in zip(probabilities, truths):
        index = min(
            bins - 1,
            int(probability * bins),
        )
        buckets[index].append(
            (probability, truth)
        )

    total = len(probabilities)
    error = 0.0

    for bucket in buckets:
        if not bucket:
            continue

        confidence = sum(
            item[0]
            for item in bucket
        ) / len(bucket)

        accuracy = sum(
            item[1]
            for item in bucket
        ) / len(bucket)

        error += (
            len(bucket) / total
        ) * abs(confidence - accuracy)

    return error
