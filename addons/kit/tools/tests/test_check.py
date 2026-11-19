"""A1: the rules, the reason codes and the fixtures."""
import os
import shutil
import unittest
from pathlib import Path
from unittest import mock

from common import PACKAGES, ROOT, SPEC, TEMPLATE, TOOLS, ZIPS, Scratch, folders, python, read_json, walk_files

import check
import review
import sortazip
import zipfix


def app_for(expect: dict):
    given = expect.get("app")
    return check.App(given["sorta"], given["api"]) if given else None


def outcome(verdict: check.Verdict) -> tuple:
    return ("ok", None) if verdict.ok else ("refused", verdict.code)


def expected(expect: dict) -> tuple:
    return (expect["verdict"], expect.get("reason"))


class Reasons(unittest.TestCase):
    def test_reasons_json_lists_every_code_once(self):
        listed = [reason["code"] for reason in read_json(SPEC / "reasons.json")["reasons"]]
        self.assertEqual(len(listed), len(set(listed)))
        self.assertEqual(set(listed), set(check.CODES))
        self.assertEqual(len(listed), 34)

    def test_reasons_have_a_group_and_meaning(self):
        for reason in read_json(SPEC / "reasons.json")["reasons"]:
            with self.subTest(reason["code"]):
                self.assertIn(reason["group"], ("archive", "files", "manifest", "install"))
                self.assertTrue(reason["meaning"].strip())

    def test_review_json_lists_every_review_code_once(self):
        listed = [reason["code"] for reason in read_json(SPEC / "review.json")["reasons"]]
        self.assertEqual(len(listed), len(set(listed)))
        self.assertEqual(set(listed), set(review.CODES))


class Packages(unittest.TestCase):
    def test_there_are_at_least_30_fixture_folders(self):
        self.assertGreaterEqual(len(folders(PACKAGES)), 30)

    def test_each_folder_gives_its_expected_verdict(self):
        for folder in folders(PACKAGES):
            with self.subTest(folder.name):
                expect = read_json(folder / "expect.json")
                self.assertIn(expect["verdict"], ("ok", "refused"))
                with Scratch() as scratch:
                    if "fifo" in expect:
                        folder = Path(shutil.copytree(folder, scratch / folder.name))
                        os.mkfifo(folder / expect["fifo"])
                    verdict = check.check_path(folder, app_for(expect))
                self.assertEqual(outcome(verdict), expected(expect), verdict.detail)

    def test_install_codes_need_the_app(self):
        for folder in folders(PACKAGES):
            expect = read_json(folder / "expect.json")
            if str(expect.get("reason", "")).startswith("install."):
                with self.subTest(folder.name):
                    self.assertTrue(check.check_path(folder).ok)


class Zips(unittest.TestCase):
    def test_each_description_gives_its_expected_verdict(self):
        descriptions = sorted(ZIPS.glob("*.json"))
        self.assertGreaterEqual(len(descriptions), 15)
        for path in descriptions:
            with self.subTest(path.stem):
                expect = read_json(path)["expect"]
                verdict = check.check_archive(zipfix.build(path), app_for(expect))
                self.assertEqual(outcome(verdict), expected(expect), verdict.detail)

    def test_the_named_archive_cases_exist(self):
        wanted = {
            "zip-path-dotdot": "zip.path", "zip-path-absolute": "zip.path", "zip-path-backslash": "zip.path",
            "zip-path-drive": "zip.path", "zip-link": "zip.link", "zip-duplicate": "zip.duplicate",
            "zip-case": "zip.case", "zip-encrypted": "zip.encrypted", "zip-ratio-entry": "zip.ratio",
            "zip-ratio-archive": "zip.ratio", "zip-entries": "zip.entries", "zip-size-packed": "zip.size",
            "zip-size-unpacked": "zip.size",
        }
        for name, code in wanted.items():
            with self.subTest(name):
                self.assertEqual(read_json(ZIPS / f"{name}.json")["expect"]["reason"], code)

    def test_zip_entries_has_201_entries(self):
        data = zipfix.build(ZIPS / "zip-entries.json")
        self.assertEqual(sortazip.end_record(data).count, 201)

    def test_the_archive_rules_run_before_anything_is_unpacked(self):
        def unpack(*_):
            raise AssertionError("unpacked before the archive rules refused it")
        for path in sorted(ZIPS.glob("*.json")):
            reason = read_json(path)["expect"].get("reason") or ""
            if reason.startswith("zip.") and reason != "zip.unreadable":
                with self.subTest(path.stem), mock.patch.object(sortazip, "unpack", unpack):
                    self.assertEqual(check.check_archive(zipfix.build(path)).code, reason)

    def test_zipfix_writes_each_archive(self):
        with Scratch() as out:
            done = python(TOOLS / "zipfix.py", ZIPS / "zip-link.json", "--out", out)
            self.assertEqual(done.returncode, 0, done.stderr)
            self.assertEqual((out / "zip-link.sortaaddon").read_bytes(), zipfix.build(ZIPS / "zip-link.json"))


