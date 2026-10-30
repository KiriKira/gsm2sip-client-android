import datetime, json, os, re, subprocess, sys, threading, time, uuid, calendar
ADB='/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb'
GW='emulator-5554'
DEV={'h1':'emulator-5556','h2':'127.0.0.1:15559'}
ORDER=sys.argv[1]
INDEX=sys.argv[2]
TARGET_GAP_NS=int(sys.argv[3]) if len(sys.argv)>3 else 500_000_000

def run(args, data=None, timeout=25, check=True):
    return subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=timeout, check=check).stdout.decode(errors='replace').strip()
def adb(serial, *args, data=None, timeout=25, check=True):
    return run([ADB,'-s',serial,*args],data,timeout,check)
def shell(serial, cmd, timeout=25):
    return adb(serial,'shell',cmd,timeout=timeout)
def internal(serial,name):
    return shell(serial,f'run-as com.callagent.host cat files/{name}')
def put(serial,obj):
    body=(json.dumps(obj,separators=(',',':'))+'\n').encode()
    adb(serial,'shell',"run-as com.callagent.host sh -c 'umask 077; cat > files/joint-command.json.tmp && mv files/joint-command.json.tmp files/joint-command.json'",data=body,timeout=12)
def result(serial,ident,timeout=15):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        try:
            d=json.loads(internal(serial,'joint-result.json'))
            if d.get('id')==ident: return d
        except Exception: pass
        time.sleep(.05)
    raise RuntimeError('runner_response_timeout')
def request(serial,op,ident):
    put(serial,{'id':ident,'op':op})
    return result(serial,ident)
def status(label):
    return request(DEV[label],'status',f'c2-{INDEX}-{label}-status-{uuid.uuid4().hex[:10]}')
def sql(query):
    cmd=['sudo','docker','exec','-i','reception-r1-a','sh','-c',
         'PGPASSWORD="$(cat /data/secrets/db_password)" psql -h 127.0.0.1 -p 55432 -U gsm2sip -At -F "|" -d gsm2sip']
    return run(cmd,(query+'\n').encode(),timeout=15).splitlines()
def latest_session():
    rows=sql("SELECT call_id::text,state,coalesce(reason,''),incoming_winner_client_device_id IS NOT NULL,to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI:SS.MS') FROM call_sessions ORDER BY created_at DESC LIMIT 1;")
    if not rows or not rows[0]: return None
    c=rows[0].split('|')
    return {'id':c[0],'state':c[1],'reason':c[2],'winner':c[3]=='t','created':c[4]}
def participants(call_id):
    q=f"SELECT state,coalesce(reason,''),count(*) FROM call_participants WHERE call_id='{call_id}'::uuid GROUP BY state,reason ORDER BY state,reason;"
    return sql(q)
def server_events(call_id):
    raw=run(['sudo','docker','logs','--tail','2000','reception-r1-a'],timeout=20,check=False)
    out=[]
    for line in raw.splitlines():
        if call_id not in line: continue
        try: d=json.loads(line)
        except Exception: continue
        if d.get('msg')=='RTC control committed':
            out.append(f"{d.get('time','')} {d.get('event','')} revision={d.get('revision','')}")
    return out
def modem_state():
    out=shell(GW,"dumpsys telephony.registry | grep -m1 'mCallState=' || true")
    m=re.search(r'mCallState=(\d+)',out)
    return int(m.group(1)) if m else None

def parse_mtime_value(s):
    m=re.fullmatch(r'(\d{4})-(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{1,9}) ([+-])(\d\d)(\d\d)',s)
    if not m: raise RuntimeError('mtime_parse_failed')
    y,mo,d,hh,mm,ss,frac,sign,oh,om=m.groups()
    off=(1 if sign=='+' else -1)*(int(oh)*3600+int(om)*60)
    sec=calendar.timegm((int(y),int(mo),int(d),int(hh),int(mm),int(ss)))-off
    return sec*1_000_000_000+int(frac.ljust(9,'0'))
def parse_mtime(serial,name):
    return parse_mtime_value(shell(serial,f'run-as com.callagent.host stat -c %y files/{name}'))
def guest_epoch_ns(serial): return int(float(shell(serial,'date +%s.%N'))*1_000_000_000)
def offset_bounds(serial,n=7):
    samples=[]
    for _ in range(n):
        a=time.time_ns(); g=guest_epoch_ns(serial); b=time.time_ns()
        samples.append((b-a,g-b,g-a))
    lo=max(x[1] for x in samples); hi=min(x[2] for x in samples)
    if lo>hi: raise RuntimeError(f'clock_offset_samples_disjoint_{serial}')
    best=min(samples,key=lambda x:x[0])
    print(f'clock_calibration_{serial}_rtt_ms={best[0]/1e6:.1f} intersection_width_ms={(hi-lo)/1e6:.1f}')
    return lo,hi
