"""Writes prelude.js into sorta_prelude.h as a C string (S07b).

    python3 -I addons/engine/host/embed_prelude.py           write the header
    python3 -I addons/engine/host/embed_prelude.py --check   fail unless it is current
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "prelude.js")
HEADER = os.path.join(HERE, "sorta_prelude.h")


def header():
    lines = ["/* Written by embed_prelude.py from prelude.js; do not edit. */",
             "static const char sa_prelude[] ="]
    for line in open(SOURCE, encoding="utf-8").read().splitlines():
        if not line.isascii():
            raise SystemExit("prelude.js must be ASCII")
        quoted = line.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'    "{quoted}\\n"')
    lines[-1] += ";"
    return "\n".join(lines) + "\n"


def main(argv):
    text = header()
    if "--check" in argv:
        current = open(HEADER, encoding="utf-8").read() if os.path.exists(HEADER) else ""
        if current != text:
            print("FAIL sorta_prelude.h is not prelude.js; run embed_prelude.py")
            return 1
        print("sorta_prelude.h matches prelude.js: ok")
        return 0
    open(HEADER, "w", encoding="utf-8").write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
