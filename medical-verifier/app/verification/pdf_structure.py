import hashlib
import re
from dataclasses import dataclass
from io import BytesIO

from pypdf import PdfReader

CAPTION_RE = re.compile(
    r"^\s*(table|figure|fig\.)\s+([A-Za-z0-9._-]+)\s*[:.\-]?",
    re.I,
)

@dataclass(frozen=True)
class ExtractedBlock:
    block_id: str
    page_number: int
    block_index: int
    block_type: str
    text: str
    related_block_ids: tuple[str, ...]
    warnings: tuple[str, ...]

@dataclass(frozen=True)
class PDFExtractionResult:
    raw_sha256: str
    pages: int
    blocks: tuple[ExtractedBlock, ...]
    extraction_quality: float
    warnings: tuple[str, ...]

def _classify_line(line: str):
    match = CAPTION_RE.match(line)
    if match:
        return "caption", match.group(2)

    if "	" in line or " | " in line:
        return "table", None

    if line.isupper() and 3 <= len(line.split()) <= 12:
        return "header", None

    if line.lower().startswith("note:") or line.lower().startswith("footnote:"):
        return "footnote", None

    return "text", None

def extract_pdf(raw_bytes: bytes) -> PDFExtractionResult:
    raw_sha256 = hashlib.sha256(raw_bytes).hexdigest()

    reader = PdfReader(BytesIO(raw_bytes))
    page_count = len(reader.pages)

    blocks = []
    warnings = []
    total_chars = 0
    nonempty_pages = 0

    for page_number, page in enumerate(reader.pages, start=1):
        try:
            text = page.extract_text() or ""
        except Exception:
            text = ""
            warnings.append(f"page_{page_number}_text_extraction_failed")

        normalized_lines = [
            " ".join(line.split())
            for line in text.splitlines()
            if line.strip()
        ]

        if normalized_lines:
            nonempty_pages += 1
            total_chars += sum(len(line) for line in normalized_lines)

        pending_caption_id = None

        for local_index, line in enumerate(normalized_lines):
            block_id = f"pdf:page:{page_number}:block:{local_index}"
            block_type, caption_key = _classify_line(line)
            block_warnings = []

            if block_type == "caption":
                block_warnings.append(
                    "caption_linkage_is_heuristic"
                )

            if "	" in line or " | " in line:
                if "table_detected_from_text_delimiters" not in block_warnings:
                    block_warnings.append(
                        "table_detected_from_text_delimiters"
                    )

            related = (
                (pending_caption_id,)
                if pending_caption_id is not None
                else ()
            )

            block = ExtractedBlock(
                block_id=block_id,
                page_number=page_number,
                block_index=local_index,
                block_type=block_type,
                text=line,
                related_block_ids=related,
                warnings=tuple(block_warnings),
            )
            blocks.append(block)

            if block_type == "caption":
                pending_caption_id = block_id
            elif pending_caption_id is not None:
                pending_caption_id = None

    if page_count == 0:
        warnings.append("pdf_has_no_pages")

    if page_count and nonempty_pages == 0:
        warnings.append("pdf_no_extractable_text")

    if blocks and any(
        "table_detected_from_text_delimiters" in block.warnings
        for block in blocks
    ):
        warnings.append(
            "table_structure_was_heuristically_detected"
        )

    if blocks and any(
        "caption_linkage_is_heuristic" in block.warnings
        for block in blocks
    ):
        warnings.append(
            "caption_relationships_are_not_native_pdf_object_links"
        )

    page_coverage = (
        nonempty_pages / page_count
        if page_count
        else 0.0
    )
    char_signal = min(1.0, total_chars / max(1, page_count * 400))

    extraction_quality = round(
        0.70 * page_coverage + 0.30 * char_signal,
        4,
    )

    if warnings:
        extraction_quality = min(
            extraction_quality,
            0.84,
        )

    return PDFExtractionResult(
        raw_sha256=raw_sha256,
        pages=page_count,
        blocks=tuple(blocks),
        extraction_quality=extraction_quality,
        warnings=tuple(sorted(set(warnings))),
    )
