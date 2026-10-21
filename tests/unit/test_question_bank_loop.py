"""Evidence-loop integrity tests for agents/specialists/question_bank.

Ported from research/amboss-uworld/model/test_verify_loop.py (success,
revision, quarantine, restart, export, rejection) and extended with the
verifier's deterministic checks and the licence gate. Every record and agent
judgment below is a fabricated software fixture, never a clinical label.
"""
import copy
import json
from pathlib import Path

import pytest

from agents.specialists.question_bank import evidence_loop as loop
from agents.specialists.question_bank.licence_gate import (
    LicenceRefused,
    attribution_notice,
    licence_decision,
)
from agents.specialists.question_bank.reviewed_store import export_reviewed
from agents.specialists.question_bank.source_list import ALLOWED_SOURCES
from agents.specialists.retrieval_agent.citation_integrity import sha256_text

TEXT = "Fixture paragraph: insulin lowers blood glucose."
HELDOUT = "Held-out fixture paragraph: glucagon raises blood glucose."
CC_BY = "https://creativecommons.org/licenses/by/4.0/"


def source(source_id="PMC101", **changes):
    value = {
        "source_id": source_id,
        "family": "pmc-oa",
        "version": source_id + ".1",
        "title": "Software fixture article",
        "authors": ["Software Fixture"],
        "license": "CC-BY-4.0",
        "license_url": CC_BY,
        "license_notice": "Fabricated fixture notice, no clinical evidence.",
        "evidence_url": "https://example.test/" + source_id,
        "split": "train",
    }
    value.update(changes)
    return value


def paragraph(source_id="PMC101", text=TEXT, split="train"):
    return {"source_id": source_id, "locator": "body/p[1]", "text": text, "split": split}


class Harness:
    def __init__(self, root: Path):
        self.root = root
        self.run = root / "run"
        self.source = source()
        self.row = paragraph()

    def start(self, rounds=2, sources=None, paragraphs=None):
        return loop.initialize(
            self.run,
            sources if sources is not None else [self.source],
            paragraphs if paragraphs is not None else [self.row],
            "Software fixture; no clinical validation",
            rounds,
        )

    def reference(self, quote="insulin lowers blood glucose"):
        return {"source_id": self.row["source_id"], "locator": self.row["locator"],
                "text_sha256": sha256_text(self.row["text"]), "quote": quote}

    def item(self, answer="Insulin lowers blood glucose."):
        texts = [("question", "What does insulin do to blood glucose?"),
                 ("answer", answer),
                 ("explanation", "Insulin lowers blood glucose.")]
        return {"item_id": "software-fixture", **{
            section: [{"claim_id": section + "-1", "text": text,
                       "evidence": [self.reference()]}]
            for section, text in texts}}

    def payload(self, task, verdict="supported", answer=None):
        if task["role"] == "writer":
            return {"item": self.item(answer) if answer else self.item()}
        return {
            "claim_verdicts": [
                {"claim_id": claim["claim_id"], "verdict": verdict,
                 "reason": "Software fixture judgment, not a clinical label.",
                 "evidence": [self.reference()]}
                for section in loop.SECTIONS for claim in task["item"][section]],
            "checks": {name: True for name in loop.CHECKS},
            "source_checks": [{"source_id": self.source["source_id"],
                               "license_scope_ok": True, "current_applicability_ok": True,
                               "reason": "Fabricated source fixture only."}],
        }

    def reply(self, role, round_number=1, verdict="supported", answer=None):
        path = self.run / "tasks" / f"{role}-{round_number}.json"
        task = loop.read_json(path)
        reply = {"task_id": task["task_id"], "task_sha256": loop.digest_file(path),
                 "evidence_sha256": task["evidence_sha256"],
                 "actor": {"id": role, "model": "mock-fixture-model", "kind": "ai"},
                 **self.payload(task, verdict, answer)}
        if role != "writer":
            reply["item_sha256"] = task["item_sha256"]
        return reply

    def put(self, role, round_number=1, verdict="supported", mutate=None, answer=None):
        reply = self.reply(role, round_number, verdict, answer)
        if mutate:
            mutate(reply)
        (self.run / "replies" / f"{role}-{round_number}.json").write_text(json.dumps(reply))

    def writer_done(self, rounds=2):
        self.start(rounds)
        self.put("writer")
        return loop.step(self.run)

    def accept(self):
        self.writer_done()
        self.put("support")
        self.put("challenge")
        return loop.step(self.run)


