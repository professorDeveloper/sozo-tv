#!/usr/bin/env python3
"""Draws the Sozo TV release banner for one version.

The artwork and its animation are the phone app's v3.2.0 banner
(banner-template.svg, copied from the sozo repository's tool/release_banner.py
setup). Only the right-hand column changes: the eyebrow, the version badge,
two tagline lines and up to four chips.

The words come from a comment in release/notes/v<version>.md:

    <!-- banner
    eyebrow: ANDROID TV
    line1: Playback that recovers,
    line2: downloads that finish.
    chips: Server switching | Corner player | 16 languages
    -->

Every key is optional. Without the comment, the eyebrow says ANDROID TV (or
SMART TV when Apple TV or webOS ship too) and the month, the tagline is a
generic one, and the chips name the TVs released, then the areas of the merged
changes (see banner_text).

--still writes a copy without animation, the frame a viewer with reduced motion
sees. It is the one rendered to PNG for Telegram, which cannot show SVG.
"""
import argparse
import datetime as dt
import html
import re
import sys
from pathlib import Path

TEMPLATE = Path("tools/release/banner-template.svg")
RIGHT = '<g transform="translate(640,0)">'
COLUMN = 600   # room to the right of x=640 before the 40px margin
MAX_CHIPS = 4

# Advance widths of Inter, in em. Close enough to size pills and badges; text
# that might still overflow is squeezed with textLength.
WIDE = {"m": .87, "w": .79, "M": .85, "W": .95, "@": .95, "%": .8, "&": .66}
NARROW = {c: .26 for c in "ijlI.,:;'|!·()[] "}
NARROW.update({"f": .36, "t": .36, "r": .37, "-": .4, "/": .4})


def text_width(text, size, spacing=0.0):
    em = 0.0
    for ch in text:
        if ch in WIDE:
            em += WIDE[ch]
        elif ch in NARROW:
            em += NARROW[ch]
        elif ch.isdigit():
            em += .62
        elif ch.isupper():
            em += .68
        else:
            em += .56
    return em * size + spacing * len(text)


def parse_block(notes_text):
    """The `<!-- banner ... -->` comment as a dict; empty when there is none."""
    match = re.search(r"<!--\s*banner\b(.*?)-->", notes_text or "", re.S)
    if not match:
        return {}
    out = {}
    for line in match.group(1).splitlines():
        key, sep, value = line.partition(":")
        if sep and key.strip():
            out[key.strip().lower()] = value.strip()
    return out


def kind_of(version):
    parts = (version.split(".") + ["0", "0"])[:3]
    return "PATCH RELEASE" if parts[2] not in ("", "0") else "NEW RELEASE"


PLATFORMS = {"androidtv": "Android TV", "appletv": "Apple TV", "webos": "LG webOS"}


def banner_text(version, notes_text="", areas=(), today=None, platforms=("androidtv",)):
    today = today or dt.date.today()
    block = parse_block(notes_text)
    patch = kind_of(version) == "PATCH RELEASE"
    several = len(platforms) > 1
    eyebrow = block.get("eyebrow") or ("SMART TV" if several else "ANDROID TV")
    if "·" not in eyebrow:
        eyebrow = f"{eyebrow} · {today.strftime('%B %Y')}"
    line1 = block.get("line1") or ("Fixes for what you reported," if patch
                                  else f"Everything new in Sozo TV {version},")
    line2 = block.get("line2") or "on the big screen."
    if block.get("chips"):
        chips = [c.strip() for c in block["chips"].split("|") if c.strip()]
    else:
        tvs = [PLATFORMS[p] for p in platforms if p in PLATFORMS] if several else []
        chips = list(dict.fromkeys(tvs + [a for a in areas if a]))
    if not chips:
        chips = ["Android TV", "Google TV · Shield"]
    return {"eyebrow": eyebrow.upper(), "version": f"v{version}", "line1": line1,
            "line2": line2, "chips": chips[:MAX_CHIPS]}


