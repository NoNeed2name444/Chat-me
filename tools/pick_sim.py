"""Picks a simulator for the CI runs, from the runner's own list.

    python3 tools/pick_sim.py iphone|ipad >> "$GITHUB_OUTPUT"

Prints <kind>_udid=, <kind>_name= and <kind>_runtime= lines. Only iOS 26 and
later can run Sorta. The newest runtime wins; on it, a standard iPhone (no
Pro, Plus, Air, e or SE) or the biggest iPad Pro. The iPad ranking is
Stethoscore's (reference/stethoscore-aec2af1c/.github/workflows/ipad-preview.yml).
"""
import json
import subprocess
import sys

LOWEST = (26,)


def rank(kind: str, name: str):
    if kind == "iphone":
        if not name.startswith("iPhone"):
            return None
        words = name.split()
        if len(words) == 2 and words[1].isdigit():
            return 2
        return 1 if "Pro" in words and "Max" not in words else 0
    if not name.startswith("iPad"):
        return None
    # the model, not the word "Pro" anywhere: an "iPad mini (A17 Pro)" is the smallest
    if name.startswith("iPad Pro"):
        model = 3
    elif name.startswith("iPad Air"):
        model = 2
    elif name.startswith("iPad mini"):
        model = 0
    else:
        model = 1
    return model * 2 + (1 if "13" in name else 0)


def main() -> None:
    kind = sys.argv[1] if len(sys.argv) > 1 else ""
    if kind not in ("iphone", "ipad"):
        raise SystemExit("usage: pick_sim.py iphone|ipad")
    devices = json.loads(subprocess.check_output(
        ["xcrun", "simctl", "list", "devices", "available", "-j"]))["devices"]
    found = []
    for runtime, listed in devices.items():
        if ".SimRuntime.iOS-" not in runtime:
            continue
        version = tuple(int(x) for x in runtime.rsplit("iOS-", 1)[1].split("-") if x.isdigit())
        if version < LOWEST:
            continue
        for device in listed:
            r = rank(kind, device["name"])
            if r is not None:
                found.append(((version, r, device["name"]), device["udid"], device["name"], runtime))
    if not found:
        raise SystemExit(f"no {kind} simulator with iOS {LOWEST[0]} or later on this runner")
    found.sort()
    _, udid, name, runtime = found[-1]
    print(f"{kind} simulator: {name}, {runtime.rsplit('.', 1)[-1]}", file=sys.stderr)
    print(f"{kind}_udid={udid}")
    print(f"{kind}_name={name}")
    print(f"{kind}_runtime={runtime.rsplit('.', 1)[-1]}")


if __name__ == "__main__":
    main()
