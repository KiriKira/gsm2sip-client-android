#!/usr/bin/env python3
"""B1 + B2 on the f123 rig: H1 has background reception OFF, H2 keeps it ON.

B1: a gateway incoming call rings only H2; H1's row stays absent/ended and H1
    never raises a Telecom call.
B2: an inbound SMS still reaches both hosts, exactly once each.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/b12'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H1, H2 = 'emulator-5562', 'emulator-5564', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/b12-summary.txt', 'w').write('\n'.join(lines) + '\n')


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



def dismiss_call_screen(serial):
    """A stale CallActionActivity ('通话已结束') swallows taps on the tab bar."""
    if 'CallActionActivity' not in adb(serial, 'shell', 'dumpsys window | grep -m1 mCurrentFocus', timeout=20):
        return
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/b12d.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/b12d.xml', timeout=15)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return
    for n in root.iter('node'):
        if (n.attrib.get('text') or '') == '关闭' or (n.attrib.get('content-desc') or '') == '关闭':
            v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
            if len(v) == 4:
                adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                time.sleep(1)
                return



def ui_text(serial):
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/b12.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/b12.xml', timeout=15)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return ''
    return ' '.join((n.attrib.get('text') or '') for n in root.iter('node'))


emit('started_utc=' + now())
rows = [line.split('|') for line in db(
    "SELECT d.id::text, COALESCE(r.incoming_enabled,false) FROM devices d "
    "LEFT JOIN device_reception r ON r.device_id=d.id WHERE d.owner_id='" + OWNER +
    "' AND d.role='client' ORDER BY d.created_at").splitlines()]
emit('clients=' + ' ; '.join('|'.join(r) for r in rows))
H1_ID, H1_ON = rows[0][0], rows[0][1]
H2_ID, H2_ON = rows[1][0], rows[1][1]
if H1_ON != 'f' or H2_ON != 't':
    raise RuntimeError('precondition_needs_h1_off_h2_on')

# --- B1: only H2 rings -------------------------------------------------------
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(3)
t0 = time.time()
ack = adb(GW, 'emu', 'gsm', 'call', NUMBER)
emit('b1_gsm_call_ack=' + ack)
call_id = ''
for _ in range(25):
    try:
        call_id = db("SELECT call_id::text FROM call_sessions WHERE owner_id='" + OWNER +
                     "' AND created_at >= to_timestamp(" + str(t0) +
                     ") ORDER BY created_at DESC LIMIT 1")
    except RuntimeError:
        call_id = ''
    if call_id:
        break
    time.sleep(0.3)
h2_ring = False
deadline = time.monotonic() + 20
while time.monotonic() < deadline:
    states = dict(line.split('|')[:2] for line in
                  db("SELECT client_device_id::text, state FROM call_participants WHERE call_id='" +
                     call_id + "'").splitlines())
    if states.get(H2_ID) == 'ringing':
        h2_ring = True
        break
    time.sleep(0.4)
states = dict(line.split('|')[:2] for line in
              db("SELECT client_device_id::text, state FROM call_participants WHERE call_id='" +
                 call_id + "'").splitlines())
h1_tc = adb(H1, 'shell', 'dumpsys telecom | grep -c state=RINGING', timeout=30)
emit('b1_h2_ringing=' + str(h2_ring) + ';b1_participants=' + str(states) +
     ';b1_h1_ringing_tc=' + h1_tc + ';utc=' + now())
h1_row = states.get(H1_ID, 'absent')
if not h2_ring or h1_row == 'ringing' or h1_tc.strip() not in ('0', ''):
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    raise RuntimeError('b1_failed')
emit('b1=PASS;h1_row=' + h1_row)
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(5)

# --- B2: SMS still reaches both ---------------------------------------------
text = 'B2SMS-%s' % datetime.datetime.now(datetime.timezone.utc).strftime('%H%M%S')
ack = adb(GW, 'emu', 'sms', 'send', NUMBER, text)
emit('b2_sms_ack=' + ack + ';text=' + text)
seen = {'h1': False, 'h2': False}
deadline = time.monotonic() + 75
while time.monotonic() < deadline:
    count = db("SELECT count(*) FROM messages WHERE owner_id='" + OWNER + "' AND body='" + text + "'")
    if count.strip() == '1':
        for key, serial in (('h1', H1), ('h2', H2)):
            dismiss_call_screen(serial)
            adb(serial, 'shell', 'input', 'tap', '240', '735')
            time.sleep(1.5)
            if text in ui_text(serial):
                seen[key] = True
        if all(seen.values()):
            break
    time.sleep(3)
settled = db("SELECT count(*) FROM messages WHERE owner_id='" + OWNER + "' AND body='" + text + "'")
emit('b2_message_rows=' + settled + ';b2_ui=' + str(seen) + ';utc=' + now())
if settled.strip() != '1' or not all(seen.values()):
    raise RuntimeError('b2_failed')
emit('b2=PASS')
emit('finished_utc=' + now())
