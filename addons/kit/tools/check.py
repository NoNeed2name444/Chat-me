"""Checks a Sorta add-on against the package rules and prints the verdict.

    python3 -I check.py <folder or .sortaaddon> [--app-version 1.0.0 --app-api 1]

A folder is packed in memory first, exactly as pack.py packs it, so a folder
and its archive always get the same verdict. With --app-version and
--app-api, it also runs the two checks the app makes at install.

It prints "ok <id> <version>" and exits 0, or "refused <code>: <detail>"
and exits 1. The codes are in addons/spec/reasons.json and the order of the
checks in addons/spec/rules.md. Standard library only.
"""
import argparse
import json
import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sortazip  # noqa: E402

KB = 1024
MAX_PACKED = 512 * KB
MAX_UNPACKED = 1024 * KB
MAX_ENTRIES = 200
MAX_MAIN = 256 * KB
MAX_RATIO = 100
MAX_NAME_BYTES = 255
MAX_FOLDERS = 2

CODES = (
    "zip.unreadable", "zip.path", "zip.link", "zip.duplicate", "zip.case", "zip.encrypted", "zip.ratio",
    "zip.entries", "zip.size",
    "file.type", "file.depth", "file.text", "main.missing", "main.size",
    "manifest.missing", "manifest.json", "format", "formatVersion", "id", "id.reserved", "name", "version",
    "minSorta", "api", "author", "license", "permissions", "hosts", "commands", "contributes",
    "install.api", "install.minSorta",
)
ROOT_FILES = {"manifest.json", "README.md", "LICENSE", "LICENSE.txt", "LICENSE.md", "CHANGELOG.md"}
NAME_CHARS = re.compile(r"[A-Za-z0-9._/-]+")
FIELDS = ("format", "formatVersion", "id", "name", "version", "author", "license", "minSorta", "api", "main",
          "permissions", "hosts", "contributes")
REQUIRED = FIELDS[:-2]
PERMISSIONS = ("ideas.read", "ideas.read.folder", "ideas.write", "commands", "cards", "board.palette", "net",
               "ai")
