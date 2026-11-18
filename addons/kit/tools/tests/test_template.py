"""A4: the Word count template, and the kit's docs."""
import re
import unittest

from common import KIT, TEMPLATE, TOOLS, Scratch, python, read_json

import check
import pack

SAMPLE = KIT / "api" / "sample-ideas.json"


def count_words(text: str) -> int:
    """Python's twin of the template's countWords."""
    return len(text.split())


def amount(n: int, one: str, many: str) -> str:
    return f"{n} {one if n == 1 else many}"


def words_in(idea: dict, titles: bool) -> int:
    return count_words(idea["text"]) + (count_words(idea["title"]) if titles else 0)


class Template(unittest.TestCase):
    def test_check_passes_it(self):
        verdict = check.check_path(TEMPLATE)
        self.assertTrue(verdict.ok, verdict.detail)
        self.assertTrue(check.check_path(TEMPLATE, check.App("1.0.0", 1)).ok)

    def test_pack_packs_it(self):
        with Scratch() as scratch:
            done = python(TOOLS / "pack.py", TEMPLATE, "--out", scratch / "t.sortaaddon")
            self.assertEqual(done.returncode, 0, done.stdout)
            self.assertTrue(check.check_path(scratch / "t.sortaaddon").ok)

    def test_it_has_what_section_8_names(self):
        manifest = read_json(TEMPLATE / "manifest.json")
        self.assertEqual(manifest["name"], "Word count")
        contributes = manifest["contributes"]
        self.assertEqual(len(contributes["commands"]), 1)
        self.assertIn("cardView", contributes)
        self.assertIn("settings", contributes)
        for name in ("README.md", "LICENSE", "main.js", "settings.json", "tests/expect.json"):
            with self.subTest(name):
                self.assertTrue((TEMPLATE / name).is_file())
        self.assertIn("Copyright (c) 2026 the Sorta authors", (TEMPLATE / "LICENSE").read_text())

    def test_expect_json_matches_a_python_twin(self):
        expect = read_json(TEMPLATE / "tests" / "expect.json")
        self.assertEqual(expect["ideas"], "api/sample-ideas.json")
        ideas = {idea["id"]: idea for idea in read_json(SAMPLE)["ideas"]}
        command = read_json(TEMPLATE / "manifest.json")["contributes"]["commands"][0]["id"]
        self.assertGreaterEqual(len(expect["commands"]), 4)
        for case in expect["commands"]:
            with self.subTest(case):
                self.assertEqual(case["command"], command)
                titles = case["settings"]["titles"]
                chosen = [ideas[i] for i in case["chosen"] if i in ideas] if case["chosen"] else list(ideas.values())
                words = sum(words_in(idea, titles) for idea in chosen)
                message = (f"{amount(words, 'word', 'words')} in {amount(len(chosen), 'idea', 'ideas')}"
                           if chosen else "No ideas to count yet")
                self.assertEqual(case["reply"], {"message": message})
        self.assertEqual({c["idea"] for c in expect["cards"]}, set(ideas))
        for case in expect["cards"]:
            with self.subTest(case):
                idea = ideas[case["idea"]]
                self.assertEqual(case["reply"], {"lines": [amount(words_in(idea, case["settings"]["titles"]),
                                                                  "word", "words")]})

    def test_main_js_matches_the_twin(self):
        main = (TEMPLATE / "main.js").read_text(encoding="utf-8")
        self.assertIn("text.trim().split(/\\s+/)", main)
        self.assertIn('"No ideas to count yet"', main)
        self.assertIn("limit: 200", main)


class Docs(unittest.TestCase):
    TOPICS = ("getting-started.md", "manifest.md", "permissions.md", "api.md", "limits.md", "review.md",
              "testing.md")

    def test_section_8s_topics_are_there(self):
        for name in self.TOPICS:
            with self.subTest(name):
                self.assertTrue((KIT / "docs" / name).is_file())

    def test_plain_markdown_with_no_pictures(self):
        for path in sorted((KIT / "docs").iterdir()):
            with self.subTest(path.name):
                self.assertEqual(path.suffix, ".md")
                text = path.read_text(encoding="utf-8")
                self.assertNotRegex(text, r"!\[")
                self.assertNotRegex(text, r"(?i)<img|<svg|<picture|data:image")

    def test_the_review_doc_lists_every_code(self):
        text = (KIT / "docs" / "review.md").read_text(encoding="utf-8")
        codes = [r["code"] for r in read_json(KIT.parent / "spec" / "reasons.json")["reasons"]]
        codes += [r["code"] for r in read_json(KIT.parent / "spec" / "review.json")["reasons"]]
        for code in codes:
            with self.subTest(code):
                self.assertRegex(text, rf"`{re.escape(code)}`")


if __name__ == "__main__":
    unittest.main()
