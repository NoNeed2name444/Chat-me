"""Pushes a copy of Sorta's code to the public Chat-me repository, on its
branch `sorta`, where GitHub's runners build it for free (docs/scoping.md,
section 4).

    python3 -I tools/mirror_chatme.py [--commit HEAD] [--chatme /home/user/chat-me] [--push]

The copy is the files of one Sorta commit under ios/, android/ and tools/
(without tools/private/), and the workflows in ci/public/, which it places
in .github/workflows/. It is committed on top of the last copy, so Chat-me
never receives Sorta's history and nothing is force-pushed. Its message
carries the Sorta commit's attribution lines. Without --push the script
builds and checks the copy and says what it would push.

Before anything is written to Chat-me, the guard stops on:
- a file with the same SHA-256 as any file under work/ or design/ in that
  Sorta commit, or any file in docs/provenance/work-sha256.txt;
- a picture or icon file, unless it is on PICTURES_ALLOWED by path and
  SHA-256, and any vector drawing;
- a session link (the scheme CLAUDE.md names), an email address, or
  something that looks like a key;
- a symbolic link, a submodule or a file over 5 MB.
It names the file and the line, never what matched.
"""
import argparse
import hashlib
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

SORTA = Path(__file__).resolve().parent.parent
TAKEN = ("ios/", "android/", "tools/")
LEFT_OUT = ("tools/private/",)
WORKFLOWS = "ci/public/"
ART = ("work/", "design/")
ART_LIST = "docs/provenance/work-sha256.txt"
CHATME_URL = re.compile(r"github\.com[/:]noneed2name444/chat-me(\.git)?/?$", re.I)
PICTURES = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".heic", ".heif", ".bmp", ".tif", ".tiff",
            ".ico", ".icns", ".svg", ".pdf", ".ai", ".psd", ".sketch", ".icon", ".car",
            ".mp4", ".mov", ".m4v"}
# Pictures that may go, by path and SHA-256, each checked first not to be
# the art. Empty until the map step adds NASA's Earth maps.
PICTURES_ALLOWED: dict[str, str] = {}
DRAWING = re.compile(rb"<vector\b|<animated-vector\b|<adaptive-icon\b|android:pathData")
# built in two halves, so this file never matches itself
SESSION_LINK = re.compile(rb"cc:" + rb"//")
EMAIL = re.compile(rb"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")
KEY = re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----|\bghp_[A-Za-z0-9]{20,}|\bgithub_pat_[A-Za-z0-9_]{20,}"
                 rb"|\bsk-ant-[A-Za-z0-9_-]{10,}|\bAKIA[0-9A-Z]{16}\b|\bAIza[0-9A-Za-z_-]{30,}")
BIGGEST = 5 * 1024 * 1024
KEPT_TRAILERS = ("Co-Authored-By", "Claude-Session")


def git(repo: Path, *args: str, data: bytes | None = None, env: dict | None = None) -> bytes:
    done = subprocess.run(["git", "-C", str(repo), *args], input=data, env=env, capture_output=True)
    if done.returncode != 0:
        sys.stderr.write(done.stderr.decode(errors="replace"))
        raise SystemExit(f"git {args[0]} failed in {repo}")
    return done.stdout


def tree(repo: Path, commit: str) -> list[tuple[str, str, str, str]]:
    """Every entry of a commit's tree: mode, type, object, path."""
    out = []
    for item in git(repo, "ls-tree", "-r", "-z", "--full-tree", commit).split(b"\0"):
        if item:
            head, path = item.split(b"\t", 1)
            mode, kind, obj = head.decode().split()
            out.append((mode, kind, obj, path.decode()))
    return out


def blobs(repo: Path, objects: list[str]) -> dict[str, bytes]:
    """The contents of blobs, read in one `git cat-file --batch`."""
    done = subprocess.run(["git", "-C", str(repo), "cat-file", "--batch"],
                          input="".join(f"{o}\n" for o in objects).encode(), capture_output=True, check=True)
    out, data, at = {}, done.stdout, 0
    for obj in objects:
        end = data.index(b"\n", at)
        size = int(data[at:end].split()[2])
        out[obj] = data[end + 1:end + 1 + size]
        at = end + 1 + size + 1
    return out


def placed(path: str) -> str | None:
    """Where a Sorta path goes in the copy, or None if it stays out."""
    if path.startswith(TAKEN) and not path.startswith(LEFT_OUT):
        return path
    name = path[len(WORKFLOWS):]
    if path.startswith(WORKFLOWS) and "/" not in name and name.endswith((".yml", ".yaml")):
        return ".github/workflows/" + name
    return None


def lines_with(pattern: re.Pattern, content: bytes) -> list[int]:
    return sorted({content.count(b"\n", 0, m.start()) + 1 for m in pattern.finditer(content)})


