import datetime as dt
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from release_banner import banner_text, render, still
from release_page import changes, compose, generate, telegram_caption

SHA = "abc123"
REPO = "o/r"
RAW = f"https://raw.githubusercontent.com/{REPO}/{SHA}/"
LOG = [
    "Player: keep focus on the seek bar (#40)",
    "chore: 1.0.4 (4)",
    "Release: a banner and a Telegram post (#41)",
    "Search: clearing the error is not an error",
]


class PageTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        (self.root / "release/notes").mkdir(parents=True)
        (self.root / "release/banners").mkdir(parents=True)
        self.assets = self.root / "dist"
        self.assets.mkdir()
        for name in ("sozo-tv-1.0.4-4.apk", "SHA256SUMS", "sozo-tv-1.0.4-release.json"):
            (self.assets / name).write_bytes(b"x")
        self.warnings = []

    def test_written_notes_get_the_banner_and_the_apk(self):
        (self.root / "release/banners/v1.0.4.svg").write_text("<svg/>")
        (self.root / "release/notes/v1.0.4.md").write_text("<!-- banner\nline1: Hi\n-->\nBody\n\n<!-- downloads -->\n")
        out = compose("1.0.4", SHA, REPO, "v1.0.4", self.assets, self.root, self.warnings.append)
        self.assertTrue(out.startswith(f'<p align="center">\n  <img src="{RAW}release/banners/v1.0.4.svg"'))
        self.assertIn("releases/download/v1.0.4/sozo-tv-1.0.4-4.apk", out)
        self.assertIn("SHA256SUMS", out)
        self.assertNotIn("release.json", out)
        self.assertNotIn("line1", out)

    def test_no_notes_file(self):
        self.assertIsNone(compose("1.0.4", SHA, REPO, "v1.0.4", self.assets, self.root, self.warnings.append))

    def test_generated_page(self):
        out = generate("1.0.4", SHA, REPO, "v1.0.4", "1.0.2", self.assets, self.root, LOG, "Hotfix.")
        for part in ("Sozo TV v1.0.4 is a patch release", "Hotfix.", "### ▶️ Player",
                     "[#40](https://github.com/o/r/pull/40)", "sozo-tv-1.0.4-4.apk",
                     "How to install it on a TV", "compare/1.0.2...v1.0.4", "sozo-tv?style"):
            self.assertIn(part, out)
        self.assertNotIn("macOS", out)
        self.assertNotIn("a banner and a Telegram post", out)

    def test_caption(self):
        out = telegram_caption("1.0.4", "", LOG, "Bugun")
        self.assertTrue(out.startswith("📺 <b>Sozo TV v1.0.4</b> · yamoq (patch)"))
        self.assertIn("▶️ <b>Player</b>: Keep focus on the seek bar", out)
        self.assertIn("📝 Bugun", out)
        self.assertLessEqual(len(out), 1024)
        self.assertEqual(changes(["chore: x"]), [])


TEMPLATE = Path(__file__).resolve().parent / "banner-template.svg"


class BannerTest(unittest.TestCase):
    def test_tv_words(self):
        t = banner_text("1.0.4", "", [], dt.date(2026, 11, 14))
        self.assertEqual(t["eyebrow"], "ANDROID TV · NOVEMBER 2026")
        self.assertEqual(t["version"], "v1.0.4")
        self.assertEqual(t["line2"], "on the big screen.")
        self.assertEqual(t["chips"], ["Android TV", "Google TV · Shield"])
        self.assertEqual(banner_text("2.0.0", "", ["Player"])["line1"], "Everything new in Sozo TV 2.0.0,")

    def test_render(self):
        template = TEMPLATE.read_text(encoding="utf-8")
        svg = render(template, banner_text("1.0.4", "", ["Player"]))
        cut = '<g transform="translate(640,0)">'
        self.assertEqual(svg[:svg.index(cut)], re.sub(r"<title>.*?</title>", "<title>Sozo TV v1.0.4</title>", template[:template.index(cut)]))
        self.assertIn(">v1.0.4</text>", svg)
        self.assertIn("ANDROID TV", svg)
        self.assertIn("@media all{", still(svg))


if __name__ == "__main__":
    unittest.main()
