#!/usr/bin/env python3
"""D2: the host is paired to two nodes; one call must appear once and be answered once.

Checks: exactly one Telecom ringing call on the host, one participant per node,
and after answering on node B the other node records answered_elsewhere.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/d2'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H = 'emulator-5562', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER_B = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
OWNER_D = '244b7991-569a-4179-be98-1289b7a3e411'
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/d2-summary.txt', 'w').write('\n'.join(lines) + '\n')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')


def adb(serial, *args, timeout=25):
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


def db(q, container, timeout=15):
    sh = ('port=$(sed -n \'s/^PG_PORT=//p\' /run/gsm2sip/ports); '
          'export PGPASSWORD="$(cat /data/secrets/db_password)"; '
          'exec psql -h 127.0.0.1 -p "$port" -U gsm2sip -d gsm2sip -XAt -F "|" -v ON_ERROR_STOP=1 -f -')
    r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', container, 'sh', '-c', sh],
                       input=q + ';\n', capture_output=True, text=True, timeout=timeout)
    return (r.stdout or r.stderr).strip()


def ui(serial):
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/d2.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/d2.xml', timeout=15)
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None


def answer(serial):
    for _ in range(3):
        adb(serial, 'shell', 'input', 'keyevent', '224')
        adb(serial, 'shell', 'cmd', 'statusbar', 'expand-notifications', timeout=15)
        time.sleep(1.5)
        root = ui(serial)
        if root is not None:
            for n in root.iter('node'):
                label = (n.attrib.get('content-desc') or '') + ' ' + (n.attrib.get('text') or '')
                if 'Answer' in label or '接听' in label:
                    v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                    if len(v) == 4:
                        xy = ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2)
                        adb(serial, 'shell', 'input', 'tap', str(xy[0]), str(xy[1]))
                        adb(serial, 'shell', 'cmd', 'statusbar', 'collapse')
                        return xy
        adb(serial, 'shell', 'cmd', 'statusbar', 'collapse')
    return None


emit('started_utc=' + now())
for serial in (H,):
    for _ in range(2):
        root = ui(serial)
        if root is None:
            break
        done = False
        for n in root.iter('node'):
            if (n.attrib.get('text') or '') == '关闭' or (n.attrib.get('content-desc') or '') == '关闭':
                v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                if len(v) == 4:
                    adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                    done = True
                    time.sleep(1.5)
                    break
        if not done:
            break

adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(4)
t0 = time.time()
emit('ack=' + adb(GW, 'emu', 'gsm', 'call', NUMBER))
call_b = ''
for _ in range(30):
    call_b = db("SELECT call_id::text FROM call_sessions WHERE owner_id='" + OWNER_B +
                "' AND created_at >= to_timestamp(" + str(t0) + ")", 'reception-r1-b')
    if call_b and 'ERROR' not in call_b:
        break
    time.sleep(0.4)
emit('call_b_hash=' + call_b.strip()[:8])

ring = False
deadline = time.monotonic() + 20
while time.monotonic() < deadline:
    out = adb(H, 'shell', 'dumpsys telecom | grep -c "state=RINGING"', timeout=30)
    if out.strip().isdigit() and int(out.strip()) >= 1:
        ring = True
        break
    time.sleep(1)
emit('host_telecom_ringing_count=' + out.strip() + ';ringing=' + str(ring))

part_b = db("SELECT state, COALESCE(reason,'-') FROM call_participants WHERE call_id='" + call_b.strip() + "'", 'reception-r1-b')
emit('node_b_participants=' + part_b.replace('\n', ' ; '))
call_d = db("SELECT call_id::text FROM call_sessions WHERE owner_id='" + OWNER_D +
            "' ORDER BY created_at DESC LIMIT 1", 'reception-r1-d')
part_d = db("SELECT state, COALESCE(reason,'-') FROM call_participants WHERE call_id='" + call_d.strip() + "'", 'reception-r1-d')
emit('node_d_participants=' + part_d.replace('\n', ' ; '))

xy = answer(H)
emit('answer_tap=' + str(xy) + ';utc=' + now())
time.sleep(6)
emit('after_answer_b=' + db("SELECT state, COALESCE(reason,'-') FROM call_participants WHERE call_id='" +
                            call_b.strip() + "'", 'reception-r1-b').replace('\n', ' ; '))
emit('after_answer_d=' + db("SELECT state, COALESCE(reason,'-') FROM call_participants WHERE call_id='" +
                            call_d.strip() + "'", 'reception-r1-d').replace('\n', ' ; '))
time.sleep(4)
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(4)
adb(H, 'shell', 'input', 'keyevent', '6')
emit('finished_utc=' + now())
