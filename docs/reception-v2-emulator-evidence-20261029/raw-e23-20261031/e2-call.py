#!/usr/bin/env python3
import subprocess,pathlib,os,time,json,re,base64,datetime,xml.etree.ElementTree as ET,hashlib
BASE=pathlib.Path('/home/ubuntu/gsm-webrtc-validation');P=BASE/'reception-v2-r1/private/e23-20261031';ADB=str(BASE/'android-sdk/platform-tools/adb');GW='emulator-5554';H='emulator-5560';GP='com.callagent.gateway';HP='com.callagent.host';NUMBER='+15550100200';lines=[];gwproc=None;injected=False;fixture=False
OWNER=(BASE/'reception-v2-r1/private/d1-isolated-20261031/owner-b.txt').read_text().strip()
def emit(s):lines.append(s);print(s,flush=True);(P/'e2-summary.txt').write_text('\n'.join(lines)+'\n')
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')
def a(serial,*args,timeout=10):
 r=subprocess.run([ADB,'-s',serial,*args],capture_output=True,text=True,timeout=timeout)
 if r.returncode:raise RuntimeError('adb_command_failed:'+str(args[:2]))
 return r.stdout.strip()
def db(q,node='b',timeout=6):
 sh='port=$(sed -n \'s/^PG_PORT=//p\' /run/gsm2sip/ports); export PGPASSWORD="$(cat /data/secrets/db_password)"; exec psql -h 127.0.0.1 -p "$port" -U gsm2sip -d gsm2sip -XAt -F "|" -v ON_ERROR_STOP=1 -f -'
 r=subprocess.run(['sudo','-n','docker','exec','-i','reception-r1-'+node,'sh','-c',sh],input=q+';\n',capture_output=True,text=True,timeout=timeout)
 if r.returncode:raise RuntimeError('readonly_db_query_failed:'+node)
 return r.stdout.strip()
def af(name):return json.loads(a(GW,'shell','run-as',GP,'cat','files/'+name,timeout=4))
def put(name,obj):
 data=base64.b64encode((json.dumps(obj,separators=(',',':'))+'\n').encode()).decode();a(GW,'shell',"run-as "+GP+" sh -c 'printf %s "+data+" | base64 -d > files/"+name+".tmp && mv files/"+name+".tmp files/"+name+"'")
def command(op):
 ident='e2-'+op+'-'+os.urandom(5).hex();put('joint-command.json',{'id':ident,'op':op})
 for _ in range(15):
  try:
   v=af('joint-result.json')
   if v.get('id')==ident:return v
  except Exception:pass
  time.sleep(.15)
 return None
def modem():
 m=re.search(r'mCallState=(\d+)',a(GW,'shell','dumpsys','telephony.registry',timeout=4));return int(m[1]) if m else -1
def focus():
 raw=a(H,'shell','dumpsys','window','windows',timeout=3)
 m=re.search(r'mCurrentFocus=Window\{[^\n]*? (com\.[^\s}]+)',raw)
 if m and m[1].startswith(HP):return m[1]
 acts=a(H,'shell','dumpsys','activity','activities',timeout=3)
 if re.search(r'(top)?ResumedActivity[^\n]*CallActionActivity',acts):return HP+'/.calls.CallActionActivity'
 return m[1] if m else ''
def snap(since,client):
 raw=db("SELECT c.call_id::text,c.state,COALESCE(c.reason,''),p.state,COALESCE(p.reason,''),t.dispatched,COALESCE(m.cellular_active,false),c.incoming_winner_client_device_id=p.client_device_id FROM call_sessions c JOIN call_participants p USING(call_id) JOIN rtc_control_calls t USING(call_id) LEFT JOIN rtc_media_sessions m ON m.call_id=c.call_id AND m.client_device_id=p.client_device_id WHERE c.owner_id='"+OWNER+"' AND c.created_at>=to_timestamp("+str(since)+") AND p.client_device_id='"+client+"' ORDER BY c.created_at DESC LIMIT 1",timeout=2)
 return raw.split('|') if raw else None
