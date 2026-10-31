#!/usr/bin/env python3
"""Copy retest evidence from the lab and write sanitised summaries into the repo.

Drops emulator addresses, ports, tunnel URLs, phone numbers and pairing codes; keeps
states, revisions, timings and log lines that carry the assertions.
"""
import pathlib
import re
import subprocess

LAB = 'gsm-lab'
SRC = '/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/retest-20261031'
DEST = pathlib.Path('/opt/data/cache/scratch/client-r1/docs/reception-v2-emulator-evidence-20261031')
FILES = [
    'd3-1-summary.txt', 'd3-2-summary.txt',
    'c1-a-summary.txt', 'c1-b-summary.txt', 'c1-c-summary.txt', 'c1-perm-summary.txt',
    'b1-summary.txt', 'b4-summary.txt', 'c3-summary.txt', 'a1-summary.txt',
]

RULES = [
    (re.compile(r'https?://[a-z0-9.-]+\.trycloudflare\.com\S*'), '<relay-url>'),
    (re.compile(r'10\.0\.2\.2:\d+'), '<node-url>'),
    (re.compile(r'http://<node-url>\S*'), '<node-url>'),
    (re.compile(r'10\.0\.2\.2'), '<node-host>'),
    (re.compile(r'emulator-55\d\d'), '<device>'),
    (re.compile(r'\+1?\d{6,15}\b'), '<number>'),
    (re.compile(r'pairing_code=\S+'), 'pairing_code=<code>'),
    (re.compile(r'(token|secret|password|apikey|api_key)=\S+', re.I), r'\1=<redacted>'),
    (re.compile(r'[A-Za-z0-9_-]{40,}'), '<redacted>'),
    (re.compile(r'/home/ubuntu/\S+|~/gsm-webrtc-validation/\S+'), '<lab-path>'),
]


def clean(text):
    for pattern, repl in RULES:
        text = pattern.sub(repl, text)
    return text


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    missing = []
    for name in FILES:
        r = subprocess.run(['ssh', LAB, 'cat %s/%s 2>/dev/null' % (SRC, name)], capture_output=True, text=True, timeout=60)
        body = r.stdout.strip()
        if not body:
            missing.append(name)
            continue
        (DEST / name).write_text(clean(body) + '\n')
        print('wrote', name, len(body))
    if missing:
        print('missing:', ', '.join(missing))


if __name__ == '__main__':
    main()
