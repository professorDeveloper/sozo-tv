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

import base64, json, os, subprocess, tempfile
spec = importlib.util.spec_from_file_location('asc', script.with_name('asc.py'))
asc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asc)
with tempfile.TemporaryDirectory() as tmp:
    key, pub = pathlib.Path(tmp, 'AuthKey.p8'), pathlib.Path(tmp, 'pub.pem')
    subprocess.run(['openssl', 'genpkey', '-algorithm', 'EC', '-pkeyopt', 'ec_paramgen_curve:P-256', '-out', key], check=True)
    subprocess.run(['openssl', 'pkey', '-in', key, '-pubout', '-out', pub], check=True)
    os.environ.update(ASC_API_KEY_ID='TESTKEY123', ASC_API_ISSUER_ID='issuer', ASC_API_KEY_PATH=str(key))
    for _ in range(20):
        head, body, sig = asc.token().split('.')
        raw = base64.urlsafe_b64decode(sig + '=' * (-len(sig) % 4))
        assert len(raw) == 64, 'ES256 signature must be 64 bytes'
        def integer(b):
            b = b.lstrip(b'\0')
            b = b'\0' + b if b[0] & 0x80 else b
            return bytes([2, len(b)]) + b
        seq = integer(raw[:32]) + integer(raw[32:])
        pathlib.Path(tmp, 'sig.der').write_bytes(bytes([0x30, len(seq)]) + seq)
        subprocess.run(['openssl', 'dgst', '-sha256', '-verify', pub, '-signature', pathlib.Path(tmp, 'sig.der')],
                       input=f'{head}.{body}'.encode(), check=True, capture_output=True)
    assert json.loads(base64.urlsafe_b64decode(body + '==='))['aud'] == 'appstoreconnect-v1'
print('App Store Connect JWT PASS (20 ES256 signatures verified)')
