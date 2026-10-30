#!/usr/bin/env python3
"""Validate a built tvOS bundle before CI artifacts or distribution."""
import pathlib
import plistlib
import subprocess
import sys

app = pathlib.Path(sys.argv[1])
with (app / 'Info.plist').open('rb') as f:
    info = plistlib.load(f)
assert info['CFBundleIdentifier'] == 'com.saikou.sozotv', 'Unexpected bundle ID'
assert info['UIDeviceFamily'] == [3], 'Not an Apple TV application'
assert info['CFBundleShortVersionString'] and info['CFBundleVersion'], 'Missing version'
with (app / 'PrivacyInfo.xcprivacy').open('rb') as f:
    privacy = plistlib.load(f)
reasons = {entry['NSPrivacyAccessedAPIType']: entry['NSPrivacyAccessedAPITypeReasons']
           for entry in privacy['NSPrivacyAccessedAPITypes']}
assert 'CA92.1' in reasons['NSPrivacyAccessedAPICategoryUserDefaults']
assert '35F9.1' in reasons['NSPrivacyAccessedAPICategorySystemBootTime']
binary = (app / info['CFBundleExecutable']).read_bytes()
for marker in [b'--profile-ui-fixture', b'--browse-ui-fixture', b'--player-smoke',
               b'--native-detail-playback', b'--friends-query=', b'player.position']:
    assert marker not in binary, f'Debug fixture leaked into Release: {marker!r}'
subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
print(f'Release validated: {info["CFBundleShortVersionString"]} ({info["CFBundleVersion"]})')
