#!/usr/bin/env python3
"""B5: turning reception off while offline must sync once the network returns.

Steps: airplane mode on -> switch 后台来电接收 off (UI shows 待同步) -> airplane off
-> within ~60 s the server flag becomes false and the UI shows 已同步, after which
an incoming call rings only on H2.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/b5'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H1, H2 = 'emulator-5562', 'emulator-5564', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/b5-summary.txt', 'w').write('\n'.join(lines) + '\n')


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
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/b5.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/b5.xml', timeout=15)
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


def toggle_reception(serial):
    dismiss_call_screen(serial)
    adb(serial, 'shell', 'input', 'keyevent', '224')
    adb(serial, 'shell', 'input', 'tap', '400', '735')
    time.sleep(3)
    for _ in range(3):
        adb(serial, 'shell', 'input', 'swipe', '240', '600', '240', '300', '300')
        time.sleep(1)
    for _ in range(6):
        root = ui(serial)
        if root is not None:
            for n in root.iter('node'):
                if n.attrib.get('content-desc') == '后台来电接收' and n.attrib.get('checkable') == 'true':
                    v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                    if len(v) == 4:
                        adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                        return True
        adb(serial, 'shell', 'input', 'swipe', '240', '300', '240', '600', '300')
        time.sleep(1)
    return False


def visible_text(serial):
    root = ui(serial)
    if root is None:
        return ''
    return ' '.join((n.attrib.get('text') or '') for n in root.iter('node'))


emit('started_utc=' + now())
rows = [line.split('|') for line in db(
    "SELECT d.id::text, COALESCE(r.incoming_enabled,false) FROM devices d "
    "LEFT JOIN device_reception r ON r.device_id=d.id WHERE d.owner_id='" + OWNER +
    "' AND d.role='client' ORDER BY d.created_at").splitlines()]
H1_ID, H2_ID = rows[0][0], rows[1][0]
emit('clients=' + ' ; '.join('|'.join(r) for r in rows))
if rows[0][1] != 't':
    raise RuntimeError('precondition_h1_reception_on')

adb(H1, 'shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
time.sleep(6)
emit('h1_airplane=' + adb(H1, 'shell', 'settings', 'get', 'global', 'airplane_mode_on'))
emit('toggle_offline=' + str(toggle_reception(H1)))
time.sleep(3)
text_offline = visible_text(H1)
emit('offline_ui_has_pending=' + str('待同步' in text_offline))
emit('server_flag_while_offline=' + db("SELECT COALESCE(incoming_enabled,false) FROM device_reception "
                                     "WHERE device_id='" + H1_ID + "'"))

adb(H1, 'shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
synced_at = None
start = time.monotonic()
deadline = start + 75
while time.monotonic() < deadline:
    flag = db("SELECT COALESCE(incoming_enabled,false) FROM device_reception WHERE device_id='" + H1_ID + "'")
    if flag == 'f':
        synced_at = round(time.monotonic() - start, 1)
        break
    time.sleep(3)
emit('server_flag_after_online=' + flag + ';synced_after_s=' + str(synced_at))
if flag != 'f':
    raise RuntimeError('b5_offline_toggle_not_synced')

text_online = visible_text(H1)
emit('online_ui_has_synced=' + str('已同步' in text_online))

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
states = {}
deadline = time.monotonic() + 18
while time.monotonic() < deadline:
    states = dict(line.split('|')[:2] for line in
                  db("SELECT client_device_id::text, state FROM call_participants WHERE call_id='" +
                     call_id + "'").splitlines())
    if states.get(H2_ID) == 'ringing':
        break
    time.sleep(0.4)
emit('after_offline_participants=' + str(states))
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(4)
adb(H2, 'shell', 'input', 'keyevent', '6')
if states.get(H2_ID) != 'ringing' or states.get(H1_ID) == 'ringing':
    raise RuntimeError('b5_call_rang_wrong_hosts')
emit('b5=PASS;utc=' + now())
emit('finished_utc=' + now())