@pytest.fixture
def h(tmp_path):
    return Harness(tmp_path)


# ------------------------------------------------------------------ success

def test_success_is_ai_reviewed_only_and_reviews_are_blind(h):
    report = h.writer_done()
    assert report["status"] == "awaiting_reviewers"
    checks = report["deterministic_checks"][0]
    assert checks["passed"] is True
    assert checks["question_validation"]["status"] == "VALIDATED"
    assert all(entry["warnings"] == [] for entry in checks["citation_integrity"])
    support = loop.read_json(h.run / "tasks/support-1.json")
    challenge = loop.read_json(h.run / "tasks/challenge-1.json")
    for task in (support, challenge):
        for hidden in ("actor", "revision_feedback", "reviews", "claim_verdicts"):
            assert hidden not in task
    differing = {"task_id", "role", "instructions"}
    assert {k: v for k, v in support.items() if k not in differing} == {
        k: v for k, v in challenge.items() if k not in differing}
    h.put("support")
    h.put("challenge")
    report = loop.step(h.run)
    assert report["status"] == loop.ACCEPTED
    for key in ("clinical_release_allowed", "clinical_validation", "accuracy_proven",
                "actor_identities_authenticated"):
        assert report[key] is False
    assert loop.audit_run(h.run)["item_sha256"] == loop.object_hash(h.item())


# ------------------------------------------------- revision and quarantine

def test_insufficient_support_revises_then_quarantines_at_bound(h):
    h.writer_done()
    original = (h.run / "tasks/writer-1.json").read_bytes()
    for round_number in (1, 2):
        h.put("support", round_number, "insufficient")
        h.put("challenge", round_number)
        report = loop.step(h.run)
        if round_number == 1:
            assert report["status"] == "awaiting_writer"
            revision = loop.read_json(h.run / "tasks/writer-2.json")
            assert "support" in revision["revision_feedback"]
            h.put("writer", 2)
            loop.step(h.run)
    assert report["status"] == loop.QUARANTINED
    assert report["round"] == 2
    assert not (h.run / "tasks/writer-3.json").exists()
    assert (h.run / "tasks/writer-1.json").read_bytes() == original
    assert report["clinical_release_allowed"] is False


def test_verifier_checks_run_before_any_ai_review(h):
    h.start(rounds=2)
    # Quotes are exact, so the mechanical checks pass; entailment does not.
    h.put("writer", answer="Insulin raises blood glucose.")
    report = loop.step(h.run)
    assert report["status"] == "awaiting_writer"
    assert not (h.run / "tasks/support-1.json").exists()
    assert not (h.run / "tasks/challenge-1.json").exists()
    findings = report["deterministic_checks"][0]["findings"]
    assert "independent_entailment:answer-1:not_supported" in findings
    assert any(f.startswith("question_validation:SOURCE_") for f in findings)
    revision = loop.read_json(h.run / "tasks/writer-2.json")
    assert "deterministic_checks" in revision["revision_feedback"]
    h.put("writer", 2, answer="Insulin raises blood glucose.")
    report = loop.step(h.run)
    assert report["status"] == loop.QUARANTINED
    assert not (h.run / "tasks/support-2.json").exists()


# ---------------------------------------------------------------- rejection

def test_missing_duplicate_and_stale_claim_reviews_are_rejected(h):
    h.writer_done()
    task_path = h.run / "tasks/support-1.json"
    task = loop.read_json(task_path)
    good = h.reply("support")
    variants = []
    missing = copy.deepcopy(good)
    missing["claim_verdicts"].pop()
    variants.append(missing)
    duplicate = copy.deepcopy(good)
    duplicate["claim_verdicts"][1] = copy.deepcopy(duplicate["claim_verdicts"][0])
    variants.append(duplicate)
    for key in ("item_sha256", "task_sha256", "evidence_sha256"):
        stale = copy.deepcopy(good)
        stale[key] = "0" * 64
        variants.append(stale)
    for broken in variants:
        with pytest.raises(ValueError):
            loop.validate_reply(broken, task, loop.digest_file(task_path), task["evidence"])


