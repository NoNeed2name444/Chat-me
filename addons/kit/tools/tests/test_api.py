"""A3: the API v1 declarations, their snapshot, and the diff."""
import unittest

from common import API_PAIRS, KIT, TOOLS, folders, python, read_json

import api_diff
import api_snapshot

DECLARATIONS = KIT / "api" / "sorta-addon-v1.d.ts"
SNAPSHOT = KIT / "api" / "v1.json"


class Snapshot(unittest.TestCase):
    def test_v1_json_describes_the_same_calls_as_the_declarations(self):
        made = api_snapshot.snapshot(DECLARATIONS.read_text(encoding="utf-8"))
        self.assertEqual(made, read_json(SNAPSHOT))
        self.assertEqual(api_snapshot.dump(made), SNAPSHOT.read_text(encoding="utf-8"))

    def test_the_snapshot_holds_section_5s_calls(self):
        calls = read_json(SNAPSHOT)["calls"]
        for name in ("sorta.ideas.list", "sorta.ideas.get", "sorta.ideas.search", "sorta.ideas.capture",
                     "sorta.ideas.tag", "sorta.ideas.untag", "sorta.ideas.link", "sorta.ideas.unlink",
                     "sorta.ideas.file", "sorta.folders.list", "sorta.commands.on", "sorta.cards.on",
                     "sorta.settings.get", "sorta.settings.on", "sorta.storage.get", "sorta.storage.set",
                     "sorta.storage.remove", "sorta.net.fetch", "sorta.has", "console.log"):
            with self.subTest(name):
                self.assertIn(name, calls)
        self.assertEqual(read_json(SNAPSHOT)["version"], {"major": 1, "minor": 0})

    def test_the_check_command(self):
        done = python(TOOLS / "api_snapshot.py", DECLARATIONS, "--check", SNAPSHOT)
        self.assertEqual(done.returncode, 0, done.stdout + done.stderr)

    def test_the_check_command_fails_on_a_changed_snapshot(self):
        changed = read_json(SNAPSHOT)
        del changed["calls"]["sorta.ideas.tag"]
        made = api_snapshot.dump(api_snapshot.snapshot(DECLARATIONS.read_text(encoding="utf-8")))
        self.assertNotEqual(api_snapshot.dump(changed), made)


class Diff(unittest.TestCase):
    def test_there_is_a_pair_for_each_kind_of_change(self):
        seen = set()
        for folder in folders(API_PAIRS):
            seen.update(kind for kind, _ in api_diff.diff(read_json(folder / "old.json"),
                                                          read_json(folder / "new.json")))
        self.assertEqual(seen, {"removal", "rename", "narrowed", "addition"})

    def test_each_pair_gives_its_expected_verdict(self):
        pairs = folders(API_PAIRS)
        self.assertGreaterEqual(len(pairs), 10)
        for folder in pairs:
            with self.subTest(folder.name):
                expect = read_json(folder / "expect.json")
                found = api_diff.diff(read_json(folder / "old.json"), read_json(folder / "new.json"))
                kinds = {kind for kind, _ in found}
                self.assertEqual(kinds & set(api_diff.FAILS), set(expect["reasons"]), found)
                if folder.name.startswith("addition"):
                    self.assertEqual(kinds, {"addition"}, found)
                done = python(TOOLS / "api_diff.py", folder / "old.json", folder / "new.json")
                self.assertEqual(done.returncode, 0 if expect["verdict"] == "pass" else 1, done.stdout)
                self.assertEqual(expect["verdict"] == "pass", not (kinds & set(api_diff.FAILS)))

    def test_v1_against_itself_is_unchanged(self):
        done = python(TOOLS / "api_diff.py", SNAPSHOT, SNAPSHOT)
        self.assertEqual(done.returncode, 0, done.stdout)
        self.assertEqual(done.stdout, "no breaking change\n")


if __name__ == "__main__":
    unittest.main()
