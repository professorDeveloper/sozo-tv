#!/usr/bin/env python3
"""Minimal App Store Connect API client for the tvOS release (stdlib + openssl only).

  asc.py next-build                      latest uploaded tvOS build number + 1
  asc.py wait VERSION BUILD [MINUTES]    block until the build is VALID; prints its id
  asc.py add-to-group BUILD_ID GROUP     add a processed build to an internal TestFlight group

Needs ASC_API_KEY_ID, ASC_API_ISSUER_ID and ASC_API_KEY_PATH (the decoded .p8).
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = 'https://api.appstoreconnect.apple.com/v1'
BUNDLE_ID = 'com.saikou.sozotv'


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=')


def der_to_raw(der):
    # openssl emits an ASN.1 SEQUENCE of two INTEGERs; JWS ES256 wants r||s, 32 bytes each.
    assert der[0] == 0x30, 'Unexpected signature encoding'
    i = 3 if der[1] & 0x80 else 2
    parts = []
    for _ in range(2):
        assert der[i] == 0x02, 'Unexpected signature encoding'
        n = der[i + 1]
        parts.append(der[i + 2:i + 2 + n].lstrip(b'\0').rjust(32, b'\0'))
        i += 2 + n
    return b''.join(parts)


def token():
    header = {'alg': 'ES256', 'kid': os.environ['ASC_API_KEY_ID'], 'typ': 'JWT'}
    now = int(time.time())
    claims = {'iss': os.environ['ASC_API_ISSUER_ID'], 'iat': now, 'exp': now + 1200, 'aud': 'appstoreconnect-v1'}
    signing_input = b64url(json.dumps(header).encode()) + b'.' + b64url(json.dumps(claims).encode())
    der = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', os.environ['ASC_API_KEY_PATH']],
                         input=signing_input, capture_output=True, check=True).stdout
    return (signing_input + b'.' + b64url(der_to_raw(der))).decode()


def call(path, params=None, body=None):
    url = f'{API}{path}'
    if params:
        url += '?' + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method='POST' if data else 'GET',
                                     headers={'Authorization': f'Bearer {token()}', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors='replace')[:600]
        sys.exit(f'App Store Connect {error.code} on {path}: {detail}')
    return json.loads(raw) if raw else {}


def app_id():
    apps = call('/apps', {'filter[bundleId]': BUNDLE_ID, 'fields[apps]': 'bundleId'})['data']
    apps = [a for a in apps if a['attributes']['bundleId'] == BUNDLE_ID]
    if not apps:
        sys.exit(f'No App Store Connect app record for {BUNDLE_ID}; create it first.')
    return apps[0]['id']


def next_build():
    builds = call('/builds', {'filter[app]': app_id(), 'filter[preReleaseVersion.platform]': 'TV_OS',
                              'sort': '-uploadedDate', 'limit': 200, 'fields[builds]': 'version'})['data']
    numbers = [int(b['attributes']['version']) for b in builds if b['attributes']['version'].isdigit()]
    print(max(numbers, default=0) + 1)


def wait(version, build, minutes=45):
    deadline = time.time() + minutes * 60
    app = app_id()
    while True:
        found = call('/builds', {'filter[app]': app, 'filter[version]': build,
                                 'filter[preReleaseVersion.version]': version,
                                 'filter[preReleaseVersion.platform]': 'TV_OS',
                                 'fields[builds]': 'processingState'})['data']
        state = found[0]['attributes']['processingState'] if found else 'NOT_LISTED_YET'
        print(f'{version} ({build}): {state}', file=sys.stderr)
        if state == 'VALID':
            print(found[0]['id'])
            return
        if state in ('FAILED', 'INVALID'):
            sys.exit(f'Build {version} ({build}) finished processing as {state}')
        if time.time() > deadline:
            sys.exit(f'Build {version} ({build}) still {state} after {minutes} minutes')
        time.sleep(30)


def add_to_group(build_id, name):
    groups = call('/betaGroups', {'filter[app]': app_id(), 'filter[name]': name,
                                  'fields[betaGroups]': 'name,isInternalGroup'})['data']
    groups = [g for g in groups if g['attributes']['name'] == name]
    if not groups:
        sys.exit(f'No TestFlight group named "{name}"')
    if not groups[0]['attributes'].get('isInternalGroup'):
        sys.exit(f'"{name}" is an external group; external testing needs Beta App Review and stays manual')
    call(f'/betaGroups/{groups[0]["id"]}/relationships/builds', body={'data': [{'type': 'builds', 'id': build_id}]})
    print(f'Added build {build_id} to "{name}"', file=sys.stderr)


if __name__ == '__main__':
    command, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ('', [])
    if command == 'next-build' and not args:
        next_build()
    elif command == 'wait' and len(args) in (2, 3):
        wait(args[0], args[1], *(int(a) for a in args[2:]))
    elif command == 'add-to-group' and len(args) == 2:
        add_to_group(*args)
    else:
        sys.exit(__doc__)
