"""The checker's fixtures as archives, for the apps' own checkers (S07b A5).

    python3 -I -B addons/conformance/archives.py            writes archives.json
    python3 -I -B addons/conformance/archives.py --check    fails if it is stale

The kit's fixtures are folders (addons/fixtures/packages) and descriptions
(addons/fixtures/zips), which only Python can turn into archives. This packs
each one the way check.py does and writes the bytes beside the verdict
check.py must give, so the Swift and Kotlin checkers are tested on the very
same bytes. A fixture that can't be an archive (a named pipe in a folder) is
listed under "left out". Each archive is raw deflate in base64, with its
size and SHA-256. No archive file is committed. Standard library only.
"""
import base64
import hashlib
import json
import sys
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ADDONS = HERE.parent
sys.path.insert(0, str(ADDONS / "kit" / "tools"))
sys.dont_write_bytecode = True

import check  # noqa: E402
import pack  # noqa: E402
import zipfix  # noqa: E402

OUT = HERE / "archives.json"


def deflated(data: bytes) -> str:
    squeeze = zlib.compressobj(9, zlib.DEFLATED, -15)
    return base64.b64encode(squeeze.compress(data) + squeeze.flush()).decode("ascii")


def entry(name: str, kind: str, data: bytes, expect: dict) -> dict:
    app = expect.get("app")
    verdict = check.check_archive(data, check.App(app["sorta"], app["api"]) if app else None)
    got = {"verdict": "ok"} if verdict.ok else {"verdict": "refused", "reason": verdict.code}
    wanted = {"verdict": expect["verdict"], **({"reason": expect["reason"]} if "reason" in expect else {})}
    if got != wanted:
        raise SystemExit(f"{name}: check.py gives {got}, and the fixture says {wanted}")
    return {"name": name, "from": kind, **wanted, **({"app": app} if app else {}), "size": len(data),
            "sha256": hashlib.sha256(data).hexdigest(), "deflated": deflated(data)}


def build() -> str:
    archives, left_out = [], []
    for folder in sorted(path for path in (ADDONS / "fixtures" / "packages").iterdir() if path.is_dir()):
        expect = json.loads((folder / "expect.json").read_text(encoding="utf-8"))
        if "fifo" in expect:
            left_out.append({"name": folder.name, "why": "a named pipe is in a folder, never in an archive"})
            continue
        archives.append(entry(folder.name, "packages", pack.archive(folder), expect))
    for path in sorted((ADDONS / "fixtures" / "zips").glob("*.json")):
        expect = json.loads(path.read_text(encoding="utf-8"))["expect"]
        archives.append(entry(path.stem, "zips", zipfix.build(path), expect))
    lines = ["{", ' "about": "Made by archives.py from addons/fixtures/packages and zips. Don\'t edit it by hand.",',
             f' "count": {len(archives)},', f' "leftOut": {json.dumps(left_out)},', ' "archives": [']
    lines += ["  " + json.dumps(a, ensure_ascii=True) + ("," if n + 1 < len(archives) else "")
              for n, a in enumerate(archives)]
    return "\n".join(lines + [" ]", "}", ""])


def main() -> int:
    text = build()
    if "--check" in sys.argv[1:]:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print("archives.json is stale: run addons/conformance/archives.py", file=sys.stderr)
            return 1
        print(f"archives.json matches the fixtures: {json.loads(text)['count']} archives")
        return 0
    OUT.write_text(text, encoding="utf-8")
    print(f"{OUT}: {json.loads(text)['count']} archives, {len(text):,} characters")
    return 0


if __name__ == "__main__":
    sys.exit(main())
