"""Paths and helpers the kit's tests share. Standard library only."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

TESTS = Path(__file__).resolve().parent
TOOLS = TESTS.parent
KIT = TOOLS.parent
ADDONS = KIT.parent
ROOT = ADDONS.parent
FIXTURES = ADDONS / "fixtures"
PACKAGES = FIXTURES / "packages"
ZIPS = FIXTURES / "zips"
API_PAIRS = FIXTURES / "api"
SUBMISSIONS = FIXTURES / "submissions"
DIRECTORY = ADDONS / "directory"
TEMPLATE = KIT / "template"
SPEC = ADDONS / "spec"

sys.path.insert(0, str(TOOLS))


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def folders(parent: Path) -> list[Path]:
    return sorted(path for path in parent.iterdir() if path.is_dir())


def python(*args, cwd=None, env=None) -> subprocess.CompletedProcess:
    """Runs a kit tool the way CI and a laptop do: python3 -I -B."""
    return subprocess.run([sys.executable, "-I", "-B", *map(str, args)], capture_output=True, text=True,
                          cwd=cwd, env=env)


class Scratch:
    """A temporary folder, removed afterwards."""

    def __enter__(self) -> Path:
        self.path = Path(tempfile.mkdtemp(prefix="sorta-kit-"))
        return self.path

    def __exit__(self, *_):
        shutil.rmtree(self.path, ignore_errors=True)


def walk_files(top: Path, skip=(".git",)):
    for here, names, files in os.walk(top):
        names[:] = sorted(n for n in names if n not in skip)
        for name in sorted(files):
            yield Path(here) / name
