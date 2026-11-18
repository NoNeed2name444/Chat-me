"""A5: the public directory repo's files, and its two workflows read as text."""
import re
import unittest

from common import DIRECTORY, read_json, walk_files

import review
import yamlish

LAYOUT = {
    "README.md", "CONTRIBUTING.md", "LICENSE", "revoked.json",
    ".github/workflows/check.yml", ".github/workflows/publish.yml",
    ".github/ISSUE_TEMPLATE/report.yml", ".github/ISSUE_TEMPLATE/submit.yml",
    ".github/CODEOWNERS",
}
WORKFLOWS = DIRECTORY / ".github" / "workflows"
PR_HEAD = re.compile(r"pull_request\.head|head_ref|head\.sha|refs/pull/")


def workflow(name: str) -> tuple[str, dict]:
    text = (WORKFLOWS / name).read_text(encoding="utf-8")
    return text, yamlish.load(text)


def steps(flow: dict) -> list[dict]:
    return [step for job in flow["jobs"].values() for step in job["steps"]]


def can_write(flow: dict) -> bool:
    scopes = [flow.get("permissions")] + [job.get("permissions") for job in flow["jobs"].values()]
    return any(scope == "write-all" or (isinstance(scope, dict) and "write" in scope.values())
               for scope in scopes)


class Layout(unittest.TestCase):
    def test_the_files_match_section_7(self):
        found = {path.relative_to(DIRECTORY).as_posix() for path in walk_files(DIRECTORY, skip=())}
        self.assertEqual(found, LAYOUT)

    def test_license(self):
        text = (DIRECTORY / "LICENSE").read_text(encoding="utf-8")
        self.assertTrue(text.startswith("MIT License\n\nCopyright (c) 2026 the Sorta authors\n"))
        self.assertIn("THE SOFTWARE IS PROVIDED \"AS IS\"", text)

    def test_revoked_json_is_empty_and_valid(self):
        data = (DIRECTORY / "revoked.json").read_bytes()
        self.assertEqual(review.review_revoked(data), [])
        self.assertEqual(read_json(DIRECTORY / "revoked.json"),
                         {"format": "sorta-addons-revoked", "formatVersion": 1, "revoked": []})

    def test_codeowners_has_a_placeholder(self):
        rules = [line for line in (DIRECTORY / ".github" / "CODEOWNERS").read_text().splitlines()
                 if line and not line.startswith("#")]
        self.assertEqual(rules, ["* @FILL_IN_OWNER_ACCOUNT"])

    def test_issue_forms(self):
        names = {"report.yml": "Report an add-on", "submit.yml": "Submit an add-on"}
        for name, title in names.items():
            with self.subTest(name):
                form = yamlish.load((DIRECTORY / ".github" / "ISSUE_TEMPLATE" / name).read_text(encoding="utf-8"))
                self.assertEqual(form["name"], title)
                self.assertTrue(form["description"])
                ids = [field["id"] for field in form["body"] if "id" in field]
                self.assertEqual(len(ids), len(set(ids)))
                for field in form["body"]:
                    self.assertIn(field["type"], ("markdown", "input", "textarea", "dropdown", "checkboxes"))
                text = (DIRECTORY / ".github" / "ISSUE_TEMPLATE" / name).read_text(encoding="utf-8")
                self.assertNotRegex(text, r"(?i)e-?mail")

    def test_readme_and_contributing_say_what_section_7_asks(self):
        readme = (DIRECTORY / "README.md").read_text(encoding="utf-8").lower()
        for words in ("install", "report", "revoked.json"):
            with self.subTest(words):
                self.assertIn(words, readme)
        contributing = (DIRECTORY / "CONTRIBUTING.md").read_text(encoding="utf-8")
        for words in ("pull request", "addons/<id>/<version>/", "7 days", "check.py", "LICENSE"):
            with self.subTest(words):
                self.assertIn(words, contributing)
        for code in review.CODES:
            with self.subTest(code):
                self.assertIn(f"`{code}`", contributing)


