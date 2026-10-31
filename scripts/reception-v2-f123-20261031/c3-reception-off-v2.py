#!/usr/bin/env python3
"""C3: while both hosts ring, H1 turns off background reception.

Expected: H1 stops ringing, H2 keeps ringing and can still answer, and the call
is not torn down by H1's opt-out.

Timing note: the gateway only rings for ~26 s, so every poll uses the DB (sub-second)
instead of `dumpsys telecom` (2-4 s per call), and the switch is positioned before
the call is injected.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/c3'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H1, H2 = 'emulator-5562', 'emulator-5564', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
SWITCH = [422, 275]
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/c3-summary.txt', 'w').write('\n'.join(lines) + '\n')


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


def ui(serial):
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/c3.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/c3.xml', timeout=15)
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None


def answer(serial):
    adb(serial, 'shell', 'cmd', 'statusbar', 'expand-notifications', timeout=15)
    for _ in range(6):
        root = ui(serial)
        if root is not None:
            for n in root.iter('node'):
                label = (n.attrib.get('content-desc') or '') + ' ' + (n.attrib.get('text') or '')
                if 'Answer' in label or '接听' in label:
                    v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                    if len(v) == 4:
                        xy = ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2)
                        adb(serial, 'shell', 'input', 'tap', str(xy[0]), str(xy[1]), timeout=15)
                        adb(serial, 'shell', 'cmd', 'statusbar', 'collapse', timeout=15)
                        return xy
        time.sleep(0.6)
    return None


def part(call_id, device_id):
    return db("SELECT state, COALESCE(reason,'-') FROM call_participants WHERE call_id='" +
              call_id + "' AND client_device_id='" + device_id + "'")


emit('started_utc=' + now())
clients = db("SELECT d.id::text, COALESCE(r.incoming_enabled,false) FROM devices d "
             "LEFT JOIN device_reception r ON r.device_id=d.id "
             "WHERE d.owner_id='" + OWNER + "' AND d.role='client' ORDER BY d.created_at")
rows = [line.split('|') for line in clients.splitlines()]
emit('clients=' + clients.replace('\n', ' ; '))
if len(rows) != 2 or any(r[1] != 't' for r in rows):
    raise RuntimeError('both_hosts_must_have_incoming_enabled')
H1_ID, H2_ID = rows[0][0], rows[1][0]

# Clean app state on both hosts: a leftover CallActionActivity or a stale call
# record stops the next offer from ringing.
for serial in (H1, H2):
    adb(serial, 'shell', 'am', 'force-stop', 'com.callagent.host')
    adb(serial, 'shell', 'input', 'keyevent', '224')
    time.sleep(1)
    adb(serial, 'shell', 'monkey', '-p', 'com.callagent.host', '-c', 'android.intent.category.LAUNCHER', '1')
for serial in (H1, H2):
    for _ in range(20):
        root = ui(serial)
        if root is not None and any(n.attrib.get('content-desc') == '设置' for n in root.iter('node')):
            break
        adb(serial, 'shell', 'input', 'keyevent', '224')
        time.sleep(2)
adb(H1, 'shell', 'input', 'keyevent', '224')
# Dismiss a leftover call screen (CallActionActivity) before navigating.
for _ in range(3):
    if 'CallActionActivity' not in adb(H1, 'shell', 'dumpsys window | grep -m1 mCurrentFocus', timeout=20):
        break
    root = ui(H1)
    if root is not None:
        for n in root.iter('node'):
            if (n.attrib.get('text') or '') == '关闭' or (n.attrib.get('content-desc') or '') == '关闭':
                v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                if len(v) == 4:
                    adb(H1, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                    break
    adb(H1, 'shell', 'input', 'keyevent', '4')
    time.sleep(1.5)
adb(H1, 'shell', 'input', 'tap', '400', '735')
time.sleep(3)
ok = False
for _ in range(3):
    adb(H1, 'shell', 'input', 'swipe', '240', '600', '240', '300', '300')
    time.sleep(0.9)
adb(H1, 'shell', 'input', 'swipe', '240', '300', '240', '600', '300')
time.sleep(1.5)
root = ui(H1)
if root is not None:
    for n in root.iter('node'):
        if n.attrib.get('content-desc') == '后台来电接收':
            v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
            if len(v) == 4:
                SWITCH[:] = [(v[0] + v[2]) // 2, (v[1] + v[3]) // 2]
            ok = True
            break
emit('h1_switch_visible=' + str(ok) + ';at=' + str(tuple(SWITCH)))
if not ok:
    raise RuntimeError('h1_reception_switch_not_on_screen')

adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(4)
t0 = time.time()
ack = adb(GW, 'emu', 'gsm', 'call', NUMBER)
emit('gsm_call_ack=' + ack)
if 'OK' not in ack:
    raise RuntimeError('gsm_ack_missing')
# Wait for *this* call's row: reading "latest" immediately raced the insert and
# returned the previous call, so every later assertion watched the wrong call.
call_id = ''
for _ in range(25):
    try:
        call_id = db("SELECT call_id::text FROM call_sessions WHERE owner_id='" + OWNER +
                     "' AND created_at >= to_timestamp(" + str(t0) + ") ORDER BY created_at DESC LIMIT 1")
    except RuntimeError:
        call_id = ''
    if call_id:
        break
    time.sleep(0.3)
emit('call_id_hash=' + call_id[:8] + ';utc=' + now())

both = False
deadline = time.monotonic() + 20
while time.monotonic() < deadline:
    states = dict(line.split('|')[:2] for line in
                  db("SELECT client_device_id::text, state FROM call_participants WHERE call_id='" +
                     call_id + "'").splitlines())
    if states.get(H1_ID) == 'ringing' and states.get(H2_ID) == 'ringing':
        both = True
        break
    time.sleep(0.4)
emit('both_ringing=' + str(both) + ';utc=' + now())
if not both:
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    raise RuntimeError('both_hosts_did_not_ring')

adb(H1, 'shell', 'input', 'tap', str(SWITCH[0]), str(SWITCH[1]))
emit('h1_switch_tapped_utc=' + now())

h1_off = h2_on = False
deadline = time.monotonic() + 12
while time.monotonic() < deadline:
    a = part(call_id, H1_ID)
    b = part(call_id, H2_ID)
    if not a.startswith('ringing') and b.startswith('ringing'):
        h1_off = h2_on = True
        emit('h1_row=' + a + ';h2_row=' + b + ';utc=' + now())
        break
    time.sleep(0.4)
emit('h1_stopped_ringing=' + str(h1_off) + ';h2_still_ringing=' + str(h2_on))

state = db("SELECT state FROM call_sessions WHERE call_id='" + call_id + "'")
emit('call_state_after_switch=' + state)
if state in ('ended', 'unknown'):
    raise RuntimeError('call_torn_down_by_opt_out')

xy = answer(H2)
emit('h2_answer_tap=' + str(xy) + ';utc=' + now())
row = ''
deadline = time.monotonic() + 20
while time.monotonic() < deadline:
    row = db("SELECT c.state, p.state, COALESCE(mm.cellular_active,false) FROM call_sessions c "
             "JOIN call_participants p USING(call_id) "
             "LEFT JOIN rtc_media_sessions mm ON mm.call_id=c.call_id AND mm.client_device_id=p.client_device_id "
             "WHERE c.call_id='" + call_id + "' AND p.client_device_id='" + H2_ID + "'")
    if row.startswith('active|accepted|t'):
        break
    time.sleep(0.6)
emit('h2_row=' + row)
h2_ok = row.startswith('active|accepted|t')
telecom = adb(H2, 'shell', 'dumpsys telecom | grep -m1 state=ACTIVE', timeout=30)
emit('h2_telecom_active=' + str('state=ACTIVE' in telecom))
emit('participants=' + db("SELECT d.id::text, p.state, COALESCE(p.reason,'-') FROM call_participants p "
                         "JOIN devices d ON d.id=p.client_device_id WHERE p.call_id='" + call_id + "'")
     .replace('\n', ' ; '))
time.sleep(4)
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(4)
emit('final=' + db("SELECT state, COALESCE(reason,'-') FROM call_sessions WHERE call_id='" + call_id + "'"))
if not (h1_off and h2_on and h2_ok):
    raise RuntimeError('c3_criteria_not_met')
emit('c3=PASS;utc=' + now())
emit('finished_utc=' + now())
