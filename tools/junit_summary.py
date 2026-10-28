"""Sums up JUnit XML results as a Markdown table, for a job's summary.

    python3 tools/junit_summary.py path/to/test-results [--expect Class=count ...]

Fails when no test ran, when any test failed or was skipped, or when a class
named with --expect ran a different number of tests.
"""
import argparse
import sys
import xml.etree.ElementTree as ElementTree
from collections import Counter
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("results", type=Path)
    parser.add_argument("--expect", action="append", default=[])
    args = parser.parse_args()
    ran, failed, skipped = Counter(), Counter(), Counter()
    for report in sorted(args.results.rglob("*.xml")):
        for case in ElementTree.parse(report).getroot().iter("testcase"):
            name = case.get("classname", "?").rsplit(".", 1)[-1]
            ran[name] += 1
            if case.find("failure") is not None or case.find("error") is not None:
                failed[name] += 1
            if case.find("skipped") is not None:
                skipped[name] += 1
    print("| Tests | Ran | Failed | Skipped |\n|---|---|---|---|")
    for name in sorted(ran):
        print(f"| {name} | {ran[name]} | {failed[name]} | {skipped[name]} |")
    problems = [] if ran else ["no test ran"]
    problems += [f"{n} failed in {name}" for name, n in failed.items()]
    problems += [f"{n} skipped in {name}" for name, n in skipped.items()]
    for wanted in args.expect:
        name, count = wanted.split("=")
        if ran[name] != int(count):
            problems.append(f"{name} ran {ran[name]} tests, not {count}")
    for problem in problems:
        print(f"\n**{problem}**", file=sys.stdout)
        print(f"::error::{problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
