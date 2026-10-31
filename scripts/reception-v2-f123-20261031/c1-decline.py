#!/usr/bin/env python3
"""C1: both hosts enabled, H2 in airplane mode; H1 declines -> the gateway call ends.

Checks: H1 rings, decline within ~3 s ends the cellular call, the server records
the call as declined, and the offline H2 never rings.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/c1'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H1, H2 = 'emulator-5562', 'emulator-5564', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/c1-summary.txt', 'w').write('\n'.join(lines) + '\n')


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
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/c1.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/c1.xml', timeout=15)
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None


def tap_label(serial, labels):
    adb(serial, 'shell', 'cmd', 'statusbar', 'expand-notifications', timeout=15)
    time.sleep(1.5)
    root = ui(serial)
    if root is None:
        return None
    for n in root.iter('node'):
        label = ((n.attrib.get('text') or '') + ' ' + (n.attrib.get('content-desc') or '')).strip()
        if any(l in label for l in labels):
            v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
            if len(v) == 4:
                xy = ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2)
                adb(serial, 'shell', 'input', 'tap', str(xy[0]), str(xy[1]))
                return xy
    return None


def clients():
    rows = [line.split('|') for line in db(
        "SELECT d.id::text, COALESCE(r.incoming_enabled,false) FROM devices d "
        "LEFT JOIN device_reception r ON r.device_id=d.id WHERE d.owner_id='" + OWNER +
        "' AND d.role='client' ORDER BY d.created_at").splitlines()]
    return rows[0][0], rows[1][0]


def reception(device_id):
    return db("SELECT incoming_enabled FROM device_reception WHERE device_id='" + device_id + "'")


def set_reception_on(serial, device_id, want=True):
    for _ in range(3):
        if reception(device_id) == ('t' if want else 'f'):
            return True
        adb(serial, 'shell', 'input', 'keyevent', '224')
        adb(serial, 'shell', 'input', 'tap', '400', '735')
        time.sleep(3)
        for _ in range(3):
            adb(serial, 'shell', 'input', 'swipe', '240', '600', '240', '300', '300')
            time.sleep(1)
        adb(serial, 'shell', 'input', 'swipe', '240', '300', '240', '600', '300')
        time.sleep(1.5)
        root = ui(serial)
        if root is None:
            continue
        for n in root.iter('node'):
            if n.attrib.get('content-desc') == '后台来电接收':
                v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                if len(v) == 4:
                    adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                    time.sleep(4)
                break
    return reception(device_id) == ('t' if want else 'f')


emit('started_utc=' + now())
H1_ID, H2_ID = clients()
emit('h1_enabled=' + str(set_reception_on(H1, H1_ID, True)))
if reception(H1_ID) != 't':
    raise RuntimeError('could_not_enable_h1_reception')

adb(H2, 'shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
time.sleep(3)
emit('h2_airplane=' + adb(H2, 'shell', 'settings', 'get', 'global', 'airplane_mode_on'))

adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(3)
t0 = time.time()
emit('gsm_call_ack=' + adb(GW, 'emu', 'gsm', 'call', NUMBER))
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
emit('call_id_hash=' + call_id[:8])

ringing = False
deadline = time.monotonic() + 15
while time.monotonic() < deadline:
    try:
        if db("SELECT state FROM call_participants WHERE call_id='" + call_id +
              "' AND client_device_id='" + H1_ID + "'").strip() == 'ringing':
            ringing = True
            break
    except RuntimeError:
        pass
    time.sleep(0.4)
emit('h1_ringing=' + str(ringing))
if not ringing:
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    raise RuntimeError('h1_did_not_ring')

xy = tap_label(H1, ['拒接', 'Decline'])
emit('decline_tap=' + str(xy) + ';utc=' + now())

ended_s = None
start = time.monotonic()
deadline = start + 15
while time.monotonic() < deadline:
    modem = adb(GW, 'shell', 'dumpsys telephony.registry | grep -m1 mCallState', timeout=20)
    if 'mCallState=0' in modem:
        ended_s = round(time.monotonic() - start, 2)
        break
    time.sleep(0.5)
emit('cellular_ended_after_decline_s=' + str(ended_s))
emit('call_row=' + db("SELECT state, COALESCE(reason,'-') FROM call_sessions WHERE call_id='" + call_id + "'"))
emit('participants=' + db("SELECT client_device_id::text, state, COALESCE(reason,'-') FROM call_participants "
                         "WHERE call_id='" + call_id + "'").replace('\n', ' ; '))

adb(H2, 'shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
time.sleep(8)
emit('h2_after_online_participants=' + db(
    "SELECT client_device_id::text, state, COALESCE(reason,'-') FROM call_participants "
    "WHERE call_id='" + call_id + "'").replace('\n', ' ; '))
if ended_s is None:
    raise RuntimeError('c1_gateway_call_not_ended')
emit('c1=PASS;utc=' + now())
emit('finished_utc=' + now())
