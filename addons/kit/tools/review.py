"""The public directory's checks, and the build of its index.

    python3 -I review.py pr --repo <dir> --base <sha>      check what a pull request adds
    python3 -I review.py all --repo <dir>                  check every listed version
    python3 -I review.py summary --repo <dir> --base <sha> permissions and hosts, as Markdown
    python3 -I review.py harness --repo <dir> [--base <sha>] --harness <program> --ideas <file>
    python3 -I review.py build --repo <dir> --out <dir>    write index.json, revoked.json and dist/

The repository holds each submission's source in addons/<id>/<version>/.
A pull request may add new versions, and revoked.json entries. The checks
run in four stages, and stop after the first stage that finds a problem,
listing every problem in that stage:
1. files: no picture, icon, PDF or font, and no binary;
2. the package: check.py's rules, on the folder packed as pack.py packs it,
   with a README.md and a LICENSE;
3. its place: the id and version match the folders, the version is above
   the last one listed, and the author's url matches the listed one;
4. its content: no payment or tip links, no points, streaks, ranks or
   leaderboards, every literal host in net.fetch declared, no base64 run
   over 1,024 characters, and no line over 500 characters in a .js file.
The codes are in addons/spec/review.json. Standard library only.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check  # noqa: E402
import pack  # noqa: E402

CODES = ("review.picture", "review.binary", "review.files", "review.folder", "review.version", "review.owner",
         "review.paid", "review.points", "review.host", "review.base64", "review.line", "review.revoked")
PICTURE_NAMES = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".heic", ".heif", ".avif", ".jxl", ".bmp", ".tif",
                 ".tiff", ".ico", ".icns", ".svg", ".svgz", ".pdf", ".psd", ".ai", ".eps", ".sketch",
                 ".ttf", ".otf", ".ttc", ".woff", ".woff2", ".eot"}
BINARY_NAMES = {".wasm", ".so", ".dylib", ".dll", ".exe", ".bin", ".class", ".jar", ".dex", ".o", ".a", ".pyc",
                ".zip", ".gz", ".tgz", ".tar", ".7z", ".sortaaddon", ".qjs", ".bc"}
LICENSE_FILES = ("LICENSE", "LICENSE.txt", "LICENSE.md")
SEEN_FILES = ("manifest.json", "README.md", "CHANGELOG.md")
PAID = re.compile(
    r"patreon\.com|ko-fi\.com|buymeacoffee\.com|paypal\.(com|me)|github\.com/sponsors|opencollective\.com"
    r"|liberapay\.com|gumroad\.com|buy\.stripe\.com|lemonsqueezy\.com|venmo\.com|cash\.app|revolut\.me"
    r"|\b(donate|donations?|tip jar|tips welcome|buy me a coffee|sponsor me|paid version|pro version"
    r"|premium version|unlock premium|upgrade to pro|in-app purchases?|subscribe to unlock)\b", re.I)
POINTS = re.compile(
    r"\b(leaderboards?|streaks?|high ?scores?|experience points|level(s|ed)? up|level-up|rank card|rank up"
    r"|your rank|ranks? of (players|people|users|friends)|ranked against)\b"
    r"|\bXP\b"
    r"|\b(earn|earns|earned|win|wins|won|gain|gains|score|scores|scored|collect|collects|unlock|unlocks)\s+"
    r"(\w+\s+)?(points?|badges?|achievements?|trophy|trophies)\b"
    r"|\bpoints? (table|system)\b", re.I)
FETCH = re.compile(r"\bfetch\s*\(\s*([\"'`])([a-zA-Z][a-zA-Z0-9+.-]*://[^\"'`/?#\s$]*)")
BASE64 = re.compile(r"[A-Za-z0-9+/_-]{1025,}")
LONGEST_LINE = 500


@dataclass
class Problem:
    code: str
    where: str
    detail: str

    def __str__(self) -> str:
        return f"{self.code} {self.where}: {self.detail}"


def picture(name: str, data: bytes) -> str | None:
    """What kind of picture or font a file is, by its name or its first bytes."""
    suffix = Path(name).suffix.lower()
    if suffix in PICTURE_NAMES:
        return f"a {suffix} file"
    head = data[:16]
    starts = {b"\x89PNG\r\n\x1a\n": "PNG", b"\xff\xd8\xff": "JPEG", b"GIF87a": "GIF", b"GIF89a": "GIF",
              b"%PDF": "PDF", b"\x00\x00\x01\x00": "ICO", b"icns": "ICNS", b"8BPS": "PSD",
              b"\x00\x01\x00\x00\x00": "TrueType font", b"OTTO": "OpenType font", b"ttcf": "font collection",
              b"wOFF": "WOFF font", b"wOF2": "WOFF2 font", b"II*\x00": "TIFF", b"MM\x00*": "TIFF",
              b"\xff\x0a": "JPEG XL", b"\x00\x00\x00\x0cJXL ": "JPEG XL"}
    for start, kind in starts.items():
        if head.startswith(start):
            return f"{kind} bytes"
    if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
        return "WebP bytes"
    if head[4:8] == b"ftyp" and head[8:12] in (b"heic", b"heix", b"hevc", b"heim", b"heis", b"mif1", b"msf1",
                                                  b"avif", b"avis"):
        return "HEIC or AVIF bytes"
    if head[:2] == b"BM" and len(data) >= 6 and int.from_bytes(data[2:6], "little") == len(data):
        return "BMP bytes"
    text = data[:1024].lstrip(b"\xef\xbb\xbf \t\r\n").lower()
    if text.startswith(b"<svg") or (text.startswith(b"<?xml") and b"<svg" in text):
        return "SVG"
    return None


def binary(name: str, data: bytes) -> str | None:
    suffix = Path(name).suffix.lower()
    if suffix in BINARY_NAMES:
        return f"a {suffix} file"
    if b"\0" in data:
        return "it has a NUL byte"
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        return "it isn't UTF-8"
    return None


# reading the repository

def git(repo: Path, *args: str) -> bytes:
    done = subprocess.run(["git", "-C", str(repo), *args], capture_output=True)
    if done.returncode:
        raise SystemExit(f"git {' '.join(args)} failed: {done.stderr.decode(errors='replace').strip()}")
    return done.stdout


def folder_files(folder: Path) -> dict[str, bytes]:
    return {item.raw_name.decode("utf-8", "replace"): item.data for item in pack.items(folder)}


def listed(repo: Path, base: str) -> dict[str, list[str]]:
    """The versions in addons/ at a commit, by id."""
    out = {}
    for line in git(repo, "ls-tree", "-r", "--name-only", "-z", base, "--", "addons/").split(b"\0"):
        parts = line.decode().split("/")
        if len(parts) >= 4:
            out.setdefault(parts[1], set()).add(parts[2])
    return {ident: sorted(versions) for ident, versions in out.items()}


def newest(versions) -> str | None:
    known = [v for v in versions if check.semver(v)]
    return max(known, key=check.semver) if known else None


def manifest_at(repo: Path, commit: str, ident: str, version: str) -> dict:
    try:
        return json.loads(git(repo, "show", f"{commit}:addons/{ident}/{version}/manifest.json"))
    except (SystemExit, ValueError):
        return {}


def local_versions(repo: Path) -> dict[str, list[str]]:
    root = repo / "addons"
    if not root.is_dir():
        return {}
    return {ident.name: sorted(v.name for v in ident.iterdir() if v.is_dir())
            for ident in sorted(root.iterdir()) if ident.is_dir()}


# the stages

def review_folder(folder: Path, ident: str, version: str, before: list[str], owner: str | None) -> list[Problem]:
    """Every problem in the first stage that has one."""
    where = f"addons/{ident}/{version}"
    try:
        files = folder_files(folder)
    except check.Refused as refusal:
        return [Problem(refusal.code, where, refusal.detail)]
    found = []
    for name, data in sorted(files.items()):
        kind = picture(name, data)
        if kind:
            found.append(Problem("review.picture", f"{where}/{name}", f"{kind}. Add-ons can't carry pictures, "
                                 "icons or fonts."))
        elif binary(name, data):
            found.append(Problem("review.binary", f"{where}/{name}", f"{binary(name, data)}. Add-ons are "
                                 "source text only."))
    if found:
        return found
    verdict = check.check_path(folder)
    if not verdict.ok:
        found.append(Problem(verdict.code, where, verdict.detail))
    if "README.md" not in files or not any(name in files for name in LICENSE_FILES):
        found.append(Problem("review.files", where, "every add-on here has a README.md and a LICENSE."))
    if found:
        return found
    manifest = verdict.manifest
    if manifest["id"] != ident or manifest["version"] != version:
        found.append(Problem("review.folder", where, f"the manifest says {manifest['id']} {manifest['version']}. "
                             "The folders must be addons/<id>/<version>/."))
    last = newest(before)
    if last and check.semver(version) and check.semver(version) <= check.semver(last):
        found.append(Problem("review.version", where, f"{version} isn't above {last}, the last version listed."))
    if owner and manifest["author"]["url"] != owner:
        found.append(Problem("review.owner", where, f"{ident} is listed under {owner}. Choose an id of your own."))
    if found:
        return found
    hosts = set(manifest.get("hosts", []))
    for name, data in sorted(files.items()):
        text = data.decode("utf-8")
        for match in PAID.finditer(text):
            found.append(Problem("review.paid", f"{where}/{name}", f"{match.group()!r}. Add-ons here are free, "
                                 "with no payment or tip links."))
        if name in SEEN_FILES:
            for match in POINTS.finditer(text):
                found.append(Problem("review.points", f"{where}/{name}", f"{match.group()!r}. Sorta has no "
                                     "points, streaks, ranks, badges or leaderboards."))
        if name.endswith(".js"):
            for match in FETCH.finditer(text):
                address = match.group(2)
                scheme, _, host = address.partition("://")
                if scheme != "https" or host.lower() not in hosts:
                    found.append(Problem("review.host", f"{where}/{name}", f"net.fetch reaches {address!r}. "
                                         "Use https:// and list the host in the manifest's hosts."))
            for number, line in enumerate(text.split("\n"), 1):
                if len(line) > LONGEST_LINE:
                    found.append(Problem("review.line", f"{where}/{name}:{number}", f"{len(line)} characters. "
                                         f"Keep each line to {LONGEST_LINE}, so reviewers can read it."))
        for match in BASE64.finditer(text):
            found.append(Problem("review.base64", f"{where}/{name}", f"a base64 run of {len(match.group()):,} "
                                 "characters. Ship readable source and data, not encoded blobs."))
    return found


def review_revoked(data: bytes) -> list[Problem]:
    def problem(detail: str) -> list[Problem]:
        return [Problem("review.revoked", "revoked.json", detail)]
    try:
        revoked = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, ValueError) as error:
        return problem(f"isn't valid JSON: {error}")
    if (not isinstance(revoked, dict) or set(revoked) != {"format", "formatVersion", "revoked"}
            or revoked["format"] != "sorta-addons-revoked" or revoked["formatVersion"] != 1
            or not isinstance(revoked["revoked"], list)):
        return problem('needs "format": "sorta-addons-revoked", "formatVersion": 1 and a "revoked" list.')
    for at, entry in enumerate(revoked["revoked"], 1):
        if not isinstance(entry, dict):
            return problem(f"entry {at} isn't an object.")
        keys = set(entry) - {"reason", "date"}
        by_id = keys in ({"id"}, {"id", "versions"}) and isinstance(entry.get("id"), str) and \
            check.ID.fullmatch(entry["id"])
        by_author = keys == {"author"} and isinstance(entry.get("author"), str) and \
            entry["author"].startswith("https://")
        versions = entry.get("versions", ["0.0.0"])
        if not (by_id or by_author) or not isinstance(versions, list) or not versions or \
                not all(check.semver(v) for v in versions):
            return problem(f"entry {at} needs an id (with an optional list of versions) or an author url.")
        if not check.label(entry.get("reason"), 200):
            return problem(f"entry {at} needs a reason of 1 to 200 characters.")
        try:
            date.fromisoformat(entry.get("date", ""))
            assert re.fullmatch(r"\d{4}-\d{2}-\d{2}", entry["date"])
        except (TypeError, ValueError, AssertionError):
            return problem(f"entry {at} needs a date like 2026-11-15.")
    return []


def review_pr(repo: Path, base: str) -> list[Problem]:
    head = git(repo, "rev-parse", "HEAD").decode().strip()
    changed = git(repo, "diff", "--name-status", "-z", "--no-renames", base, head).split(b"\0")
    pairs = [(changed[i].decode(), changed[i + 1].decode()) for i in range(0, len(changed) - 1, 2)]
    before, found, groups = listed(repo, base), [], {}
    for status, path in pairs:
        if path == "revoked.json":
            found += review_revoked((repo / path).read_bytes())
        elif path.startswith("addons/"):
            parts = path.split("/")
            if len(parts) < 4:
                found.append(Problem("review.folder", path, "add-ons go in addons/<id>/<version>/."))
            elif parts[2] in before.get(parts[1], []):
                found.append(Problem("review.version", path, f"{parts[1]} {parts[2]} is listed already, so it "
                                     "never changes. Submit a new version."))
            else:
                groups.setdefault((parts[1], parts[2]), status)
    for (ident, version), _ in sorted(groups.items()):
        folder = repo / "addons" / ident / version
        if not folder.is_dir():
            continue
        last = newest(before.get(ident, []))
        owner = manifest_at(repo, base, ident, last).get("author", {}).get("url") if last else None
        found += review_folder(folder, ident, version, before.get(ident, []), owner)
    return found


def review_all(repo: Path) -> list[Problem]:
    found = []
    for path in sorted((repo / "addons").glob("*")) if (repo / "addons").is_dir() else []:
        if not path.is_dir():
            found.append(Problem("review.folder", str(path.relative_to(repo)), "add-ons go in addons/<id>/<version>/."))
    for ident, versions in local_versions(repo).items():
        ordered = sorted(versions, key=lambda v: check.semver(v) or (-1,))
        owner = None
        for at, version in enumerate(ordered):
            found += review_folder(repo / "addons" / ident / version, ident, version, ordered[:at], owner)
            if owner is None:
                manifest_path = repo / "addons" / ident / version / "manifest.json"
                try:
                    owner = json.loads(manifest_path.read_text(encoding="utf-8"))["author"]["url"]
                except (OSError, ValueError, KeyError, TypeError):
                    owner = None
    revoked = repo / "revoked.json"
    found += review_revoked(revoked.read_bytes()) if revoked.exists() else []
    return found


def submissions(repo: Path, base: str | None) -> list[tuple[str, str]]:
    """The versions to look at: the ones a pull request adds, or all of them."""
    if base is None:
        return [(i, v) for i, vs in local_versions(repo).items() for v in vs]
    before, out = listed(repo, base), set()
    for path in git(repo, "diff", "--name-only", "-z", base, "HEAD").split(b"\0"):
        parts = path.decode().split("/")
        if len(parts) >= 4 and parts[0] == "addons" and parts[2] not in before.get(parts[1], []) and \
                (repo / "addons" / parts[1] / parts[2]).is_dir():
            out.add((parts[1], parts[2]))
    return sorted(out)


def summary(repo: Path, base: str) -> str:
    before = listed(repo, base)
    lines = ["## Permissions and hosts", ""]
    others = sorted(path for path in git(repo, "diff", "--name-only", "-z", base, "HEAD").decode().split("\0")
                    if path and not path.startswith("addons/") and path != "revoked.json")
    if others:
        lines += ["This pull request also changes files outside addons/ and revoked.json, so a code owner "
                  "reviews it as a change to the directory itself:", ""] + [f"- {path}" for path in others] + [""]
    found = submissions(repo, base)
    if not found:
        return "\n".join(lines + ["This pull request adds no add-on version.", ""])
    lines += ["| Add-on | Version | Permissions | Hosts | Since the last version |", "|---|---|---|---|---|"]
    for ident, version in found:
        try:
            manifest = json.loads((repo / "addons" / ident / version / "manifest.json").read_text(encoding="utf-8"))
            permissions, hosts = list(manifest.get("permissions", [])), list(manifest.get("hosts", []))
        except (OSError, ValueError, AttributeError, TypeError):
            lines.append(f"| {ident} | {version} | unreadable manifest | | |")
            continue
        last = newest(before.get(ident, []))
        if last:
            old = manifest_at(repo, base, ident, last)
            added = sorted(set(permissions) - set(old.get("permissions", []))) + \
                sorted(f"host {h}" for h in set(hosts) - set(old.get("hosts", [])))
            gone = sorted(set(old.get("permissions", [])) - set(permissions)) + \
                sorted(f"host {h}" for h in set(old.get("hosts", [])) - set(hosts))
            change = "; ".join(filter(None, [f"adds {', '.join(added)}" if added else "",
                                             f"drops {', '.join(gone)}" if gone else ""])) or f"same as {last}"
        else:
            change = "new add-on"
        lines.append(f"| {ident} | {version} | {', '.join(permissions) or 'none'} | {', '.join(hosts) or 'none'} "
                     f"| {change} |")
    return "\n".join(lines + [""])


def run_harness(repo: Path, base: str | None, program: str, ideas: str) -> int:
    failed = 0
    for ident, version in submissions(repo, base):
        folder = repo / "addons" / ident / version
        manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        contributes = manifest.get("contributes", {})
        runs = [["--command", c["id"]] for c in contributes.get("commands", [])]
        if "cardView" in contributes:
            runs.append(["--card", contributes["cardView"]["id"]])
        for extra in runs:
            done = subprocess.run([program, "run", str(folder), "--ideas", ideas, *extra], capture_output=True,
                                  text=True)
            print(f"{ident} {version} {' '.join(extra)}: exit {done.returncode}")
            sys.stdout.write(done.stdout + done.stderr)
            failed += done.returncode != 0
    return 1 if failed else 0


def is_revoked(revoked: list[dict], manifest: dict) -> bool:
    for entry in revoked:
        if entry.get("author") == manifest["author"]["url"]:
            return True
        if entry.get("id") == manifest["id"] and manifest["version"] in entry.get("versions", [manifest["version"]]):
            return True
    return False


def build(repo: Path, out: Path) -> dict:
    """index.json, revoked.json and dist/ for every version that is listed
    and not revoked."""
    head = git(repo, "rev-parse", "HEAD").decode().strip()
    (out / "dist").mkdir(parents=True, exist_ok=True)
    revoked_path = repo / "revoked.json"
    revoked = json.loads(revoked_path.read_text(encoding="utf-8"))["revoked"] if revoked_path.exists() else []
    addons = []
    for ident, versions in local_versions(repo).items():
        entries, latest = [], None
        for version in sorted(versions, key=check.semver):
            folder = repo / "addons" / ident / version
            data, verdict = pack.pack(folder)
            if not verdict.ok:
                raise SystemExit(f"addons/{ident}/{version}: refused {verdict.code}: {verdict.detail}")
            if is_revoked(revoked, verdict.manifest):
                continue
            name = f"{ident}-{version}.sortaaddon"
            (out / "dist" / name).write_bytes(data)
            manifest = verdict.manifest
            source = git(repo, "log", "-1", "--format=%H", "--", f"addons/{ident}/{version}").decode().strip()
            entries.append({"version": version, "file": f"dist/{name}", "sha256": hashlib.sha256(data).hexdigest(),
                            "size": len(data), "permissions": manifest["permissions"],
                            "hosts": manifest.get("hosts", []), "minSorta": manifest["minSorta"],
                            "api": int(manifest["api"]), "commit": source})
            latest = manifest
        if entries:
            addons.append({"id": ident, "name": latest["name"], "author": latest["author"], "versions": entries})
    addons.sort(key=lambda a: (a["name"].casefold(), a["id"]))
    index = {"format": "sorta-addons-index", "formatVersion": 1, "builtFrom": head, "addons": addons}
    (out / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    revoked = repo / "revoked.json"
    (out / "revoked.json").write_bytes(revoked.read_bytes())
    return index


def main() -> int:
    parser = argparse.ArgumentParser(description="The public directory's checks and index build.")
    parser.add_argument("mode", choices=("pr", "all", "summary", "harness", "build"))
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument("--base", help="the commit a pull request is based on")
    parser.add_argument("--harness", help="the harness program, for the harness mode")
    parser.add_argument("--ideas", help="the sample ideas, for the harness mode")
    parser.add_argument("--out", type=Path, help="the folder to build into, for the build mode")
    args = parser.parse_args()
    repo = args.repo.resolve()
    if args.mode in ("pr", "summary") and not args.base:
        parser.error(f"{args.mode} needs --base")
    if args.mode == "summary":
        sys.stdout.write(summary(repo, args.base))
        return 0
    if args.mode == "harness":
        if not args.harness or not args.ideas:
            parser.error("harness needs --harness and --ideas")
        return run_harness(repo, args.base, args.harness, args.ideas)
    if args.mode == "build":
        if not args.out:
            parser.error("build needs --out")
        index = build(repo, args.out)
        print(f"built {sum(len(a['versions']) for a in index['addons'])} version(s) of {len(index['addons'])} "
              f"add-on(s) into {args.out}")
        return 0
    found = review_pr(repo, args.base) if args.mode == "pr" else review_all(repo)
    for problem in found:
        print(problem)
    print(f"{len(found)} problem(s)" if found else "no problems")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
