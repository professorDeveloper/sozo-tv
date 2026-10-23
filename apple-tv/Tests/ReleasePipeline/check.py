#!/usr/bin/env python3
"""Exercise the release signing gate with a valid profile and rejected variants."""
import copy
import datetime
import importlib.util
import pathlib

script = pathlib.Path(__file__).resolve().parents[2] / 'scripts/prepare-release.py'
spec = importlib.util.spec_from_file_location('release_profile', script)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
now = datetime.datetime(2026, 10, 8, tzinfo=datetime.timezone.utc)
team = 'ABCDEFGHIJ'
profile = {'UUID': '12345678-1234-1234-1234-123456789ABC', 'TeamIdentifier': [team],
           'ApplicationIdentifierPrefix': [team], 'Platform': ['tvOS'],
           'ExpirationDate': now + datetime.timedelta(days=1),
           'Entitlements': {'application-identifier': f'{team}.com.saikou.sozotv',
                            'com.apple.developer.team-identifier': team, 'get-task-allow': False}}
assert module.validate(profile, team, now) == profile['UUID']
variants = [('wrong platform', lambda p: p.update(Platform=['iOS'])),
            ('expired', lambda p: p.update(ExpirationDate=now-datetime.timedelta(seconds=1))),
            ('wrong team', lambda p: p.update(TeamIdentifier=['ZYXWVUTSRQ'])),
            ('wrong bundle', lambda p: p['Entitlements'].update({'application-identifier':f'{team}.com.other.app'})),
            ('development', lambda p: p['Entitlements'].update({'get-task-allow':True})),
            ('ad hoc', lambda p: p.update(ProvisionedDevices=['device'])),
            ('enterprise', lambda p: p.update(ProvisionsAllDevices=True))]
for label, change in variants:
    bad = copy.deepcopy(profile); change(bad)
    try: module.validate(bad, team, now)
    except AssertionError: continue
    raise AssertionError(f'Accepted {label} profile')
print('Release profile gates PASS (valid + 7 rejected variants)')
