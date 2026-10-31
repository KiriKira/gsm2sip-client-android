#!/usr/bin/env python3
"""Change only R1 Node B's TURN URLs for the bounded C3 media test."""

import copy
import argparse
import fcntl
import hashlib
import json
import os
import pathlib
import signal
import subprocess
import time
import uuid


PRIVATE = pathlib.Path(
    "/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/c3-b-turn-20261031"
)
CONTAINER = "reception-r1-b"
ORIGINAL = "reception-r1-b-c3-original-20261031"
REPLACED = "reception-r1-b-c3-private-turn-stopped-20261031"
IMAGE = "sha256:1f6fd76b942af40b751ede535ade03604de858ca4a40054406d4afb56fa5c1d4"
OLD_B_ID = "c925cdafaaa4280d79ca2d8440f0aee646102e19fc566aa261d59b558053408e"
OWNER_HASH = "59fe171b02"
INSTALL_HASH = "1887710c25"
SIM_HASH = "ea30615cb2"
H1_DEVICE_HASH = "e5c4f45b75"
H2_DEVICE_HASH = "5aaedd7904"
ADB = "/home/ubuntu/gsm-webrtc-validation/android-sdk/platform-tools/adb"
H2_SERIAL = "127.0.0.1:15559"
TURN_URLS = (
    "turn:172.31.15.170:13478?transport=tcp,"
    "turn:127.0.0.1:13479?transport=tcp"
)


def output(args):
    return subprocess.check_output(args, text=True).strip()


def inspect(name):
    return json.loads(output(["sudo", "docker", "inspect", name]))[0]


def names():
    return set(output(["sudo", "docker", "ps", "-a", "--format", "{{.Names}}"]).splitlines())


def healthy(obj):
    return obj.get("State", {}).get("Running") and obj.get("State", {}).get("Health", {}).get("Status") == "healthy"


def environment(obj):
    return {
        item.split("=", 1)[0]: item.split("=", 1)[1]
        for item in obj.get("Config", {}).get("Env", []) if "=" in item
    }


def environment_file(path):
    return {
        item.split("=", 1)[0]: item.split("=", 1)[1]
        for item in path.read_text(encoding="utf-8").splitlines() if "=" in item
    }


def db_query(node, sql):
    return subprocess.check_output(
        ["sudo", "docker", "exec", "-i", f"reception-r1-{node}", "sh", "-c",
         "port=$(sed -n 's/^PG_PORT=//p' /run/gsm2sip/ports); "
         "export PGPASSWORD=\"$(cat /data/secrets/db_password)\"; "
         "exec psql -h 127.0.0.1 -p $port -U gsm2sip -d gsm2sip -XAt -F '|' -v ON_ERROR_STOP=1 -f -"],
        input=sql.rstrip(";\n") + ";\n", text=True,
    ).strip()


def active_calls(node):
    value = db_query(node, "SELECT count(*) FROM call_sessions WHERE state NOT IN ('ended','unknown')")
    if not value.isdecimal():
        raise RuntimeError(f"active_call_query_invalid:{node}")
    return int(value)


def c3_owner_state():
    sql = f"""
SELECT substring(md5(d.id::text),1,10), substring(md5(d.owner_id::text),1,10),
       d.role, d.state, r.protocol_version, r.incoming_enabled,
       EXISTS (SELECT 1 FROM sessions s WHERE s.device_id=d.id
               AND (s.access_expires_at>clock_timestamp() OR s.refresh_expires_at>clock_timestamp())),
       COALESCE(p.failures,-1), COALESCE(p.call_failures,-1),
       COALESCE(p.next_attempt_at<=clock_timestamp(),false)
FROM devices d
LEFT JOIN device_reception r ON r.device_id=d.id
LEFT JOIN push_endpoints p ON p.device_id=d.id
WHERE substring(md5(d.owner_id::text),1,10)='{OWNER_HASH}'
  AND substring(md5(d.id::text),1,10) IN ('{H1_DEVICE_HASH}','{H2_DEVICE_HASH}')
ORDER BY 1
"""
    rows = [line.split("|") for line in db_query("b", sql).splitlines() if line]
    expected = {H1_DEVICE_HASH, H2_DEVICE_HASH}
    if len(rows) != 2 or {row[0] for row in rows} != expected or any(len(row) != 10 for row in rows):
        raise RuntimeError("c3_exact_h1_h2_owner_rows_missing")
    for row in rows:
        if (row[1] != OWNER_HASH or row[2] != "client" or row[3] != "active"
                or row[4] != "2" or row[5] != "t" or row[6] != "t"
                or row[7] != "0" or row[8] != "0" or row[9] != "t"):
            raise RuntimeError(f"c3_h1_h2_session_reception_or_fcm_gate:{row[0]}")
    return tuple(row[0] for row in rows)


