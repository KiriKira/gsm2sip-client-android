#!/usr/bin/env python3
"""B6: turning background SMS sync off on H1 must not stop calls from ringing.

Checks:
  1. H1 sync off -> server flag sms_enabled=false.
  2. SMS to the gateway -> H1 gets no sms push and shows nothing; H2 shows it.
  3. Incoming call -> H1 still rings (reception is independent of sync).
  4. Re-enabling sync delivers the backlog exactly once.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/b6'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H1, H2 = 'emulator-5562', 'emulator-5564', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
SWITCH = '后台短信同步'
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/b6-summary.txt', 'w').write('\n'.join(lines) + '\n')


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
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/b6.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/b6.xml', timeout=15)
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None



def dismiss_call_screen(serial):
    if 'CallActionActivity' not in adb(serial, 'shell', 'dumpsys window | grep -m1 mCurrentFocus', timeout=20):
        return
    root = ui(serial)
    if root is None:
        adb(serial, 'shell', 'input', 'keyevent', '4')
        return
    for n in root.iter('node'):
        if (n.attrib.get('text') or '') == '关闭' or (n.attrib.get('content-desc') or '') == '关闭':
            v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
            if len(v) == 4:
                adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                time.sleep(1)
                return


def toggle_sync(serial):
    """Open settings, scroll, tap the 后台短信同步 tile switch."""
    dismiss_call_screen(serial)
    adb(serial, 'shell', 'input', 'keyevent', '224')
    adb(serial, 'shell', 'input', 'tap', '400', '735')
    time.sleep(3)
    for _ in range(4):
        adb(serial, 'shell', 'input', 'swipe', '240', '600', '240', '300', '300')
        time.sleep(1)
    for step in range(6):
        root = ui(serial)
        if root is not None:
            for n in root.iter('node'):
                if n.attrib.get('content-desc') == SWITCH and n.attrib.get('checkable') == 'true':
                    v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                    if len(v) == 4:
                        adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                        return True
        adb(serial, 'shell', 'input', 'swipe', '240', '300', '240', '600', '300')
        time.sleep(1)
    return False


def ui_text(serial):
    root = ui(serial)
    if root is None:
        return ''
    return ' '.join((n.attrib.get('text') or '') for n in root.iter('node'))


emit('started_utc=' + now())
rows = [line.split('|') for line in db(
    "SELECT d.id::text, COALESCE(r.incoming_enabled,false), COALESCE(r.sms_enabled,false) "
    "FROM devices d LEFT JOIN device_reception r ON r.device_id=d.id WHERE d.owner_id='" + OWNER +
    "' AND d.role='client' ORDER BY d.created_at").splitlines()]
H1_ID, H2_ID = rows[0][0], rows[1][0]
emit('clients=' + ' ; '.join('|'.join(r) for r in rows))
if rows[0][2] != 't' or rows[1][2] != 't':
    raise RuntimeError('precondition_both_sms_enabled')

emit('toggle_sync_off=' + str(toggle_sync(H1)))
time.sleep(6)
flags = db("SELECT COALESCE(sms_enabled,false) FROM device_reception WHERE device_id='" + H1_ID + "'")
emit('h1_sms_enabled_after_off=' + flags)
if flags != 'f':
    raise RuntimeError('sms_sync_did_not_turn_off')

adb(H1, 'logcat', '-c', timeout=20)
text = 'B6SMS-%s' % datetime.datetime.now(datetime.timezone.utc).strftime('%H%M%S')
emit('sms_ack=' + adb(GW, 'emu', 'sms', 'send', NUMBER, text) + ';text=' + text)
time.sleep(20)
h1_pushes = adb(H1, 'logcat', '-d', '-v', 'time')
h1_sms_push = 'kind=sms' in h1_pushes
emit('h1_sms_push_seen=' + str(h1_sms_push))
emit('rows=' + db("SELECT count(*) FROM messages WHERE owner_id='" + OWNER + "' AND body='" + text + "'"))

# call must still ring on H1 even with sync off
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(3)
t0 = time.time()
adb(GW, 'emu', 'gsm', 'call', NUMBER)
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
h1_ring = False
deadline = time.monotonic() + 18
while time.monotonic() < deadline:
    try:
        if db("SELECT state FROM call_participants WHERE call_id='" + call_id +
              "' AND client_device_id='" + H1_ID + "'").strip() == 'ringing':
            h1_ring = True
            break
    except RuntimeError:
        pass
    time.sleep(0.4)
emit('h1_rings_with_sms_sync_off=' + str(h1_ring))
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(5)
adb(H1, 'shell', 'input', 'keyevent', '6')

emit('toggle_sync_on=' + str(toggle_sync(H1)))
seen = False
deadline = time.monotonic() + 90
while time.monotonic() < deadline:
    adb(H1, 'shell', 'input', 'tap', '240', '735')
    time.sleep(2)
    if text in ui_text(H1):
        seen = True
        break
    time.sleep(4)
emit('backlog_delivered_after_reenable=' + str(seen) + ';utc=' + now())
emit('h1_sms_enabled_now=' + db("SELECT COALESCE(sms_enabled,false) FROM device_reception WHERE device_id='" + H1_ID + "'"))
if not h1_ring or not seen or h1_sms_push:
    raise RuntimeError('b6_failed')
emit('b6=PASS')
emit('finished_utc=' + now())
