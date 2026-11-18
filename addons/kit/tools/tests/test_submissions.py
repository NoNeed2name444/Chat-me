"""A6: sample pull requests, checked by check.yml's own Python steps.

Each folder in addons/fixtures/submissions/ is one pull request:
- base/: the add-ons already on main (optional);
- pr/: the files the pull request adds or changes;
- files.json (optional): files whose bytes can't be committed, such as PNG
  bytes, as {"path": [pieces]}, each piece a string, {"hex": ...} or
  {"text": ..., "times": n};
- expect.json: {"verdict": "pass" or "fail", "reasons": [codes]}.

The test builds the two checkouts check.yml makes: pr/, a Git repository
with a base commit and the pull request's commit, and trusted/, the
directory's files with the kit. Then it runs each of check.yml's steps that
calls Python, word for word, in order, the way GitHub runs them: a step
runs only when the ones before it passed, unless it says if: always().
The harness step runs a stand-in harness that records each call; the real
harness, and its build step, are S07b's.
"""
import json
import os
import shutil
import stat
import subprocess
import unittest
from pathlib import Path

from common import DIRECTORY, KIT, SUBMISSIONS, Scratch, folders, read_json

import yamlish

STAND_IN = """#!/usr/bin/env python3
import json, os, sys
with open(os.environ["HARNESS_LOG"], "a", encoding="utf-8") as log:
    log.write(json.dumps(sys.argv[1:]) + "\\n")
"""
GIT_ENV = {"GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "", "GIT_COMMITTER_NAME": "Test",
           "GIT_COMMITTER_EMAIL": "", "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_NOSYSTEM": "1"}
A6 = {"review.picture", "review.binary", "review.host", "review.paid", "review.points", "review.version",
      "review.folder", "review.base64", "review.line"}


def pieces(parts: list) -> bytes:
    out = b""
    for part in parts:
        if isinstance(part, str):
            out += part.encode("utf-8")
        elif "hex" in part:
            out += bytes.fromhex(part["hex"])
        else:
            out += part["text"].encode("utf-8") * part["times"]
    return out


def copy_tree(source: Path, target: Path) -> None:
    if source.is_dir():
        shutil.copytree(source, target, dirs_exist_ok=True, ignore=shutil.ignore_patterns("__pycache__"))


def git(repo: Path, *args: str) -> str:
    done = subprocess.run(["git", "-C", str(repo), "-c", "commit.gpgsign=false", *args], capture_output=True,
                          text=True, env={**os.environ, **GIT_ENV})
    if done.returncode:
        raise AssertionError(f"git {' '.join(args)}: {done.stderr}")
    return done.stdout.strip()


def python_steps() -> list[dict]:
    flow = yamlish.load((DIRECTORY / ".github" / "workflows" / "check.yml").read_text(encoding="utf-8"))
    return [step for job in flow["jobs"].values() for step in job["steps"]
            if str(step.get("run", "")).startswith("python3 -I ")]


def workspace(case: Path, top: Path) -> str:
    """Builds trusted/ and pr/ under top, and returns the base commit."""
    copy_tree(DIRECTORY, top / "trusted")
    copy_tree(KIT, top / "trusted" / "kit")
    harness = top / "trusted" / "kit" / "harness" / "harness"
    harness.parent.mkdir(parents=True, exist_ok=True)
    harness.write_text(STAND_IN, encoding="utf-8")
    harness.chmod(harness.stat().st_mode | stat.S_IXUSR)
    repo = top / "pr"
    repo.mkdir()
    git(repo, "init", "--quiet", "--initial-branch=main")
    copy_tree(DIRECTORY, repo)
    copy_tree(case / "base", repo)
    git(repo, "add", "--all", ".")
    git(repo, "commit", "--quiet", "-m", "main")
    base = git(repo, "rev-parse", "HEAD")
    copy_tree(case / "pr", repo)
    if (case / "files.json").exists():
        for name, parts in read_json(case / "files.json").items():
            path = repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(pieces(parts))
    git(repo, "add", "--all", ".")
    git(repo, "commit", "--quiet", "-m", "pull request")
    return base