try:
 emit('started_utc='+now())
 for n in 'abc':
  if db("SELECT count(*) FROM call_sessions WHERE state NOT IN ('ended','unknown')",n)!='0':raise RuntimeError('nonended_call_preflight:'+n)
 client=db("SELECT id::text FROM devices WHERE owner_id='"+OWNER+"' AND role='client' AND created_at>='2026-10-31T04:00:00Z' ORDER BY created_at DESC LIMIT 1")
 if not re.fullmatch(r'[a-fA-F0-9-]{36}',client):raise RuntimeError('new_E_Host_identity_missing')
 (P/'h3-client-id.private').write_text(client+'\n');emit('h3_identity_hash='+hashlib.sha256(client.encode()).hexdigest()[:12])
 view=db("SELECT r.protocol_version,r.incoming_enabled,r.sms_enabled,r.revision,(SELECT count(*) FROM push_endpoints e WHERE e.device_id=d.id) FROM devices d JOIN device_reception r ON r.device_id=d.id WHERE d.id='"+client+"'")
 emit('h3_reception_version_incoming_sms_revision_endpoint_count='+view)
 if not view.startswith('2|t|t|') or not view.endswith('|0'):raise RuntimeError('stream_reception_preflight_failed')
 if modem()!=0:raise RuntimeError('modem_not_idle')
 original=af('joint-fixture.json');(P/'e2-gateway-fixture-before.json').write_text(json.dumps(original)+'\n')
 if original.get('pairing_code') or original.get('reconfirm_retained_emulator_sim') or original.get('reconfirm_operation_id'):raise RuntimeError('fixture_contains_identity_mutation')
 prepared=dict(original);prepared.update(reuse_pairing=True,preserve_old_sessions=True,clear_existing_mesh_nodes=False);put('joint-fixture.json',prepared);fixture=True
 for name in ['joint-ready.json','joint-result.json','joint-finished.json','joint-stage.txt','joint-command.json']:
  r=subprocess.run([ADB,'-s',GW,'shell','run-as',GP,'cat','files/'+name],capture_output=True,text=True,timeout=4)
  if r.returncode==0:(P/('e2-before-'+name)).write_text(r.stdout)
 a(GW,'shell',"run-as "+GP+" sh -c 'rm -f files/joint-ready.json files/joint-result.json files/joint-finished.json files/joint-stage.txt files/joint-command.json'")
 stream=(P/'e2-gateway-runner.raw.log').open('wb');gwproc=subprocess.Popen([ADB,'-s',GW,'shell','am','instrument','-w',GP+'.test/com.callagent.lab.GatewayJointInstrumentation'],stdout=stream,stderr=subprocess.STDOUT);(P/'e2-gateway-runner.pid').write_text(str(gwproc.pid)+'\n')
 deadline=time.monotonic()+50
 while time.monotonic()<deadline:
  try:
   if af('joint-ready.json').get('status')=='READY':break
  except Exception:pass
  if gwproc.poll() is not None:raise RuntimeError('gateway_runner_exited_before_READY')
  time.sleep(.2)
 else:raise RuntimeError('gateway_READY_timeout')
 emit('gateway_runner=READY;retained_pairing_SIM=true;synthetic_cellular_PCM=true')
 if modem()!=0:raise RuntimeError('modem_changed_before_idle')
 a(H,'shell','input','keyevent','223');emit('screen_off_idle_start_utc='+now());idle_start=time.monotonic()
 deadline=time.monotonic()+10;power=idle=''
 while time.monotonic()<deadline:
  power=a(H,'shell','dumpsys','power');idle=a(H,'shell','dumpsys','deviceidle')
  if 'mWakefulness=Asleep' in power and 'mForceIdle=false' in idle:break
  time.sleep(.4)
 (P/'e2-screenoff-power-before.txt').write_text(power);(P/'e2-deviceidle-before.txt').write_text(idle)
 if 'mWakefulness=Asleep' not in power or 'mForceIdle=false' not in idle:raise RuntimeError('screenoff_without_forceidle_preflight_failed_after_wait:wake='+('Asleep' if 'mWakefulness=Asleep' in power else 'not_Asleep')+';forceidle='+('false' if 'mForceIdle=false' in idle else 'other'))
 while time.monotonic()-idle_start<61:time.sleep(.5)
 emit('screen_off_idle_duration_s='+str(round(time.monotonic()-idle_start,2)))
 since=time.time();emit('gsm_injection_utc='+now());ack=a(GW,'emu','gsm','call',NUMBER,timeout=4);injected=True
 if 'OK' not in ack:raise RuntimeError('gsm_ack_missing')
 deadline=time.monotonic()+30;answer_xy=None;observations=[];woke=False
 while time.monotonic()<deadline:
  if not woke and 'mWakefulness=Asleep' in a(H,'shell','dumpsys','power',timeout=3):
   # A real user would press power to see the incoming call; the app is expected to
   # have requested the screen, so wake once and keep the wakefulness in the log.
   a(H,'shell','input','keyevent','224',timeout=3);woke=True
   emit('operator_wake_utc='+now())
  component=focus()
  if component==HP+'/'+HP+'.calls.CallActionActivity' or component==HP+'/.calls.CallActionActivity':
   emit('call_activity_visible_utc='+now());r=subprocess.run([ADB,'-s',H,'shell','uiautomator','dump','--compressed','/sdcard/e2-call.xml'],capture_output=True,text=True,timeout=20)
   raw=a(H,'exec-out','cat','/sdcard/e2-call.xml',timeout=6);(P/'e2-call-ui.xml').write_text(raw)
   root=ET.fromstring(raw);n=next((n for n in root.iter('node') if n.get('text')=='接听' or n.get('content-desc')=='接听'),None)
   if n is not None:
    v=list(map(int,re.findall(r'\d+',n.get('bounds'))));answer_xy=((v[0]+v[2])//2,(v[1]+v[3])//2);break
   raise RuntimeError('call_activity_visible_answer_button_missing')
  time.sleep(.12)
 if answer_xy is None:raise RuntimeError('normal_incoming_UI_not_observed')
 before=snap(since,client)
 if not before or before[3]!='ringing':raise RuntimeError('participant_not_ringing_before_answer')
 emit('normal_UI_answer_bounds='+str(answer_xy));emit('answer_tap_utc='+now());a(H,'shell','input','tap',str(answer_xy[0]),str(answer_xy[1]),timeout=2)
 deadline=time.monotonic()+16;last=None
 while time.monotonic()<deadline:
  last=snap(since,client)
  # rtc_control_calls.dispatched is an outgoing-only ledger (the 'dispatch' op requires
  # direction=outgoing); an answered incoming call legitimately keeps it false, so the
  # incoming assertion is: call active, participant accepted, cellular active, winner set.
  # snap() columns: [1]=call.state [3]=participant.state [5]=dispatched (outgoing-only)
  # [6]=cellular_active [7]=incoming_winner==this device
  if last and last[1]=='active' and last[3]=='accepted' and last[6]=='t' and last[7]=='t':break
  if last and last[1] in ['ended','unknown']:raise RuntimeError('call_ended_before_full_accept:'+str(last[1:]))
  time.sleep(.15)
 else:raise RuntimeError('full_accept_timeout:'+str(last[1:] if last else None))
 emit('call_state=active;participant=accepted;dispatched='+str(last[5])+';cellular_active='+str(last[6])+';H3_winner='+str(last[7])+';utc='+now())
 telecom=a(H,'shell','dumpsys','telecom',timeout=4);(P/'e2-host-telecom-active.txt').write_text(telecom)
 if 'ACTIVE' not in telecom:raise RuntimeError('host_Telecom_ACTIVE_not_found')
 emit('host_Telecom_ACTIVE=true;gateway_modem_state='+str(modem()));time.sleep(5)
 result=command('status');(P/'e2-gateway-active-status.json').write_text(json.dumps(result))
 emit('gateway_bridge='+str(result.get('bridge') if result else None)+';gateway_Telecom='+str(result.get('telecom_state') if result else None))
 emit('E2_call_assertion=PASS')
except Exception as e:
 emit('E2_call_assertion=NOT_PASSED;error_type='+type(e).__name__+';error='+str(e)[:400])
finally:
 if injected:
  try:emit('gsm_cancel='+a(GW,'emu','gsm','cancel',NUMBER,timeout=4).replace('\n',' '))
  except Exception as e:emit('cancel_error='+type(e).__name__)
 if gwproc is not None:
  try:
   command('stop');gwproc.wait(timeout=10);emit('gateway_runner_cleanup=finished')
  except Exception as e:emit('gateway_runner_cleanup_unconfirmed='+type(e).__name__)
 if fixture:
  try:put('joint-fixture.json',original);emit('gateway_fixture_restored=true')
  except Exception as e:emit('gateway_fixture_restore_error='+type(e).__name__)
 try:emit('gateway_modem_final='+str(modem()))
 except Exception:pass
 emit('finished_utc='+now())
