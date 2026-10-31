#!/usr/bin/env python3
"""Run read-only SQL on reception-r1-b: labq.py < file.sql"""
import subprocess, sys
SH = ("port=$(sed -n 's/^PG_PORT=//p' /run/gsm2sip/ports); "
      "export PGPASSWORD=\"$(cat /data/secrets/db_password)\"; "
      "exec psql -h 127.0.0.1 -p \"$port\" -U gsm2sip -d gsm2sip -XAt -F '|' -v ON_ERROR_STOP=1 -f -")
q = sys.stdin.read()
r = subprocess.run(['sudo', '-n', 'docker', 'exec', '-i', 'reception-r1-b', 'sh', '-c', SH],
                   input=q, capture_output=True, text=True, timeout=20)
print((r.stdout or r.stderr).strip())
