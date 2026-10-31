#!/usr/bin/env python3
"""Clone the reception node B container into a new node (default: d, port 18113).

Copies image, env and bind mount; gives the clone its own network/subnet and host
port. Never prints env values.
"""
import json, subprocess, sys

src = 'reception-r1-b'
name = sys.argv[1] if len(sys.argv) > 1 else 'reception-r1-d'
port = sys.argv[2] if len(sys.argv) > 2 else '18113'
subnet = sys.argv[3] if len(sys.argv) > 3 else '172.30.103.0/29'
data = '/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-' + name.rsplit('-', 1)[-1]


def run(*args, check=True):
    r = subprocess.run(['sudo', '-n', 'docker', *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise SystemExit('docker %s failed: %s' % (args[0], (r.stderr or r.stdout)[:400]))
    return (r.stdout or '').strip()


info = json.loads(run('inspect', src))[0]
image = info['Config']['Image']
envs = info['Config']['Env']
entrypoint = info['Config']['Entrypoint'] or []
mount_src = info['Mounts'][0]['Source'].rsplit('/', 1)[0]

run('network', 'create', '--subnet', subnet, name, check=False)
run('rm', '-f', name, check=False)
args = ['create', '--name', name,
        '--network', name,
        '--restart', 'unless-stopped',
        '-p', '127.0.0.1:%s:8080' % port,
        '-v', '%s/data-%s:/data' % (mount_src, name.rsplit('-', 1)[-1])]
for e in envs:
    args += ['-e', e]
if entrypoint:
    args += ['--entrypoint', entrypoint[0]]
args.append(image)
print(run(*args)[:120])
print(run('start', name)[:120])