ID = re.compile(r"[a-z0-9-]+(\.[a-z0-9-]+)+")
RESERVED = "com.cramdown"
NUMBER = r"(0|[1-9][0-9]{0,8})"
IDENTIFIER = r"(0|[1-9][0-9]{0,8}|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
SEMVER = re.compile(rf"{NUMBER}\.{NUMBER}\.{NUMBER}(-{IDENTIFIER}(\.{IDENTIFIER})*)?")
HOST_LABEL = re.compile(r"[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
KEY = re.compile(r"[a-z][A-Za-z0-9]{0,31}")
COMMAND_ID = re.compile(r"[a-z0-9][a-z0-9-]{0,31}")
COLOUR = re.compile(r"#[0-9A-Fa-f]{6}")
CONTRIBUTES = ("commands", "cardView", "settings", "boardPalette")
NEEDS = {"commands": "commands", "cardView": "cards", "boardPalette": "board.palette"}
LICENSES = frozenset(json.loads((Path(__file__).resolve().parent / "osi-licenses.json").read_text())["licenses"])


class Refused(Exception):
    def __init__(self, code: str, detail: str):
        assert code in CODES, code
        super().__init__(f"{code}: {detail}")
        self.code, self.detail = code, detail


@dataclass
class App:
    """What the app knows about itself at install: its version and its API's major version."""
    version: str
    api: int


@dataclass
class Verdict:
    code: str | None
    detail: str
    manifest: dict | None = None
    files: dict | None = None

    @property
    def ok(self) -> bool:
        return self.code is None


def check_archive(data: bytes, app: App | None = None) -> Verdict:
    """The verdict on an archive's bytes."""
    try:
        files = archive_rules(data)
        manifest = package_rules(files)
        if app:
            install_rules(manifest, app)
    except Refused as refusal:
        return Verdict(refusal.code, refusal.detail)
    return Verdict(None, f"{manifest['id']} {manifest['version']}", manifest, files)


def check_path(path: Path, app: App | None = None) -> Verdict:
    """The verdict on a folder (packed in memory) or a .sortaaddon file."""
    if path.is_dir():
        import pack
        try:
            data = pack.archive(path)
        except Refused as refusal:
            return Verdict(refusal.code, refusal.detail)
        return check_archive(data, app)
    return check_archive(path.read_bytes(), app)


# The archive, from its central directory, then unpacked

def archive_rules(data: bytes) -> dict[str, bytes]:
    if len(data) > MAX_PACKED:
        raise Refused("zip.size", f"the archive is {len(data):,} bytes. The limit is {MAX_PACKED:,} bytes.")
    try:
        end = sortazip.end_record(data)
        if end.count > MAX_ENTRIES:
            raise Refused("zip.entries", f"the archive has {end.count} entries. The limit is {MAX_ENTRIES}.")
        entries = sortazip.central_directory(data, end)
    except sortazip.Unreadable as problem:
        raise Refused("zip.unreadable", str(problem)) from None
    for entry in entries:
        if entry.flags & 0x2041 or entry.method == 99:
            raise Refused("zip.encrypted", f"{entry.name!r} is encrypted. Add-ons can't be.")
    for entry in entries:
        if entry.method not in (sortazip.STORE, sortazip.DEFLATE):
            raise Refused("zip.unreadable", f"{entry.name!r} uses compression method {entry.method}. "
                                            "Use stored or deflated entries.")
    for entry in entries:
        path_rule(entry.raw_name)
    for entry in entries:
        if (entry.external >> 16) & 0xF000 == 0xA000:
            raise Refused("zip.link", f"{entry.name!r} is a symbolic link. Put the file itself in the add-on.")
    seen, folded = set(), {}
    for entry in entries:
        if entry.name in seen:
            raise Refused("zip.duplicate", f"{entry.name!r} is in the archive twice.")
        seen.add(entry.name)
    for entry in entries:
        other = folded.setdefault(entry.name.lower(), entry.name)
        if other != entry.name:
            raise Refused("zip.case", f"{other!r} and {entry.name!r} differ only in case.")
    for entry in entries:
        if entry.size > MAX_RATIO * entry.packed:
            raise Refused("zip.ratio", f"{entry.name!r} unpacks to {entry.size:,} bytes from {entry.packed:,}, "
                                       f"more than {MAX_RATIO} times its packed size.")
    total = sum(entry.size for entry in entries)
    if total > MAX_RATIO * len(data):
        raise Refused("zip.ratio", f"the archive unpacks to {total:,} bytes from {len(data):,}, "
                                   f"more than {MAX_RATIO} times its size.")
    if total > MAX_UNPACKED:
        raise Refused("zip.size", f"the files add up to {total:,} bytes. The limit is {MAX_UNPACKED:,} bytes.")
    try:
        contents = sortazip.unpack(data, end, entries)
    except sortazip.Unreadable as problem:
        raise Refused("zip.unreadable", str(problem)) from None
    return {entry.name: content for entry, content in zip(entries, contents)}


def path_rule(raw: bytes) -> None:
    name = raw.decode("latin-1")
    if not raw or len(raw) > MAX_NAME_BYTES:
        raise Refused("zip.path", f"{name!r} is empty or longer than {MAX_NAME_BYTES} bytes.")
    if b"\\" in raw:
        raise Refused("zip.path", f"{name!r} has a backslash. Use / between folders.")
    if raw.startswith(b"/"):
        raise Refused("zip.path", f"{name!r} is an absolute path. Use a path inside the add-on.")
    if re.match(rb"[A-Za-z]:", raw):
        raise Refused("zip.path", f"{name!r} starts with a drive letter. Use a path inside the add-on.")
    parts = name[:-1].split("/") if name.endswith("/") else name.split("/")
    if ".." in parts:
        raise Refused("zip.path", f"{name!r} has a '..' part. Use a path inside the add-on.")
    if "" in parts or "." in parts:
        raise Refused("zip.path", f"{name!r} has an empty or '.' part.")
    if not NAME_CHARS.fullmatch(name):
        raise Refused("zip.path", f"{name!r} has a character other than A to Z, a to z, 0 to 9, '.', '_', "
                                  "'-' and '/'.")


# The files and the manifest

def package_rules(files: dict[str, bytes]) -> dict:
    for name in files:
        if not allowed_file(name):
            raise Refused("file.type", f"{name!r} isn't allowed. An add-on holds manifest.json, .js and .json "
                                       "files, README.md, LICENSE (or LICENSE.txt or LICENSE.md) and CHANGELOG.md.")
    for name in files:
        if name.count("/") > MAX_FOLDERS:
            raise Refused("file.depth", f"{name!r} is more than {MAX_FOLDERS} folders deep.")
    for name, content in files.items():
        if b"\0" in content:
            raise Refused("file.text", f"{name!r} has a NUL byte. Every file must be UTF-8 text.")
        try:
            content.decode("utf-8")
        except UnicodeDecodeError:
            raise Refused("file.text", f"{name!r} isn't UTF-8 text.") from None
    if "manifest.json" not in files:
        raise Refused("manifest.missing", "there's no manifest.json at the top of the add-on.")
    manifest = parse_json(files["manifest.json"].decode("utf-8"), "manifest.json", "manifest.json")
    if not isinstance(manifest, dict):
        raise Refused("manifest.json", "manifest.json must hold one JSON object.")
    manifest_rules(manifest, files)
    return manifest


def allowed_file(name: str) -> bool:
    if name.endswith("/"):
        return False
    parts = name.split("/")
    if any(part.startswith(".") for part in parts):
        return False
    if len(parts) == 1 and name in ROOT_FILES:
        return True
    return name.endswith((".js", ".json"))


def parse_json(text: str, name: str, code: str):
    def no_duplicates(pairs):
        keys = [key for key, _ in pairs]
        if len(keys) != len(set(keys)):
            raise Refused(code, f"{name} has a key twice.")
        return dict(pairs)

    def no_constant(word):
        raise Refused(code, f"{name} has {word}, which isn't JSON.")

    try:
        return json.loads(text, object_pairs_hook=no_duplicates, parse_constant=no_constant)
    except json.JSONDecodeError as problem:
        raise Refused(code, f"{name} isn't valid JSON: {problem.msg}, line {problem.lineno}.") from None


def whole(value, low: int, high: int) -> bool:
    """A JSON number whose value is a whole number from low to high."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return False
    if isinstance(value, float) and (not math.isfinite(value) or not value.is_integer()):
        return False
    return low <= value <= high


def label(value, longest: int) -> bool:
    """A short piece of text a person reads: 1 to `longest` characters, no
    control characters, and no space at either end."""
    return (isinstance(value, str) and 1 <= len(value) <= longest and value[0] != " " and value[-1] != " "
            and not any(ord(c) < 0x20 or 0x7F <= ord(c) <= 0x9F for c in value))


def semver(value) -> tuple | None:
    if not isinstance(value, str) or not SEMVER.fullmatch(value):
        return None
    core, _, pre = value.partition("-")
    numbers = tuple(int(n) for n in core.split("."))
    if not pre:
        return numbers + ((1,),)
    marks = tuple((0, int(p), "") if p.isdigit() else (1, 0, p) for p in pre.split("."))
    return numbers + ((0,) + marks,)


def manifest_rules(manifest: dict, files: dict[str, bytes]) -> None:
    if manifest.get("format") != "sorta-addon":
        raise Refused("format", 'format must be "sorta-addon".')
    if not whole(manifest.get("formatVersion"), 1, 1):
        raise Refused("formatVersion", "formatVersion must be 1.")
    unknown = sorted(set(manifest) - set(FIELDS))
    if unknown:
        raise Refused("manifest.json", f"manifest.json has a field the format doesn't define: {unknown[0]!r}.")
    ident = manifest.get("id")
    if not isinstance(ident, str) or not 3 <= len(ident) <= 64 or not ID.fullmatch(ident):
        raise Refused("id", "id must be 3 to 64 characters of a to z, 0 to 9, '.' and '-', with at least one "
                            "dot, like io.github.example.wordcount.")
    if ident == RESERVED or ident.startswith(RESERVED + "."):
        raise Refused("id.reserved", f"ids that start with {RESERVED}. are Sorta's own.")
    if not label(manifest.get("name"), 30):
        raise Refused("name", "name must be 1 to 30 characters, with no space at either end.")
    if semver(manifest.get("version")) is None:
        raise Refused("version", "version must be a semantic version like 1.0.0, without build metadata.")
    if semver(manifest.get("minSorta")) is None:
        raise Refused("minSorta", "minSorta must be a semantic version like 1.0.0, without build metadata.")
    if not whole(manifest.get("api"), 1, 99):
        raise Refused("api", "api must be the API's major version, a whole number. It is 1 for now.")
    author_rule(manifest.get("author"))
    if manifest.get("license") not in LICENSES:
        raise Refused("license", "license must be an SPDX id on the OSI's list, like MIT or Apache-2.0.")
    main = manifest.get("main")
    if not isinstance(main, str) or not main.endswith(".js") or main not in files:
        raise Refused("main.missing", "main must name a .js file in the add-on, like main.js.")
    if len(files[main]) > MAX_MAIN:
        raise Refused("main.size", f"{main} is {len(files[main]):,} bytes. The limit is {MAX_MAIN:,} bytes.")
    permissions = manifest.get("permissions")
    if not isinstance(permissions, list) or any(p not in PERMISSIONS for p in permissions):
        raise Refused("permissions", "permissions must be a list of these: " + ", ".join(PERMISSIONS) + ".")
    if len(set(permissions)) != len(permissions):
        raise Refused("permissions", "permissions names a permission twice.")
    if "ideas.read" in permissions and "ideas.read.folder" in permissions:
        raise Refused("permissions", "ask for ideas.read or ideas.read.folder, not both.")
    hosts_rule(manifest.get("hosts", []), "net" in permissions)
    contributes = manifest.get("contributes", {})
    if isinstance(contributes, dict) and "commands" in contributes:
        commands_rule(contributes["commands"])
    contributes_rule(contributes, set(permissions), files)


def author_rule(author) -> None:
    if not isinstance(author, dict) or set(author) != {"name", "url"}:
        raise Refused("author", "author must have a name and a url, and nothing else.")
    name, url = author["name"], author["url"]
    if not label(name, 60) or "@" in name:
        raise Refused("author", "author's name must be 1 to 60 characters, with no @.")
    host = url[len("https://"):].split("/")[0].split("?")[0].split("#")[0] if isinstance(url, str) else ""
    if (not isinstance(url, str) or not url.startswith("https://") or len(url) > 200 or not host or "@" in host
            or any(c.isspace() or ord(c) < 0x20 for c in url)):
        raise Refused("author", "author's url must be an https:// address of up to 200 characters, "
                                "without a user name.")


def hosts_rule(hosts, wants_net: bool) -> None:
    if not isinstance(hosts, list):
        raise Refused("hosts", "hosts must be a list of host names.")
    if wants_net and not hosts:
        raise Refused("hosts", "the net permission needs hosts: the HTTPS hosts the add-on may reach.")
    if hosts and not wants_net:
        raise Refused("hosts", "hosts are only for the net permission. Ask for net, or leave hosts empty.")
    for host in hosts:
        parts = host.split(".") if isinstance(host, str) else []
        if (len(parts) < 2 or len(host) > 253 or not all(HOST_LABEL.fullmatch(p) for p in parts)
                or parts[-1].isdigit()):
            raise Refused("hosts", f"{host!r} isn't a host name. Write each host in full and in lowercase, "
                                   "like api.example.com, with no scheme, port, path or wildcard.")
    if len(set(hosts)) != len(hosts):
        raise Refused("hosts", "hosts names a host twice.")


def commands_rule(commands) -> None:
    if not isinstance(commands, list) or not 1 <= len(commands) <= 5:
        raise Refused("commands", "contributes.commands must list 1 to 5 commands.")
    ids = set()
    for command in commands:
        if not isinstance(command, dict) or set(command) != {"id", "title"}:
            raise Refused("commands", "each command has an id and a title, and nothing else.")
        if not isinstance(command["id"], str) or not COMMAND_ID.fullmatch(command["id"]):
            raise Refused("commands", "a command's id is 1 to 32 characters of a to z, 0 to 9 and '-'.")
        if not label(command["title"], 30):
            raise Refused("commands", f"command {command['id']!r} needs a title of 1 to 30 characters.")
        if command["id"] in ids:
            raise Refused("commands", f"command {command['id']!r} is listed twice.")
        ids.add(command["id"])


def contributes_rule(contributes, permissions: set, files: dict[str, bytes]) -> None:
    if not isinstance(contributes, dict):
        raise Refused("contributes", "contributes must be an object.")
    unknown = sorted(set(contributes) - set(CONTRIBUTES))
    if unknown:
        raise Refused("contributes", f"contributes has a field the format doesn't define: {unknown[0]!r}.")
    for field, permission in NEEDS.items():
        if (field in contributes) != (permission in permissions):
            raise Refused("contributes", f"contributes.{field} and the {permission} permission go together. "
                                         "Ask for both or neither.")
    view = contributes.get("cardView")
    if view is not None and (not isinstance(view, dict) or set(view) != {"id", "title"}
                             or not isinstance(view["id"], str) or not COMMAND_ID.fullmatch(view["id"])
                             or not label(view["title"], 30)):
        raise Refused("contributes", "cardView has an id of 1 to 32 characters of a to z, 0 to 9 and '-', "
                                     "and a title of 1 to 30 characters.")
    if "settings" in contributes:
        settings_rule(data_file(contributes["settings"], "settings", files))
    if "boardPalette" in contributes:
        palette_rule(data_file(contributes["boardPalette"], "boardPalette", files))


def data_file(name, field: str, files: dict[str, bytes]):
    if not isinstance(name, str) or not name.endswith(".json") or name not in files:
        raise Refused("contributes", f"contributes.{field} must name a .json file in the add-on.")
    return parse_json(files[name].decode("utf-8"), name, "contributes")


def settings_rule(schema) -> None:
    items = schema.get("settings") if isinstance(schema, dict) and set(schema) == {"settings"} else None
    if not isinstance(items, list) or not 1 <= len(items) <= 20:
        raise Refused("contributes", 'the settings file holds {"settings": [...]}, with 1 to 20 settings.')
    keys = set()
    for item in items:
        if not isinstance(item, dict) or not isinstance(item.get("key"), str) or not KEY.fullmatch(item["key"]):
            raise Refused("contributes", "each setting has a key of 1 to 32 letters and digits, starting with "
                                         "a lowercase letter.")
        key, kind, default = item["key"], item.get("type"), item.get("default")
        fields = {"toggle": {"key", "type", "title", "default"},
                  "text": {"key", "type", "title", "default", "maxLength"},
                  "choice": {"key", "type", "title", "default", "choices"}}.get(kind)
        if fields is None or not set(item) <= fields or not {"key", "type", "title", "default"} <= set(item):
            raise Refused("contributes", f"setting {key!r} needs a type (toggle, text or choice), a title and "
                                         "a default, and only the fields its type takes.")
        if not label(item["title"], 40) or key in keys:
            raise Refused("contributes", f"setting {key!r} needs a title of 1 to 40 characters and a key of "
                                         "its own.")
        keys.add(key)
        if kind == "toggle" and not isinstance(default, bool):
            raise Refused("contributes", f"setting {key!r} is a toggle, so its default is true or false.")
        if kind == "text":
            longest = item.get("maxLength", 200)
            if not whole(longest, 1, 200) or not isinstance(default, str) or len(default) > longest:
                raise Refused("contributes", f"setting {key!r} is text: maxLength is 1 to 200, and the default "
                                             "is text no longer than that.")
        if kind == "choice":
            choices = item.get("choices")
            if (not isinstance(choices, list) or not 2 <= len(choices) <= 10
                    or any(not isinstance(c, dict) or set(c) != {"value", "title"} or not isinstance(c["value"], str)
                           or not KEY.fullmatch(c["value"]) or not label(c["title"], 40) for c in choices)
                    or len({c["value"] for c in choices}) != len(choices)
                    or default not in [c["value"] for c in choices]):
                raise Refused("contributes", f"setting {key!r} is a choice: 2 to 10 choices, each with its own "
                                             "value and a title, and a default that is one of the values.")


def palette_rule(palette) -> None:
    if not isinstance(palette, dict) or set(palette) != {"day", "night"}:
        raise Refused("contributes", "a board palette has a day set and a night set, and nothing else.")
    for time, tones in palette.items():
        if (not isinstance(tones, dict) or set(tones) != {"page", "card", "cardText", "link", "folders"}
                or not all(isinstance(tones[k], str) and COLOUR.fullmatch(tones[k])
                           for k in ("page", "card", "cardText", "link"))
                or not isinstance(tones["folders"], list) or len(tones["folders"]) > 6
                or not all(isinstance(t, str) and COLOUR.fullmatch(t) for t in tones["folders"])):
            raise Refused("contributes", f"the {time} set has page, card, cardText and link, each like #1F2A33, "
                                         "and up to 6 folder tones.")
        for front, back, least in (("cardText", "card", 4.5), ("card", "page", 3.0), ("link", "page", 3.0)):
            ratio = contrast(tones[front], tones[back])
            if ratio < least:
                raise Refused("contributes", f"by {time}, {front} on {back} has a contrast of {ratio:.2f}:1. "
                                             f"It needs at least {least}:1.")


def contrast(one: str, two: str) -> float:
    def luminance(colour: str) -> float:
        channels = []
        for at in (1, 3, 5):
            c = int(colour[at:at + 2], 16) / 255
            channels.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    light, dark = sorted((luminance(one), luminance(two)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


def install_rules(manifest: dict, app: App) -> None:
    if manifest["api"] > app.api:
        raise Refused("install.api", f"the add-on needs API {int(manifest['api'])}. This Sorta has API {app.api}.")
    if semver(manifest["minSorta"]) > semver(app.version):
        raise Refused("install.minSorta", f"the add-on needs Sorta {manifest['minSorta']} or later. "
                                          f"This is {app.version}.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Check a Sorta add-on: a folder or a .sortaaddon file.")
    parser.add_argument("path", type=Path)
    parser.add_argument("--app-version", help="also run the install checks, as this Sorta version")
    parser.add_argument("--app-api", type=int, default=1, help="the API major version for the install checks")
    args = parser.parse_args()
    if args.app_version and semver(args.app_version) is None:
        parser.error("--app-version must be a semantic version like 1.0.0")
    if not args.path.exists():
        parser.error(f"{args.path} doesn't exist")
    verdict = check_path(args.path, App(args.app_version, args.app_api) if args.app_version else None)
    print(f"ok {verdict.detail}" if verdict.ok else f"refused {verdict.code}: {verdict.detail}")
    return 0 if verdict.ok else 1


if __name__ == "__main__":
    sys.exit(main())