def gateway_sim_state():
    # `gateways.protocol_version` is the /v1 heartbeat contract (always 1);
    # Reception v2 is asserted separately on the H1/H2 device_reception rows.
    sql = f"""
SELECT
 (SELECT count(*) FROM devices d JOIN gateways g ON g.device_id=d.id
   WHERE substring(md5(d.owner_id::text),1,10)='{OWNER_HASH}' AND d.role='gateway'
     AND d.state='active' AND substring(md5(g.install_id::text),1,10)='{INSTALL_HASH}'
     AND g.protocol_version=1
     AND EXISTS (SELECT 1 FROM sessions ss WHERE ss.device_id=d.id
                 AND (ss.access_expires_at>clock_timestamp() OR ss.refresh_expires_at>clock_timestamp()))),
 (SELECT count(*) FROM sim_bindings s JOIN devices d ON d.id=s.gateway_id JOIN gateways g ON g.device_id=d.id
   WHERE substring(md5(d.owner_id::text),1,10)='{OWNER_HASH}' AND d.role='gateway'
     AND d.state='active' AND s.owner_id=d.owner_id
     AND substring(md5(g.install_id::text),1,10)='{INSTALL_HASH}'
     AND substring(md5(s.sim_id::text),1,10)='{SIM_HASH}' AND s.state='active'
     AND s.identity_verified AND s.mapping_revision=g.mapping_revision AND s.slot_index=0
     AND g.protocol_version=1
     AND EXISTS (SELECT 1 FROM sessions ss WHERE ss.device_id=d.id
                 AND (ss.access_expires_at>clock_timestamp() OR ss.refresh_expires_at>clock_timestamp())))
"""
    row = db_query("b", sql).split("|")
    if len(row) != 2:
        raise RuntimeError("gateway_sim_state_shape_invalid")
    return tuple(int(value) for value in row)


def read_stop_gates():
    free_kib = int(output(["df", "-Pk", "/"]).splitlines()[1].split()[3])
    mem_kib = int(next(line.split()[1] for line in pathlib.Path("/proc/meminfo").read_text().splitlines()
                       if line.startswith("MemAvailable:")))
    if free_kib < 3 * 1024 * 1024 or mem_kib < 1024 * 1024:
        raise RuntimeError("resource_stop_gate_crossed")
    for node in "abc":
        obj = inspect(f"reception-r1-{node}")
        if not healthy(obj) or active_calls(node) != 0:
            raise RuntimeError(f"node_health_or_call_stop_gate:{node}")
    sidecar = inspect("reception-r1-private-turn")
    if not sidecar.get("State", {}).get("Running") or sidecar.get("RestartCount", 0) != 0:
        raise RuntimeError("private_turn_listener_not_healthy")
    owner_state = c3_owner_state()
    sim_state = gateway_sim_state()
    if set(owner_state) != {H1_DEVICE_HASH, H2_DEVICE_HASH} or sim_state != (1, 1):
        raise RuntimeError("c3_owner_or_verified_gateway_sim_topology_changed")
    reverse = output([ADB, "-s", H2_SERIAL, "reverse", "--list"])
    if not any(line.split()[-2:] == ["tcp:13479", "tcp:13479"] for line in reverse.splitlines() if len(line.split()) >= 3):
        raise RuntimeError("h2_turn_adb_reverse_missing")
    listeners = output(["ss", "-lntup"])
    required = ("172.31.15.170:13478", "127.0.0.1:13479")
    if any(value not in listeners for value in required):
        raise RuntimeError("private_turn_or_loopback_forward_listener_missing")
    return free_kib, mem_kib


def read_restore_resource_gates():
    """Resource stop floor, usable while a node is starting or being recovered."""
    free_kib = int(output(["df", "-Pk", "/"]).splitlines()[1].split()[3])
    mem_kib = int(next(line.split()[1] for line in pathlib.Path("/proc/meminfo").read_text().splitlines()
                       if line.startswith("MemAvailable:")))
    if free_kib < 3 * 1024 * 1024 or mem_kib < 1024 * 1024:
        raise RuntimeError("restore_resource_stop_gate_crossed")
    return free_kib, mem_kib


def read_restore_peer_gates():
    """A/C safety gates; independent of the B container currently warming up."""
    gates = read_restore_resource_gates()
    for node in "ac":
        obj = inspect(f"reception-r1-{node}")
        if not healthy(obj) or active_calls(node) != 0:
            raise RuntimeError(f"restore_node_health_or_call_safety_gate:{node}")
    return gates


def read_restore_gates():
    """Full restore audit gates, independent of test-only TURN/Host readiness."""
    gates = read_restore_peer_gates()
    obj = inspect("reception-r1-b")
    if not healthy(obj) or active_calls("b") != 0:
        raise RuntimeError("restore_node_health_or_call_safety_gate:b")
    return gates


def verify_runtime(obj, expected_env, old):
    host = obj["HostConfig"]
    old_host = copy.deepcopy(old["HostConfig"])
    new_host = copy.deepcopy(host)
    for value in (old_host, new_host):
        # Docker records --mount and the original bind syntax in different fields.
        value.pop("Binds", None)
        value.pop("Mounts", None)
    mounts = [(m.get("Type"), m.get("Source"), m.get("Destination"), m.get("RW"))
              for m in obj.get("Mounts", [])]
    new_config = copy.deepcopy(obj["Config"])
    old_config = copy.deepcopy(old["Config"])
    for value in (new_config, old_config):
        value.pop("Env", None)
        value.pop("Image", None)
    # Docker CLI `create` sets these client-attach defaults to true. The
    # existing compose-created B has them false; they do not alter container
    # runtime or the json-file log driver. Normalize only false -> true.
    for key in ("AttachStdout", "AttachStderr"):
        if old_config.get(key) is False and new_config.get(key) is True:
            new_config[key] = False
    # CLI create emits false where the compose-created container records null;
    # both mean the OOM killer is not disabled. Never normalize true.
    if old_host.get("OomKillDisable") is None and new_host.get("OomKillDisable") is False:
        new_host["OomKillDisable"] = None
    return (
        obj.get("Image") == IMAGE
        and environment(obj) == expected_env
        and obj["Config"].get("Hostname") == old["Config"].get("Hostname")
        and new_config == old_config
        and new_host == old_host
        and mounts == [("bind", "/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-b", "/data", True)]
    )


