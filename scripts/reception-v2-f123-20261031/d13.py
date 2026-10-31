#!/usr/bin/env python3
"""D1 + D3 on the three-node rig (B has the hosts, D and E are gateway-only).

D1: with D/E alive, an inbound call must still reach the host through B.
D3: with D/E stopped, the same call must still be delivered by B.
"""
import datetime, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/d13'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, H = 'emulator-5562', 'emulator-5566'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
H_ID = '2d326382-3a88-4b10-9bc0-781b255297cc'
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/d13-summary.txt', 'w').write('\n'.join(lines) + '\n')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')


def adb(serial, *args, timeout=25):
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


def db(q, container='reception-r1-b', timeout=15):
    sh = ('port=$(sed -n \'s/^PG_PORT=//p\' /run/gsm2sip/ports); '
          'export PGPASSWORD="$(cat /data/secrets/db_password)"; '
          'exec psql -h 127.0.0.1 -p "$port" -U gsm2sip -d gsm2sip -XAt -F "|" -v ON_ERROR_STOP=1 -f -')
    r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', container, 'sh', '-c', sh],
                       input=q + ';\n', capture_output=True, text=True, timeout=timeout)
    out = (r.stdout or '').strip()
    if not out or 'ERROR' in out:
        raise RuntimeError('db:' + (out or r.stderr.strip())[:160])
    return out


def ui(serial):
    adb(serial, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/d13.xml')
    raw = adb(serial, 'exec-out', 'cat', '/sdcard/d13.xml', timeout=15)
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None


def dismiss(serial):
    for _ in range(2):
        root = ui(serial)
        if root is None:
            return
        hit = False
        for n in root.iter('node'):
            if (n.attrib.get('text') or '') == '关闭' or (n.attrib.get('content-desc') or '') == '关闭':
                v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                if len(v) == 4:
                    adb(serial, 'shell', 'input', 'tap', str((v[0] + v[2]) // 2), str((v[1] + v[3]) // 2))
                    hit = True
                    time.sleep(1.5)
                    break
        if not hit:
            return


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


def one_call(tag):
    adb(GW, 'logcat', '-c', timeout=20)
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    time.sleep(4)
    t0 = time.time()
    ack = adb(GW, 'emu', 'gsm', 'call', NUMBER)
    emit(tag + '_ack=' + ack)
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
    ringing = False
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        try:
            if db("SELECT state FROM call_participants WHERE call_id='" + call_id +
                  "' AND client_device_id='" + H_ID + "'").strip() == 'ringing':
                ringing = True
                break
        except RuntimeError:
            pass
        time.sleep(0.4)
    offer = [l.split(') ')[-1] for l in adb(GW, 'logcat', '-d', '-v', 'time').splitlines()
             if 'incoming_offered' in l]
    emit(tag + '_ringing=' + str(ringing) + ';offer=' + (offer[-1][:90] if offer else 'none'))
    xy = answer(H) if ringing else None
    emit(tag + '_answer_tap=' + str(xy))
    row = ''
    deadline = time.monotonic() + 22
    while time.monotonic() < deadline:
        try:
            row = db("SELECT c.state, p.state, COALESCE(mm.cellular_active,false) FROM call_sessions c "
                     "JOIN call_participants p USING(call_id) "
                     "LEFT JOIN rtc_media_sessions mm ON mm.call_id=c.call_id AND mm.client_device_id=p.client_device_id "
                     "WHERE c.call_id='" + call_id + "' AND p.client_device_id='" + H_ID + "'")
        except RuntimeError:
            row = ''
        if row.startswith('active|accepted|t'):
            break
        time.sleep(0.6)
    emit(tag + '_row=' + row)
    time.sleep(3)
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
    time.sleep(4)
    adb(H, 'shell', 'input', 'keyevent', '6')
    dismiss(H)
    return ringing and row.startswith('active|accepted|t')


emit('started_utc=' + now())
dismiss(H)
emit('nodes_before=' + ','.join('%s=%s' % (c[-1], db('SELECT count(*) FROM gateways', c))
                               for c in ('reception-r1-b', 'reception-r1-d', 'reception-r1-e')))
ok1 = one_call('d1')
emit('d1=' + ('PASS' if ok1 else 'FAIL'))

subprocess.run(['sudo', '-n', 'docker', 'stop', 'reception-r1-d', 'reception-r1-e'],
               capture_output=True, text=True)
time.sleep(8)
emit('stopped_d_e=' + now())
ok3 = one_call('d3')
emit('d3=' + ('PASS' if ok3 else 'FAIL'))

subprocess.run(['sudo', '-n', 'docker', 'start', 'reception-r1-d', 'reception-r1-e'],
               capture_output=True, text=True)
time.sleep(10)
emit('restarted_d_e=' + now())
emit('finished_utc=' + now())
if not (ok1 and ok3):
    raise RuntimeError('d1_or_d3_failed')