def prepare_persistent_command(serial,ident,op):
    body=(json.dumps({'id':ident,'op':op},separators=(',',':'))+'\n').encode()
    cmd="run-as com.callagent.host sh -c 'umask 077; cat > files/joint-command.pending.tmp && mv files/joint-command.pending.tmp files/joint-command.pending.json'"
    adb(serial,'shell',cmd,data=body,timeout=12)
    wait_cmd="run-as com.callagent.host sh -c 'IFS= read -r trigger; if [ \"$trigger\" = fire ]; then mv files/joint-command.pending.json files/joint-command.json; stat -c %y files/joint-command.json; fi'"
    proc=subprocess.Popen([ADB,'-s',serial,'shell',wait_cmd],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    time.sleep(.15)
    if proc.poll() is not None: raise RuntimeError(f'persistent_shell_not_ready_{serial}')
    return proc

def fire_persistent(proc):
    m0=time.monotonic_ns()
    proc.stdin.write(b'fire\n');proc.stdin.flush()
    stamp=proc.stdout.readline().decode(errors='replace').strip()
    m1=time.monotonic_ns()
    if not stamp: raise RuntimeError('persistent_shell_no_mtime')
    return {'command_mtime':parse_mtime_value(stamp),'host_mono_start':m0,'host_mono_ack':m1,'host_write_window_ms':(m1-m0)/1e6}
def short(s): return s[:8] if s else 'none'

# Ensure previous call state is terminal and clear it through the runner, not by editing app data.
for label,serial in DEV.items():
    d=status(label)
    if d.get('phase') in ('ACTIVE','DIALING','ANSWERING','INCOMING_RINGING'):
        raise RuntimeError(f'precondition_active_call_{label}')
    request(serial,'clear',f'c2-{INDEX}-{label}-clear-before-{uuid.uuid4().hex[:8]}')
    d=status(label)
    if d.get('phase') not in (None,'IDLE') or d.get('call_id') is not None or d.get('listening') is not True:
        raise RuntimeError(f'precondition_not_idle_{label}')
print(f'case=C2 trial={INDEX} order={ORDER} preflight=idle_listening')
logs={}; logfiles={}
for label,serial in DEV.items():
    path=f'/tmp/c2-{INDEX}-{label}.log'
    f=open(path,'w')
    p=subprocess.Popen([ADB,'-s',serial,'logcat','-v','epoch','-s','GsmRtc','HostCall','HostPush'],stdout=f,stderr=subprocess.DEVNULL)
    logs[label]=(p,f); logfiles[label]=path
start_ns=time.time_ns()
started=adb(GW,'emu','gsm','call','5550100',timeout=10)
print(f'gsm_call_command={started or "returned"} host_utc_ns={start_ns}')
if 'OK' not in started.upper(): raise RuntimeError('gsm_call_injection_not_ok')

# Gate on this fresh modem call reaching both app runtimes under the same call ID.
deadline=time.monotonic()+22
fresh=None; h1=None; h2=None; session=None
while time.monotonic()<deadline:
    h1=status('h1'); h2=status('h2')
    if (h1.get('status')=='OK' and h2.get('status')=='OK' and
        h1.get('listening') is True and h2.get('listening') is True and
        h1.get('phase')=='INCOMING_RINGING' and h2.get('phase')=='INCOMING_RINGING' and
        h1.get('call_id') and h1.get('call_id')==h2.get('call_id')):
        candidate=h1['call_id']
        session=latest_session()
        if session and session['id']==candidate:
            fresh=candidate
            break
    time.sleep(.10)
if not fresh:
    adb(GW,'emu','gsm','cancel','5550100',timeout=10,check=False)
    for p,f in logs.values(): p.terminate(); f.close()
    raise RuntimeError('C2 precondition failed: both runners did not report the same fresh INCOMING_RINGING call ID')
# Verify per-device fresh app ring evidence before dispatching either action.
for label in DEV:
    p,f=logs[label]; f.flush()
    data=open(logfiles[label],errors='replace').read()
    rings=[line for line in data.splitlines() if 'Incoming call ringing' in line]
    if not rings:
        adb(GW,'emu','gsm','cancel','5550100',timeout=10,check=False)
        raise RuntimeError(f'C2 precondition failed: {label} fresh HostCall ringing log absent')
    print(f'{label}_fresh_hostcall_ringing_epoch={rings[-1].split()[0]}')
print(f'fresh_call_id_short={short(fresh)} server_created_utc={session["created"]} server_state={session["state"]}')
print(f'h1_phase={h1.get("phase")} h2_phase={h2.get("phase")} same_call_id=yes')
off_pre={k:offset_bounds(s) for k,s in DEV.items()}
first,second=('h1','h2') if ORDER=='answer-first' else ('h2','h1')
first_op='answer' if first=='h1' else 'reject'
second_op='reject' if second=='h2' else 'answer'
ids={first:f'c2-{INDEX}-{first}-{uuid.uuid4().hex[:10]}',second:f'c2-{INDEX}-{second}-{uuid.uuid4().hex[:10]}' }
# Prepare payloads and persistent device-side shells before the scheduled instant.
procs={label:prepare_persistent_command(DEV[label],ids[label],op) for label,op in ((first,first_op),(second,second_op))}
base=time.monotonic_ns()+1_000_000_000
sched=[]; errs=[]
def schedule(label,op,target):
    while time.monotonic_ns()<target: time.sleep(.001)
    try: sched.append((label,op,fire_persistent(procs[label])))
    except Exception as e: errs.append((label,type(e).__name__))
t1=base;t2=base+TARGET_GAP_NS
threads=[threading.Thread(target=schedule,args=(first,first_op,t1)),threading.Thread(target=schedule,args=(second,second_op,t2))]
for t in threads:t.start()
for t in threads:t.join()
for p in procs.values():
    p.stdin.close()
    p.wait(timeout=5)
if errs: raise RuntimeError('action_dispatch_error_'+','.join(x[0] for x in errs))
actions={}
for label,op,d in sched:
    response=result(DEV[label],ids[label])
    d['result_mtime']=parse_mtime(DEV[label],'joint-result.json')
    d['reply_status']=response.get('status');d['reply_phase']=response.get('phase')
    actions[label]=(op,d)
    print(f'action_{label}={op} reply={response.get("status")} phase={response.get("phase")} trigger_window_ms={d["host_write_window_ms"]:.1f} handler_window_ms={(d["result_mtime"]-d["command_mtime"])/1e6:.1f}')
off_post={k:offset_bounds(s) for k,s in DEV.items()}
for k in DEV: off_pre[k]=(min(off_pre[k][0],off_post[k][0]),max(off_pre[k][1],off_post[k][1]))
first_data=actions[first][1];second_data=actions[second][1]
cmd_low=(second_data['host_mono_start']-first_data['host_mono_ack'])/1e9
cmd_high=(second_data['host_mono_ack']-first_data['host_mono_start'])/1e9
print(f'command_commit_host_monotonic_bounds_seconds=[{cmd_low:.3f},{cmd_high:.3f}]')
print(f'runner_poll_interval_ms=200 handler_start_bounds_seconds=[{cmd_low-0.200:.3f},{cmd_high+0.200:.3f}] target={TARGET_GAP_NS/1e9:.3f}')

# Observe actual convergence. Runner OK is not an outcome assertion.
state={}
end=time.monotonic()+8
while time.monotonic()<end:
    for label in DEV:
        try: state[label]=status(label).get('phase') or 'IDLE'
        except Exception: state[label]='UNKNOWN'
    if 'ACTIVE' in state.values() or set(state.values())=={'ENDED'}: break
    time.sleep(.2)
# If one host is active, make sure the other late reject did not hang it up.
time.sleep(3)
for label in DEV:
    try: state[label]=status(label).get('phase') or 'IDLE'
    except Exception: state[label]='UNKNOWN'
modem_before=modem_state()
server=latest_session();parts=participants(fresh);events=server_events(fresh)
print(f'final_phases={state} gateway_modem_call_state={modem_before}')
print(f'server_call_short={short(server["id"] if server else None)} state={server["state"] if server else "missing"} reason={server["reason"] if server else ""} winner={server["winner"] if server else False}')
print('server_participant_states='+json.dumps(parts,separators=(',',':')))
print('server_control_events='+json.dumps(events,separators=(',',':')))
if list(state.values()).count('ACTIVE')==1 and modem_before not in (None,0):
    winner=next(k for k,v in state.items() if v=='ACTIVE')
    print(f'outcome=CONNECTED_STABLE winner_host={winner}')
    d=request(DEV[winner],'hangup',f'c2-{INDEX}-{winner}-hangup-{uuid.uuid4().hex[:8]}')
    print(f'test_hangup_reply={d.get("status")}')
    time.sleep(1)
    state={k:(status(k).get('phase') or 'IDLE') for k in DEV}
    print(f'after_test_hangup_phases={state}')
    adb(GW,'emu','gsm','cancel','5550100',timeout=10,check=False)
    accepted=set(state.values()).issubset({'ENDED','IDLE',None})
elif set(state.values())=={'ENDED'} and modem_before==0 and server and server['id']==fresh and server['state']=='ended':
    print('outcome=GLOBAL_ENDED_BOTH_STOPPED')
    adb(GW,'emu','gsm','cancel','5550100',timeout=10,check=False)
    accepted=True
else:
    print('outcome=FAIL_INCONSISTENT_OR_UNRESOLVED')
    accepted=False
for label,(p,f) in logs.items():
    p.terminate(); f.flush(); f.close()
for label in DEV:
    data=open(logfiles[label],errors='replace').read()
    print(f'{label}_fresh_control_incoming={"control_incoming" in data} fresh_disconnect={"control_disconnected" in data}')
for label,serial in DEV.items():
    try: request(serial,'clear',f'c2-{INDEX}-{label}-clear-after-{uuid.uuid4().hex[:8]}')
    except Exception: pass
print('trial_result='+('PASS' if accepted else 'FAIL'))
if not accepted: sys.exit(2)
