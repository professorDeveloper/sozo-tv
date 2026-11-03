#!/usr/bin/env python3
"""Prints the Sozo API base URL the release build ships with, without a trailing slash.

    python3 tools/release/api_base.py

The address is stored encoded in app/build.gradle.kts so it is not plain text
in a public repository; this decodes it the same way the Gradle build does.
SOZO_API_BASE_URL in the environment wins when set.
"""
import base64
import os
import re
import sys

PATH = 'app/build.gradle.kts'


def main() -> None:
    override = os.environ.get('SOZO_API_BASE_URL', '').strip()
    if override:
        print(override.rstrip('/'))
        return

    src = open(PATH, encoding='utf-8').read()
    key = re.search(r'val key = "([^"]+)"\.reversed\(\)', src)
    payload = re.search(r'readLocalProperty\("SOZO_API_BASE_URL",\s*decodeAddress\("([^"]+)"\)', src)
    if not key or not payload:
        sys.exit(f'could not find the encoded SOZO_API_BASE_URL in {PATH}')

    k = key.group(1)[::-1].encode()
    raw = base64.b64decode(payload.group(1))
    url = bytes(b ^ k[i % len(k)] for i, b in enumerate(raw)).decode()
    if not url.startswith('https://'):
        sys.exit('decoded SOZO_API_BASE_URL is not an https URL')
    print(url.rstrip('/'))


if __name__ == '__main__':
    main()
