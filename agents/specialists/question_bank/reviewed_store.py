"""Export an accepted evidence-loop item to the reviewed question store.

Each record is the verifier's own QuestionArtifact (api/schemas/question.py)
filled per docs/architecture/verifier/QUESTION_GENERATION_CONTRACT.md: source
ids, versions, locators, passage hashes and snapshot hashes, the SOURCE status
from validate_question, plus the claim units, the AI review trail and a
per-item attribution notice for every parent source.

The store, not the raw sources, is what any later training may draw from, and
only items whose review state allows it. Nothing here is clinician-adjudicated:
requires_human_review stays true and clinical_release_allowed stays false.
"""
from datetime import datetime, timezone
from pathlib import Path

from api.schemas.question import QuestionArtifact
from agents.specialists.question_bank.evidence_loop import (
    ACCEPTED,
    SECTIONS,
    audit_run,
    digest_file,
    object_hash,
    require,
    write_new,
)
from agents.specialists.question_bank.licence_gate import (
    attribution_notice,
    licence_decision,
    require_licensed,
)

GENERATOR_VERSION = "question_bank.evidence_loop/1"
MODIFICATIONS = (
    "Selected passages adapted into original AI-written educational question, answer "
    "and explanation, checked by the medical verifier and by separate AI support and "
    "challenge reviews. Not clinician-adjudicated."
)


def _cited(item):
    refs = []
    for section in SECTIONS:
        for claim in item[section]:
            for ref in claim["evidence"]:
                key = (ref["source_id"], ref["locator"])
                if key not in refs:
                    refs.append(key)
    return refs


def export_reviewed(run, store, exported_at=None):
    """Write one reviewed record; refuse anything not accepted or changed."""
    run, store = Path(run), Path(store)
    report = audit_run(run)
    require(report["status"] == ACCEPTED, "Run has not passed the evidence loop")
    packet = report["evidence"]
    require_licensed(packet["sources"])
    item = report["item"]
    item_sha = object_hash(item)
    checks = report["deterministic_checks"][-1]
    require(checks["passed"] and checks["question_validation"]["status"] == "VALIDATED",
            "Verifier checks did not validate the item")

    rows = {(row["source_id"], row["locator"]): row for row in packet["paragraphs"]}
    sources = {source["source_id"]: source for source in packet["sources"]}
    for section in SECTIONS:
        for claim in item[section]:
            for ref in claim["evidence"]:
                row = rows[(ref["source_id"], ref["locator"])]
                require(row["split"] == "train" and row["text_sha256"] == ref["text_sha256"]
                        and ref["quote"] in row["text"], "Changed source or quote")

    cited = _cited(item)
    evidence_ids = [f"{source_id}#{locator}" for source_id, locator in cited]
    parent_ids = sorted({source_id for source_id, _ in cited})
    question_id = f"qbank:{item_sha[:32]}"
    artifact = QuestionArtifact(
        question_id=question_id,
        prompt=" ".join(c["text"] for c in item["question"]),
        answer=" ".join(c["text"] for c in item["answer"]),
        curriculum_snapshot_id=f"evidence:{report['evidence_sha256']}",
        source_evidence_ids=evidence_ids,
        source_locators=[locator for _, locator in cited],
        source_versions=sorted({sources[s]["version"] for s in parent_ids
                                if sources[s].get("version")}),
        source_passage_hashes={
            f"{s}#{loc}": rows[(s, loc)]["text_sha256"] for s, loc in cited},
        source_snapshot_hashes={
            f"{s}#{loc}": rows[(s, loc)]["text_sha256"] for s, loc in cited},
        validation_status=checks["question_validation"]["status"],
        validation_warnings=checks["question_validation"]["warnings"],
        supporting_source_ids=sorted(set(
            checks["question_validation"]["supporting_source_ids"])),
        requires_human_review=True,
        generated_at=exported_at or datetime.now(timezone.utc).isoformat(),
        generator_version=GENERATOR_VERSION,
        verification_mode="curriculum_faithful",
    )
    record = {
        "schema_version": 1,
        "question": artifact.model_dump(mode="json"),
        "explanation": " ".join(c["text"] for c in item["explanation"]),
        "item": item,
        "item_sha256": item_sha,
        "current_evidence_status": "not_checked",
        "review": {
            "state": "ai_reviewed",
            "kinds": ["verifier_deterministic", "ai_support", "ai_challenge"],
            "rounds_used": report["round"],
            "max_rounds": report["max_rounds"],
            "actors_self_reported": report["actors"],
            "clinically_adjudicated": False,
            "human_expert_reviewed": False,
        },
        "provenance": {
            "run_journal_sha256": digest_file(run / "journal.jsonl"),
            "evidence_sha256": report["evidence_sha256"],
            "parent_source_ids": parent_ids,
            "deterministic_checks": checks,
        },
        "licences": {s: licence_decision(sources[s]).licence for s in parent_ids},
        "attribution": [
            {"source_id": s, "notice": attribution_notice(sources[s], MODIFICATIONS)}
            for s in parent_ids
        ],
        "clinical_release_allowed": False,
    }
    path = store / "items" / f"{item_sha}.json"
    require(not path.exists(), "Item already in the reviewed store")
    write_new(path, record)
    return record
