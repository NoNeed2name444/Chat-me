"""Runs the kit's tests, and fails on any skipped test or expected failure.

    python3 -I -B addons/kit/tools/tests/run.py [--min-tests N]

--min-tests fails the run when fewer tests ran than that, so a test file
that stops being found can't pass unseen. Standard library only.
"""
import argparse
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the kit's tests.")
    parser.add_argument("--min-tests", type=int, default=0)
    args = parser.parse_args()
    suite = unittest.defaultTestLoader.discover(str(HERE), pattern="test_*.py", top_level_dir=str(HERE))
    result = unittest.TextTestRunner(verbosity=2, stream=sys.stdout).run(suite)
    problems = []
    if result.skipped:
        problems.append(f"{len(result.skipped)} skipped: {', '.join(test.id() for test, _ in result.skipped)}")
    if result.expectedFailures or result.unexpectedSuccesses:
        problems.append("expected failures aren't allowed")
    if result.testsRun < args.min_tests:
        problems.append(f"{result.testsRun} tests ran, fewer than {args.min_tests}")
    for problem in problems:
        print(f"FAILED: {problem}")
    print(f"{result.testsRun} tests, {len(result.failures)} failures, {len(result.errors)} errors, "
          f"{len(result.skipped)} skipped")
    return 0 if result.wasSuccessful() and not problems else 1


if __name__ == "__main__":
    sys.exit(main())
