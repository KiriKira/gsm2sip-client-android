#!/usr/bin/env python3
"""C2: both hosts ring, then answer one and decline the other ~0.5 s apart.

Pass criteria (plan C2): each run produces exactly one outcome - either the call
connects and a late decline does not hang it up, or the whole call ends and both
hosts stop. Never "one host in call while the gateway call is already gone".
"""
import datetime, re, subprocess, time

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/c2'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H1, H2 = 'emulator-5562', 'emulator-5564', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
DECLINE, ANSWER = (134, 322), (346, 322)
results = []


def log(s):
    print(s, flush=True)
    open(P + '/c2-summary.txt', 'w').write('\n'.join(results + [s]) + '\n')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')


def adb(serial, *args, timeout=25):
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


def db(q, timeout=15):
    sh = ('port=$(sed -n \'s/^PG_PORT=//p\' /run/gsm2sip/ports); '
          'export PGPASSWORD="$(cat /data/secrets/db_password)"; '
          'exec psql -h 127.0.0.1 -p "$port" -U gsm2sip -d gsm2sip -XAt -F "|" -v ON_ERROR_STOP=1 -f -')
    r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', 'reception-r1-b', 'sh', '-c', sh],
                       input=q + ';\n', capture_output=True, text=True, timeout=timeout)
    out = (r.stdout or '').strip()
    if not out or 'ERROR' in out:
        raise RuntimeError('db:' + (out or r.stderr.strip())[:160])
    return out



def ensure_actions(serial):
    """Wake the screen and make sure the call notification actions are on screen."""
    for _ in range(3):
        adb(serial, 'shell', 'input', 'keyevent', '224')
        adb(serial, 'shell', 'input', 'keyevent', '82')
        adb(serial, 'shell', 'cmd', 'statusbar', 'expand-notifications', timeout=15)
        time.sleep(1.2)
        adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/c2c.xml')
        raw = adb(serial, 'exec-out', 'cat', '/sdcard/c2c.xml', timeout=15)
        if 'Answer' in raw and 'Decline' in raw:
            return True
    return False


def states(call_id):
    rows = db("SELECT client_device_id::text, state, COALESCE(reason,'-') FROM call_participants "
              "WHERE call_id='" + call_id + "'").splitlines()
    return {r.split('|')[0]: r.split('|')[1] + '/' + r.split('|')[2] for r in rows}


def call_state(call_id):
    return db("SELECT state FROM call_sessions WHERE call_id='" + call_id + "'")


def modem_busy():
    return 'mCallState=0' not in adb(GW, 'shell', 'dumpsys telephony.registry | grep -m1 mCallState', timeout=20)


log('started_utc=' + now())
rows = [line.split('|') for line in db(
    "SELECT d.id::text, COALESCE(r.incoming_enabled,false) FROM devices d "
    "LEFT JOIN device_reception r ON r.device_id=d.id WHERE d.owner_id='" + OWNER +
    "' AND d.role='client' ORDER BY d.created_at").splitlines()]
if len(rows) != 2 or any(r[1] != 't' for r in rows):
    raise RuntimeError('both_hosts_must_be_enabled')
H1_ID, H2_ID = rows[0][0], rows[1][0]
adb(H2, 'shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')

plans = [('answer_h1_decline_h2', H1, H1_ID, H2, H2_ID),
         ('answer_h2_decline_h1', H2, H2_ID, H1, H1_ID),
         ('answer_h1_decline_h2', H1, H1_ID, H2, H2_ID),
         ('answer_h2_decline_h1', H2, H2_ID, H1, H1_ID)]

for label, a_serial, a_id, d_serial, d_id in plans:
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    for serial in (H1, H2):
        adb(serial, 'shell', 'cmd', 'statusbar', 'collapse')
        adb(serial, 'shell', 'input', 'keyevent', '6')
    time.sleep(4)
    t0 = time.time()
    adb(GW, 'emu', 'gsm', 'call', NUMBER)
    call_id = ''
    for _ in range(30):
        try:
            call_id = db("SELECT call_id::text FROM call_sessions WHERE owner_id='" + OWNER +
                         "' AND created_at >= to_timestamp(" + str(t0) + ") ORDER BY created_at DESC LIMIT 1")
        except RuntimeError:
            call_id = ''
        if call_id:
            break
        time.sleep(0.3)
    both = False
    deadline = time.monotonic() + 18
    while time.monotonic() < deadline:
        try:
            s = states(call_id)
        except RuntimeError:
            s = {}
        if s.get(H1_ID, '').startswith('ringing') and s.get(H2_ID, '').startswith('ringing'):
            both = True
            break
        time.sleep(0.4)
    if not both:
        adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
        log(label + ': SKIP both_ringing=False')
        continue
    ok_a = ensure_actions(a_serial)
    ok_d = ensure_actions(d_serial)
    if not (ok_a and ok_d):
        adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
        log(label + ': SKIP actions_not_visible ok_a=%s ok_d=%s' % (ok_a, ok_d))
        continue
    adb(a_serial, 'shell', 'input', 'tap', str(ANSWER[0]), str(ANSWER[1]))
    time.sleep(0.5)
    adb(d_serial, 'shell', 'input', 'tap', str(DECLINE[0]), str(DECLINE[1]))
    tapped = now()
    for serial in (H1, H2):
        adb(serial, 'shell', 'cmd', 'statusbar', 'collapse')

    samples = []
    for wait in (2, 4, 4):
        time.sleep(wait)
        try:
            samples.append({'t': wait, 'call': call_state(call_id), 'parts': states(call_id),
                            'modem_busy': modem_busy()})
        except RuntimeError as exc:
            samples.append({'t': wait, 'error': str(exc)[:80]})
    last = samples[-1]
    parts = last.get('parts', {})
    accepted = [k for k, v in parts.items() if v.startswith('accepted')]
    verdict = 'FAIL'
    if last.get('call') == 'active' and len(accepted) == 1 and last.get('modem_busy'):
        verdict = 'PASS_CONNECTED'
    elif last.get('call') in ('ended', 'unknown') and not accepted:
        verdict = 'PASS_ENDED'
    elif last.get('call') == 'active' and not last.get('modem_busy'):
        verdict = 'FAIL_INCONSISTENT'
    log('%s: %s tapped=%s %s' % (label, verdict, tapped, samples))
    results.append('%s: %s' % (label, verdict))
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    for serial in (H1, H2):
        adb(serial, 'shell', 'input', 'keyevent', '6')
    time.sleep(3)

log('finished_utc=' + now())
