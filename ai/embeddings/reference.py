#!/usr/bin/env python3
"""The embedding reference both apps are checked against (S08 A7).

Standard library only. It reads the int8 table and vocab that quantize.py
wrote and ai/fixtures/embeddings/texts.json, and writes vectors.json and
library.json beside it:

    python3 -I ai/embeddings/reference.py

The steps, which AIEmbeddings.swift and AIEmbeddings.kt follow exactly:

1. Normalise as BERT's uncased tokeniser does: drop U+0000, U+FFFD and other
   control/format/unassigned/private/surrogate characters (tab, CR and LF are
   kept as whitespace); turn whitespace into a space; put spaces around Han
   ideographs; NFD, then drop nonspacing marks; lowercase.
2. Split on whitespace, and split every punctuation character off on its own
   (ASCII punctuation, and Unicode categories Pc Pd Pe Pf Pi Po Ps).
3. WordPiece: longest match first, "##" before a piece that continues a word;
   a word longer than 100 characters, or one with no match, is [UNK].
4. Drop [UNK] (id 1), keep the first 512 ids, average their rows (value *
   scale, in double precision) and scale the mean to length 1. No ids: no
   vector.

A text is covered when at least half of its letters are Latin (U+0041 to
U+024F); otherwise both apps use IdeaEchoes' uncommon-word match.
"""
import json
import math
import struct
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TABLE = ROOT / "ai/embeddings/potion-base-4M/potion-base-4M-int8.bin"
VOCAB = ROOT / "ai/embeddings/potion-base-4M/vocab.txt"
FIXTURES = ROOT / "ai/fixtures/embeddings"
UNK = 1
MAX_TOKENS = 512
MAX_WORD = 100
HAN = ((0x4E00, 0x9FFF), (0x3400, 0x4DBF), (0x20000, 0x2A6DF), (0x2A700, 0x2B73F), (0x2B740, 0x2B81F),
       (0x2B920, 0x2CEAF), (0xF900, 0xFAFF), (0x2F800, 0x2FA1F))
PUNCT_CATEGORIES = {"Pc", "Pd", "Pe", "Pf", "Pi", "Po", "Ps"}
OTHER_CATEGORIES = {"Cc", "Cf", "Cn", "Co", "Cs"}
WHITESPACE = {0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0x85, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000} | set(range(0x2000, 0x200B))


def is_whitespace(c: str) -> bool:
    return ord(c) in WHITESPACE


def is_control(c: str) -> bool:
    if c in "\t\n\r":
        return False
    return unicodedata.category(c) in OTHER_CATEGORIES


def is_han(c: str) -> bool:
    o = ord(c)
    return any(a <= o <= b for a, b in HAN)


def is_punct(c: str) -> bool:
    o = ord(c)
    if 33 <= o <= 47 or 58 <= o <= 64 or 91 <= o <= 96 or 123 <= o <= 126:
        return True
    return unicodedata.category(c) in PUNCT_CATEGORIES


def normalize(text: str) -> str:
    out = []
    for c in text:
        if ord(c) in (0, 0xFFFD) or is_control(c):
            continue
        out.append(" " if is_whitespace(c) else c)
    spaced = []
    for c in out:
        spaced.append(" " + c + " " if is_han(c) else c)
    text = unicodedata.normalize("NFD", "".join(spaced))
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return text.lower()


def pre_tokenize(text: str):
    words, word = [], []
    for c in text:
        if is_whitespace(c):
            if word:
                words.append("".join(word))
                word = []
        elif is_punct(c):
            if word:
                words.append("".join(word))
                word = []
            words.append(c)
        else:
            word.append(c)
    if word:
        words.append("".join(word))
    return words


def wordpiece(word: str, vocab: dict):
    if len(word) > MAX_WORD:
        return [UNK]
    ids, start = [], 0
    while start < len(word):
        end, found = len(word), None
        while start < end:
            piece = word[start:end] if start == 0 else "##" + word[start:end]
            if piece in vocab:
                found = vocab[piece]
                break
            end -= 1
        if found is None:
            return [UNK]
        ids.append(found)
        start = end
    return ids


def token_ids(text: str, vocab: dict):
    ids = []
    for word in pre_tokenize(normalize(text)):
        ids.extend(wordpiece(word, vocab))
    return [i for i in ids if i != UNK][:MAX_TOKENS]


