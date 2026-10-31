#!/usr/bin/env python3
import subprocess,sys,time,xml.etree.ElementTree as E,re,json,pathlib,os
adb='/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb';serial='emulator-5560'
private=pathlib.Path('/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/e3-retry-20261031')
def run(*args,timeout=16,check=True):
 p=subprocess.run([adb,'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if check and p.returncode:raise RuntimeError('adb_error:'+str(args[:2]))
 return p.stdout
def dump():
 run('shell','uiautomator','dump','--compressed','/sdcard/e23-diag.xml')
 return E.fromstring(run('exec-out','cat','/sdcard/e23-diag.xml'))
def tap(n):
 v=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
 if len(v)!=4:raise RuntimeError('bounds_invalid')
 run('shell','input','tap',str((v[0]+v[2])//2),str((v[1]+v[3])//2));time.sleep(.3)
def find(root,label):return next((n for n in root.iter('node') if label in [n.get('text'),n.get('content-desc'),n.get('resource-id')]),None)
run('shell','input','keyevent','224');run('shell','wm','dismiss-keyguard');run('shell','am','start','-n','com.callagent.host/.MainActivity');time.sleep(.5)
root=dump();n=find(root,'com.callagent.host:id/tab_settings')
if n is not None:tap(n)
for i in range(9):
 root=dump();n=find(root,'待机统计')
 if n is not None:break
 run('shell','input','swipe','240','650','240','250','300');time.sleep(.2)
if n is None:raise RuntimeError('stats_tile_missing')
tap(n);root=dump();n=find(root,'分享统计')
if n is None:raise RuntimeError('share_stats_missing')
run('shell','run-as','com.callagent.lab.capture','rm','-f','files/export.json',check=False);tap(n)
for i in range(3):
 root=dump();n=find(root,'R1 Diagnostics Capture')
 if n is not None:break
 run('shell','input','swipe','240','650','240','350','300');time.sleep(.2)
if n is None:
 (private/(sys.argv[1]+'-share-ui.xml')).write_bytes(E.tostring(root));raise RuntimeError('capture_target_missing')
tap(n)
for attempt in range(15):
 data=run('exec-out','run-as','com.callagent.lab.capture','cat','files/export.json')
 try:obj=json.loads(data);break
 except (ValueError,TypeError):time.sleep(.3)
else:raise RuntimeError('capture_export_not_ready')
out=private/(sys.argv[1]+'.json');out.write_bytes(data);os.chmod(out,0o600)
x=obj['segments'][-1];print(json.dumps({'file':out.name,'sampled_at':x['sampled_at'],'events':x.get('events'), 'locks':x.get('wake_locks'),'listening_requested':obj.get('listening_requested'),'wss_live':obj.get('wss_live'),'reception':obj.get('reception')},ensure_ascii=False))
