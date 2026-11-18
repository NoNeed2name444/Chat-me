"""Checks the vendored QuickJS-ng against SOURCES.md (S07b, A1).

    python3 -I addons/engine/verify.py           each file's SHA-256, and no file unlisted
    python3 -I addons/engine/verify.py --fetch   also each file against the same path
                                                 at the pinned commit on GitHub (CI)

Exit status is non-zero on any difference.
"""
import hashlib
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ENGINE = os.path.join(HERE, "quickjs-ng")
SOURCES = os.path.join(HERE, "SOURCES.md")
RAW = "https://raw.githubusercontent.com/quickjs-ng/quickjs/{commit}/{name}"


def listed():
    text = open(SOURCES, encoding="utf-8").read()
    commit = re.search(r"\*\*Commit:\*\* `([0-9a-f]{40})`", text)
    if not commit:
        raise SystemExit("SOURCES.md names no full commit")
    files = dict(re.findall(r"^\| `([^`]+)` \| `([0-9a-f]{64})` \|$", text, re.M))
    if not files:
        raise SystemExit("SOURCES.md lists no file")
    return commit.group(1), files


def main(argv):
    commit, files = listed()
    problems = []
    present = sorted(os.listdir(ENGINE))
    for name in present:
        if name not in files:
            problems.append(f"{name}: in the folder, not in SOURCES.md")
    for name, digest in sorted(files.items()):
        path = os.path.join(ENGINE, name)
        if not os.path.isfile(path):
            problems.append(f"{name}: listed, not in the folder")
            continue
        data = open(path, "rb").read()
        if hashlib.sha256(data).hexdigest() != digest:
            problems.append(f"{name}: SHA-256 differs from SOURCES.md")
        if "--fetch" in argv:
            url = RAW.format(commit=commit, name=name)
            with urllib.request.urlopen(url, timeout=60) as reply:
                upstream = reply.read()
            if upstream != data:
                problems.append(f"{name}: differs from {url}")
    for p in problems:
        print("FAIL", p)
    what = "and against GitHub at " + commit if "--fetch" in argv else "against SOURCES.md"
    print(f"{len(files)} files checked {what}: {'FAIL' if problems else 'ok'}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