def run_steps(top: Path, base: str) -> list[tuple[dict, subprocess.CompletedProcess | None]]:
    env = {**os.environ, "GITHUB_STEP_SUMMARY": str(top / "summary.md"), "HARNESS_LOG": str(top / "harness.log")}
    (top / "summary.md").write_text("")
    (top / "harness.log").write_text("")
    results, failed = [], False
    for step in python_steps():
        if failed and step.get("if") != "always()":
            results.append((step, None))
            continue
        step_env = {key: (base if value == "${{ github.event.pull_request.base.sha }}" else value)
                    for key, value in step.get("env", {}).items()}
        done = subprocess.run(["bash", "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", step["run"]],
                              cwd=top, env={**env, **step_env}, capture_output=True, text=True)
        failed = failed or done.returncode != 0
        results.append((step, done))
    return results


def codes(output: str) -> set[str]:
    return {line.split(" ", 1)[0] for line in output.splitlines()
            if " addons/" in line or " revoked.json" in line}


class Submissions(unittest.TestCase):
    def test_check_yml_has_the_python_steps(self):
        modes = [step["run"].split()[3] for step in python_steps()]
        self.assertEqual(modes, ["pr", "summary", "harness"])

    def test_each_sample_gets_its_verdict_and_reason(self):
        cases = folders(SUBMISSIONS)
        self.assertGreaterEqual(len(cases), 14)
        for case in cases:
            expect = read_json(case / "expect.json")
            with self.subTest(case.name), Scratch() as top:
                base = workspace(case, top)
                results = run_steps(top, base)
                review, summary, harness = (done for _, done in results)
                self.assertEqual(summary.returncode, 0, summary.stderr)
                self.assertTrue((top / "summary.md").read_text().startswith("## Permissions and hosts"))
                if expect["verdict"] == "pass":
                    self.assertEqual(review.returncode, 0, review.stdout + review.stderr)
                    self.assertEqual(review.stdout, "no problems\n")
                    self.assertIsNotNone(harness)
                    self.assertEqual(harness.returncode, 0, harness.stdout + harness.stderr)
                    self.assertEqual(expect["reasons"], [])
                else:
                    self.assertEqual(review.returncode, 1, review.stdout + review.stderr)
                    self.assertEqual(review.stderr, "")
                    self.assertEqual(codes(review.stdout), set(expect["reasons"]), review.stdout)
                    self.assertIsNone(harness, "the harness step runs only after the checks pass")

    def test_the_bad_samples_cover_a6(self):
        reasons = set()
        for case in folders(SUBMISSIONS):
            expect = read_json(case / "expect.json")
            if expect["verdict"] == "fail":
                self.assertEqual(len(expect["reasons"]), 1, case.name)
                reasons.update(expect["reasons"])
        self.assertTrue(A6 <= reasons, A6 - reasons)
        self.assertTrue(any(not r.startswith("review.") for r in reasons), "a bad manifest needs a sample too")

    def test_the_picture_sample_is_png_bytes_in_a_json_file(self):
        files = read_json(SUBMISSIONS / "bad-picture" / "files.json")
        [(name, parts)] = files.items()
        self.assertTrue(name.endswith(".json"))
        self.assertTrue(pieces(parts).startswith(b"\x89PNG\r\n\x1a\n"))

    def test_the_line_and_base64_samples_are_the_sizes_a6_names(self):
        line = pieces(read_json(SUBMISSIONS / "bad-line" / "files.json").popitem()[1]).decode()
        self.assertGreaterEqual(max(len(text) for text in line.split("\n")), 600)
        blob = pieces(read_json(SUBMISSIONS / "bad-base64" / "files.json").popitem()[1]).decode()
        self.assertGreater(len(json.loads(blob)["blob"]), 1024)

    def test_the_summary_and_harness_calls_for_the_good_samples(self):
        wanted = {
            "good-new": (["io.github.example.tidy-titles"], [["--command", "tidy"]]),
            "good-update": (["io.github.example.daycount", "adds cards"], [["--command", "oldest"],
                                                                           ["--card", "age"]]),
            "good-net": (["io.github.example.rhymes", "api.example.org"], [["--command", "rhyme"]]),
            "good-revoke": (["adds no add-on version"], []),
        }
        for name, (words, calls) in wanted.items():
            with self.subTest(name), Scratch() as top:
                run_steps(top, workspace(SUBMISSIONS / name, top))
                summary = (top / "summary.md").read_text()
                for word in words:
                    self.assertIn(word, summary)
                logged = [json.loads(line) for line in (top / "harness.log").read_text().splitlines()]
                self.assertEqual([call[-2:] for call in logged], calls)
                for call in logged:
                    self.assertEqual(call[0], "run")
                    self.assertEqual(call[2:4], ["--ideas", "trusted/kit/api/sample-ideas.json"])


if __name__ == "__main__":
    unittest.main()