class Check(unittest.TestCase):
    def setUp(self):
        self.text, self.flow = workflow("check.yml")

    def test_it_triggers_on_pull_request_only(self):
        self.assertEqual(set(self.flow["on"]), {"pull_request"})
        self.assertNotIn("pull_request_target", self.text)
        self.assertNotIn("workflow_run", self.text)

    def test_it_can_only_read(self):
        self.assertEqual(self.flow["permissions"], {"contents": "read"})
        for job in self.flow["jobs"].values():
            self.assertNotIn("permissions", job)
        self.assertFalse(can_write(self.flow))
        self.assertNotRegex(self.text, r":\s*write")

    def test_ubuntu_latest_and_no_secret(self):
        for job in self.flow["jobs"].values():
            self.assertEqual(job["runs-on"], "ubuntu-latest")
        self.assertNotIn("secrets.", self.text)
        self.assertNotIn("GITHUB_TOKEN", self.text)

    def test_checkouts_keep_no_credentials(self):
        for step in steps(self.flow):
            if str(step.get("uses", "")).startswith("actions/checkout@"):
                with self.subTest(step["name"]):
                    self.assertIs(step["with"]["persist-credentials"], False)

    def test_the_checks_come_from_the_base_branch(self):
        trusted = [s for s in steps(self.flow) if s.get("with", {}).get("path") == "trusted"]
        self.assertEqual(len(trusted), 1)
        self.assertEqual(trusted[0]["with"]["ref"], "${{ github.event.pull_request.base.sha }}")
        for step in steps(self.flow):
            run = step.get("run", "")
            if "review.py" in run:
                with self.subTest(step["name"]):
                    self.assertIn("python3 -I trusted/kit/tools/review.py ", run)
                    self.assertNotIn("${{", run)

    def test_it_runs_the_review_the_summary_and_the_harness(self):
        runs = [step.get("run", "") for step in steps(self.flow)]
        for mode in ("pr", "summary", "harness"):
            with self.subTest(mode):
                self.assertEqual(sum(f"review.py {mode} " in run for run in runs), 1)
        self.assertIn("make -C trusted/kit/harness", runs)


