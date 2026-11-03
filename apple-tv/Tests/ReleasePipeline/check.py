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

import contextlib, io
def fake(responses):
    calls = []
    def call(path, params=None, body=None):
        calls.append((path, params, body))
        return responses(path, params or {}, body)
    return call, calls
def builds(*versions):
    return {'data': [{'id': f'b{v}', 'attributes': {'version': v}} for v in versions]}
def expect_exit(fn, *args):
    try:
        with contextlib.redirect_stderr(io.StringIO()): fn(*args)
    except SystemExit as e:
        assert e.code not in (None, 0); return str(e.code)
    raise AssertionError(f'{fn.__name__}{args} did not fail')
def stdout_of(fn, *args):
    out = io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()): fn(*args)
    return out.getvalue().strip()
app = {'data': [{'id': 'app1', 'attributes': {'bundleId': asc.BUNDLE_ID}}]}
def with_builds(data):
    return lambda path, params, body: app if path == '/apps' else data
asc.tagged_builds = lambda: []
asc.call, _ = fake(with_builds(builds('3', '12', '1.2', 'abc')))
assert stdout_of(asc.next_build) == '13'
asc.call, _ = fake(with_builds(builds()))
assert stdout_of(asc.next_build) == '1'
asc.tagged_builds = lambda: [20]
asc.call, _ = fake(with_builds(builds('12')))
assert stdout_of(asc.next_build) == '21', 'a fresh tag must beat an unlisted build'
asc.tagged_builds = lambda: []
asc.call, calls = fake(with_builds(builds('5')))
expect_exit(asc.check_build, '1.0.0', '5')
stdout_of(asc.check_build, '1.0.0', '6')
assert calls[-1][1]['filter[preReleaseVersion.version]'] == '1.0.0'

asc.time.sleep = lambda s: None
def states(*seq):
    it = iter(seq)
    def respond(path, params, body):
        if path == '/apps': return app
        state = next(it)
        return {'data': []} if state is None else {'data': [{'id': 'B1', 'attributes': {'processingState': state}}]}
    return respond
asc.call, _ = fake(states(None, 'PROCESSING', 'VALID'))
assert stdout_of(asc.wait, '1.0.0', '7') == 'B1'
for bad in ('FAILED', 'INVALID'):
    asc.call, _ = fake(states('PROCESSING', bad))
    assert bad in expect_exit(asc.wait, '1.0.0', '7')
asc.call, _ = fake(states(*['PROCESSING'] * 5))
real_time, clock = asc.time.time, iter([0, 0, 120])
asc.time.time = lambda: next(clock)
assert 'after 1 minutes' in expect_exit(asc.wait, '1.0.0', '7', 1)
asc.time.time = real_time

def groups(internal):
    def respond(path, params, body):
        if path == '/apps': return app
        if path == '/betaGroups':
            return {'data': [{'id': 'G1', 'attributes': {'name': 'Team', 'isInternalGroup': internal}},
                             {'id': 'G2', 'attributes': {'name': 'Team 2', 'isInternalGroup': True}}]}
        return {}
    return respond
asc.call, calls = fake(groups(False))
assert 'external' in expect_exit(asc.add_to_group, 'B1', 'Team')
assert not any(c[2] for c in calls), 'nothing may be posted to an external group'
asc.call, calls = fake(groups(True))
stdout_of(asc.add_to_group, 'B1', 'Team')
assert calls[-1][0] == '/betaGroups/G1/relationships/builds'
assert calls[-1][2] == {'data': [{'type': 'builds', 'id': 'B1'}]}
asc.call, _ = fake(groups(True))
expect_exit(asc.add_to_group, 'B1', 'Nobody')
print('App Store Connect client PASS (next-build, check-build, wait states, group guard)')