def test_misquoted_unit_is_refused(h):
    h.start()
    task_path = h.run / "tasks/writer-1.json"
    task = loop.read_json(task_path)
    broken = h.reply("writer")
    broken["item"]["answer"][0]["evidence"][0]["quote"] = "insulin lowers blood pressure"
    with pytest.raises(ValueError, match="Misquoted unit"):
        loop.validate_reply(broken, task, loop.digest_file(task_path), task["evidence"])
    (h.run / "replies/writer-1.json").write_text(json.dumps(broken))
    with pytest.raises(ValueError, match="Misquoted unit"):
        loop.step(h.run)
    indexed, _, _ = loop.journal(h.run)
    assert "replies/writer-1.json" not in indexed


def test_broken_evidence_references_never_pass(h):
    h.start()
    task_path = h.run / "tasks/writer-1.json"
    task = loop.read_json(task_path)
    for key, value in (("quote", "This invented phrase is absent from the source."),
                       ("source_id", "PMC202"), ("text_sha256", "0" * 64)):
        broken = h.reply("writer")
        broken["item"]["answer"][0]["evidence"][0][key] = value
        with pytest.raises(ValueError):
            loop.validate_reply(broken, task, loop.digest_file(task_path), task["evidence"])


def test_heldout_evidence_cannot_enter_a_writer_packet(h):
    with pytest.raises(ValueError):
        h.start(sources=[source("PMC202", split="validation")],
                paragraphs=[paragraph("PMC202", HELDOUT, "validation")])
    assert not h.run.exists()


def test_every_selected_source_requires_explicit_review(h):
    h.writer_done()
    task_path = h.run / "tasks/support-1.json"
    task = loop.read_json(task_path)
    missing = h.reply("support")
    missing["source_checks"] = []
    with pytest.raises(ValueError):
        loop.validate_reply(missing, task, loop.digest_file(task_path), task["evidence"])
    h.put("support", mutate=lambda r: r["source_checks"][0].update(license_scope_ok=False))
    h.put("challenge")
    assert loop.step(h.run)["status"] == "awaiting_writer"


def test_actor_cannot_review_own_output_or_claim_human_review(h):
    h.writer_done()
    h.put("support", mutate=lambda r: r["actor"].update(id="writer"))
    with pytest.raises(ValueError):
        loop.step(h.run)
    (h.run / "replies/support-1.json").unlink()
    h.put("support", mutate=lambda r: r["actor"].update(kind="human"))
    with pytest.raises(ValueError):
        loop.step(h.run)


# ------------------------------------------------------------------ licence

def test_non_cc_by_source_is_refused_before_a_run_starts(h):
    refused = [
        source(license="CC-BY-NC-4.0"),
        source(license="CC BY-NC-SA 4.0"),
        source(license="CC-BY-ND-4.0"),
        source(license="CC-BY-SA-4.0", license_url="https://creativecommons.org/licenses/by-sa/4.0/"),
        source(license=""),
        source(license="UNKNOWN"),
        source(license="MIT"),
        source(license_notice=""),
        source(license_url="https://creativecommons.org/licenses/by-nc/4.0/"),
        source(license_notice="This article is CC BY-NC 4.0."),
    ]
    for bad in refused:
        h.source = bad
        with pytest.raises(LicenceRefused):
            h.start()
        assert not h.run.exists()


@pytest.mark.parametrize("family", [
    "openstax-anatomy-physiology-2e", "nice", "who", "medlineplus", "statpearls",
    "medmcqa", "medqa", "mmlu",
])
def test_refused_families_fail_even_when_relabelled_cc_by(family):
    decision = licence_decision(source("X1", family=family))
    assert decision.allowed is False
    assert decision.reason.startswith("refused_source:" + family)


