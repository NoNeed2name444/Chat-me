import hashlib
import re
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

IDENTIFIER_PATTERNS=(("doi",re.compile(r"10\.\d{4,9}/[-._;()/:a-z0-9]+",re.I)),("pmid",re.compile(r"\bpmid[:\s]*(\d{6,9})\b",re.I)),("nct",re.compile(r"\bNCT\d{8}\b",re.I)))

def canonical_url(url: str | None) -> str | None:
    if not url: return None
    parts=urlsplit(url.strip())
    if not parts.netloc: return url.strip().lower()
    query=[(k,v) for k,v in parse_qsl(parts.query) if not k.lower().startswith(("utm_","fbclid","gclid"))]
    return urlunsplit((parts.scheme.lower(),parts.netloc.lower(),parts.path.rstrip("/"),urlencode(sorted(query)),"")).lower()

def identifiers(text: str) -> dict[str,str]:
    out={}
    for kind,pattern in IDENTIFIER_PATTERNS:
        m=pattern.search(text or "")
        if m: out[kind]=(m.group(1) if m.lastindex else m.group(0)).lower()
    return out

def evidence_identity(*, canonical_id: str | None, url: str | None, title: str="", passage: str="") -> str:
    if canonical_id: return canonical_id.strip().lower()
    normalized=canonical_url(url)
    if normalized: return normalized
    payload=re.sub(r"\s+"," ",f"{title} {passage}".lower()).strip()
    return "content:"+hashlib.sha256(payload.encode("utf-8")).hexdigest()[:24]
