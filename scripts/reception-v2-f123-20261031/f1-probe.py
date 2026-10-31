#!/usr/bin/env python3
"""Sample one incoming-offer cycle on the F1 pair: DB state + host Telecom, 1 Hz."""
import subprocess, time, re, sys

R1 = '/home/ubuntu/gsm-webrtc-validation/reception-v2-r1'
P = R1 + '/private/f123-fresh-20261031'
ADB = '/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb'
GW, HOST = 'emulator-5562', 'emulator-5564'
OWNER = open(P + '/owner.private').read().strip()
NUMBER = open(P + '/f1-number.private').read().strip()

SH = ("port=$(sed -n 's/^PG_PORT=//p' /run/gsm2sip/ports); "
      "export PGPASSWORD=\"$(cat /data/secrets/db_password)\"; "
      "exec psql -h 127.0.0.1 -p \"$port\" -U gsm2sip -d gsm2sip -XAt -F '|' -v ON_ERROR_STOP=1 -f -")

Q = ("SELECT c.state, COALESCE(c.reason,'-'), p.state, COALESCE(p.reason,'-'), "
     "t.dispatched, COALESCE(mm.cellular_active,false), "
     "(c.incoming_winner_client_device_id = p.client_device_id)::text, "
     "p.created_at::text, COALESCE(p.accepted_at::text,'-') "
     "FROM call_sessions c "
     "JOIN call_participants p USING(call_id) "
     "JOIN rtc_control_calls t USING(call_id) "
     "LEFT JOIN rtc_media_sessions mm ON mm.call_id=c.call_id AND mm.client_device_id=p.client_device_id "
     "WHERE c.owner_id='" + OWNER + "' ORDER BY c.created_at DESC LIMIT 1")


def db():
    r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', 'reception-r1-b', 'sh', '-c', SH],
                       input=Q + ';\n', capture_output=True, text=True, timeout=10)
    return (r.stdout or r.stderr).strip()


def adb(serial, *args, timeout=10):
    r = subprocess.run([ADB, '-s', serial, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
time.sleep(4)
print('modem', re.search(r'mCallState=(\d+)', adb(GW, 'shell', 'dumpsys', 'telephony.registry')).group(1))
print('ack', adb(GW, 'emu', 'gsm', 'call', NUMBER))
for i in range(int(sys.argv[1]) if len(sys.argv) > 1 else 20):
    telecom = adb(HOST, 'shell', 'dumpsys', 'telecom')
    ringing = 'RINGING' in telecom
    print('t=%02d ringing=%s | %s' % (i, ringing, db()))
    time.sleep(1)
adb(GW, 'emu', 'gsm', 'cancel', NUMBER)
