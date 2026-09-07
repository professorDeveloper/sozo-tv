#!/usr/bin/env python3
"""Checks the translated string resources against the English originals.

    python3 tools/check_strings.py

## Why this exists

Android resolves a missing string by falling back to `values/`, so a gap is a
word in the wrong language rather than a crash — invisible to the build, and to
anyone not reading that language.

A mismatched format specifier is not invisible. `getString(R.string.x, name)`
against a translation that dropped `%1$s`, or turned it into `%1$d`, throws at
the moment that screen renders, and only on a device set to that language.
That is what this fails the build on.

## The two tiers

`en`, `uz` and `ru` are written by the team, so a key missing from them is a
build failure — someone here can fix it before merging. The other seven are
machine-translated and topped up in batches; blocking a PR on them would not
produce seven translations, it would produce seven copies of the English string
pasted in to get the build green. Those are reported instead.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / 'app/src/main/res'
BLOCKING = {'uz', 'ru'}

# Deliberately the same in every language, so a locale file that omits them is
# correct rather than incomplete. Reporting these would train people to ignore
# the output, which is the one thing a check like this cannot survive.
NEVER_TRANSLATED = {
    # nav-graph destination labels — never drawn on a leanback screen
    'homescreen', 'contactscreen', 'search_screen', 'categories_screen',
    'livetvplayerscreen', 'tv_garden_screen', 'view_all_screen', 'sourcescreen',
    'aniyomi_settings', 'splash_screen', 'detailpage', 'castdetailscreen',
    'trailerplayerscreen', 'episodescreen', 'seriesplayerscreen',
    'my_account_page', 'bookmark_screen', 'historypage', 'anilistscreen',
    'news_page', 'qualit_dialog',
    # brand and proper nouns
    'app_name', 'sozo', 'aniyomi', 'cloudstream', 'telegram', 'anilist', 'mal',
    'poppins', 'eshonov_fakhriyor', 'amp_sozoapp',
    # punctuation, XML entities, layout-preview samples
    'quot', 'amp', 'space', 'g', 'meta_separator', 'stepper_value_sample',
    'news_time_sample', 'episode_2_will_be_released_in',
    'e_g_natsume_yuujinchou', 'plugin_failed_entry', 'plugin_error_other',
    'country_language', 'search_results_for',
}
FORMAT = re.compile(r'%(?:\d+\$)?[.\d]*[a-zA-Z]')


def load(directory):
    """name -> the text that carries the format specifiers."""
    out = {}
    for path in sorted(directory.glob('strings*.xml')):
        root = ET.parse(path).getroot()
        for node in root.findall('string'):
            out[node.get('name')] = ''.join(node.itertext())
        for node in root.findall('plurals'):
            item = node.find("item[@quantity='other']") or node.find('item')
            if item is not None:
                out[node.get('name')] = ''.join(item.itertext())
    return out


def main():
    english = {k: v for k, v in load(RES / 'values').items()
               if k not in NEVER_TRANSLATED}
    if not english:
        print(f'No string resources under {RES}/values', file=sys.stderr)
        return 2

    failures = 0
    advisory = 0

    for directory in sorted(RES.glob('values-*')):
        locale = directory.name[len('values-'):]
        if not (directory / 'strings.xml').exists():
            continue
        translated = load(directory)

        # 1. A format specifier that changed shape is a crash on that locale.
        for name, text in translated.items():
            if name not in english:
                print(f'✗ {locale}: "{name}" is not in values/')
                failures += 1
                continue
            want = sorted(FORMAT.findall(english[name]))
            got = sorted(FORMAT.findall(text))
            if want != got:
                print(f'✗ {locale}/{name}: format {got} does not match English {want}')
                failures += 1

        # 2. A missing key falls back to English — a gap, not a crash.
        missing = sorted(set(english) - set(translated))
        if missing:
            blocks = locale in BLOCKING
            mark = '✗' if blocks else '·'
            print(f'{mark} {locale}: {len(missing)} string(s) still in English'
                  f'{"" if blocks else " (advisory)"}')
            for name in missing[:15]:
                print(f'      {name}')
            if len(missing) > 15:
                print(f'      … and {len(missing) - 15} more')
            if blocks:
                failures += len(missing)
            else:
                advisory += len(missing)

    locales = len(list(RES.glob('values-*')))
    print(f'\n{locales} translated locale(s), {len(english)} string(s) in values/.')
    if advisory:
        print(f'{advisory} advisory gap(s) — those render in English until the '
              f'next translation pass.')
    if failures:
        print(f'✗ {failures} problem(s).')
        return 1
    print('✓ Every translation matches the English format specifiers.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