def guard(entries, contents, art_hashes) -> list[str]:
    problems = []
    for mode, kind, obj, path, target in entries:
        if kind != "blob" or mode not in ("100644", "100755"):
            problems.append(f"{path}: a {kind} with mode {mode}, not a plain file")
            continue
        content = contents[obj]
        digest = hashlib.sha256(content).hexdigest()
        if len(content) > BIGGEST:
            problems.append(f"{path}: {len(content)} bytes, over {BIGGEST}")
        if digest in art_hashes:
            problems.append(f"{path}: the same file as one of the art's")
        if Path(path).suffix.lower() in PICTURES and PICTURES_ALLOWED.get(path) != digest:
            problems.append(f"{path}: a picture or icon file not on the allowed list")
        if path.endswith(".xml") and DRAWING.search(content):
            problems.append(f"{path}: a vector drawing")
        for what, pattern in (("a session link", SESSION_LINK), ("an email address", EMAIL),
                              ("something like a key", KEY)):
            for line in lines_with(pattern, content):
                problems.append(f"{path}:{line}: {what}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--commit", default="HEAD")
    parser.add_argument("--chatme", type=Path, default=Path("/home/user/chat-me"))
    parser.add_argument("--push", action="store_true")
    args = parser.parse_args()

    commit = git(SORTA, "rev-parse", "--verify", f"{args.commit}^{{commit}}").decode().strip()
    everything = tree(SORTA, commit)
    entries = [(m, k, o, p, placed(p)) for m, k, o, p in everything if placed(p)]
    if not entries:
        raise SystemExit("nothing to copy")
    art = [(o, p) for m, k, o, p in everything if p.startswith(ART) and k == "blob"]
    contents = blobs(SORTA, sorted({o for _, k, o, _, _ in entries if k == "blob"} | {o for o, _ in art}))
    art_hashes = {hashlib.sha256(contents[o]).hexdigest() for o, _ in art}
    for row in git(SORTA, "show", f"{commit}:{ART_LIST}").decode().splitlines():
        if row.strip():
            art_hashes.add(row.split()[0].lower())
    problems = guard(entries, contents, art_hashes)
    for problem in problems:
        print(f"guard: {problem}")
    if problems:
        print(f"stopped: {len(problems)} problem(s), nothing written to Chat-me")
        return 1
    print(f"guard: {len(entries)} files checked against {len(art_hashes)} art files, nothing found")

    url = git(args.chatme, "remote", "get-url", "origin").decode().strip()
    if not CHATME_URL.search(url):
        raise SystemExit(f"{args.chatme} is not a clone of Chat-me")
    parent = ""
    if git(args.chatme, "ls-remote", "--heads", "origin", "sorta").strip():
        git(args.chatme, "fetch", "-q", "origin", "refs/heads/sorta:refs/remotes/origin/sorta")
        parent = git(args.chatme, "rev-parse", "refs/remotes/origin/sorta").decode().strip()

    with tempfile.TemporaryDirectory() as scratch:
        env = dict(os.environ, GIT_INDEX_FILE=os.path.join(scratch, "index"))
        listing = []
        for mode, _, obj, _, target in entries:
            written = git(args.chatme, "hash-object", "-w", "--stdin", data=contents[obj]).decode().strip()
            if written != obj:
                raise SystemExit(f"{target}: written as {written}, read as {obj}")
            listing.append(f"{mode} {obj}\t{target}\n")
        git(args.chatme, "update-index", "--add", "--index-info", data="".join(listing).encode(), env=env)
        snapshot = git(args.chatme, "write-tree", env=env).decode().strip()

    if parent and git(args.chatme, "rev-parse", f"{parent}^{{tree}}").decode().strip() == snapshot:
        print(f"Chat-me's sorta already holds this copy: {parent}")
        return 0
    message = copy_message(commit)
    made = git(args.chatme, "commit-tree", snapshot, *(["-p", parent] if parent else []),
               "-F", "-", data=message.encode()).decode().strip()
    after = f" on {parent[:7]}" if parent else ", the first"
    print(f"copy of Sorta {commit[:7]}: tree {snapshot}, commit {made}{after}")
    if not args.push:
        print("not pushed (no --push)")
        return 0
    git(args.chatme, "push", "-q", "origin", f"{made}:refs/heads/sorta")
    git(args.chatme, "update-ref", "refs/remotes/origin/sorta", made)
    print(f"pushed to Chat-me's sorta: {made}")
    return 0


def copy_message(commit: str) -> str:
    """The copy's commit message, with the Sorta commit's attribution lines."""
    body = git(SORTA, "log", "-1", "--format=%B", commit)
    trailers = subprocess.run(["git", "interpret-trailers", "--parse"], input=body,
                              capture_output=True, check=True).stdout.decode().splitlines()
    kept = [t for t in trailers if t.split(":", 1)[0] in KEPT_TRAILERS]
    return (f"Sorta snapshot of {commit[:7]}\n\n"
            f"The committed files under ios/, android/ and tools/, and the CI\n"
            f"workflows, of Sorta {commit} (tools/mirror_chatme.py).\n"
            + ("\n" + "\n".join(kept) + "\n" if kept else ""))


if __name__ == "__main__":
    sys.exit(main())
