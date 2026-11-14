#!/usr/bin/env python3
"""Builds the Android TV release body from release/notes/v<version>.md.

A copy of the phone app's tool/compose_release_notes.py (sozo repository),
adapted to one APK and the TV's paths.

Images may be written relative to the notes file. They become links to the
exact commit being released, so they work in a draft before anything reaches
the default branch. An image that is not in the repository is removed and
reported, so a release never shows a broken picture.

<!-- downloads --> becomes a table of the files actually attached.

The version's banner (tool/release_banner.py) goes on top when the notes do not
already show one.

With --auto, a version that has no notes file still gets a full page in the
same layout, built from the titles of what was merged since the last tag.

--telegram writes the caption of the Telegram announcement (HTML, at most 1024
characters): the Uzbek summary of the notes when they have one, otherwise the
merged changes.

Exit codes: 0 written, 3 no notes file for this version and no --auto.
"""
import argparse
import html
import posixpath
import re
import sys
from pathlib import Path

from release_banner import banner_text, kind_of


IMAGE_BLOCK = re.compile(r'<p[^>]*>\s*(<img\b[^>]*>)\s*</p>\n?', re.I)
HTML_IMG = re.compile(r'<img\b[^>]*?\bsrc="([^"]+)"[^>]*>', re.I)
MD_IMG = re.compile(r'!\[[^\]]*\]\(([^)\s]+)\)')


def is_relative(src):
    return not re.match(r'^(?:[a-z][a-z0-9+.-]*:|//|#|/)', src, re.I)


def rewrite_images(text, notes_path, repo_root, repo, sha, warn):
    base = f"https://raw.githubusercontent.com/{repo}/{sha}/"
    notes_dir = notes_path.parent.relative_to(repo_root).as_posix()

    def target(src):
        path = posixpath.normpath(posixpath.join(notes_dir, src))
        if path.startswith("../") or not (repo_root / path).is_file():
            warn(f"image not found, removed: {src}")
            return None
        return base + path

    def block(match):
        src = HTML_IMG.search(match.group(1))
        if src and is_relative(src.group(1)) and target(src.group(1)) is None:
            return ""
        return match.group(0)

    def html(match):
        src = match.group(1)
        if not is_relative(src):
            return match.group(0)
        url = target(src)
        return match.group(0).replace(src, url, 1) if url else ""

    def markdown(match):
        src = match.group(1)
        if not is_relative(src):
            return match.group(0)
        url = target(src)
        return match.group(0).replace(src, url, 1) if url else ""

    text = IMAGE_BLOCK.sub(block, text)
    # A missing image in a block was already removed and warned about once.
    return MD_IMG.sub(markdown, HTML_IMG.sub(html, text))


def downloads_table(assets, repo, tag, version):
    names = sorted(p.name for p in assets.iterdir()) if assets.is_dir() else []
    rows = []
    for name in names:
        if name.startswith(f"sozo-tv-{version}-") and name.endswith(".apk"):
            url = f"https://github.com/{repo}/releases/download/{tag}/{name}"
            rows.append(f"| 📺 Android TV, Google TV, Shield, TV boxes | [`{name}`]({url}) |")
    if not rows:
        return "The files are attached to this release."
    lines = ["| Device | File |", "|---|---|", *rows]
    sums = "SHA256SUMS"
    if sums in names:
        url = f"https://github.com/{repo}/releases/download/{tag}/{sums}"
        lines += ["", f"🔐 To check a download, compare it with [`{sums}`]({url})."]
    return "\n".join(lines)


SITE = "https://github.com/professorDeveloper/sozo-tv"
BANNER_BLOCK = re.compile(r"<!--\s*banner\b.*?-->\n*", re.S)

# Merged-change titles read "Area: what changed (#12)". These areas are about
# the repository, not the app, and stay out of the notes.
INTERNAL = {"release", "ci", "chore", "docs", "doc", "issue forms", "build", "test",
            "tests", "deps", "workflow", "readme", "merge"}
AREA_ICONS = {"player": "▶️", "desktop player": "🖥️", "desktop": "🖥️", "sources": "🧩",
              "source": "🧩", "downloads": "⬇️", "download": "⬇️", "novels": "📚",
              "manga": "📖", "tv": "📺", "ios": "📱", "android": "🤖", "settings": "⚙️",
              "search": "🔎", "home": "🏠", "profiles": "👨‍👩‍👧", "subtitles": "💬",
              "languages": "🌍", "highlights": "⭐"}
TITLE = re.compile(r"^(?P<area>[^:]{1,30}):\s*(?P<text>.+?)(?:\s*\(#(?P<pr>\d+)\))?$")


