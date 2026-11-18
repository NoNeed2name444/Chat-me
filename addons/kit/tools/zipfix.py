"""Builds the archive-level test cases from their JSON descriptions.

    python3 -I zipfix.py <description.json>... --out <folder>

Each description in addons/fixtures/zips/ is built at test time, so no .zip
or .sortaaddon file is ever committed. A description looks like this:

    {
      "about": "what the case is",
      "from": "ok-minimal",
      "entries": [{"name": "../main.js", "text": "..."}],
      "expect": {"verdict": "refused", "reason": "zip.path"}
    }

"from" (optional) starts from the files of a folder in
addons/fixtures/packages/, sorted by name. Each listed entry then replaces
the file of the same name, or is added after them in the order given. An
entry has a name, its contents as one of "text" (UTF-8), "hex", "repeat"
({"text": ..., "times": n}) or "lines" ({"count": n}, which makes "line 0",
"line 1" and so on, one per line), and optionally:
- "method": "store" (the default) or "deflate";
- "encrypted": true, which sets the encrypted flag;
- "link": true, which marks the entry as a symbolic link;
- "same_as": an earlier entry's name, which points this directory entry at
  that entry's bytes instead of writing new ones;
- "count": n, which repeats the entry n times, with {n} in its name
  replaced by 001, 002 and so on;
- "bad_crc": true, which writes a CRC one bit off.
A top-level "cut_end": n cuts n bytes from the end of the archive.
Standard library only.
"""
import argparse
import json
import stat
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pack  # noqa: E402
import sortazip  # noqa: E402

PACKAGES = Path(__file__).resolve().parents[2] / "fixtures" / "packages"
CONTENTS = ("text", "hex", "repeat", "lines")


def contents(entry: dict) -> bytes:
    kinds = [kind for kind in CONTENTS if kind in entry]
    if len(kinds) > 1:
        raise ValueError(f"{entry['name']}: give one of {', '.join(CONTENTS)}")
    if "text" in entry:
        return entry["text"].encode("utf-8")
    if "hex" in entry:
        return bytes.fromhex(entry["hex"])
    if "repeat" in entry:
        return entry["repeat"]["text"].encode("utf-8") * entry["repeat"]["times"]
    if "lines" in entry:
        return "".join(f"line {n}\n" for n in range(entry["lines"]["count"])).encode("utf-8")
    return b""


def items(description: dict, packages: Path = PACKAGES) -> list[sortazip.Item]:
    base = pack.items(packages / description["from"]) if "from" in description else []
    listed, names = [], []
    for entry in description["entries"]:
        for n in range(1, entry.get("count", 1) + 1):
            name = entry["name"].replace("{n}", f"{n:03d}")
            item = sortazip.Item(
                name.encode("utf-8"), contents(entry),
                method=sortazip.DEFLATE if entry.get("method") == "deflate" else sortazip.STORE,
                flags=1 if entry.get("encrypted") else 0,
                external=(stat.S_IFLNK | 0o777) << 16 if entry.get("link") else 0,
                made_by=0x0314 if entry.get("link") else 20,
                bad_crc=bool(entry.get("bad_crc")))
            if "same_as" in entry:
                item.same_as = names.index(entry["same_as"])
            listed.append(item)
            names.append(name)
    replaced = {item.raw_name for item in listed}
    kept = [item for item in base if item.raw_name not in replaced]
    for item in listed:
        if item.same_as is not None:
            item.same_as += len(kept)
    return kept + listed


def build(path: Path, packages: Path = PACKAGES) -> bytes:
    description = json.loads(path.read_text(encoding="utf-8"))
    data = sortazip.build(items(description, packages))
    return data[:len(data) - description.get("cut_end", 0)]


def main() -> int:
    parser = argparse.ArgumentParser(description="Build archives from zip test case descriptions.")
    parser.add_argument("descriptions", type=Path, nargs="+")
    parser.add_argument("--out", type=Path, required=True, help="the folder to write the archives to")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for path in args.descriptions:
        data = build(path)
        target = args.out / (path.stem + ".sortaaddon")
        target.write_bytes(data)
        print(f"{target}: {len(data):,} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
