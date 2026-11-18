# The embedding table

Both apps read the same files in `potion-base-4M/` (S08, A7).

- **Model:** Model2Vec `potion-base-4M` by Minish Lab, https://huggingface.co/minishlab/potion-base-4M
- **Revision:** `9b3cff412d30be9ae8603fe10224c224f3401869`, downloaded 15 Nov 2026.
- **Licence:** MIT. The model card says `license: mit`, and `potion-base-4M/LICENSE` is the MIT licence that came with it ("Copyright (c) 2024 Thomas van Dongen").
- **What it is:** a fixed table of 29,528 tokens × 128 numbers, distilled from `baai/bge-base-en-v1.5`, English. Nothing is trained on a person's ideas.

| File | Bytes | SHA-256 |
|---|---|---|
| input `model.safetensors` | 15,118,424 | `8a7140edd17ffab30ddcff1135eda127df57abfae856203dbd7b3d061295c31a` |
| input `tokenizer.json` | 683,666 | `e67e803f624fb4d67dea1c730d06e1067e1b14d830e2c2202569e3ef0f70bb50` |
| output `potion-base-4M/potion-base-4M-int8.bin` | 3,897,712 | `f4c94e3415866e5abe3e34a97186658284472524c762775b975de70d654534c7` |
| output `potion-base-4M/vocab.txt` | 219,690 | `1394523a67ddd404a825428018c0582a6998bcfa044ecbcbf1f4d71adb94c61c` |

**Made by** `python3 -I ai/embeddings/quantize.py <download-dir> ai/embeddings/potion-base-4M` (standard library only): int8, one float32 scale per row.

**Checked on 18 Nov 2026:**
- the token ids of all 50 texts in `ai/fixtures/embeddings/texts.json` from `reference.py` equal those of Hugging Face `tokenizers` 0.22.1 reading the original `tokenizer.json` (with no special tokens, and `[UNK]` dropped);
- the int8 vectors keep a cosine of at least 0.99996 with vectors pooled from the original float32 table.

`reference.py` writes `ai/fixtures/embeddings/vectors.json` and `library.json`, which both apps' tests must match.
