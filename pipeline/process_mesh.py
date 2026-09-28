#!/usr/bin/env python3
"""Repeatable mesh intake gate. Blender performs conversion/LOD generation headlessly."""
from __future__ import annotations

import argparse, hashlib, json
from pathlib import Path

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()

parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True, type=Path)
parser.add_argument("--id", required=True)
parser.add_argument("--fma", required=True)
parser.add_argument("--source-url", required=True)
parser.add_argument("--license", required=True)
parser.add_argument("--out", required=True, type=Path)
args = parser.parse_args()

args.out.mkdir(parents=True, exist_ok=True)
record = {
    "id": args.id, "fma_id": args.fma, "source_url": args.source_url,
    "license": args.license, "input_sha256": sha256(args.input),
    "coordinate_system": "right-handed, metres, +Y superior, +Z anterior",
    "smoothing": "none at intake; any later smoothing must be explicitly logged",
    "state": "intake-verified, not clinically approved"
}
(args.out / f"{args.id}.intake.json").write_text(json.dumps(record, indent=2) + "\n")
print(json.dumps(record, indent=2))
