"""Checks a built Sorta.app against docs/scoping.md: its ids, its name, its
launch screen, its icon and its Share extension.

    python3 tools/check_ios_app.py path/to/Sorta.app [--icon AppIcon] [--alternates A,B] [--share]

The name must also come from the String Catalog, InfoPlist.xcstrings, which
the build compiles into the app as InfoPlist.strings in en.lproj or as
InfoPlist.loctable. The launch screen is the Page colour alone, with no
storyboard. Without --icon the app must carry no icon (the public builds);
with it, the named icon set must be the app's icon (the builds the owner
installs). The alternate icons must be exactly the ones named, none by
default. With --share (CI builds with SORTA_SHARE_EXTENSION=YES) the Share
extension must be in PlugIns, as com.cramdown.sorta.share for the share
sheet; without it, the app must carry no extension, so a device build spends
no App ID on one (ios/project.yml).
"""
import argparse
import pathlib
import plistlib
import re
import sys

WANTED = {
    "CFBundleIdentifier": "com.cramdown.sorta",
    "CFBundleDisplayName": "Sorta",
    "MinimumOSVersion": "26.0",
    "UIDeviceFamily": [1, 2],
    "UILaunchScreen": {"UIColorName": "Page"},
    # ProMotion's 120 Hz on an iPhone (ios/project.yml)
    "CADisableMinimumFrameDurationOnPhone": True,
    "UISupportedInterfaceOrientations": [
        "UIInterfaceOrientationPortrait",
        "UIInterfaceOrientationLandscapeLeft",
        "UIInterfaceOrientationLandscapeRight",
    ],
    "UISupportedInterfaceOrientations~ipad": [
        "UIInterfaceOrientationPortrait",
        "UIInterfaceOrientationPortraitUpsideDown",
        "UIInterfaceOrientationLandscapeLeft",
        "UIInterfaceOrientationLandscapeRight",
    ],
}
STRINGS_LINE = re.compile(r'"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;')


def strings_file(path: pathlib.Path) -> dict:
    """A compiled .strings file: a property list, or "key" = "value"; text."""
    raw = path.read_bytes()
    try:
        return plistlib.loads(raw)
    except Exception:
        pass
    text = raw.decode("utf-16") if raw[:2] in (b"\xff\xfe", b"\xfe\xff") else raw.decode("utf-8-sig")
    return {unescape(k): unescape(v) for k, v in STRINGS_LINE.findall(text)}


def unescape(text: str) -> str:
    return re.sub(r"\\(.)", r"\1", text)


def catalog_names(app: pathlib.Path) -> dict:
    """The display name each language's compiled InfoPlist table gives."""
    names = {}
    for table in sorted(app.glob("*.lproj/InfoPlist.strings")):
        names[table.parent.stem] = strings_file(table).get("CFBundleDisplayName")
    loctable = app / "InfoPlist.loctable"
    if loctable.exists():
        for language, strings in plistlib.loads(loctable.read_bytes()).items():
            if isinstance(strings, dict) and "CFBundleDisplayName" in strings:
                names.setdefault(language, strings.get("CFBundleDisplayName"))
    return names


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=pathlib.Path)
    parser.add_argument("--icon", default="")
    parser.add_argument("--alternates", default="")
    parser.add_argument("--share", action="store_true")
    args = parser.parse_args()
    info = plistlib.loads((args.app / "Info.plist").read_bytes())
    problems = []
    for key, value in WANTED.items():
        seen = info.get(key)
        print(f"{key}: {seen!r}")
        if seen != value:
            problems.append(f"{key} is {seen!r}, not {value!r}")
    names = catalog_names(args.app)
    print(f"names from the String Catalog: {names!r}")
    if names.get("en") != WANTED["CFBundleDisplayName"]:
        problems.append(f"the String Catalog's English name is {names.get('en')!r}, not {WANTED['CFBundleDisplayName']!r}")
    if "UILaunchStoryboardName" in info:
        problems.append(f"a launch storyboard is named ({info['UILaunchStoryboardName']!r})")
    schemes = [s for kind in info.get("CFBundleURLTypes", []) for s in kind.get("CFBundleURLSchemes", [])]
    print(f"URL schemes: {schemes!r}")
    if schemes != ["sorta"]:
        problems.append(f"the URL schemes are {schemes!r}, not ['sorta']")
    icon = info.get("CFBundleIcons", {}).get("CFBundlePrimaryIcon", {}).get("CFBundleIconName", "")
    print(f"icon: {icon!r}")
    if icon != args.icon:
        problems.append(f"the icon is {icon!r}, not {args.icon!r}")
    alternates = sorted(info.get("CFBundleIcons", {}).get("CFBundleAlternateIcons", {}))
    wanted = sorted(name for name in args.alternates.split(",") if name)
    print(f"alternate icons: {alternates!r}")
    if alternates != wanted:
        problems.append(f"the alternate icons are {alternates!r}, not {wanted!r}")
    plugins = args.app / "PlugIns"
    extensions = sorted(p.name for p in plugins.glob("*.appex")) if plugins.is_dir() else []
    print(f"extensions: {extensions!r}")
    if not args.share:
        if extensions:
            problems.append(f"the app carries {extensions!r}, each one more App ID on a device")
    elif extensions != ["SortaShare.appex"]:
        problems.append(f"the extensions are {extensions!r}, not ['SortaShare.appex']")
    else:
        share = plistlib.loads((plugins / "SortaShare.appex" / "Info.plist").read_bytes())
        point = share.get("NSExtension", {}).get("NSExtensionPointIdentifier")
        print(f"Share extension: {share.get('CFBundleIdentifier')!r}, {point!r}")
        if share.get("CFBundleIdentifier") != "com.cramdown.sorta.share":
            problems.append(f"the Share extension's id is {share.get('CFBundleIdentifier')!r}")
        if point != "com.apple.share-services":
            problems.append(f"the Share extension's point is {point!r}, not the share sheet's")
    for problem in problems:
        print(f"::error::{problem}")
    print("Sorta.app: as the scoping packet says" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
