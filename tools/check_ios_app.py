"""Checks a built Sorta.app against docs/scoping.md: its ids, its launch
screen and its icon.

    python3 tools/check_ios_app.py path/to/Sorta.app [--icon AppIcon]

The launch screen is the Page colour alone, with no storyboard. Without
--icon the app must carry no icon (the public builds); with it, the named
icon set must be the app's icon (the builds the owner installs).
"""
import argparse
import pathlib
import plistlib
import sys

WANTED = {
    "CFBundleIdentifier": "com.cramdown.sorta",
    "CFBundleDisplayName": "Sorta",
    "MinimumOSVersion": "26.0",
    "UIDeviceFamily": [1, 2],
    "UILaunchScreen": {"UIColorName": "Page"},
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=pathlib.Path)
    parser.add_argument("--icon", default="")
    args = parser.parse_args()
    info = plistlib.loads((args.app / "Info.plist").read_bytes())
    problems = []
    for key, value in WANTED.items():
        seen = info.get(key)
        print(f"{key}: {seen!r}")
        if seen != value:
            problems.append(f"{key} is {seen!r}, not {value!r}")
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
    for problem in problems:
        print(f"::error::{problem}")
    print("Sorta.app: as the scoping packet says" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
