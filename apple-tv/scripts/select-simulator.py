#!/usr/bin/env python3
"""Choose the latest installed available tvOS runtime; never use a user's device."""
import json
import subprocess

def listing(kind):
    return json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', kind, '--json']))[kind]

runtimes = [r for r in listing('runtimes') if r.get('isAvailable') and '.tvOS-' in r['identifier']]
if not runtimes:
    raise SystemExit('No available tvOS runtime. Install it in Xcode Settings > Components.')
runtime = max(runtimes, key=lambda r: tuple(int(part) for part in r['version'].split('.')))
devices = [d for d in listing('devicetypes') if d.get('productFamily') == 'Apple TV']
# Apple TV 4K is supported by the current tvOS runtimes.
preferred = next((d for d in devices if '3rd generation' in d['name'] and '4K' in d['name']), None)
if preferred is None:
    preferred = next((d for d in devices if '4K' in d['name']), None)
if preferred is None:
    raise SystemExit('No Apple TV 4K simulator device type installed.')
print(runtime['identifier'])
print(preferred['identifier'])
