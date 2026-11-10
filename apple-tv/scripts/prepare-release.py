#!/usr/bin/env python3
"""Reject wrong/expired/development profiles before importing signing material."""
import datetime
import os
import plistlib
import re
import sys

BUNDLE_ID = 'com.saikou.sozotv'

def validate(profile, team, now=None):
    now = now or datetime.datetime.now(datetime.timezone.utc)
    assert re.fullmatch(r'[A-Z0-9]{10}', team), 'Invalid team ID'
    assert profile.get('TeamIdentifier') == [team], 'Profile belongs to another team'
    assert 'tvOS' in profile.get('Platform', []), 'Profile is not for tvOS'
    expires = profile['ExpirationDate'].replace(tzinfo=datetime.timezone.utc)
    assert expires > now, 'Provisioning profile has expired'
    entitlements = profile['Entitlements']
    prefix = profile['ApplicationIdentifierPrefix'][0]
    assert entitlements.get('application-identifier') == f'{prefix}.{BUNDLE_ID}', 'Profile bundle ID does not match'
    assert entitlements.get('com.apple.developer.team-identifier') == team, 'Entitlement team mismatch'
    assert entitlements.get('get-task-allow') is False, 'Development profile cannot distribute'
    assert not profile.get('ProvisionedDevices') and not profile.get('ProvisionsAllDevices'), 'Use an App Store distribution profile'
    assert re.fullmatch(r'[A-Fa-f0-9-]{36}', profile['UUID']), 'Invalid profile UUID'
    return profile['UUID']

if __name__ == '__main__':
    with open(sys.argv[1], 'rb') as f:
        profile = plistlib.load(f)
    team = os.environ['APPLE_TV_TEAM_ID']
    uuid = validate(profile, team)
    options = {'method': 'app-store-connect', 'destination': 'upload' if os.environ.get('SOZO_TV_UPLOAD_TESTFLIGHT') == 'true' else 'export',
               'signingStyle': 'manual', 'teamID': team, 'signingCertificate': 'Apple Distribution',
               'provisioningProfiles': {BUNDLE_ID: uuid}, 'manageAppVersionAndBuildNumber': False,
               'uploadSymbols': True}
    with open(sys.argv[2], 'wb') as f:
        plistlib.dump(options, f)
    print(uuid)
