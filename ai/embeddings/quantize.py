#!/usr/bin/env python3
"""Quantise potion-base-4M to the int8 table both apps read.

Standard library only. Run with -I, pointing at a download directory:

    python3 -I ai/embeddings/quantize.py <download-dir> ai/embeddings/potion-base-4M

Input: model.safetensors (one F32 tensor "embeddings", rows x dims) and
tokenizer.json (WordPiece vocab). Output, in the target directory:

  potion-base-4M-int8.bin  little-endian: b"SRTE", u32 version (1), u32 rows,
                           u32 dims, then rows x f32 scales, then rows x dims
                           int8 values. A row is value * scale.
  vocab.txt                one token per line, line n is token id n.

Each row's scale is max(|row|) / 127, stored as float32; values are
round-half-away-from-zero of row / scale, clamped to [-127, 127].
"""
import hashlib
import json
import math
import struct
import sys
from pathlib import Path

MAGIC = b"SRTE"
VERSION = 1
TABLE = "potion-base-4M-int8.bin"
VOCAB = "vocab.txt"


def read_safetensors(path: Path):
    data = path.read_bytes()
    (size,) = struct.unpack_from("<Q", data, 0)
    header = json.loads(data[8:8 + size])
    tensor = header["embeddings"]
    if tensor["dtype"] != "F32":
        raise SystemExit("expected F32 embeddings")
    rows, dims = tensor["shape"]
    start, end = tensor["data_offsets"]
    raw = data[8 + size + start:8 + size + end]
    if len(raw) != rows * dims * 4:
        raise SystemExit("tensor size does not match its shape")
    return rows, dims, raw


def round_half_away(x: float) -> int:
    return int(math.floor(abs(x) + 0.5)) * (1 if x >= 0 else -1)


def quantize(rows: int, dims: int, raw: bytes):
    scales = bytearray()
    values = bytearray()
    for r in range(rows):
        row = struct.unpack_from("<%df" % dims, raw, r * dims * 4)
        biggest = max(abs(v) for v in row)
        scale = struct.unpack("<f", struct.pack("<f", biggest / 127.0))[0]
        scales += struct.pack("<f", scale)
        for v in row:
            q = 0 if scale == 0 else max(-127, min(127, round_half_away(v / scale)))
            values += struct.pack("<b", q)
    return bytes(scales), bytes(values)


def vocab_lines(tokenizer: Path, rows: int):
    model = json.loads(tokenizer.read_text(encoding="utf-8"))["model"]
    by_id = sorted(model["vocab"].items(), key=lambda item: item[1])
    if [i for _, i in by_id] != list(range(rows)):
        raise SystemExit("vocab ids are not 0..rows-1")
    for token, _ in by_id:
        if "\n" in token or "\r" in token:
            raise SystemExit("a token holds a line break")
    return "".join(token + "\n" for token, _ in by_id)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    source, target = Path(sys.argv[1]), Path(sys.argv[2])
    rows, dims, raw = read_safetensors(source / "model.safetensors")
    scales, values = quantize(rows, dims, raw)
    table = MAGIC + struct.pack("<III", VERSION, rows, dims) + scales + values
    (target / TABLE).write_bytes(table)
    (target / VOCAB).write_text(vocab_lines(source / "tokenizer.json", rows), encoding="utf-8")
    for name, path in (("input model.safetensors", source / "model.safetensors"),
                       ("input tokenizer.json", source / "tokenizer.json"),
                       ("output " + TABLE, target / TABLE),
                       ("output " + VOCAB, target / VOCAB)):
        print(f"{name}: {path.stat().st_size} bytes, sha256 {hashlib.sha256(path.read_bytes()).hexdigest()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
