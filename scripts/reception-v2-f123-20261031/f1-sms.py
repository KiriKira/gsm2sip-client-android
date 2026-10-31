#!/usr/bin/env python3
"""F1 SMS: one inbound SMS to the F1 gateway must reach the Host exactly once."""
import datetime, json, re, subprocess, time, xml.etree.ElementTree as ET

BASE = '/home/ubuntu/gsm-webrtc-validation'
R1 = BASE + '/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031/f1-sms'
ADB = BASE + '/android-sdk/platform-tools/adb'
GW, HOST = 'emulator-5562', 'emulator-5564'
HP = 'com.callagent.host'
SENDER = open(R1 + '/private/f123-fresh-20261031/f1-number.private').read().strip()
OWNER = open(R1 + '/private/f123-fresh-20261031/owner.private').read().strip()
TEXT = 'F1SMS-%s' % datetime.datetime.now(datetime.timezone.utc).strftime('%H%M%S')
lines = []


def emit(s):
    lines.append(s)
    print(s, flush=True)
    open(P + '/f1-sms-summary.txt', 'w').write('\n'.join(lines) + '\n')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')


def adb(serial, *args, timeout=25):
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


def db(q, timeout=10):
    sh = ('port=$(sed -n \'s/^PG_PORT=//p\' /run/gsm2sip/ports); '
          'export PGPASSWORD="$(cat /data/secrets/db_password)"; '
          'exec psql -h 127.0.0.1 -p "$port" -U gsm2sip -d gsm2sip -XAt -F "|" -v ON_ERROR_STOP=1 -f -')
    r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', 'reception-r1-b', 'sh', '-c', sh],
                       input=q + ';\n', capture_output=True, text=True, timeout=timeout)
    return (r.stdout or r.stderr).strip()


def ui_text():
    adb(HOST, 'shell', 'uiautomator', 'dump', '--compressed', '/sdcard/f1sms.xml')
    raw = adb(HOST, 'exec-out', 'cat', '/sdcard/f1sms.xml', timeout=15)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return ''
    return ' '.join((n.attrib.get('text') or '') for n in root.iter('node'))


# Make sure the Host app is foregrounded on its 短信 tab: a leftover dialer call
# window swallowed the taps in the first attempt and looked like a missing SMS.
adb(HOST, 'shell', 'input', 'keyevent', '6')
adb(HOST, 'shell', 'am', 'force-stop', 'com.google.android.dialer')
adb(HOST, 'shell', 'am', 'force-stop', 'com.callagent.host')
adb(HOST, 'shell', 'input', 'keyevent', '224')
adb(HOST, 'shell', 'input', 'keyevent', '82')
time.sleep(1)
adb(HOST, 'shell', 'monkey', '-p', 'com.callagent.host', '-c', 'android.intent.category.LAUNCHER', '1')
time.sleep(9)
adb(HOST, 'shell', 'input', 'tap', '240', '735')
time.sleep(3)

emit('started_utc=' + now())
emit('text=' + TEXT)
emit('owner_hash=' + OWNER[:8])
ack = adb(GW, 'emu', 'sms', 'send', SENDER, TEXT)
emit('sms_ack=' + ack.replace('\n', ' '))
if 'OK' not in ack:
    raise RuntimeError('sms_ack_missing')

count_sql = ("SELECT count(*) FROM messages m WHERE m.owner_id='" + OWNER +
             "' AND m.body = '" + TEXT + "'")
seen_ui = False
db_count = 0
deadline = time.monotonic() + 60
while time.monotonic() < deadline:
    db_count = int(db(count_sql) or 0)
    adb(HOST, 'shell', 'input', 'tap', '240', '735')  # 短信 tab
    time.sleep(1.2)
    body = ui_text()
    seen_ui = TEXT in body
    if db_count >= 1 and seen_ui:
        break
    time.sleep(2)

emit('db_message_rows=' + str(db_count))
emit('host_ui_shows_text=' + str(seen_ui))
time.sleep(4)
repeat = int(db(count_sql) or 0)
emit('db_rows_after_settle=' + str(repeat))
if db_count != 1 or repeat != 1:
    raise RuntimeError('sms_row_count_not_one:first=%d,settled=%d' % (db_count, repeat))
if not seen_ui:
    raise RuntimeError('host_ui_missing_sms')
emit('f1_sms=PASS;utc=' + now())
emit('finished_utc=' + now())