def fitted(x, y, cls, text, size, extra=""):
    attrs = f'x="{x}" y="{y}" class="{cls}"{extra}'
    if text_width(text, size) > COLUMN:
        attrs += f' textLength="{COLUMN}" lengthAdjust="spacingAndGlyphs"'
    return f"<text {attrs}>{html.escape(text)}</text>"


def right_column(t):
    badge_w = max(150, round(text_width(t["version"], 30) + 62))
    chips, x = [], 0
    for chip in t["chips"]:
        w = max(64, round(text_width(chip, 14.5) + 30))
        if x + w > COLUMN:
            break
        chips.append(
            f'<g transform="translate({x},0)"><rect width="{w}" height="34" rx="17" '
            f'fill="#ffffff" fill-opacity=".06" stroke="#ffffff" stroke-opacity=".14"/>'
            f'<text x="{w / 2:g}" y="22.5" text-anchor="middle" class="chip">'
            f"{html.escape(chip)}</text></g>")
        x += w + 10
    eyebrow = html.escape(t["eyebrow"])
    return "\n".join([
        RIGHT,
        f'  <g class="anim" style="animation-name:r0"><text x="0" y="190" class="eyebrow">{eyebrow}</text></g>',
        '  <g class="anim" style="animation-name:r1"><text x="-6" y="318" class="name">Sozo</text></g>',
        f'  <g class="anim" style="animation-name:r2"><g transform="translate(348,262)">'
        f'<rect width="{badge_w}" height="50" rx="25" fill="url(#ver)"/>'
        f'<text x="{badge_w / 2:g}" y="35" text-anchor="middle" class="ver">'
        f'{html.escape(t["version"])}</text></g></g>',
        f'  <g class="anim" style="animation-name:r3">{fitted(0, 378, "tag", t["line1"], 25)}',
        f'     {fitted(0, 412, "tag2", t["line2"], 25)}</g>',
        f'  <g class="anim" style="animation-name:r4"><g transform="translate(0,452)">{"".join(chips)}</g></g>',
        "</g>",
        "</svg>",
        "",
    ])


def render(template_svg, t, still_frame=False):
    cut = template_svg.find(RIGHT)
    if cut < 0:
        raise ValueError(f"the template has no {RIGHT}")
    svg = template_svg[:cut] + right_column(t)
    svg = re.sub(r"<title>.*?</title>", f"<title>Sozo TV {html.escape(t['version'])}</title>", svg, count=1)
    return still(svg) if still_frame else svg


def still(svg):
    """The template's reduced-motion rule, applied to everyone: no pen strokes
    or ring, the painted mark and every line of text in place."""
    return svg.replace("@media (prefers-reduced-motion: reduce){", "@media all{", 1)


def platform_list(text):
    return [p for p in (x.strip() for x in text.split(",")) if p in PLATFORMS] or ["androidtv"]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--version", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--still", action="store_true")
    p.add_argument("--root", default=".")
    p.add_argument("--log", default="", help="`git log --format=%%s` since the last tag; its areas are the chips when the notes name none")
    p.add_argument("--keep", action="store_true", help="leave an existing banner alone")
    p.add_argument("--platforms", default="androidtv", help="comma-separated: androidtv,appletv,webos")
    a = p.parse_args()
    root = Path(a.root)
    out = Path(a.out)
    if a.keep and out.is_file():
        print(f"{out} already exists; left as it is")
        return 0
    notes = root / "release" / "notes" / f"v{a.version}.md"
    notes_text = notes.read_text(encoding="utf-8") if notes.is_file() else ""
    committed = root / "release" / "banners" / f"v{a.version}.svg"
    if a.still and committed.is_file():
        # A banner already in the repository, drawn or hand-edited, is the one
        # people saw on the release page; the PNG shows the same.
        svg = still(committed.read_text(encoding="utf-8"))
        t = {"from": str(committed)}
    else:
        from release_page import areas, changes
        log = Path(a.log).read_text(encoding="utf-8").splitlines() if a.log else []
        t = banner_text(a.version, notes_text, areas(changes(log)), platforms=platform_list(a.platforms))
        svg = render((root / TEMPLATE).read_text(encoding="utf-8"), t, a.still)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(svg, encoding="utf-8")
    print(f"{out}: {t}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