def stop_wait(name, timeout=30, require_clean=True):
    obj = inspect(name)
    if obj.get("State", {}).get("Status") == "created":
        return {"status": "created", "clean": True}
    started_at = obj.get("State", {}).get("StartedAt")
    if not started_at or started_at.startswith("0001-"):
        raise RuntimeError(f"container_started_at_missing:{name}")
    started = time.monotonic()
    if obj.get("State", {}).get("Running"):
        subprocess.run(["sudo", "docker", "stop", "--time", str(timeout), name], check=True,
                       stdout=subprocess.DEVNULL)
    elapsed = time.monotonic() - started
    stopped = inspect(name)
    if stopped.get("State", {}).get("Running"):
        raise RuntimeError(f"container_did_not_stop_gracefully:{name}")
    logs = subprocess.run(["sudo", "docker", "logs", "--since", started_at, name], check=True,
                          capture_output=True)
    log_bytes = logs.stdout + logs.stderr
    marker = b"[gsm2sip] stopping" in log_bytes
    state = stopped.get("State", {})
    clean = (state.get("ExitCode") == 0 and not state.get("OOMKilled")
             and not state.get("Error") and elapsed < timeout and marker)
    record = (
        f"container_id={stopped['Id']}\nstatus={state.get('Status')}\n"
        f"exit_code={state.get('ExitCode')}\noom_killed={state.get('OOMKilled')}\n"
        f"state_error_present={bool(state.get('Error'))}\nstop_elapsed_seconds={elapsed:.3f}\n"
        f"shutdown_marker_present={marker}\nlog_sha256={hashlib.sha256(log_bytes).hexdigest()}\n"
        f"clean={clean}\n"
    )
    evidence = PRIVATE / f"shutdown-{stopped['Id'][:12]}-{int(time.time())}.txt"
    write_private(evidence, record)
    if require_clean and not clean:
        raise RuntimeError(f"container_shutdown_not_clean:{name}")
    return {"status": state.get("Status"), "clean": clean, "evidence": str(evidence)}


def disable_restart_for_retained_failure(name):
    obj = inspect(name)
    if obj.get("State", {}).get("Running"):
        raise RuntimeError(f"refusing_restart_policy_change_while_running:{name}")
    subprocess.run(["sudo", "docker", "update", "--restart=no", name], check=True,
                   stdout=subprocess.DEVNULL)
    updated = inspect(name)
    if updated["HostConfig"].get("RestartPolicy", {}).get("Name") != "no":
        raise RuntimeError(f"failed_container_restart_policy_not_disabled:{name}")


