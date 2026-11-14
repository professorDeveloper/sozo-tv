#!/usr/bin/env python3
"""Writes one release version into all three TV apps.

    python3 tools/release/stamp_version.py 1.0.4 4

Android TV: versionName/versionCode in app/build.gradle.kts.
Apple TV:   MARKETING_VERSION/CURRENT_PROJECT_VERSION in apple-tv/project.yml.
LG webOS:   "version" in webos/appinfo.json and VERSION in webos/js/app.js.
            webOS only takes x.y.z; any other name leaves it as it is.
"""
import re
import sys
from pathlib import Path


def sub_once(path, pattern, repl):
    p = Path(path)
    src = p.read_text(encoding='utf-8')
    out, n = re.subn(pattern, repl, src, count=1, flags=re.M)
    if n != 1:
        sys.exit(f'{path}: expected one match for {pattern}')
    p.write_text(out, encoding='utf-8')


def main() -> None:
    name, code = sys.argv[1], sys.argv[2]
    sub_once('app/build.gradle.kts', r'^(\s*versionCode\s*=\s*)\d+', rf'\g<1>{code}')
    sub_once('app/build.gradle.kts', r'^(\s*versionName\s*=\s*)"[^"]*"', rf'\g<1>"{name}"')

    if Path('apple-tv/project.yml').is_file():
        sub_once('apple-tv/project.yml', r'^(\s*MARKETING_VERSION:\s*)"[^"]*"', rf'\g<1>"{name}"')
        sub_once('apple-tv/project.yml', r'^(\s*CURRENT_PROJECT_VERSION:\s*)"[^"]*"', rf'\g<1>"{code}"')

    if Path('webos/appinfo.json').is_file():
        if re.fullmatch(r'\d+\.\d+\.\d+', name):
            sub_once('webos/appinfo.json', r'^(\s*"version"\s*:\s*)"[^"]*"', rf'\g<1>"{name}"')
            sub_once('webos/js/app.js', r"^(\s*var VERSION = )'[^']*'", rf"\g<1>'{name}'")
        else:
            print(f'::warning::webOS takes only x.y.z; {name} not written to webos/')


if __name__ == '__main__':
    main()