class Coverage(unittest.TestCase):
    def test_every_code_has_a_fixture(self):
        groups = {reason["code"]: reason["group"] for reason in read_json(SPEC / "reasons.json")["reasons"]}
        by_folder = {read_json(f / "expect.json").get("reason") for f in folders(PACKAGES)}
        by_zip = {read_json(p)["expect"].get("reason") for p in ZIPS.glob("*.json")}
        for code, group in groups.items():
            with self.subTest(code):
                if group == "archive":
                    self.assertIn(code, by_zip)
                else:
                    self.assertIn(code, by_folder, "each file, manifest and install code needs a folder fixture")

    def test_valid_packages_are_covered_too(self):
        self.assertGreaterEqual(sum(read_json(f / "expect.json")["verdict"] == "ok" for f in folders(PACKAGES)), 5)
        self.assertTrue(any(read_json(p)["expect"]["verdict"] == "ok" for p in ZIPS.glob("*.json")))


class Command(unittest.TestCase):
    TOOL = TOOLS / "check.py"

    def test_ok(self):
        done = python(self.TOOL, PACKAGES / "ok-minimal")
        self.assertEqual(done.returncode, 0, done.stdout + done.stderr)
        self.assertRegex(done.stdout, r"^ok \S+ \S+\n$")

    def test_refused(self):
        done = python(self.TOOL, PACKAGES / "id-reserved")
        self.assertEqual(done.returncode, 1)
        self.assertTrue(done.stdout.startswith("refused id.reserved: "), done.stdout)

    def test_install_flags(self):
        done = python(self.TOOL, PACKAGES / "install-api", "--app-version", "1.0.0", "--app-api", "1")
        self.assertEqual(done.returncode, 1)
        self.assertTrue(done.stdout.startswith("refused install.api: "), done.stdout)


class NeverATraceback(unittest.TestCase):
    """Hostile input gets a refusal code from both programs, never a crash."""

    def refusals(self, change, code):
        with Scratch() as scratch:
            folder = Path(shutil.copytree(TEMPLATE, scratch / "addon"))
            change(folder)
            for tool, extra in ((TOOLS / "check.py", []), (TOOLS / "pack.py", ["--out", scratch / "out"])):
                with self.subTest(tool.name):
                    done = python(tool, folder, *extra)
                    self.assertEqual(done.returncode, 1, done.stdout + done.stderr)
                    self.assertEqual(done.stderr, "")
                    self.assertTrue(done.stdout.startswith(f"refused {code}: "), done.stdout[:200])
                    self.assertFalse((scratch / "out").exists())

    def test_a_manifest_of_200000_nested_lists(self):
        self.refusals(lambda f: (f / "manifest.json").write_text("[" * 200000 + "]" * 200000), "manifest.json")

    def test_a_settings_file_of_200000_nested_lists(self):
        self.refusals(lambda f: (f / "settings.json").write_text("[" * 200000 + "]" * 200000), "contributes")

    def test_a_number_of_5001_digits(self):
        def change(folder):
            path = folder / "manifest.json"
            text = path.read_text(encoding="utf-8")
            self.assertIn('"formatVersion": 1,', text)
            path.write_text(text.replace('"formatVersion": 1,', '"formatVersion": 1' + "0" * 5000 + ","),
                            encoding="utf-8")
        self.refusals(change, "manifest.json")

    def test_a_named_pipe_in_the_folder(self):
        self.refusals(lambda f: os.mkfifo(f / "p.json"), "file.type")

    def test_the_json_reader_catches_what_the_parser_raises(self):
        for error in (RecursionError(), ValueError("Exceeds the limit (4300 digits)")):
            with self.subTest(type(error).__name__), mock.patch.object(check.json, "loads", side_effect=error):
                with self.assertRaises(check.Refused) as caught:
                    check.parse_json("{}", "manifest.json", "manifest.json")
                self.assertEqual(caught.exception.code, "manifest.json")

    def test_refused_is_one_class(self):
        import pack
        self.assertIs(check.Refused, sortazip.Refused)
        self.assertIs(pack.check.Refused, sortazip.Refused)


class Repository(unittest.TestCase):
    def test_no_archive_is_committed(self):
        # reference/ is Stethoscore's frozen copy, which is never edited and
        # never mirrored; everything else is Sorta's own
        found = [str(p.relative_to(ROOT)) for p in walk_files(ROOT, skip=(".git", "reference"))
                 if p.suffix.lower() in (".zip", ".sortaaddon")]
        self.assertEqual(found, [])


if __name__ == "__main__":
    unittest.main()