def changes(log_lines):
    """[(area, text, pr)] from `git log --format=%s`, oldest first, app-facing only."""
    out = []
    for line in reversed([l.strip() for l in log_lines if l.strip()]):
        m = TITLE.match(line)
        if m:
            area, text, pr = m.group("area").strip(), m.group("text"), m.group("pr")
        else:
            pr_m = re.search(r"\s*\(#(\d+)\)$", line)
            area, text, pr = "", line[:pr_m.start()] if pr_m else line, pr_m and pr_m.group(1)
        if area.lower() in INTERNAL or re.match(r"(?i)^(merge|revert|bump)\b", line):
            continue
        if re.match(r"(?i)^sozo\s+v?\d", area):
            area = "Highlights"
        text = text[:1].upper() + text[1:]
        out.append((area or "Improvements", text, pr))
    return out


def ordered(items):
    """Grouped by area: Highlights first, then in order of appearance, Improvements last."""
    order = list(dict.fromkeys(a for a, _, _ in items))
    rank = lambda a: 0 if a == "Highlights" else 2 if a == "Improvements" else 1
    order.sort(key=rank)
    return [(area, [i for i in items if i[0] == area]) for area in order]


def areas(items):
    return [a for a, _ in ordered(items) if a not in ("Highlights", "Improvements")]


def icon(area):
    return AREA_ICONS.get(area.lower(), "✨")


def add_banner(text, version, repo, sha, repo_root):
    path = f"release/banners/v{version}.svg"
    text = BANNER_BLOCK.sub("", text)
    if "banner.svg" in text or not (repo_root / path).is_file():
        return text
    url = f"https://raw.githubusercontent.com/{repo}/{sha}/{path}"
    return (f'<p align="center">\n  <img src="{url}" alt="Sozo v{version}" width="100%" />\n'
            f"</p>\n\n{text}")


def notes_path(repo_root, version):
    return repo_root / "release" / "notes" / f"v{version}.md"


def compose(version, sha, repo, tag, assets, repo_root, warn):
    notes = notes_path(repo_root, version)
    if not notes.is_file():
        return None
    text = notes.read_text(encoding="utf-8")
    text = rewrite_images(text, notes, repo_root, repo, sha, warn)
    text = add_banner(text, version, repo, sha, repo_root)
    return text.replace("<!-- downloads -->", downloads_table(assets, repo, tag, version))


def generate(version, sha, repo, tag, previous, assets, repo_root, log_lines, intro=""):
    """A page in the layout of the phone app's v3.2.0 notes, for a version nobody wrote up."""
    items = changes(log_lines)
    t = banner_text(version, "", areas(items))
    patch = kind_of(version) == "PATCH RELEASE"
    lines = [
        '<p align="center">',
        f"  <b>Sozo TV v{version} is a {'patch' if patch else 'new'} release.</b><br/>",
        f"  {html.escape(t['line1'])} {html.escape(t['line2'])}",
        "</p>",
        "",
        '<p align="center">',
        f'  <a href="#-download"><img src="https://img.shields.io/badge/⬇_Download_v{version}-E8E8E8?style=for-the-badge&labelColor=111111" alt="Download" height="36"/></a>',
        '  <a href="https://telegram.me/sozoapp"><img src="https://img.shields.io/badge/Telegram-Sozo_channel-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white&labelColor=111111" alt="Telegram" height="36"/></a>',
        '  <a href="https://discord.gg/n22URhYvMR"><img src="https://img.shields.io/badge/Discord-Join_the_server-5865F2?style=for-the-badge&logo=discord&logoColor=white&labelColor=111111" alt="Discord" height="36"/></a>',
        f'  <a href="{SITE}"><img src="https://img.shields.io/github/stars/professorDeveloper/sozo-tv?style=for-the-badge&logo=github&label=Star&color=FFB27A&labelColor=111111" alt="Star Sozo TV" height="36"/></a>',
        "</p>",
        "",
    ]
    if intro.strip():
        lines += [intro.strip(), ""]
    lines += ["---", "", "## ✨ What's new", ""]
    if items:
        for area, group in ordered(items):
            lines += [f"### {icon(area)} {area}", ""]
            for _, text, pr in group:
                link = f" ([#{pr}](https://github.com/{repo}/pull/{pr}))" if pr else ""
                lines.append(f"- {text}{link}")
            lines.append("")
    else:
        lines += ["Fixes and smaller improvements.", ""]
    lines += [
        "---", "", "## ⬇ Download", "",
        downloads_table(assets, repo, tag, version), "",
        "<details>",
        "<summary>📺 How to install it on a TV</summary>",
        "",
        "- **Updating:** if Sozo TV is already installed, it offers this version itself.",
        "- **With Downloader:** install *Downloader* from the TV's app store, allow it to install",
        "  apps, and open the APK link above in it.",
        "- **With a USB stick:** copy the APK onto it and open it with the TV's file manager.",
        "",
        "If the TV says the app is not installed, an older Sozo TV signed with a different key",
        "is on it: uninstall that one first.",
        "</details>",
        "",
        "---", "", "## 💬 Join the community", "",
        f"- 📣 **Telegram**: [@sozoapp](https://telegram.me/sozoapp) for new versions and announcements first.",
        "- 🎧 **Discord**: [discord.gg/n22URhYvMR](https://discord.gg/n22URhYvMR) to suggest ideas and report bugs.",
        "- 🛟 **In the app**: Profile → **Help & Support**.",
        f"- 🐛 **On GitHub**: [a source isn't working]({SITE}/issues/new?template=source_not_working.yml) · [bug report]({SITE}/issues/new?template=bug_report.yml) · [feature request]({SITE}/issues/new?template=feature_request.yml)",
        f"- ⭐ **If Sozo is useful to you, [star the repo]({SITE})**.",
        "",
    ]
    if previous:
        lines.append(f'<sub>Full list of changes: <a href="https://github.com/{repo}/compare/{previous}...{tag}">'
                     f"{previous}...{tag}</a></sub>")
    body = "\n".join(lines) + "\n"
    return add_banner(body, version, repo, sha, repo_root)


