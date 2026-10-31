#!/usr/bin/env python3
"""F1 incoming call on the fresh F AVDs (main builds, v1/capability path).

The D1-derived driver requires the R1 reception switch and the D1 owner topology,
neither of which exists on the F1 fresh pairing, so this is a purpose-built
assertion: gateway offers -> Host shows CallActionActivity -> operator answers ->
call active with the Host as winner -> cancel -> modem idle.
"""
import datetime, json, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/f1-incoming'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, HOST = 'emulator-5562', 'emulator-5564'
GP, HP = 'com.callagent.gateway', 'com.callagent.host'
NUMBER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/f1-incoming-summary.txt', 'w').write('\n'.join(lines) + '\n')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')


def adb(serial, *args, timeout=20):
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    if r.returncode:
        raise RuntimeError('adb_failed:' + serial + ':' + str(args[:2]))
    return r.stdout.strip()


def adb_soft(serial, *args, timeout=30):
    """Like adb() but a non-zero exit (grep with no match) is not an error."""
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


def db(q, node='b', timeout=8):
    sh = ('port=$(sed -n \'s/^PG_PORT=//p\' /run/gsm2sip/ports); '
          'export PGPASSWORD="$(cat /data/secrets/db_password)"; '
          'exec psql -h 127.0.0.1 -p "$port" -U gsm2sip -d gsm2sip -XAt -F "|" -v ON_ERROR_STOP=1 -f -')
    r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', 'reception-r1-' + node, 'sh', '-c', sh],
                       input=q + ';\n', capture_output=True, text=True, timeout=timeout)
    if r.returncode:
        raise RuntimeError('db_failed:' + r.stderr.strip()[:120])
    return r.stdout.strip()


def ui_dump():
    adb(HOST, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/f1in.xml', timeout=25)
    raw = adb(HOST, 'exec-out', 'cat', '/sdcard/f1in.xml', timeout=10)
    return ET.fromstring(raw)


def focus():
    raw = adb(HOST, 'shell', 'dumpsys', 'window', 'windows', timeout=8)
    m = re.search(r'mCurrentFocus=Window\{[^\n]*? (com\.[^\s}]+)', raw)
    comp = m[1] if m else ''
    if comp.startswith(HP):
        return comp
    # With the screen off or the keyguard up, mCurrentFocus is the keyguard/launcher
    # while the call activity is still the top resumed activity.
    acts = adb(HOST, 'shell', 'dumpsys', 'activity', 'activities', timeout=8)
    if re.search(r'(top)?ResumedActivity[^\n]*CallActionActivity', acts):
        return HP + '/.calls.CallActionActivity'
    return comp


def modem():
    m = re.search(r'mCallState=(\d+)', adb(GW, 'shell', 'dumpsys', 'telephony.registry'))
    return int(m.group(1)) if m else -1


def snap(since):
    q = ("SELECT c.state,COALESCE(c.reason,''),p.state,COALESCE(p.reason,''),t.dispatched,"
         "COALESCE(mm.cellular_active,false),c.incoming_winner_client_device_id=p.client_device_id "
         "FROM call_sessions c JOIN call_participants p USING(call_id) "
         "JOIN rtc_control_calls t USING(call_id) "
         "LEFT JOIN rtc_media_sessions mm ON mm.call_id=c.call_id AND mm.client_device_id=p.client_device_id "
         "WHERE c.owner_id='" + OWNER + "' AND c.created_at>=to_timestamp(" + str(since) + ") "
         "ORDER BY c.created_at DESC LIMIT 1")
    raw = db(q)
    return raw.split('|') if raw else None


client = db("SELECT id::text FROM devices WHERE owner_id='" + OWNER + "' AND role='client' "
            "ORDER BY created_at DESC LIMIT 1")
emit('started_utc=' + now())
emit('owner_hash=' + OWNER[:8] + ';client_hash=' + client[:8])
if modem() != 0:
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER, timeout=10)
    for _ in range(12):
        if modem() == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError('modem_not_idle_before')

