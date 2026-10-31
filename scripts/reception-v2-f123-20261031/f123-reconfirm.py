#!/usr/bin/env python3
"""Re-confirm the retained emulator SIM for the f123 gateway after an app restart.

The fingerprintless AVD makes the gateway re-evaluate its SIM identity on every
cold start, which moves sim_bindings to unverified. The joint fixture can confirm
the retained emulator SIM explicitly; this drives that path.
"""
import json, subprocess, time, uuid, re

ADB = '/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb'
GW = 'emulator-5562'
GPKG = 'com.callagent.gateway'
BASE = 'http://10.0.2.2:18111'
SIM = '13031e89-318c-4b28-9882-3ffa10740f77'


def adb(*args, timeout=60):
    r = subprocess.run([ADB, '-s', GW, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '') + (r.stderr or '')


config = {'api_url': BASE, 'reuse_pairing': True,
          'reconfirm_retained_emulator_sim': SIM,
          'reconfirm_operation_id': str(uuid.uuid4())}
open('/tmp/jf.json', 'w').write(json.dumps(config))
print(adb('push', '/tmp/jf.json', '/data/local/tmp/jf.json').strip())
print(adb('shell', 'am', 'force-stop', GPKG).strip())
print(adb('shell', 'run-as', GPKG, 'cp', '/data/local/tmp/jf.json', 'files/joint-fixture.json').strip())
print(adb('shell', 'run-as', GPKG, 'rm', '-f', 'files/joint-ready.json',
          'files/joint-finished.json', 'files/joint-command.json', 'files/joint-result.json').strip())

subprocess.Popen([ADB, '-s', GW, 'shell', 'am', 'instrument', '-w',
                  GPKG + '.test/com.callagent.lab.GatewayJointInstrumentation'],
                 stdout=open('/tmp/f123-reconfirm-runner.log', 'wb'),
                 stderr=subprocess.STDOUT, start_new_session=True)

ready = None
for _ in range(90):
    time.sleep(1)
    raw = adb('shell', 'run-as', GPKG, 'cat', 'files/joint-ready.json')
    try:
        data = json.loads(raw[raw.index('{'):])
    except (ValueError, IndexError):
        continue
    if data.get('status') == 'READY':
        ready = data
        break
print('ready=' + json.dumps(ready or {}, ensure_ascii=False)[:300])