def wait_healthy(name, old, expected_env, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        free_kib = int(output(["df", "-Pk", "/"]).splitlines()[1].split()[3])
        mem_kib = int(next(line.split()[1] for line in pathlib.Path("/proc/meminfo").read_text().splitlines()
                           if line.startswith("MemAvailable:")))
        if free_kib < 3 * 1024 * 1024 or mem_kib < 1024 * 1024:
            raise RuntimeError("resource_stop_gate_crossed_during_health_wait")
        for node in "ac":
            if not healthy(inspect(f"reception-r1-{node}")) or active_calls(node) != 0:
                raise RuntimeError(f"peer_node_or_call_stop_gate:{node}")
        sidecar = inspect("reception-r1-private-turn")
        if not sidecar.get("State", {}).get("Running") or sidecar.get("RestartCount", 0) != 0:
            raise RuntimeError("private_turn_listener_lost_during_node_b_health_wait")
        obj = inspect(name)
        if healthy(obj):
            if not verify_runtime(obj, expected_env, old):
                raise RuntimeError("node_b_runtime_config_mismatch")
            if active_calls("b") != 0:
                raise RuntimeError("active_call_detected_after_node_b_health")
            return obj
        if not obj.get("State", {}).get("Running"):
            raise RuntimeError("node_b_exited_before_health")
        time.sleep(3)
    raise RuntimeError("node_b_health_timeout")


def wait_healthy_for_restore(expected_id, expected_env, old, timeout=120):
    """Wait for B without requiring test-only TURN, ADB reverse, or Host push gates."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        read_restore_resource_gates()
        obj = inspect(CONTAINER)
        if obj["Id"] != expected_id:
            raise RuntimeError("restore_wait_primary_container_id_changed")
        if healthy(obj):
            if (environment(obj) != expected_env
                    or not verify_runtime(obj, expected_env, old)
                    or active_calls("b") != 0):
                raise RuntimeError("restore_wait_original_identity_config_or_calls_invalid")
            return obj
        if not obj.get("State", {}).get("Running"):
            raise RuntimeError("restore_wait_node_b_exited_before_health")
        time.sleep(3)
    raise RuntimeError("restore_wait_node_b_health_timeout")


def restore_final_audit(expected_id, expected_env, old):
    """Independently prove final B identity/config/health and the cluster safety gates."""
    gates = restore_final_b_audit(expected_id, expected_env, old)
    read_restore_peer_gates()
    return gates


def restore_b_identity_health_audit(expected_id, expected_env, old):
    """Audit B itself without resource or peer gates, for non-destructive error handling."""
    current = inspect(CONTAINER)
    if (current["Id"] != expected_id or not healthy(current)
            or environment(current) != expected_env
            or not verify_runtime(current, expected_env, old)
            or active_calls("b") != 0):
        raise RuntimeError("restore_b_identity_config_health_or_calls_unverified")
    return current


def restore_final_b_audit(expected_id, expected_env, old):
    """Prove B itself even when an unrelated A/C peer check is unavailable."""
    gates = read_restore_resource_gates()
    restore_b_identity_health_audit(expected_id, expected_env, old)
    return gates


def rollback(old_id, replacement_id, replacement_env, old_env, old, temp_name):
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    current = names()
    if temp_name in current:
        temp = inspect(temp_name)
        if not replacement_id or temp["Id"] != replacement_id:
            raise RuntimeError("rollback_refused_unidentified_temporary_container")
        if not verify_runtime(temp, replacement_env, old):
            raise RuntimeError("rollback_refused_temporary_config_mismatch")
        if temp.get("State", {}).get("Status") == "created":
            subprocess.run(["sudo", "docker", "rm", temp_name], check=True,
                           stdout=subprocess.DEVNULL)
        elif temp.get("State", {}).get("Running"):
            stop_wait(temp_name, require_clean=False)
            temp = inspect(temp_name)
            if temp.get("State", {}).get("Running"):
                raise RuntimeError("rollback_temp_container_still_running")
            disable_restart_for_retained_failure(temp_name)
            failed = f"{temp_name}-stopped"
            subprocess.run(["sudo", "docker", "rename", temp_name, failed], check=True)
        else:
            disable_restart_for_retained_failure(temp_name)
            failed = f"{temp_name}-stopped"
            subprocess.run(["sudo", "docker", "rename", temp_name, failed], check=True)
    current = names()
    if CONTAINER in current:
        current_obj = inspect(CONTAINER)
        current_id = current_obj["Id"]
        if current_id == old_id:
            if not current_obj.get("State", {}).get("Running"):
                subprocess.run(["sudo", "docker", "start", CONTAINER], check=True,
                               stdout=subprocess.DEVNULL)
        elif replacement_id and current_id == replacement_id:
            if not verify_runtime(current_obj, replacement_env, old):
                raise RuntimeError("rollback_refused_primary_replacement_config_mismatch")
            try:
                stop_wait(CONTAINER)
                subprocess.run(["sudo", "docker", "rm", CONTAINER], check=True,
                               stdout=subprocess.DEVNULL)
            except (RuntimeError, subprocess.CalledProcessError):
                stopped = inspect(CONTAINER)
                if stopped.get("State", {}).get("Running"):
                    raise RuntimeError("rollback_replacement_still_running")
                disable_restart_for_retained_failure(CONTAINER)
                failed = f"{CONTAINER}-c3-failed-{int(time.time())}"
                subprocess.run(["sudo", "docker", "rename", CONTAINER, failed], check=True)
        else:
            raise RuntimeError("rollback_refused_unknown_container_at_primary_name")
    current = names()
    if ORIGINAL in current:
        backup = inspect(ORIGINAL)
        if backup["Id"] != old_id:
            raise RuntimeError("rollback_refused_original_name_id_mismatch")
        if CONTAINER in names():
            raise RuntimeError("rollback_refused_primary_name_occupied")
        subprocess.run(["sudo", "docker", "rename", ORIGINAL, CONTAINER], check=True)
    restored = inspect(CONTAINER)
    if restored["Id"] != old_id:
        raise RuntimeError("rollback_original_container_id_not_restored")
    if not restored.get("State", {}).get("Running"):
        subprocess.run(["sudo", "docker", "start", CONTAINER], check=True,
                       stdout=subprocess.DEVNULL)
    wait_healthy(CONTAINER, old, old_env)
    if environment(inspect(CONTAINER)) != old_env:
        raise RuntimeError("rollback_original_environment_mismatch")


def try_restore_original_id(old_id, old_env, replacement_id, replacement_env, old):
    """Reconcile names by exact IDs, then try original B without test-only gates."""
    current_names = names()
    if CONTAINER in current_names:
        current = inspect(CONTAINER)
        if current["Id"] == old_id:
            if healthy(current):
                return restore_final_audit(old_id, old_env, old)
            if current.get("State", {}).get("Running"):
                try:
                    wait_healthy_for_restore(old_id, old_env, old)
                    return restore_final_audit(old_id, old_env, old)
                except RuntimeError as wait_error:
                    current = inspect(CONTAINER)
                    if healthy(current):
                        # B is serving again; leave it in place if only an
                        # unrelated cluster/resource audit is failing.
                        restore_b_identity_health_audit(old_id, old_env, old)
                        raise RuntimeError("restore_original_b_healthy_but_full_audit_incomplete") from wait_error
                    if active_calls("b") != 0:
                        raise RuntimeError("restore_reconcile_refused_active_calls_on_original") from wait_error
                    read_restore_resource_gates()
                subprocess.run(["sudo", "docker", "stop", "--time", "30", CONTAINER],
                               check=True, stdout=subprocess.DEVNULL)
                current = inspect(CONTAINER)
            if current.get("State", {}).get("Running"):
                raise RuntimeError("restore_reconcile_original_still_running_unhealthy")
            disable_restart_for_retained_failure(CONTAINER)
            failed = f"{ORIGINAL}-failed-{int(time.time())}"
            if failed in names():
                raise RuntimeError("restore_reconcile_failed_original_name_collision")
            subprocess.run(["sudo", "docker", "rename", CONTAINER, failed], check=True)
        elif current["Id"] == replacement_id:
            if not verify_runtime(current, replacement_env, old):
                raise RuntimeError("restore_reconcile_private_turn_config_mismatch")
            if current.get("State", {}).get("Running"):
                if active_calls("b") != 0:
                    raise RuntimeError("restore_reconcile_refused_active_calls_on_private_turn")
                stop_wait(CONTAINER, require_clean=False)
                current = inspect(CONTAINER)
            if current.get("State", {}).get("Running"):
                raise RuntimeError("restore_reconcile_private_turn_still_running")
            if REPLACED in names():
                raise RuntimeError("restore_reconcile_replaced_name_collision")
            subprocess.run(["sudo", "docker", "rename", CONTAINER, REPLACED], check=True)
        else:
            raise RuntimeError("restore_reconcile_refused_unknown_primary_container")

    current_names = names()
    if CONTAINER in current_names:
        raise RuntimeError("restore_reconcile_primary_name_not_free")
    if ORIGINAL not in current_names or inspect(ORIGINAL)["Id"] != old_id:
        raise RuntimeError("restore_reconcile_exact_original_container_not_available")
    subprocess.run(["sudo", "docker", "rename", ORIGINAL, CONTAINER], check=True)
    candidate = inspect(CONTAINER)
    if candidate["Id"] != old_id or environment(candidate) != old_env:
        raise RuntimeError("restore_reconcile_original_identity_changed_after_rename")
    if not candidate.get("State", {}).get("Running"):
        subprocess.run(["sudo", "docker", "start", CONTAINER], check=True,
                       stdout=subprocess.DEVNULL)
    wait_healthy_for_restore(old_id, old_env, old)
    return restore_final_audit(old_id, old_env, old)


def restore_private_turn_fallback(old_id, old_env, replacement_id, replacement_env, old):
    """Keep the known private-TURN B serving if the original cannot be restored."""
    current_names = names()
    if CONTAINER in current_names:
        current = inspect(CONTAINER)
        if current["Id"] == replacement_id:
            if not verify_runtime(current, replacement_env, old):
                raise RuntimeError("fallback_primary_private_turn_config_mismatch")
            if not current.get("State", {}).get("Running"):
                subprocess.run(["sudo", "docker", "start", CONTAINER], check=True,
                               stdout=subprocess.DEVNULL)
            wait_healthy_for_restore(replacement_id, replacement_env, old)
            return restore_final_audit(replacement_id, replacement_env, old)
        if current["Id"] != old_id:
            raise RuntimeError("fallback_refused_unknown_primary_container")
        if current.get("State", {}).get("Running"):
            if active_calls("b") != 0:
                raise RuntimeError("fallback_refused_active_calls_on_original")
            subprocess.run(["sudo", "docker", "stop", "--time", "30", CONTAINER],
                           check=True, stdout=subprocess.DEVNULL)
            current = inspect(CONTAINER)
        if current.get("State", {}).get("Running"):
            raise RuntimeError("fallback_original_container_could_not_be_stopped")
        disable_restart_for_retained_failure(CONTAINER)
        failed = f"{ORIGINAL}-failed-{int(time.time())}"
        if failed in names():
            raise RuntimeError("fallback_failed_original_name_collision")
        subprocess.run(["sudo", "docker", "rename", CONTAINER, failed], check=True)

    current_names = names()
    replacement_name = None
    for name in (REPLACED,):
        if name in current_names and inspect(name)["Id"] == replacement_id:
            replacement_name = name
            break
    if replacement_name is None:
        raise RuntimeError("fallback_private_turn_container_not_found_by_exact_id")
    replacement = inspect(replacement_name)
    if not verify_runtime(replacement, replacement_env, old):
        raise RuntimeError("fallback_private_turn_retained_config_mismatch")
    if replacement.get("State", {}).get("Running"):
        if active_calls("b") != 0:
            raise RuntimeError("fallback_refused_active_calls_on_retained_private_turn")
        stop_wait(replacement_name, require_clean=False)
        replacement = inspect(replacement_name)
    if replacement.get("State", {}).get("Running"):
        raise RuntimeError("fallback_private_turn_container_could_not_be_stopped")
    if CONTAINER in names():
        raise RuntimeError("fallback_primary_name_occupied_before_private_turn_rename")
    subprocess.run(["sudo", "docker", "rename", replacement_name, CONTAINER], check=True)
    subprocess.run(["sudo", "docker", "start", CONTAINER], check=True,
                   stdout=subprocess.DEVNULL)
    wait_healthy_for_restore(replacement_id, replacement_env, old)
    return restore_final_audit(replacement_id, replacement_env, old)


def restore_original():
    # The recovery path ignores terminal disconnect/termination and finishes
    # with an exact-ID health and zero-call audit before returning.
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    signal.signal(signal.SIGHUP, signal.SIG_IGN)
    gates = read_restore_gates()
    current_names = names()
    if ORIGINAL not in current_names or CONTAINER not in current_names or REPLACED in current_names:
        raise RuntimeError("unexpected_names_for_original_node_b_restore")
    old_env_path = PRIVATE / "node-b-env-before"
    if not old_env_path.is_file() or old_env_path.stat().st_mode & 0o077:
        raise RuntimeError("private_node_b_original_env_missing_or_permissions_too_open")
    old_env = environment_file(old_env_path)
    original = inspect(ORIGINAL)
    current = inspect(CONTAINER)
    if (original["Id"] != OLD_B_ID or original.get("State", {}).get("Running")
            or not verify_runtime(original, old_env, original)
            or environment(original) != old_env):
        raise RuntimeError("retained_original_node_b_does_not_match_saved_identity_config_or_env")
    replacement_env = dict(old_env)
    replacement_env["RTC_TURN_URLS"] = TURN_URLS
    if (not healthy(current) or not verify_runtime(current, replacement_env, original)
            or environment(current) != replacement_env or active_calls("b") != 0):
        raise RuntimeError("current_private_turn_node_b_not_healthy_or_exactly_identified")
    replaced_id = current["Id"]
    old_id = original["Id"]
    recovery_path_used = False

    try:
        if active_calls("b") != 0:
            raise RuntimeError("restore_refused_active_calls_before_node_b_stop")
        stop_wait(CONTAINER)
        stopped_private = inspect(CONTAINER)
        if stopped_private["Id"] != replaced_id or stopped_private.get("State", {}).get("Running"):
            raise RuntimeError("restore_private_turn_stop_identity_or_state_mismatch")
        if REPLACED in names():
            raise RuntimeError("restore_private_turn_name_collision")
        subprocess.run(["sudo", "docker", "rename", CONTAINER, REPLACED], check=True)
        subprocess.run(["sudo", "docker", "rename", ORIGINAL, CONTAINER], check=True)
        subprocess.run(["sudo", "docker", "start", CONTAINER], check=True,
                       stdout=subprocess.DEVNULL)
        restored = wait_healthy_for_restore(old_id, old_env, original)
        if (restored["Id"] != old_id or environment(restored) != old_env
                or not verify_runtime(restored, old_env, original)
                or any(active_calls(node) != 0 for node in "abc")):
            raise RuntimeError("restored_original_node_b_postcheck_failed")
        gates = restore_final_audit(old_id, old_env, original)
    except BaseException as restore_error:
        # If original B is already healthy with the exact saved identity, never
        # switch away from it just because a resource/peer audit failed.
        current_names = names()
        if CONTAINER in current_names and inspect(CONTAINER)["Id"] == old_id:
            try:
                restore_b_identity_health_audit(old_id, old_env, original)
            except BaseException:
                pass
            else:
                try:
                    gates = restore_final_audit(old_id, old_env, original)
                except BaseException as audit_error:
                    raise RuntimeError(
                        "original_node_b_restored_and_healthy;cluster_or_resource_audit_incomplete;"
                        f"audit_error={type(audit_error).__name__}"
                    ) from audit_error
                recovery_path_used = True

        if recovery_path_used:
            pass
        else:
            try:
                gates = try_restore_original_id(
                    old_id, old_env, replaced_id, replacement_env, original)
            except BaseException as original_recovery_error:
                current_names = names()
                if CONTAINER in current_names and inspect(CONTAINER)["Id"] == old_id:
                    try:
                        restore_b_identity_health_audit(old_id, old_env, original)
                    except BaseException:
                        pass
                    else:
                        try:
                            gates = restore_final_audit(old_id, old_env, original)
                        except BaseException as audit_error:
                            raise RuntimeError(
                                "original_node_b_restored_and_healthy;cluster_or_resource_audit_incomplete;"
                                f"audit_error={type(audit_error).__name__}"
                            ) from audit_error
                        recovery_path_used = True

                if not recovery_path_used:
                    try:
                        fallback_gates = restore_private_turn_fallback(
                            old_id, old_env, replaced_id, replacement_env, original)
                    except BaseException as recovery_error:
                        state = []
                        try:
                            current_names = names()
                            for name in sorted(current_names.intersection({CONTAINER, ORIGINAL, REPLACED})):
                                obj = inspect(name)
                                state.append(f"{name}:{obj['Id'][:12]}:{obj['State'].get('Status')}"
                                             f":healthy={healthy(obj)}")
                        except BaseException:
                            state = ["final_state_inspection_failed"]
                        raise RuntimeError(
                            "restore_recovery_unverified;" + ",".join(state)
                            + f";primary_error={type(restore_error).__name__}"
                            + f";original_recovery_error={type(original_recovery_error).__name__}"
                            + f";fallback_error={type(recovery_error).__name__}"
                        ) from recovery_error
                    raise RuntimeError(
                        "original_restore_failed;private_turn_fallback_independently_verified;"
                        f"root_free_kib={fallback_gates[0]};mem_available_kib={fallback_gates[1]}"
                    ) from original_recovery_error
                recovery_path_used = True

    disable_restart_for_retained_failure(REPLACED)
    retained = inspect(REPLACED)
    if (retained["Id"] != replaced_id or retained.get("State", {}).get("Running")
            or retained["HostConfig"].get("RestartPolicy", {}).get("Name") != "no"):
        raise RuntimeError("retained_private_turn_container_final_state_not_verified")
    print(f"original_node_b_restored=PASS;recovery_path_used={recovery_path_used}")
    print("original_node_b_id_and_env=PASS")
    print("original_node_b_config_equivalent=PASS")
    print("a_b_c_healthy_and_zero_active_calls=PASS")
    print(f"retained_private_turn_container_id={replaced_id};restart_policy=no")
    print(f"restore_root_free_kib={gates[0]};mem_available_kib={gates[1]}")


def write_private(path, content):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(content)


def supported_host_config(host):
    allowed = {
        "Binds", "Mounts", "NetworkMode", "RestartPolicy", "PortBindings", "IpcMode",
        "CgroupnsMode", "Runtime", "LogConfig", "ShmSize",
    }
    defaults = {
        "ContainerIDFile": "", "AutoRemove": False, "VolumeDriver": "", "VolumesFrom": None,
        "ConsoleSize": [0, 0], "CapAdd": None, "CapDrop": None, "Dns": None, "DnsOptions": [],
        "DnsSearch": [], "ExtraHosts": None, "GroupAdd": None, "Cgroup": "", "Links": None,
        "OomScoreAdj": 0, "PidMode": "", "Privileged": False, "PublishAllPorts": False,
        "ReadonlyRootfs": False, "SecurityOpt": None, "UTSMode": "", "UsernsMode": "",
        "Isolation": "", "CpuShares": 0, "Memory": 0, "NanoCpus": 0, "CgroupParent": "",
        "BlkioWeight": 0, "BlkioWeightDevice": [], "BlkioDeviceReadBps": [],
        "BlkioDeviceWriteBps": [], "BlkioDeviceReadIOps": [], "BlkioDeviceWriteIOps": [],
        "CpuPeriod": 0, "CpuQuota": 0, "CpuRealtimePeriod": 0, "CpuRealtimeRuntime": 0,
        "CpusetCpus": "", "CpusetMems": "", "Devices": [], "DeviceCgroupRules": None,
        "DeviceRequests": None, "MemoryReservation": 0, "MemorySwap": 0, "MemorySwappiness": None,
        "OomKillDisable": None, "PidsLimit": None, "Ulimits": [], "CpuCount": 0, "CpuPercent": 0,
        "IOMaximumIOps": 0, "IOMaximumBandwidth": 0,
        "MaskedPaths": ["/proc/acpi", "/proc/asound", "/proc/interrupts", "/proc/kcore",
                        "/proc/keys", "/proc/latency_stats", "/proc/sched_debug", "/proc/scsi",
                        "/proc/timer_list", "/proc/timer_stats", "/sys/devices/virtual/powercap",
                        "/sys/firmware"],
        "ReadonlyPaths": ["/proc/bus", "/proc/fs", "/proc/irq", "/proc/sys", "/proc/sysrq-trigger"],
    }
    unknown = {key: value for key, value in host.items()
               if key not in allowed and (key not in defaults or value != defaults[key])}
    expected = {
        "Binds": ["/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-b:/data"],
        "Mounts": None,
        "NetworkMode": "reception-r1-b",
        "RestartPolicy": {"Name": "unless-stopped", "MaximumRetryCount": 0},
        "PortBindings": {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": "18111"}]},
        "IpcMode": "private", "CgroupnsMode": "private", "Runtime": "runc",
        "LogConfig": {"Type": "json-file", "Config": {}}, "ShmSize": 67108864,
    }
    if unknown or any(host.get(k) != v for k, v in expected.items()):
        raise RuntimeError("node_b_host_config_has_unpreserved_options")


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--preflight-only", action="store_true")
    mode.add_argument("--restore-original", action="store_true")
    args = parser.parse_args()
    lock_path = PRIVATE / "node-b-c3-switch.lock"
    lock_fd = os.open(lock_path, os.O_WRONLY | os.O_CREAT, 0o600)
    fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    if args.restore_original:
        restore_original()
        return
    if ORIGINAL in names() or CONTAINER not in names():
        raise RuntimeError("unexpected_node_b_container_names")
    gates = read_stop_gates()
    old = inspect(CONTAINER)
    old_env_path = PRIVATE / "node-b-env-before"
    dump_path = PRIVATE / "node-b-before.pgdump"
    if not old_env_path.is_file() or not dump_path.is_file() or dump_path.stat().st_size < 100:
        raise RuntimeError("private_node_b_recovery_files_missing")
    if dump_path.read_bytes()[:5] != b"PGDMP":
        raise RuntimeError("private_node_b_recovery_dump_not_custom_pg_dump")
    if not healthy(old) or old.get("Image") != IMAGE:
        raise RuntimeError("node_b_health_or_image_changed")
    old_env = environment(old)
    if old_env != environment_file(old_env_path):
        raise RuntimeError("node_b_environment_backup_mismatch")
    if old_env_path.stat().st_mode & 0o077:
        raise RuntimeError("private_env_file_permissions_too_open")
    supported_host_config(old["HostConfig"])
    expected_mount = [("bind", "/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-b", "/data", True)]
    mounts = [(m.get("Type"), m.get("Source"), m.get("Destination"), m.get("RW"))
              for m in old.get("Mounts", [])]
    if (mounts != expected_mount or old["HostConfig"].get("NetworkMode") != "reception-r1-b"
            or old["HostConfig"].get("PortBindings") != {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": "18111"}]}
            or old["HostConfig"].get("RestartPolicy", {}).get("Name") != "unless-stopped"):
        raise RuntimeError("node_b_runtime_no_longer_matches_risk_record")
    if old_env.get("RTC_TURN_URLS") == TURN_URLS:
        raise RuntimeError("node_b_turn_url_already_changed")
    if args.preflight_only:
        print("node_b_private_turn_preflight=PASS; no container or environment changes made")
        print(f"old_container_id={old['Id']};image_digest={old['Image']}")
        print(f"root_free_kib={gates[0]};mem_available_kib={gates[1]}")
        return
    replacement_env = dict(old_env)
    replacement_env["RTC_TURN_URLS"] = TURN_URLS
    env_path = PRIVATE / "node-b.env.private-turn"
    write_private(env_path, "".join(f"{key}={value}\n" for key, value in replacement_env.items()))

    host = old["HostConfig"]
    temp_name = f"reception-r1-b-c3-tmp-{uuid.uuid4().hex}"
    if temp_name in names():
        raise RuntimeError("temporary_container_name_collision")
    run_args = [
        "sudo", "docker", "create", "--name", temp_name,
        "--hostname", old["Config"]["Hostname"],
        "--restart", "unless-stopped",
        "--network", host["NetworkMode"],
        "--publish", "127.0.0.1:18111:8080/tcp",
        "--mount", "type=bind,src=/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-b,dst=/data",
        "--env-file", str(env_path),
        "--shm-size", str(host.get("ShmSize", 67108864)),
        "--ipc", host.get("IpcMode", "private"),
        "--cgroupns", host.get("CgroupnsMode", "private"),
        "--runtime", host.get("Runtime", "runc"),
        "--log-driver", host.get("LogConfig", {}).get("Type", "json-file"),
    ]
    for key, value in host.get("LogConfig", {}).get("Config", {}).items():
        run_args.extend(["--log-opt", f"{key}={value}"])
    run_args.append(IMAGE)
    old_id = old["Id"]
    replacement_id = None
    try:
        signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
        create_error = None
        try:
            run = subprocess.run(run_args, check=True, text=True, capture_output=True)
            replacement_id = run.stdout.strip().splitlines()[-1]
        except subprocess.CalledProcessError as failure:
            create_error = failure
        current = names()
        if temp_name in current:
            candidate = inspect(temp_name)
            candidate_id = candidate["Id"]
            if len(candidate_id) != 64 or any(ch not in "0123456789abcdef" for ch in candidate_id):
                raise RuntimeError("replacement_temp_container_id_invalid") from create_error
            if not verify_runtime(candidate, replacement_env, old):
                raise RuntimeError("replacement_temp_container_config_not_verified") from create_error
            if replacement_id and replacement_id != candidate_id:
                raise RuntimeError("replacement_temp_container_id_mismatch")
            replacement_id = candidate_id
        if create_error is not None:
            raise create_error
        if not replacement_id or len(replacement_id) != 64 or any(ch not in "0123456789abcdef" for ch in replacement_id):
            raise RuntimeError("replacement_container_id_invalid")
        candidate = inspect(temp_name)
        if candidate["Id"] != replacement_id or not verify_runtime(candidate, replacement_env, old):
            raise RuntimeError("replacement_temp_container_identity_or_config_mismatch")
        # Verify the complete, stopped replacement before allowing the old
        # service to stop. `docker create` does not execute the entrypoint.
        stop_wait(CONTAINER)
        subprocess.run(["sudo", "docker", "rename", CONTAINER, ORIGINAL], check=True)
        subprocess.run(["sudo", "docker", "rename", temp_name, CONTAINER], check=True)
        subprocess.run(["sudo", "docker", "start", CONTAINER], check=True, stdout=subprocess.DEVNULL)
        replacement = wait_healthy(CONTAINER, old, replacement_env)
        if set(c3_owner_state()) != {H1_DEVICE_HASH, H2_DEVICE_HASH} or gateway_sim_state() != (1, 1):
            raise RuntimeError("c3_topology_changed_after_node_b_restart")
        if any(active_calls(node) != 0 for node in "abc"):
            raise RuntimeError("active_call_detected_after_node_b_restart")
    except BaseException:
        if replacement_id is None and temp_name in names():
            candidate = inspect(temp_name)
            if verify_runtime(candidate, replacement_env, old):
                replacement_id = candidate["Id"]
        rollback_error = None
        try:
            rollback(old_id, replacement_id, replacement_env, old_env, old, temp_name)
        except BaseException as error:
            rollback_error = error
        try:
            restored = inspect(CONTAINER)
            restored_ok = (
                restored["Id"] == old_id
                and healthy(restored)
                and environment(restored) == old_env
                and verify_runtime(restored, old_env, old)
                and all(healthy(inspect(f"reception-r1-{node}")) for node in "abc")
                and all(active_calls(node) == 0 for node in "abc")
            )
        except BaseException as error:
            restored_ok = False
            verify_error = error
        else:
            verify_error = None
        if not restored_ok:
            raise RuntimeError(
                "rollback_failed_original_node_b_not_independently_verified"
                + (f":{verify_error!r}" if verify_error else "")
            ) from (rollback_error or verify_error)
        if rollback_error:
            raise RuntimeError(
                "rollback_reported_error_but_original_node_b_independently_verified"
            ) from rollback_error
        raise

    print("node_b_private_turn_update=PASS")
    print("only_environment_difference=RTC_TURN_URLS")
    print("runtime_config_equivalent_except_bind_attach_and_default_null_representation=PASS")
    print("image_mount_network_port_restart_health=PASS")
    print("exact_h1_h2_active_sessions_v2_reception_and_fcm=PASS")
    print("gateway_install_identity_and_verified_sim_match=PASS")
    print("active_calls=0;original_node_b_container_id_retained_stopped")
    print(f"pre_update_root_free_kib={gates[0]};mem_available_kib={gates[1]}")


if __name__ == "__main__":
    main()
