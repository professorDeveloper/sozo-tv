#!/usr/bin/env python3
"""Writes versionName and versionCode into app/build.gradle.kts.

    python3 tools/release/stamp_version.py 1.0.4 4
"""
import re
import sys

PATH = 'app/build.gradle.kts'


def main() -> None:
    name, code = sys.argv[1], sys.argv[2]
    src = open(PATH, encoding='utf-8').read()
    src, n1 = re.subn(r'(?m)^(\s*versionCode\s*=\s*)\d+', rf'\g<1>{code}', src, count=1)
    src, n2 = re.subn(r'(?m)^(\s*versionName\s*=\s*)"[^"]*"', rf'\g<1>"{name}"', src, count=1)
    if n1 != 1 or n2 != 1:
        sys.exit(f'expected one versionCode and one versionName, replaced {n1}/{n2}')
    open(PATH, 'w', encoding='utf-8').write(src)


if __name__ == '__main__':
    main()
