"""Packs a folder into a Sorta add-on, the same bytes on any machine.

    python3 -I pack.py <folder> [--out <file>]

The archive holds every file in the folder, sorted by name, stored without
compression, dated 1 Jan 1980, with no extra fields, no comments and no
file modes. So the same files always give the same SHA-256, whatever their
times, the order they were made in, or the tool that unpacks them. A
symbolic link goes in as a link, which check then refuses.

Before writing, pack checks the archive with check.py and refuses whatever
check refuses, with the same code. Without --out, it writes
<id>-<version>.sortaaddon in the current folder. It prints the file's name,
size and SHA-256. Standard library only.
"""
import argparse
import hashlib
import os
import stat
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check  # noqa: E402
import sortazip  # noqa: E402

LINK = (stat.S_IFLNK | 0o777) << 16


def items(folder: Path) -> list[sortazip.Item]:
    """One entry per file and link under the folder, sorted by name."""
    found = []
    for top, folders, names in os.walk(folder, followlinks=False):
        here = Path(top)
        for name in folders + names:
            path = here / name
            raw = os.fsencode(path.relative_to(folder).as_posix())
            mode = path.lstat().st_mode
            if stat.S_ISLNK(mode):
                found.append(sortazip.Item(raw, os.fsencode(os.readlink(path)), external=LINK, made_by=0x0314))
            elif stat.S_ISREG(mode):
                found.append(sortazip.Item(raw, path.read_bytes()))
            elif not stat.S_ISDIR(mode):
                raise sortazip.Refused("file.type", f"{path.relative_to(folder)} isn't a file or a folder.")
    found.sort(key=lambda item: item.raw_name)
    return found


def archive(folder: Path) -> bytes:
    """The archive's bytes, before any check."""
    return sortazip.build(items(folder))


def pack(folder: Path) -> tuple[bytes, check.Verdict]:
    """The archive and check's verdict on it."""
    try:
        data = archive(folder)
    except sortazip.Refused as refusal:
        return b"", check.Verdict(refusal.code, refusal.detail)
    return data, check.check_archive(data)


def main() -> int:
    parser = argparse.ArgumentParser(description="Pack a folder into a .sortaaddon file.")
    parser.add_argument("folder", type=Path)
    parser.add_argument("--out", type=Path, help="where to write the file")
    args = parser.parse_args()
    if not args.folder.is_dir():
        parser.error(f"{args.folder} isn't a folder")
    data, verdict = pack(args.folder)
    if not verdict.ok:
        print(f"refused {verdict.code}: {verdict.detail}")
        return 1
    out = args.out or Path(f"{verdict.manifest['id']}-{verdict.manifest['version']}.sortaaddon")
    out.write_bytes(data)
    print(f"packed {out}: {len(data):,} bytes, SHA-256 {hashlib.sha256(data).hexdigest()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
