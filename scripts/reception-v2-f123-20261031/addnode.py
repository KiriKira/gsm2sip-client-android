#!/usr/bin/env python3
"""Add an extra node to the gateway app via its own UI (no force-stop).

Usage: addnode.py <url> <code-file>
Keeps the app process alive so the retained SIM binding is not re-evaluated.
"""
import re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = '/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb'
GW = 'emulator-5562'
url = sys.argv[1]
code = open(sys.argv[2]).read().strip()


def adb(*args, timeout=30):
    r = subprocess.run([ADB, '-s', GW, *args], capture_output=True, text=True, timeout=timeout)
    return (r.stdout or '').strip()


def dump():
    adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/an.xml')
    raw = adb('exec-out', 'cat', '/sdcard/an.xml', timeout=20)
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None


def center(node):
    v = list(map(int, re.findall(r'\d+', node.attrib.get('bounds', ''))))
    return ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2) if len(v) == 4 else None


def tap(xy):
    adb('shell', 'input', 'tap', str(xy[0]), str(xy[1]))


def find(root, pred):
    if root is None:
        return None
    for n in root.iter('node'):
        if pred(n):
            return n
    return None


def open_settings():
    root = dump()
    tab = find(root, lambda n: n.attrib.get('content-desc') == '设置')
    if tab:
        tap(center(tab))
        time.sleep(3)
    return dump()


root = open_settings()
entry = find(root, lambda n: (n.attrib.get('text') or '') == '添加节点')
tries = 0
while entry is None and tries < 6:
    tries += 1
    adb('shell', 'input', 'swipe', '240', '600', '240', '300', '300')
    time.sleep(1.5)
    root = dump()
    entry = find(root, lambda n: (n.attrib.get('text') or '') == '添加节点')
print('entry_found=' + str(entry is not None))
if entry is None:
    raise SystemExit(1)
tap(center(entry))
time.sleep(3)

root = dump()
fields = [n for n in root.iter('node') if 'EditText' in (n.attrib.get('class') or '')]
print('fields=%d' % len(fields))
tap(center(fields[0]))
time.sleep(0.8)
adb('shell', 'input', 'text', url)
time.sleep(0.8)
adb('shell', 'input', 'keyevent', '4')
time.sleep(1.5)

root = dump()
fields = [n for n in root.iter('node') if 'EditText' in (n.attrib.get('class') or '')]
tap(center(fields[1]))
time.sleep(0.8)
adb('shell', 'input', 'text', code)
time.sleep(0.8)
adb('shell', 'input', 'keyevent', '4')
time.sleep(1.5)

root = dump()
add = find(root, lambda n: (n.attrib.get('text') or '') == '添加')
if add is None:
    raise SystemExit('add_button_missing')
tap(center(add))
time.sleep(14)
root = dump()
texts = [n.attrib.get('text') or '' for n in (root.iter('node') if root is not None else [])]
print('after: ' + ' | '.join(t for t in texts if t and ('节点' in t or '同步' in t or '181' in t or '10.0.2.2' in t))[:200])
