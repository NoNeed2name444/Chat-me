"""Checks a built Sorta APK against docs/scoping.md with aapt2: its package,
its name, its SDK levels, its activity, its icon and its splash mark.

    python3 tools/check_apk.py path/to/aapt2 path/to/app.apk [--art]

Without --art the icon must be the system's default and the splash mark
clear (the public builds); with it, the art's adaptive icon and its splash
drawable (the builds the owner installs).
"""
import argparse
import re
import subprocess
import sys

WANTED = [
    ("package", r"^package: name='com\.cramdown\.sorta' versionCode='\d+' versionName='[^']+'"),
    ("minimum SDK", r"^minSdkVersion:'26'$"),
    ("target SDK", r"^targetSdkVersion:'37'$"),
    ("name", r"^application-label:'Sorta'$"),
    ("activity", r"^launchable-activity: name='com\.cramdown\.sorta\.MainActivity'"),
]
# drawable/splash_mark in the default configuration, the splash theme's mark
# (android/app/src/main/res/values-v31/themes.xml): a clear colour without
# the art (android/app/plain/res), the art's drawable with it
SPLASH = {False: "#00000000", True: "@drawable/branchstone_splash"}


def splash_mark(aapt2: str, apk: str) -> tuple[str, str]:
    """The splash mark's default value, a reference given by its name, and
    the lines aapt2 printed for it."""
    dump = subprocess.run([aapt2, "dump", "resources", apk], capture_output=True, text=True, check=True).stdout
    names = dict(re.findall(r"^\s*resource (0x[0-9a-fA-F]+) (\S+)", dump, re.M))
    found = re.search(r"^\s*resource 0x[0-9a-fA-F]+ drawable/splash_mark\b.*\n((?:[ \t]+\(.*\n?)*)", dump, re.M)
    if not found:
        return "missing", ""
    for line in found.group(1).splitlines():
        config, _, value = line.strip().partition(") ")
        if config == "(":
            value = value.strip()
            if value.startswith("@0x"):
                value = "@" + names.get(value[1:], value[1:])
            return value.lower() if value.startswith("#") else value, found.group(0)
    return "no default value", found.group(0)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("aapt2")
    parser.add_argument("apk")
    parser.add_argument("--art", action="store_true")
    args = parser.parse_args()
    badging = subprocess.run([args.aapt2, "dump", "badging", args.apk],
                             capture_output=True, text=True, check=True).stdout
    print(badging)
    problems = [f"no {what} line matching {pattern}" for what, pattern in WANTED
                if not re.search(pattern, badging, re.M)]
    icons = re.findall(r"^application-icon-\d+:'([^']*)'$", badging, re.M)
    if args.art:
        if not icons or not all("ic_launcher" in icon for icon in icons):
            problems.append(f"the icon is {icons!r}, not the art's ic_launcher")
    elif any("ic_launcher" in icon for icon in icons):
        problems.append(f"a build without the art has the art's icon: {icons!r}")
    mark, lines = splash_mark(args.aapt2, args.apk)
    print(f"splash mark: {mark}")
    if mark != SPLASH[args.art]:
        problems.append(f"the splash mark is {mark!r}, not {SPLASH[args.art]!r}")
        print(lines)
    for problem in problems:
        print(f"::error::{problem}")
    print("APK: as the scoping packet says" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