class Table:
    def __init__(self):
        data = TABLE.read_bytes()
        magic, version, rows, dims = data[:4], *struct.unpack_from("<III", data, 4)
        if magic != b"SRTE" or version != 1:
            raise SystemExit("not an SRTE v1 table")
        self.rows, self.dims = rows, dims
        self.scales = struct.unpack_from("<%df" % rows, data, 16)
        self.values = data[16 + 4 * rows:]
        lines = VOCAB.read_text(encoding="utf-8").split("\n")
        if lines[-1] == "":
            lines.pop()
        self.vocab = {t: i for i, t in enumerate(lines)}

    def row(self, i: int):
        s = float(self.scales[i])
        chunk = self.values[i * self.dims:(i + 1) * self.dims]
        return [float(q - 256 if q > 127 else q) * s for q in chunk]

    def vector(self, ids):
        if not ids:
            return None
        total = [0.0] * self.dims
        for i in ids:
            for d, v in enumerate(self.row(i)):
                total[d] += v
        mean = [t / len(ids) for t in total]
        length = math.sqrt(sum(v * v for v in mean))
        if length == 0:
            return None
        return [v / length for v in mean]


def covered(text: str) -> bool:
    letters = [c for c in text if unicodedata.category(c).startswith("L")]
    latin = [c for c in letters if 0x41 <= ord(c) <= 0x24F]
    return bool(letters) and 2 * len(latin) >= len(letters)


def cosine(a, b):
    return sum(x * y for x, y in zip(a, b))


# IdeaEchoes' rule (ios/Sorta/Ideas/IdeaEchoes.swift), for the Arabic expectation only.
def echo_words(text: str, stop: set):
    out, word = [], []
    for c in text.lower() + " ":
        if c in "'’":
            continue
        if c.isalnum():
            word.append(c)
            continue
        w = "".join(word)
        word = []
        if len(w) >= 3 and not w.isdigit() and w not in stop and w not in out:
            out.append(w)
    return out


def echoes(ideas, text, made, stop):
    words = [set(echo_words(i["text"], stop)) for i in ideas]
    ceiling = max(2, len(ideas) // 20)
    score = {}
    for w in echo_words(text, stop):
        holders = [k for k, own in enumerate(words) if w in own]
        if not 1 <= len(holders) <= ceiling:
            continue
        for k in holders:
            if ideas[k]["made"] <= made:
                score[k] = score.get(k, 0.0) + 1.0 / len(holders)
    ranked = sorted(score, key=lambda k: (-score[k], -ideas[k]["made"], ideas[k]["id"]))
    return [ideas[k]["id"] for k in ranked[:5]]


def main() -> int:
    table = Table()
    spec = json.loads((FIXTURES / "texts.json").read_text(encoding="utf-8"))
    vectors = []
    for text in spec["texts"]:
        ids = token_ids(text, table.vocab)
        vec = table.vector(ids)
        vectors.append({"text": text, "covered": covered(text), "ids": ids,
                        "vector": None if vec is None else [round(v, 7) for v in vec]})
    library = [(item["id"], table.vector(token_ids(item["text"], table.vocab))) for item in spec["library"]]
    queries = []
    for q in spec["queries"]:
        qv = table.vector(token_ids(q, table.vocab))
        ranked = sorted(((cosine(qv, v), i) for i, v in library), key=lambda s: (-s[0], s[1]))
        margin = ranked[4][0] - ranked[5][0]
        gaps = min(ranked[k][0] - ranked[k + 1][0] for k in range(5))
        if margin < 1e-4 or gaps < 1e-5:  # far above the two apps' rounding (about 1e-12)
            raise SystemExit(f"query {q!r}: neighbours too close to call ({margin:.5f}, {gaps:.5f})")
        queries.append({"text": q, "top5": [i for _, i in ranked[:5]],
                        "scores": [round(s, 6) for s, _ in ranked[:5]], "smallestGap": round(min(margin, gaps), 6)})
    stop = set()  # the stop words are English; none is Arabic
    arabic = spec["arabic"]
    arabic_out = {"query": arabic["query"], "covered": covered(arabic["query"]),
                  "ideasCovered": [covered(i["text"]) for i in arabic["ideas"]],
                  "echoes": echoes(arabic["ideas"], arabic["query"], arabic["made"], stop)}
    head = {"about": "Written by ai/embeddings/reference.py from texts.json; do not edit by hand.",
            "table": "ai/embeddings/potion-base-4M/potion-base-4M-int8.bin", "tolerance": 1e-4}
    (FIXTURES / "vectors.json").write_text(json.dumps({**head, "vectors": vectors}, ensure_ascii=False, indent=1) + "\n",
                                           encoding="utf-8")
    (FIXTURES / "library.json").write_text(json.dumps({**head, "queries": queries, "arabic": arabic_out},
                                                      ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(vectors)} vectors, {len(queries)} queries, arabic echoes {arabic_out['echoes']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
