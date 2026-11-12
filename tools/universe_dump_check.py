#!/usr/bin/env python3
"""The Universe dump's check (S05h).

    universe_dump_check.py FRESH COMMITTED PLANNER

FRESH is the dump tools/universe_dump.swift just wrote; COMMITTED the copy
Android's UniversePlanTest reads (android/app/src/test/resources/universe/
ios-dump.json); PLANNER ios/Sorta/Maps/GraphUniverse.swift. The planner's blob
hash (as `git hash-object` gives it: SHA-1 of "blob <size>\\0" and the bytes, so
no git is needed) is written into FRESH, which is then uploaded. FRESH must
equal COMMITTED: strings, integers and booleans exactly, numbers within 1e-5,
and the same hash. With no COMMITTED file it fails, asking for the upload to be
committed.
"""
import hashlib
import json
import os
import sys

TOLERANCE = 1e-5


def blob_hash(path):
    with open(path, "rb") as f:
        data = f.read()
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def compare(a, b, where, bad):
    if len(bad) >= 20:
        return
    if isinstance(a, bool) or isinstance(b, bool):
        if a is not b:
            bad.append("%s: %r != %r" % (where, a, b))
    elif isinstance(a, (int, float)) and isinstance(b, (int, float)):
        if isinstance(a, int) and isinstance(b, int):
            if a != b:
                bad.append("%s: %r != %r" % (where, a, b))
        elif abs(a - b) > TOLERANCE:
            bad.append("%s: %r != %r" % (where, a, b))
    elif isinstance(a, str) and isinstance(b, str):
        if a != b:
            bad.append("%s: %r != %r" % (where, a, b))
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            bad.append("%s: %d items != %d" % (where, len(a), len(b)))
            return
        for k, (x, y) in enumerate(zip(a, b)):
            compare(x, y, "%s[%d]" % (where, k), bad)
    elif isinstance(a, dict) and isinstance(b, dict):
        if sorted(a) != sorted(b):
            bad.append("%s: keys %s != %s" % (where, sorted(a), sorted(b)))
            return
        for key in sorted(a):
            compare(a[key], b[key], "%s.%s" % (where, key), bad)
    else:
        bad.append("%s: %r != %r" % (where, a, b))


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    fresh_path, committed_path, planner = sys.argv[1:]
    with open(fresh_path, encoding="utf-8") as f:
        text = f.read()
    fresh = json.loads(text)
    if "hash" not in fresh:
        # the tool's own layout kept (a body a line), the hash put first
        fresh = {"hash": blob_hash(planner), "plans": fresh["plans"]}
        text = '{\n  "hash": "%s",\n' % fresh["hash"] + text[text.index("{") + 1:].lstrip("\n")
        with open(fresh_path, "w", encoding="utf-8") as f:
            f.write(text)
    elif fresh["hash"] != blob_hash(planner):
        print("the fresh dump carries another planner's hash")
        return 1
    names = ", ".join(sorted(fresh["plans"]))
    print("planner %s; plans: %s" % (fresh["hash"], names))
    if not os.path.exists(committed_path):
        print("no committed dump: commit the uploaded universe-dump")
        return 1
    with open(committed_path, encoding="utf-8") as f:
        committed = json.load(f)
    bad = []
    if committed.get("hash") != fresh["hash"]:
        bad.append("hash: committed %s, planner %s" % (committed.get("hash"), fresh["hash"]))
    compare(fresh.get("plans"), committed.get("plans"), "plans", bad)
    if bad:
        print("the committed dump differs from iOS's planner (commit the uploaded universe-dump):")
        for line in bad:
            print("  " + line)
        return 1
    print("the committed dump matches iOS's planner")
    return 0


if __name__ == "__main__":
    sys.exit(main())
