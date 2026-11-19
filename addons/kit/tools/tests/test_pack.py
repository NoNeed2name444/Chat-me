"""A2: pack builds the same bytes every time, and agrees with check."""
import hashlib
import os
import random
import shutil
import unittest
from pathlib import Path

from common import PACKAGES, TEMPLATE, TOOLS, Scratch, folders, python, read_json

import check
import pack
import sortazip


def copy_shuffled(source: Path, target: Path, seed: int) -> None:
    """Copies a folder with its files made in a shuffled order, with other
    times and modes, so only the names and bytes stay the same."""
    rng = random.Random(seed)
    files = sorted(p.relative_to(source) for p in source.rglob("*") if p.is_file())
    rng.shuffle(files)
    for name in files:
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes((source / name).read_bytes())
        os.chmod(path, rng.choice((0o600, 0o644, 0o755)))
        stamp = rng.randint(400_000_000, 1_900_000_000)
        os.utime(path, (stamp, stamp))


class Determinism(unittest.TestCase):
    def test_two_shuffled_copies_give_the_same_sha256(self):
        sources = [TEMPLATE] + [f for f in folders(PACKAGES) if read_json(f / "expect.json")["verdict"] == "ok"]
        for source in sources:
            with self.subTest(source.name), Scratch() as scratch:
                copy_shuffled(source, scratch / "one", 1)
                copy_shuffled(source, scratch / "two", 2)
                one, _ = pack.pack(scratch / "one")
                two, _ = pack.pack(scratch / "two")
                self.assertTrue(one)
                self.assertEqual(hashlib.sha256(one).hexdigest(), hashlib.sha256(two).hexdigest())

    def test_the_archive_has_fixed_values(self):
        data, verdict = pack.pack(TEMPLATE)
        self.assertTrue(verdict.ok, verdict.detail)
        end = sortazip.end_record(data)
        entries = sortazip.central_directory(data, end)
        names = [entry.raw_name for entry in entries]
        self.assertEqual(names, sorted(names))
        for entry in entries:
            with self.subTest(entry.name):
                self.assertEqual(entry.method, sortazip.STORE)
                self.assertEqual(entry.external, 0)
                self.assertEqual(entry.flags, 0)
        # no extra fields and no comments: each directory entry is its fixed size plus its name
        self.assertEqual(end.directory_size, sum(sortazip.CENTRAL_SIZE + len(n) for n in names))
        self.assertEqual(data[-2:], b"\0\0")

    def test_the_command_writes_the_same_bytes(self):
        with Scratch() as scratch:
            out = scratch / "word-count.sortaaddon"
            done = python(TOOLS / "pack.py", TEMPLATE, "--out", out)
            self.assertEqual(done.returncode, 0, done.stdout + done.stderr)
            data = out.read_bytes()
            self.assertEqual(data, pack.pack(TEMPLATE)[0])
            self.assertIn(hashlib.sha256(data).hexdigest(), done.stdout)


class AgreesWithCheck(unittest.TestCase):
    def test_pack_refuses_what_check_refuses_with_the_same_code(self):
        for folder in folders(PACKAGES):
            with self.subTest(folder.name):
                data, verdict = pack.pack(folder)
                self.assertEqual(verdict.code, check.check_path(folder).code)
                if not verdict.ok:
                    done = python(TOOLS / "pack.py", folder, "--out", Path(os.devnull))
                    self.assertEqual(done.returncode, 1)
                    self.assertTrue(done.stdout.startswith(f"refused {verdict.code}: "), done.stdout)

    def test_check_passes_everything_pack_built(self):
        sources = [TEMPLATE] + folders(PACKAGES)
        built = 0
        with Scratch() as scratch:
            for source in sources:
                data, verdict = pack.pack(source)
                if not verdict.ok:
                    continue
                with self.subTest(source.name):
                    out = scratch / f"{source.name}.sortaaddon"
                    out.write_bytes(data)
                    self.assertTrue(check.check_path(out).ok)
                    done = python(TOOLS / "check.py", out)
                    self.assertEqual(done.returncode, 0, done.stdout)
                    built += 1
        self.assertGreaterEqual(built, 10)

    def test_a_link_in_the_folder_is_refused_as_a_link(self):
        with Scratch() as scratch:
            shutil.copytree(PACKAGES / "ok-minimal", scratch / "addon")
            (scratch / "addon" / "other.js").symlink_to("main.js")
            data, verdict = pack.pack(scratch / "addon")
            self.assertEqual(verdict.code, "zip.link")
            self.assertEqual(check.check_path(scratch / "addon").code, "zip.link")


if __name__ == "__main__":
    unittest.main()