def test_openfda_is_cc0_unless_flagged_and_pmc_is_per_article():
    cc0 = "https://creativecommons.org/publicdomain/zero/1.0/"
    label = {"source_id": "openfda:spl-1", "family": "openfda", "license": "CC0",
             "license_url": cc0, "title": "Fixture label", "publisher": "openFDA"}
    assert licence_decision(label).allowed is True
    assert licence_decision({**label, "licence_flags": ["third_party_image"]}).allowed is False
    assert licence_decision({**label, "license": "CC-BY-4.0", "license_url": CC_BY}).allowed is False
    assert licence_decision(source()).allowed is True
    assert licence_decision(source(license="CC0-1.0", license_url=cc0)).allowed is True
    assert licence_decision(source("not-a-pmcid")).allowed is False
    assert "CC0" in attribution_notice(label, "fixture change")
    for listed in ALLOWED_SOURCES:
        # Authors arrive at ingestion; until then the gate refuses attribution.
        assert licence_decision(listed).reason.startswith("attribution_unavailable")
        assert licence_decision({**listed, "authors": ["A"],
                                 "license_notice": "CC BY 4.0"}).allowed is True


# -------------------------------------------------------------- restart

def test_restart_resumes_from_files_and_rejects_changed_history(h):
    h.writer_done()
    before = sorted(p.name for p in (h.run / "tasks").iterdir())
    again = loop.step(h.run)
    assert again["status"] == "awaiting_reviewers"
    assert sorted(p.name for p in (h.run / "tasks").iterdir()) == before
    h.put("support")
    h.put("challenge")
    assert loop.step(h.run)["status"] == loop.ACCEPTED

    reply_path = h.run / "replies/writer-1.json"
    original = reply_path.read_bytes()
    reply_path.write_bytes(original + b" ")
    with pytest.raises(ValueError):
        loop.audit_run(h.run)
    reply_path.write_bytes(original)
    journal_path = h.run / "journal.jsonl"
    lines = journal_path.read_text().splitlines()
    first = json.loads(lines[0])
    first["previous"] = "f" * 64
    lines[0] = json.dumps(first)
    journal_path.write_text("\n".join(lines) + "\n")
    with pytest.raises(ValueError):
        loop.audit_run(h.run)


# ------------------------------------------------------------------ export

def test_export_requires_acceptance_and_keeps_provenance(h):
    h.writer_done()
    store = h.root / "store"
    with pytest.raises(ValueError):
        export_reviewed(h.run, store)
    assert not store.exists()
    h.put("support")
    h.put("challenge")
    loop.step(h.run)
    record = export_reviewed(h.run, store, exported_at="2026-10-21T00:00:00+00:00")
    question = record["question"]
    assert question["validation_status"] == "VALIDATED"
    assert question["requires_human_review"] is True
    assert question["source_evidence_ids"] == ["PMC101#body/p[1]"]
    assert question["source_locators"] == ["body/p[1]"]
    assert question["source_versions"] == ["PMC101.1"]
    assert question["source_passage_hashes"] == {"PMC101#body/p[1]": sha256_text(TEXT)}
    assert question["supporting_source_ids"] == ["PMC101#body/p[1]"]
    assert record["review"]["clinically_adjudicated"] is False
    assert record["clinical_release_allowed"] is False
    assert record["current_evidence_status"] == "not_checked"
    assert record["licences"] == {"PMC101": "CC-BY-4.0"}
    notice = record["attribution"][0]["notice"]
    assert "Software Fixture" in notice and CC_BY in notice and "Changes:" in notice
    stored = json.loads((store / "items" / f"{record['item_sha256']}.json").read_text())
    assert stored == record
    with pytest.raises(ValueError):
        export_reviewed(h.run, store)
    evidence = h.run / "evidence.json"
    evidence.write_text(evidence.read_text().replace("insulin lowers", "insulin raises"))
    with pytest.raises(ValueError):
        export_reviewed(h.run, h.root / "changed")


def test_export_refuses_a_quarantined_run(h):
    h.writer_done(rounds=1)
    h.put("support", verdict="contradicted")
    h.put("challenge")
    assert loop.step(h.run)["status"] == loop.QUARANTINED
    with pytest.raises(ValueError):
        export_reviewed(h.run, h.root / "store")


# ------------------------------------------------------- no network, no key

def test_package_makes_no_network_call_and_reads_no_key():
    package = Path(loop.__file__).parent
    banned = ("urllib", "httpx", "requests", "socket", "http.client", "api_key",
              "os.environ", "getenv", "settings")
    for path in package.glob("*.py"):
        text = path.read_text()
        for word in banned:
            assert word not in text, f"{path.name} mentions {word}"