# ---------------------------------------------------------------- Telegram ----

CAPTION_LIMIT = 1024
UZ_SUMMARY = re.compile(r"<summary>[^<]*🇺🇿.*?</summary>(.*?)</details>", re.S)


def md_to_tg(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"\[([^\]]+)\]\((https?://[^)\s]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])", r"<i>\1</i>", text)
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", text)


def shorten(md, limit=230):
    """Cuts a markdown bullet at a sentence end, never inside **bold**."""
    if len(md) <= limit:
        return md
    for m in reversed(list(re.finditer(r"[.!?](?=\s)", md[:limit]))):
        head = md[:m.end()]
        if head.count("**") % 2 == 0 and head.count("[") == head.count("]"):
            return head
    return md


def telegram_caption(version, notes_text, log_lines, intro=""):
    t = banner_text(version, notes_text, areas(changes(log_lines)))
    kind = "yamoq (patch)" if kind_of(version) == "PATCH RELEASE" else "yangi versiya"
    head = [f"📺 <b>Sozo TV v{html.escape(version)}</b> · {kind}",
            f"<i>{html.escape(t['line1'])} {html.escape(t['line2'])}</i>"]
    if intro.strip():
        head += ["", f"📝 {html.escape(intro.strip())}"]
    head += ["", "✨ <b>Nima yangi</b>"]
    tail = ["", "⬇️ APK shu xabar ostida · 📣 @sozoapp"]

    bullets = []
    uz = UZ_SUMMARY.search(notes_text or "")
    if uz:
        bullets = [shorten(l.strip()[2:]) for l in uz.group(1).splitlines()
                   if l.strip().startswith("- ")]
    if not bullets:
        bullets = [f"{icon(a)} **{a}**: {txt}" for a, group in ordered(changes(log_lines))
                   for _, txt, _ in group]
    if not bullets:
        bullets = ["Tuzatishlar va kichik yaxshilanishlar."]

    out = list(head)
    for b in bullets:
        line = "• " + md_to_tg(b) if not re.match(r"^\W", b) else md_to_tg(b)
        if len("\n".join(out + [line] + tail)) > CAPTION_LIMIT:
            break
        out.append(line)
    if len(out) == len(head):
        out.append(md_to_tg(bullets[0])[:CAPTION_LIMIT - len("\n".join(head + tail)) - 2] + "…")
    return "\n".join(out + tail)


def read_log(path):
    return Path(path).read_text(encoding="utf-8").splitlines() if path else []


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    for name in ("sha", "repo", "tag", "assets", "out", "telegram", "previous", "log"):
        parser.add_argument(f"--{name}", default="")
    parser.add_argument("--intro", default="", help="text from the release form, put on top")
    parser.add_argument("--auto", action="store_true", help="generate a page when there are no notes")
    parser.add_argument("--root", default=".")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    log_lines = read_log(args.log)

    if args.telegram:
        notes = notes_path(root, args.version)
        text = notes.read_text(encoding="utf-8") if notes.is_file() else ""
        Path(args.telegram).write_text(
            telegram_caption(args.version, text, log_lines, args.intro), encoding="utf-8")
    if not args.out:
        return 0

    warn = lambda message: print(f"::warning::{message}")
    body = compose(args.version, args.sha, args.repo, args.tag, Path(args.assets), root, warn)
    if body is None and args.auto:
        body = generate(args.version, args.sha, args.repo, args.tag, args.previous,
                        Path(args.assets), root, log_lines, args.intro)
    if body is None:
        print(f"No release/notes/v{args.version}.md")
        return 3
    Path(args.out).write_text(body, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