def answer_from_notification():
    """The main Host build rings via a call notification (actions Decline/Answer)."""
    adb(HOST, 'shell', 'cmd', 'statusbar', 'expand-notifications', timeout=10)
    for _ in range(10):
        root = ui_dump()
        for n in root.iter('node'):
            label = (n.attrib.get('content-desc') or '') + ' ' + (n.attrib.get('text') or '')
            if 'Answer' in label or '接听' in label:
                v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
                if len(v) == 4:
                    xy = ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2)
                    adb(HOST, 'shell', 'input', 'tap', str(xy[0]), str(xy[1]), timeout=10)
                    return xy
        time.sleep(0.8)
    return None


answer_xy = None
since = 0
attempts = 0
for attempt in range(3):
    attempts = attempt + 1
    adb(GW, 'emu', 'gsm', 'cancel', NUMBER, timeout=10)
    time.sleep(6)
    since = time.time()
    ack = adb(GW, 'emu', 'gsm', 'call', NUMBER, timeout=10)
    emit('attempt=' + str(attempts) + ';gsm_call_ack=' + ack.replace('\n', ' '))
    if 'OK' not in ack:
        raise RuntimeError('gsm_ack_missing')
    # The main (v1) Host rings through its own ConnectionService; the R1 branch's
    # CallActionActivity does not exist here, so wait for the server-side ringing
    # state and answer with the platform answer key.
    deadline = time.monotonic() + 24
    ringing = None
    while time.monotonic() < deadline:
        s = snap(since)
        if s and s[0] in ('ringing', 'connecting') and s[2] == 'ringing':
            ringing = s
            break
        if s and s[0] in ('ended', 'unknown'):
            break
        time.sleep(0.5)
    if ringing is None:
        emit('attempt=' + str(attempts) + ';no_ring_state')
        continue
    emit('ringing_state=' + '|'.join(ringing) + ';utc=' + now())
    # The R1 Host build raises CallActionActivity with an in-app answer button;
    # the main build only posts a call notification. Try the activity first.
    xy = None
    win = adb_soft(HOST, 'shell', 'dumpsys window windows')
    if 'CallActionActivity' in win:
        root = ui_dump()
        n = next((n for n in root.iter('node')
                  if n.attrib.get('text') == '接听' or n.attrib.get('content-desc') == '接听'), None)
        if n is not None:
            v = list(map(int, re.findall(r'\d+', n.attrib.get('bounds', ''))))
            xy = ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2)
            adb(HOST, 'shell', 'input', 'tap', str(xy[0]), str(xy[1]), timeout=10)
    if xy is None:
        xy = answer_from_notification()
    emit('answer_tap=' + str(xy) + ';utc=' + now())
    adb(HOST, 'shell', 'cmd', 'statusbar', 'collapse', timeout=10)
    last = None
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        last = snap(since)
        if last and last[0] == 'active' and last[2] == 'accepted' and last[5] == 't' and last[6] == 't':
            break
        if last and last[0] in ('ended', 'unknown'):
            raise RuntimeError('call_ended_before_accept:' + str(last))
        time.sleep(0.5)
    if last and last[0] == 'active' and last[2] == 'accepted':
        break
    emit('attempt=' + str(attempts) + ';accept_failed:' + str(last))
else:
    raise RuntimeError('normal_incoming_call_not_answered')
# The full telecom dump is ~800 lines and can exceed a short adb timeout, which
# used to fail this assertion on an actually-active call; grep on the device.
telecom = ''
for _ in range(12):
    telecom = adb_soft(HOST, 'shell', 'dumpsys telecom | grep -m1 state=ACTIVE')
    if 'state=ACTIVE' in telecom:
        break
    time.sleep(1)
if 'state=ACTIVE' not in telecom:
    raise RuntimeError('host_telecom_not_active')
emit('host_telecom_ACTIVE=true;gateway_modem=' + str(modem()) + ';utc=' + now())
time.sleep(6)
emit('f1_incoming=PASS;utc=' + now())
emit('cleanup_cancel=' + adb(GW, 'emu', 'gsm', 'cancel', NUMBER, timeout=10).replace('\n', ' '))
time.sleep(4)
emit('modem_final=' + str(modem()))
emit('finished_utc=' + now())
