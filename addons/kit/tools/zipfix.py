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
- "bad_crc": true, which writes a CRC one bit off;
- "comment": text, which gives the directory entry a comment;
- "before": text, which writes those bytes ahead of the entry's local
  header, where no entry accounts for them.
A top-level "cut_end": n cuts n bytes from the end of the archive,
"comment": text gives the archive a comment, "gap": text writes those bytes
between the last entry and the central directory, and "prepend": text
writes those bytes before the first entry and moves every offset to match.
Standard library only.
"""
import argparse
import json
import stat
import struct
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
                bad_crc=bool(entry.get("bad_crc")), comment=entry.get("comment", "").encode("utf-8"),
                before=entry.get("before", "").encode("utf-8"))
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
    data = sortazip.build(items(description, packages), description.get("comment", "").encode("utf-8"))
    if "gap" in description:
        data = with_gap(data, description["gap"].encode("utf-8"))
    if "prepend" in description:
        data = prepended(data, description["prepend"].encode("utf-8"))
    return data[:len(data) - description.get("cut_end", 0)]


def prepended(data: bytes, first: bytes) -> bytes:
    """The archive after `first`, with each entry's offset and the
    directory's moved by its length."""
    end = sortazip.end_record(data)
    moved, at = bytearray(data), end.directory_at
    for _ in range(end.count):
        name_len, extra_len, comment_len = struct.unpack_from("<HHH", moved, at + 28)
        struct.pack_into("<I", moved, at + 42, struct.unpack_from("<I", moved, at + 42)[0] + len(first))
        at += sortazip.CENTRAL_SIZE + name_len + extra_len + comment_len
    struct.pack_into("<I", moved, end.end_at + 16, end.directory_at + len(first))
    return first + bytes(moved)


def with_gap(data: bytes, gap: bytes) -> bytes:
    """The archive with `gap` between its last entry and its central
    directory, and the end record pointing at the directory's new place."""
    end = sortazip.end_record(data)
    moved = bytearray(data[:end.directory_at] + gap + data[end.directory_at:])
    struct.pack_into("<I", moved, end.end_at + len(gap) + 16, end.directory_at + len(gap))
    return bytes(moved)


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
