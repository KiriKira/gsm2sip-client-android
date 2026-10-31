#!/usr/bin/env python3
"""Run the D1 real GSM -> SoulGem -> FCM -> normal Madoka UI call path.

This deliberately avoids HostJointInstrumentation's idle-signaling readiness
gate. It uses the already paired test owner and only taps the visible answer
and hangup controls in the production UI. Raw logs and the summary stay in the
remote private evidence directory.
"""

import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

ADB = os.environ.get("ADB", "/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb")
GW = os.environ.get("GW_SERIAL", "emulator-5554")
HOST = os.environ.get("HOST_SERIAL", "127.0.0.1:15559")
NUMBER = os.environ.get("TEST_NUMBER", "+15550100200")
PRIVATE = Path(os.environ.get(
    "PRIVATE_DIR",
    "/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/d1-isolated-20261031",
))
HOST_PKG = "com.callagent.host"
GW_PKG = "com.callagent.gateway"


def now_utc():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def adb(serial, *args, timeout=12):
    return subprocess.run(
        [ADB, "-s", serial, *args], stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, timeout=timeout, check=True,
    ).stdout.decode(errors="replace").strip()


def db_query(node, sql):
    proc = subprocess.run(
        ["sudo", "-n", "docker", "exec", "-i", f"reception-r1-{node}",
         "sh", "-c", "PGPASSWORD=\"$(cat /data/secrets/db_password)\" "
         "psql -h 127.0.0.1 -p 55432 -U gsm2sip -At -F '|' -d gsm2sip -f -"],
        input=(sql.rstrip(";\n") + ";\n").encode(),
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if proc.returncode:
        raise RuntimeError(f"read-only node {node.upper()} query failed")
    return proc.stdout.decode().strip()


def owner(node):
    value = (PRIVATE / f"owner-{node}.txt").read_text().strip()
    if not re.fullmatch(r"[0-9a-fA-F-]{36}", value):
        raise RuntimeError("saved isolated owner identity is malformed")
    return value


def topology():
    result = {}
    for node in "abc":
        own = owner(node)
        sql = (
            "SELECT "
            f"(SELECT COUNT(*) FROM devices d LEFT JOIN device_reception r ON r.device_id=d.id "
            "LEFT JOIN rtc_control_capabilities c ON c.device_id=d.id "
            f"WHERE d.owner_id='{own}'::uuid AND d.role='client' AND d.state='active' "
            "AND CASE WHEN r.protocol_version=2 THEN r.incoming_enabled ELSE COALESCE(c.enabled,false) END),"
            f"(SELECT COUNT(*) FROM devices d JOIN push_endpoints p ON p.device_id=d.id "
            f"WHERE d.owner_id='{own}'::uuid AND d.role='client' AND d.state='active' "
            "AND p.failures=0 AND p.call_failures=0 AND p.next_attempt_at<=now()),"
            f"(SELECT COUNT(*) FROM call_sessions WHERE owner_id='{own}'::uuid "
            "AND state NOT IN ('ended','unknown'))"
        )
        fields = db_query(node, sql).split("|")
        if len(fields) != 3:
            raise RuntimeError(f"node {node.upper()} preflight returned an invalid row")
        result[node] = tuple(int(field) for field in fields)
    if result != {"a": (0, 0, 0), "b": (1, 1, 0), "c": (0, 0, 0)}:
        raise RuntimeError("the D1 owner topology or healthy real FCM endpoint changed")
    return result


def modem_state(serial):
    output = adb(serial, "shell", "dumpsys", "telephony.registry")
    match = re.search(r"mCallState=(\d+)", output)
    return int(match.group(1)) if match else -1


def xml_dump(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/d1-ui-window.xml", timeout=20)
    raw = adb(serial, "shell", "cat", "/sdcard/d1-ui-window.xml", timeout=8)
    return ET.fromstring(raw)


def checked_incoming_toggle():
    root = xml_dump(HOST)
    for node in root.iter("node"):
        if node.attrib.get("content-desc") == "后台来电接收":
            return node.attrib.get("checked") == "true"
    raise RuntimeError("normal Host settings page did not expose the incoming-reception switch")


def tap_label(serial, label):
    root = xml_dump(serial)
    for node in root.iter("node"):
        if node.attrib.get("text") == label or node.attrib.get("content-desc") == label:
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if bounds and node.attrib.get("enabled", "true") == "true":
                left, top, right, bottom = map(int, bounds.groups())
                x, y = (left + right) // 2, (top + bottom) // 2
                adb(serial, "shell", "input", "tap", str(x), str(y), timeout=8)
                return x, y
    return None


def new_call_snapshot(start_epoch):
    own = owner("b")
    sql = (
        "SELECT c.call_id::text,c.state,COALESCE(c.reason,''),"
        "COALESCE(ROUND(EXTRACT(EPOCH FROM (c.expires_at-now()))::numeric,1),-1),"
        "COALESCE(BOOL_OR(m.cellular_active),false),"
        "COALESCE(BOOL_OR(p.state='accepted'),false),"
        "COALESCE(STRING_AGG(COALESCE(p.reason,''),','),'') "
        "FROM call_sessions c LEFT JOIN rtc_media_sessions m ON m.call_id=c.call_id "
        "LEFT JOIN call_participants p ON p.call_id=c.call_id "
        f"WHERE c.owner_id='{own}'::uuid AND c.direction='incoming' "
        f"AND c.created_at>=to_timestamp({start_epoch:.3f}) "
        "GROUP BY c.call_id ORDER BY c.created_at DESC LIMIT 1"
    )
    row = db_query("b", sql)
    if not row:
        return None
    fields = row.split("|", 6)
    if len(fields) != 7:
        raise RuntimeError("new call SQL snapshot has an invalid shape")
    return {
        "call_id": fields[0],
        "state": fields[1],
        "reason": fields[2],
        "remaining_s": float(fields[3]),
        "cellular_active": fields[4] == "t",
        "accepted": fields[5] == "t",
        "participant_reasons": fields[6],
    }


def start_logcat(serial, filename):
    stream = (PRIVATE / filename).open("wb")
    os.chmod(stream.name, 0o600)
    proc = subprocess.Popen(
        [ADB, "-s", serial, "logcat", "-v", "epoch", "-T", "1", "-s",
         "GsmCallManager:I", "GatewayService:I", "GsmRtc:I", "CallOrchestrator:I",
         "HostCall:I", "HostPush:I", "HostCallService:I", "Telecom:I", "*:S"],
        stdout=stream, stderr=subprocess.DEVNULL,
    )
    return proc, stream


def stop_logcat(proc_and_stream):
    proc, stream = proc_and_stream
    proc.terminate()
    try:
        proc.wait(timeout=4)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=4)
    stream.close()


def host_call_ui_active():
    root = xml_dump(HOST)
    return any(node.attrib.get("text") == "通话中" for node in root.iter("node"))


def write_summary(lines):
    target = PRIVATE / "d1-ui-call-summary.txt"
    target.write_text("D1 normal-UI real-call attempt\n" + "\n".join(lines) + "\n")
    os.chmod(target, 0o600)


def main():
    PRIVATE.mkdir(parents=True, exist_ok=True)
    os.chmod(PRIVATE, 0o700)
    logs = []
    lines = [f"start_utc={now_utc()}"]
    result = "NOT_RUN"
    injected = False
    call_id = None
    try:
        if modem_state(GW) != 0 or modem_state(HOST) != 0:
            raise RuntimeError("a modem was not idle before D1")
        if not checked_incoming_toggle():
            raise RuntimeError("normal Host incoming-reception switch is off")
        counts = topology()
        lines.append("owner_topology=A0,B1,C0")
        lines.append("healthy_real_fcm_endpoint=B1")
        lines.append("active_calls_before=0")
        lines.append("host_incoming_switch=enabled")
        lines.append("host_push_mode=real_FCM_expected")
        logs = [start_logcat(GW, "d1-ui-call-gw-raw.log"),
                start_logcat(HOST, "d1-ui-call-host-raw.log")]

        # Background the already-open Host app; this preserves the normal
        # preference and lets the real FCM wake path bring up its call UI.
        adb(HOST, "shell", "input", "keyevent", "3", timeout=8)
        time.sleep(1)
        start_epoch = time.time()
        lines.append("gsm_injection_utc=" + now_utc())
        injection = adb(GW, "emu", "gsm", "call", NUMBER, timeout=8)
        if not re.search(r"\bOK\b", injection):
            result = "FAIL_GSM_INJECTION_NOT_ACCEPTED"
            raise RuntimeError(result)
        injected = True

        ring_seen_at = None
        answer_tap = None
        ring_ui_deadline = time.monotonic() + 30
        while time.monotonic() < ring_ui_deadline:
            snapshot = new_call_snapshot(start_epoch)
            if snapshot:
                call_id = snapshot["call_id"]
                if snapshot["remaining_s"] >= 0:
                    lines.append(f"server_window_remaining_at_first_snapshot_s={snapshot['remaining_s']:.1f}")
            try:
                tapped = tap_label(HOST, "接听")
            except subprocess.SubprocessError:
                tapped = None
            if tapped:
                answer_tap = tapped
                ring_seen_at = time.monotonic()
                lines.append("normal_ui_answer_tap_utc=" + now_utc())
                lines.append(f"answer_button_center={tapped[0]},{tapped[1]}")
                break
            if snapshot and snapshot["state"] in ("ended", "unknown"):
                result = "FAIL_CALL_ENDED_BEFORE_VISIBLE_ANSWER"
                break
            time.sleep(0.25)
        if answer_tap is None:
            if result == "NOT_RUN":
                result = "FAIL_NO_VISIBLE_ANSWER_WITHIN_30S"
            raise RuntimeError(result)

        if call_id is None:
            snapshot = new_call_snapshot(start_epoch)
            call_id = snapshot["call_id"] if snapshot else None
        if call_id is None:
            result = "FAIL_SERVER_SESSION_NOT_FOUND"
            raise RuntimeError(result)

        # Confirm the call becomes active in the Host Telecom UI, Homura modem,
        # and production server session before hanging up through the UI.
        active_samples = 0
        deadline = time.monotonic() + 20
        last_snapshot = None
        while time.monotonic() < deadline:
            last_snapshot = new_call_snapshot(start_epoch)
            host_telecom = host_call_ui_active()
            gw_state = modem_state(GW)
            if (last_snapshot and last_snapshot["state"] == "active"
                    and last_snapshot["cellular_active"] and last_snapshot["accepted"]
                    and host_telecom and gw_state == 2):
                active_samples += 1
            else:
                active_samples = 0
            if active_samples >= 2:
                lines.append("full_active_utc=" + now_utc())
                lines.append("full_active_evidence=server_active,cellular_active,Host_Telecom_ACTIVE,Gateway_modem_OFFHOOK")
                break
            if last_snapshot and last_snapshot["state"] in ("ended", "unknown"):
                result = "FAIL_CALL_TERMINATED_BEFORE_FULL_ACTIVE"
                break
            time.sleep(0.4)
        if active_samples < 2:
            if result == "NOT_RUN":
                result = "FAIL_FULL_ACTIVE_NOT_CONFIRMED"
            raise RuntimeError(result)

        hangup_tap = tap_label(HOST, "挂断")
        if not hangup_tap:
            result = "FAIL_NORMAL_UI_HANGUP_BUTTON_NOT_FOUND"
            raise RuntimeError(result)
        lines.append("normal_ui_hangup_tap_utc=" + now_utc())

        cleanup_deadline = time.monotonic() + 12
        final = None
        while time.monotonic() < cleanup_deadline:
            final = new_call_snapshot(start_epoch)
            if final and final["state"] == "ended" and modem_state(GW) == 0:
                result = "PASS"
                lines.append("normal_cleanup_utc=" + now_utc())
                lines.append("final_server_state=ended")
                lines.append("final_participant_reasons=" + (final["participant_reasons"] or "none"))
                break
            time.sleep(0.4)
        if result != "PASS":
            result = "FAIL_NORMAL_CLEANUP_NOT_CONFIRMED"
            raise RuntimeError(result)
    except Exception as exc:
        lines.append(f"exception={type(exc).__name__}:{str(exc)[:160]}")
    finally:
        if injected and result != "PASS" and modem_state(GW) != 0:
            try:
                cleanup = adb(GW, "emu", "gsm", "cancel", NUMBER, timeout=8)
                lines.append("failure_cleanup=caller_cancel_issued")
                lines.append("failure_cleanup_result=" + ("OK" if re.search(r"\bOK\b", cleanup) else "not_confirmed"))
            except Exception:
                lines.append("failure_cleanup=caller_cancel_failed")
        if logs:
            for log in logs:
                stop_logcat(log)
        lines.append("result=" + result)
        lines.append("finish_utc=" + now_utc())
        write_summary(lines)
        print("\n".join(lines))
    if result != "PASS":
        sys_exit = 1
    else:
        sys_exit = 0
    raise SystemExit(sys_exit)


if __name__ == "__main__":
    main()
