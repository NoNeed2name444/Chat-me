"""A5: nothing under addons/ is a picture, an icon, a font or a binary,
judged by its name and by its first bytes, with the same tests the
directory's review uses, except for one Latin-1 fixture that is text but
not UTF-8. Python's own __pycache__ folders are left out:
they are never committed, and CI runs with -B so it makes none."""
import unittest

from common import ADDONS, walk_files

import review

# file.text needs a folder fixture that isn't UTF-8: Latin-1 text, which has
# no NUL byte, so Git and the mirror treat it as text
LATIN_1 = {"fixtures/packages/file-text-latin1/names.json"}


class Scan(unittest.TestCase):
    def test_no_picture_or_font_under_addons(self):
        found = []
        for path in walk_files(ADDONS, skip=(".git", "__pycache__")):
            kind = review.picture(path.name, path.read_bytes())
            if kind:
                found.append(f"{path.relative_to(ADDONS)}: {kind}")
        self.assertEqual(found, [])

    def test_no_binary_under_addons(self):
        found = []
        for path in walk_files(ADDONS, skip=(".git", "__pycache__")):
            data = path.read_bytes()
            kind = review.binary(path.name, data)
            if kind and not (path.relative_to(ADDONS).as_posix() in LATIN_1 and b"\0" not in data):
                found.append(f"{path.relative_to(ADDONS)}: {kind}")
        self.assertEqual(found, [])

    def test_the_one_text_file_that_isnt_utf_8_is_latin_1_text(self):
        for name in LATIN_1:
            data = (ADDONS / name).read_bytes()
            with self.subTest(name):
                self.assertNotIn(b"\0", data)
                text = data.decode("latin-1")
                self.assertFalse([c for c in text if (ord(c) < 32 and c not in "\n\t") or 0x7F <= ord(c) < 0xA0])

    def test_the_scan_knows_each_kind(self):
        samples = {
            "PNG": b"\x89PNG\r\n\x1a\n" + b"\0" * 8, "JPEG": b"\xff\xd8\xff\xe0", "GIF": b"GIF89a",
            "WebP": b"RIFF\0\0\0\0WEBPVP8 ", "HEIC": b"\0\0\0\x18ftypheic", "AVIF": b"\0\0\0\x18ftypavif",
            "SVG": b"<?xml version='1.0'?>\n<svg xmlns='http://www.w3.org/2000/svg'/>", "PDF": b"%PDF-1.7",
            "ICO": b"\0\0\1\0\1\0", "ICNS": b"icns\0\0\0\x08", "TrueType": b"\0\1\0\0\0\x10",
            "OpenType": b"OTTO\0\x10", "WOFF": b"wOFF\0\1", "WOFF2": b"wOF2\0\1", "TIFF": b"II*\0",
        }
        for kind, data in samples.items():
            with self.subTest(kind):
                self.assertIsNotNone(review.picture("data.json", data))
        for name in ("logo.png", "icon.svg", "font.woff2", "art.heic", "page.pdf", "face.ttf"):
            with self.subTest(name):
                self.assertIsNotNone(review.picture(name, b"{}"))
        self.assertIsNone(review.picture("main.js", b"// <svg> in a comment is only text\n"))
        self.assertIsNotNone(review.binary("main.js", b"a\0b"))
        self.assertIsNotNone(review.binary("main.js", b"\xff\xfe"))


if __name__ == "__main__":
    unittest.main()