class Publish(unittest.TestCase):
    def setUp(self):
        self.text, self.flow = workflow("publish.yml")

    def test_it_triggers_on_push_to_main_only(self):
        self.assertEqual(self.flow["on"], {"push": {"branches": ["main"]}})
        self.assertNotIn("pull_request", self.text)
        self.assertNotIn("workflow_run", self.text)

    def test_only_the_push_job_can_write_and_only_contents(self):
        self.assertEqual(self.flow["permissions"], {"contents": "read"})
        self.assertEqual(set(self.flow["jobs"]), {"build", "push"})
        self.assertNotIn("permissions", self.flow["jobs"]["build"])
        self.assertEqual(self.flow["jobs"]["push"]["permissions"], {"contents": "write"})
        self.assertEqual(self.flow["jobs"]["push"]["needs"], "build")
        for job in self.flow["jobs"].values():
            self.assertEqual(job["runs-on"], "ubuntu-latest")
        self.assertNotIn("secrets.", self.text)

    def test_the_build_job_keeps_no_credentials_and_uploads_the_built_files(self):
        build = self.flow["jobs"]["build"]["steps"]
        checkout = [s for s in build if str(s.get("uses", "")).startswith("actions/checkout@")]
        self.assertEqual(len(checkout), 1)
        self.assertIs(checkout[0]["with"]["persist-credentials"], False)
        runs = [s.get("run", "") for s in build]
        self.assertIn("python3 -I repo/kit/tools/review.py build --repo repo --out built", runs)
        self.assertFalse(any("git push" in run or "commit-tree" in run for run in runs))
        upload = [s for s in build if str(s.get("uses", "")).startswith("actions/upload-artifact@")]
        self.assertEqual([(s["with"]["name"], s["with"]["path"]) for s in upload], [("index", "built")])
        self.assertEqual(build[-1], upload[0])

    def test_the_push_job_runs_nothing_from_the_repository(self):
        push = self.flow["jobs"]["push"]["steps"]
        download = [s for s in push if str(s.get("uses", "")).startswith("actions/download-artifact@")]
        self.assertEqual([(s["with"]["name"], s["with"]["path"]) for s in download], [("index", "built")])
        uses = [str(s["uses"]).split("@")[0] for s in push if "uses" in s]
        self.assertEqual(uses, ["actions/checkout", "actions/download-artifact"])
        scripts = [s["run"] for s in push if "run" in s]
        self.assertEqual(len(scripts), 1)
        self.assertIn('out="$GITHUB_WORKSPACE/built"', scripts[0])
        for word in ("python", "make", "review.py", "pack.py", "harness", "node"):
            with self.subTest(word):
                self.assertNotIn(word, scripts[0])
        commands = {line.split()[0].strip("(") for line in scripts[0].splitlines()
                    if line.strip() and not line.strip().startswith("#")}
        self.assertLessEqual(commands, {"out=\"$GITHUB_WORKSPACE/built\"", "git_dir=\"$PWD/.git\"", "export", "rm",
                                        "cd", "tree=$(git", "parent=()", "if", "git", "echo", "exit",
                                        "parent=(-p", "fi", "commit=$(git"})

    def test_it_runs_the_same_checks(self):
        runs = [step.get("run", "") for step in steps(self.flow)]
        _, check = workflow("check.yml")
        self.assertIn("python3 -I repo/kit/tools/review.py all --repo repo", runs)
        harness = [run for run in runs if "review.py harness" in run]
        self.assertEqual(len(harness), 1)
        check_harness = [s["run"] for s in steps(check) if "review.py harness" in s.get("run", "")][0]
        self.assertEqual(harness[0].replace("repo/", "").replace(" --repo repo", ""),
                         check_harness.replace("trusted/", "").replace(" --repo pr --base \"$BASE_SHA\"", ""))
        self.assertIn("make -C repo/kit/harness", runs)
        order = [next(i for i, run in enumerate(runs) if key in run)
                 for key in ("review.py all", "review.py harness", "review.py build", "git push")]
        self.assertEqual(order, sorted(order))

    def test_it_pushes_the_index_files_to_the_index_branch_only(self):
        script = next(step["run"] for step in steps(self.flow) if "git push" in step.get("run", ""))
        self.assertIn("add -- index.json revoked.json dist", script)
        pushes = [line.strip() for line in script.splitlines() if re.search(r"\bgit\b.*\bpush\b", line)]
        self.assertEqual(pushes, ['git push origin "$commit:refs/heads/index"'])
        self.assertNotRegex(self.text, r"refs/heads/main|:main\b|origin main\b|HEAD:main")
        self.assertNotIn("--force", self.text)
        self.assertNotRegex(self.text, r"push\s+-f\b|\+[^ ]*:refs/")
        self.assertNotRegex(self.text, r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}")

    def test_it_checks_out_main_as_pushed(self):
        checkouts = [s for s in steps(self.flow) if str(s.get("uses", "")).startswith("actions/checkout@")]
        self.assertEqual(len(checkouts), 2)
        for checkout in checkouts:
            self.assertNotIn("ref", checkout["with"])


class NoPullRequestHeadWhereWritable(unittest.TestCase):
    def test_neither_workflow_checks_out_a_pull_requests_head_in_a_job_that_can_write(self):
        for name in ("check.yml", "publish.yml"):
            text, flow = workflow(name)
            with self.subTest(name):
                if can_write(flow):
                    self.assertNotRegex(text, PR_HEAD)
                    self.assertNotIn("pull_request", text)
                for step in steps(flow):
                    ref = str(step.get("with", {}).get("ref", ""))
                    self.assertNotRegex(ref, PR_HEAD)


if __name__ == "__main__":
    unittest.main()
