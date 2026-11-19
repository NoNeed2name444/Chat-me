"""Where the kit's checks and the engine join (S07, section 8; addons/spec/directory.md).

    python3 -I -B addons/conformance/directory_join.py

For each sample pull request in addons/fixtures/submissions/ that passes the
written checks, this makes the two checkouts the directory's check.yml makes,
in the directory repository's own layout (kit/ and engine/ at the top), and
runs check.yml's steps word for word: the review, the summary, the harness
build (make -C trusted/kit/harness, whose ENGINE is ../../engine) and the
harness run. A sample passes unless its expect.json names the code the
harness must stop it with ("harness": "limit.time"). Standard library only.
"""
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ADDONS = HERE.parent
sys.path.insert(0, str(ADDONS / "kit" / "tools" / "tests"))
sys.dont_write_bytecode = True

from common import DIRECTORY, SUBMISSIONS, Scratch, folders, read_json  # noqa: E402
import test_submissions as steps  # noqa: E402
import yamlish  # noqa: E402


def every_step() -> list[dict]:
    flow = yamlish.load((DIRECTORY / ".github" / "workflows" / "check.yml").read_text(encoding="utf-8"))
    return [step for job in flow["jobs"].values() for step in job["steps"] if "run" in step]


def trusted_tree(top: Path) -> Path:
    """The base branch's checkout, as the directory holds it: its own files, kit/ and engine/."""
    trusted = top / "trusted"
    steps.copy_tree(DIRECTORY, trusted)
    shutil.copytree(ADDONS / "kit", trusted / "kit", ignore=shutil.ignore_patterns("__pycache__", "build", "harness"))
    shutil.copytree(ADDONS / "kit" / "harness", trusted / "kit" / "harness",
                    ignore=shutil.ignore_patterns("build", "harness"))
    shutil.copytree(ADDONS / "engine", trusted / "engine")
    return trusted


def run(case: Path, trusted: Path) -> list[str]:
    """The problems with this sample; none means it went as its expect.json says."""
    expect = read_json(case / "expect.json")
    wanted = expect.get("harness")
    with Scratch() as top:
        base = steps.workspace(case, top)
        # the real harness instead of the tests' stand-in: one trusted checkout serves every sample,
        # so the engine is built once (make finds it built from the second sample on)
        shutil.rmtree(top / "trusted")
        (top / "trusted").symlink_to(trusted, target_is_directory=True)
        env = {**steps.os.environ, "GITHUB_STEP_SUMMARY": str(top / "summary.md")}
        out = []
        for step in every_step():
            step_env = {key: (base if value == "${{ github.event.pull_request.base.sha }}" else value)
                        for key, value in step.get("env", {}).items()}
            done = subprocess.run(["bash", "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", step["run"]],
                                  cwd=top, env={**env, **step_env}, capture_output=True, text=True)
            last = "review.py harness" in step["run"]
            if last:
                print(done.stdout + done.stderr, end="")
            if done.returncode and not (last and wanted):
                return out + [f"{step['name']}: exit {done.returncode}\n{done.stdout}{done.stderr}"]
            if last and wanted:
                if done.returncode == 0:
                    out.append(f"the harness step passed, and {wanted} should have failed it")
                if f'"code":"{wanted}"' not in done.stdout:
                    out.append(f"the harness step didn't report {wanted}")
        return out


def main() -> int:
    cases = [case for case in folders(SUBMISSIONS) if read_json(case / "expect.json")["verdict"] == "pass"]
    failing = [case for case in cases if "harness" in read_json(case / "expect.json")]
    bad = 0
    with Scratch() as shared:
        trusted = trusted_tree(shared)
        for case in cases:
            print(f"== {case.name}")
            problems = run(case, trusted)
            for problem in problems:
                print(f"PROBLEM {problem}")
            bad += bool(problems)
    print(f"{len(cases)} samples, {len(failing)} that the harness must fail, {bad} that went wrong")
    return 1 if bad or not failing or len(cases) <= len(failing) else 0


if __name__ == "__main__":
    sys.exit(main())
