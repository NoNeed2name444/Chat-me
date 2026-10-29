#!/usr/bin/env python3
"""Run the app's standalone Swift test suites - here, on Linux CI, or on macOS CI.

Copied from Stethoscore (reference/stethoscore-aec2af1c/tools/swift_suites.py).
The suites are listed once, in tools/swift_suites.txt ("suite <name>
<Test.swift> <sources...>"); the workflow and this script both read it. Each
suite is a Foundation-only check() script in ios/UnitTests/Suites.

    tools/swift_suites.py                     every suite
    tools/swift_suites.py --only sync,dataio  some
    tools/swift_suites.py --affected origin/personal   only suites whose test or
                                              sources changed since that commit
    tools/swift_suites.py --platform linux    skip the suites marked mac-only
    tools/swift_suites.py --list              names, and where each can run

Output is one line per suite (PASS / FAIL / NO BUILD, with the counts of checks
passed and failed) and a last summary line; full logs go to
ios/build/swift-suites/out/<suite>.log (ios/.gitignore keeps build/ out). Exit status is non-zero if anything failed.
Mac-only suites (they import Apple frameworks Linux lacks) are listed in
tools/swift_suites_mac_only.txt, which --classify rewrites from a local run.
"""
import argparse, os, re, shutil, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SUITES = os.path.join(ROOT, "tools/swift_suites.txt")
MAC_ONLY = os.path.join(ROOT, "tools/swift_suites_mac_only.txt")
VARS = {"W": "ios/Sorta/Ward", "S": "ios/Sorta/Shared", "M": "ios/Sorta/Maps"}
TESTS = "ios/UnitTests/Suites"
BUILD = os.path.join(ROOT, "ios/build/swift-suites")

def suites():
    text = open(SUITES).read().replace("\\\n", " ")
    out = {}
    for line in text.split("\n"):
        line = line.strip()
        if not line.startswith("suite ") or line.startswith("suite ()"):
            continue
        parts = line.split()[1:]
        name, test, srcs = parts[0], parts[1], parts[2:]
        srcs = [re.sub(r"\$(\w)", lambda m: VARS[m.group(1)], s) for s in srcs]
        out[name] = (test, srcs)
    return out

def mac_only():
    if not os.path.exists(MAC_ONLY): return set()
    return {l.split("#")[0].strip() for l in open(MAC_ONLY) if l.split("#")[0].strip()}

def run(name, test, srcs, swiftc, opt):
    b = os.path.join(BUILD, name); os.makedirs(b, exist_ok=True)
    shutil.copy(os.path.join(ROOT, TESTS, test), os.path.join(b, "main.swift"))
    log = []
    c = subprocess.run([swiftc, opt, *srcs, os.path.join(b, "main.swift"), "-o", os.path.join(b, "run")],
                       cwd=ROOT, capture_output=True, text=True)
    log.append(c.stdout + c.stderr); log.append(f"compile rc={c.returncode}")
    passed = failed = 0
    if c.returncode != 0:
        status, detail = "NO BUILD", next((l for l in (c.stdout + c.stderr).splitlines() if "error:" in l), "")
    else:
        try:
            r = subprocess.run([os.path.join(b, "run")], cwd=ROOT, capture_output=True, text=True, timeout=600)
            out, rc = r.stdout + r.stderr, r.returncode
        except subprocess.TimeoutExpired as e:
            out, rc = (e.stdout or b"").decode() if isinstance(e.stdout, bytes) else (e.stdout or ""), 124
        log.append(out); log.append(f"run rc={rc}")
        # a check prints "ok"/"PASS" or "FAIL" first (the suites differ)
        fails = [l for l in out.splitlines() if l.startswith("FAIL ")]
        passed = sum(l.startswith(("ok ", "PASS ")) for l in out.splitlines())
        failed = len(fails)
        # a suite that prints only its failures ends with its own count:
        # "all N passed" or "F of N failed"
        m = re.search(r"^all (\d+) passed$|^(\d+) of (\d+) failed$", out, re.M)
        if m and m.group(1):
            passed = max(passed, int(m.group(1)))
        elif m:
            passed = max(passed, int(m.group(3)) - int(m.group(2)))
        # a suite passes only when it exits 0, ran at least one check and
        # printed no FAIL line
        status = "PASS" if rc == 0 and passed and not failed else "FAIL"
        detail = fails[0] if fails else ("" if rc == 0 else f"exit {rc}")
    os.makedirs(os.path.join(BUILD, "out"), exist_ok=True)
    open(os.path.join(BUILD, "out", name + ".log"), "w").write("\n".join(log))
    return name, status, passed, failed, re.sub(r"^.*?/ios/", "ios/", detail)[:200]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--affected", default="", help="a git ref: suites whose files changed since it")
    ap.add_argument("--platform", choices=["linux", "mac", "any"], default="any",
                    help="linux: skip mac-only suites; mac: only mac-only suites")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--list-chosen", action="store_true", help="print the suites that would run, and stop")
    ap.add_argument("--classify", action="store_true", help="rewrite the mac-only list from this run's NO BUILDs")
    ap.add_argument("-j", type=int, default=os.cpu_count() or 2)
    ap.add_argument("--swiftc", default=shutil.which("swiftc") or "/opt/swift/usr/bin/swiftc")
    ap.add_argument("--release", dest="opt", action="store_const", const="-O", default="-Onone",
                    help="compile with -O, as CI does (default -Onone: faster to build here)")
    a = ap.parse_args()
    all_s, macs = suites(), mac_only()
    if a.list:
        for n in all_s: print(f"{n:22} {'mac-only' if n in macs else 'linux+mac'}")
        return 0
    chosen = list(all_s)
    if a.only: chosen = [n for n in chosen if n in set(a.only.split(","))]
    if a.affected:
        changed = set(subprocess.run(["git", "diff", "--name-only", a.affected, "--"], cwd=ROOT,
                                     capture_output=True, text=True).stdout.split())
        changed |= set(subprocess.run(["git", "diff", "--name-only"], cwd=ROOT, capture_output=True, text=True).stdout.split())
        if any(c.startswith(".github/workflows/swift-tests") or c.startswith("tools/swift_suites") for c in changed):
            pass  # the list itself changed: everything
        else:
            chosen = [n for n in chosen if (TESTS + "/" + all_s[n][0]) in changed or set(all_s[n][1]) & changed]
    if a.platform == "linux": chosen = [n for n in chosen if n not in macs]
    if a.platform == "mac": chosen = [n for n in chosen if n in macs]
    if a.list_chosen:
        print("\n".join(chosen)); return 0
    if not chosen:
        print("no suites to run"); return 0
    with ThreadPoolExecutor(max_workers=a.j) as ex:
        results = list(ex.map(lambda n: run(n, *all_s[n], a.swiftc, a.opt), chosen))
    bad = 0
    for n, st, ok, no, d in sorted(results, key=lambda r: (r[1] == "PASS", r[0])):
        print(f"{st:8} {n:22} {ok:5} passed {no:4} failed  {d}")
        bad += st != "PASS"
    print(f"{len(results) - bad}/{len(results)} suites passed")
    if a.classify:
        nob = sorted(n for n, st, *_ in results if st == "NO BUILD")
        open(MAC_ONLY, "w").write("# Suites that need Apple frameworks Linux lacks; they run on macOS CI.\n"
                                  "# Rewritten by tools/swift_suites.py --classify on Linux.\n" + "".join(n + "\n" for n in nob))
    return 1 if bad else 0

sys.exit(main())
