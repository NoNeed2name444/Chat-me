"""Checks a built Sorta APK against docs/scoping.md with aapt2: its package,
its name, its SDK levels, its activity and its icon.

    python3 tools/check_apk.py path/to/aapt2 path/to/app.apk [--art]

Without --art the icon must be the system's default (the public builds);
with it, the art's adaptive icon (the builds the owner installs).
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
    for problem in problems:
        print(f"::error::{problem}")
    print("APK: as the scoping packet says" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
