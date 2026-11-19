"""Reads the small part of YAML the directory's workflows and issue forms
use: mappings, lists, plain and quoted scalars, [a, b] lists and | blocks.
It refuses anything else, so a workflow can't hold a part the tests can't
see. The tests have no PyYAML, because the kit is standard library only."""
import json
import re


class Unsupported(ValueError):
    pass


def load(text: str):
    lines = []
    for number, raw in enumerate(text.split("\n"), 1):
        if "\t" in raw[:len(raw) - len(raw.lstrip())]:
            raise Unsupported(f"line {number}: a tab in the indent")
        lines.append((number, raw))
    value, at = block(lines, 0, 0)
    at = skip(lines, at)
    if at < len(lines):
        raise Unsupported(f"line {lines[at][0]}: unexpected indent")
    return value


def blank(raw: str) -> bool:
    stripped = raw.strip()
    return not stripped or stripped.startswith("#")


def skip(lines, at):
    while at < len(lines) and blank(lines[at][1]):
        at += 1
    return at


def indent(raw: str) -> int:
    return len(raw) - len(raw.lstrip(" "))


def block(lines, at, level):
    at = skip(lines, at)
    if at >= len(lines) or indent(lines[at][1]) < level:
        return None, at
    level = indent(lines[at][1])
    if lines[at][1].lstrip().startswith("- ") or lines[at][1].strip() == "-":
        return sequence(lines, at, level)
    return mapping(lines, at, level)


KEY = re.compile(r"([A-Za-z0-9_.-]+|\"[^\"]*\"|'[^']*'):(?: (.*)|$)")


def mapping(lines, at, level, first=None):
    out = {}
    while True:
        at = skip(lines, at)
        if first is None:
            if at >= len(lines) or indent(lines[at][1]) != level:
                if at < len(lines) and indent(lines[at][1]) > level:
                    raise Unsupported(f"line {lines[at][0]}: unexpected indent")
                return out, at
            number, text = lines[at][0], lines[at][1].strip()
            at += 1
        else:
            number, text = first
            first = None
        match = KEY.fullmatch(text)
        if not match:
            raise Unsupported(f"line {number}: not a key: {text!r}")
        key = scalar(match.group(1), number)
        if key in out:
            raise Unsupported(f"line {number}: {key!r} twice")
        rest = (match.group(2) or "").strip()
        if rest in ("|", "|-", ">"):
            out[key], at = literal(lines, at, level, rest)
        elif rest and not rest.startswith("#"):
            out[key] = scalar(rest, number)
        else:
            out[key], at = block(lines, at, level + 1)


def sequence(lines, at, level):
    out = []
    while True:
        at = skip(lines, at)
        if at >= len(lines) or indent(lines[at][1]) != level or not lines[at][1].lstrip().startswith("-"):
            return out, at
        number, raw = lines[at]
        text = raw.strip()[1:].strip()
        at += 1
        if not text:
            item, at = block(lines, at, level + 1)
        elif KEY.fullmatch(text):
            item, at = mapping(lines, at, level + 2, first=(number, text))
        else:
            item = scalar(text, number)
        out.append(item)


def literal(lines, at, level, style):
    body, deeper = [], None
    while at < len(lines):
        raw = lines[at][1]
        if raw.strip() and indent(raw) <= level:
            break
        if raw.strip() and deeper is None:
            deeper = indent(raw)
        body.append(raw[deeper:] if deeper is not None and raw.strip() else "")
        at += 1
    while body and not body[-1]:
        body.pop()
    if style == ">":
        raise Unsupported("folded blocks aren't used")
    return "\n".join(body) + ("" if style == "|-" else "\n"), at


def scalar(text: str, number: int):
    text = text.strip()
    if text.startswith('"'):
        if not text.endswith('"') or len(text) < 2:
            raise Unsupported(f"line {number}: an unclosed string")
        return json.loads(text)
    if text.startswith("'"):
        if not text.endswith("'") or len(text) < 2:
            raise Unsupported(f"line {number}: an unclosed string")
        return text[1:-1].replace("''", "'")
    if text.startswith("["):
        if not text.endswith("]"):
            raise Unsupported(f"line {number}: an unclosed list")
        inner = text[1:-1].strip()
        return [scalar(part, number) for part in inner.split(",")] if inner else []
    if text.startswith(("{", "&", "*", "!", "%", "@", "`")):
        raise Unsupported(f"line {number}: {text[0]} isn't supported")
    if " #" in text:
        text = text.split(" #", 1)[0].rstrip()
    if ": " in text or text.endswith(":"):
        raise Unsupported(f"line {number}: a plain value with ': ' in it needs quotes")
    if text in ("true", "false"):
        return text == "true"
    if re.fullmatch(r"-?[0-9]+", text):
        return int(text)
    return text
