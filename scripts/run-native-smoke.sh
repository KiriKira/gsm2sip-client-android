#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${PJSUA_SMOKE_OUT_DIR:-$ROOT/artifacts/android-ui-smoke/native}"
if [[ "$OUT" != /* ]]; then OUT="$ROOT/$OUT"; fi
mkdir -p "$OUT"

RUN_LOG="$OUT/run.log"
RESULTS="$OUT/results.json"
CHECKS_FILE="$OUT/.checks.tsv"
INSTRUMENTATION_LOG="$OUT/instrumentation.txt"
LOGCAT_LOG="$OUT/logcat.txt"
HOST_APK_VERIFY="$OUT/host-apksigner-verify.txt"
SMOKE_APK_VERIFY="$OUT/native-smoke-apksigner-verify.txt"
BUILD_LOG="$OUT/build.log"
: > "$RUN_LOG"
: > "$CHECKS_FILE"
exec > >(tee -a "$RUN_LOG") 2>&1

STATUS="FAIL"
PHASE="preflight"
CURRENT_CHECK=""
ERROR_MESSAGE=""
STARTED_UTC="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
INSTRUMENTATION_EXIT_CODE=""
ADB_TIMEOUT_SECONDS="${PJSUA_SMOKE_ADB_TIMEOUT_SECONDS:-45}"

record() {
    local name="$1" state="$2" detail="${3:-}"
    detail="${detail//$'\t'/ }"
    detail="${detail//$'\n'/ }"
    printf '%s\t%s\t%s\n' "$name" "$state" "$detail" >> "$CHECKS_FILE"
}

die() {
    ERROR_MESSAGE="$*"
    printf 'ERROR: %s\n' "$ERROR_MESSAGE" >&2
    exit 1
}

write_results() {
    local exit_code="$1"
    if command -v python3 >/dev/null 2>&1; then
        PJSUA_ROOT="$ROOT" \
        PJSUA_OUT="$OUT" \
        PJSUA_STATUS="$STATUS" \
        PJSUA_PHASE="$PHASE" \
        PJSUA_EXIT_CODE="$exit_code" \
        PJSUA_ERROR="$ERROR_MESSAGE" \
        PJSUA_STARTED_UTC="$STARTED_UTC" \
        PJSUA_INSTRUMENTATION_EXIT_CODE="${INSTRUMENTATION_EXIT_CODE:-}" \
        PJSUA_HOST_APK="${HOST_APK:-}" \
        PJSUA_SMOKE_APK="${SMOKE_APK:-}" \
        PJSUA_KEYSTORE="${KEYSTORE:-}" \
        PJSUA_DEVICE_SERIAL="${DEVICE_SERIAL:-}" \
        python3 - "$CHECKS_FILE" "$INSTRUMENTATION_LOG" "$RESULTS" <<'PY'
import datetime
import json
import os
import pathlib
import re
import sys

checks_path, instrumentation_path, results_path = map(pathlib.Path, sys.argv[1:])
root = pathlib.Path(os.environ["PJSUA_ROOT"])
out = pathlib.Path(os.environ["PJSUA_OUT"])
checks = {}
if checks_path.exists():
    for line in checks_path.read_text(errors="replace").splitlines():
        parts = line.split("\t", 2)
        if len(parts) == 3:
            checks[parts[0]] = {"status": parts[1], "detail": parts[2]}

instrumentation = instrumentation_path.read_text(errors="replace") if instrumentation_path.exists() else ""
def status_value(key):
    matches = re.findall(r"^INSTRUMENTATION_STATUS: " + re.escape(key) + r"=(.*)$", instrumentation, re.M)
    return matches[-1].strip() if matches else None

codes = re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", instrumentation, re.M)
codec_text = status_value("codecs")
codecs = [item.strip() for item in codec_text.split(",") if item.strip()] if codec_text else []
failure_values = re.findall(r"^INSTRUMENTATION_STATUS: failure=(.*)$", instrumentation, re.M)
observed = {
    "adb_instrumentation_exit_code": int(os.environ.get("PJSUA_INSTRUMENTATION_EXIT_CODE") or 0)
        if os.environ.get("PJSUA_INSTRUMENTATION_EXIT_CODE") else None,
    "instrumentation_result_code": int(codes[-1]) if codes else None,
    "native_library": status_value("native_library"),
    "audio_device": status_value("audio_device"),
    "microphone_capture": status_value("microphone_capture"),
    "sip_call": status_value("sip_call"),
    "codecs": codecs,
    "failure": failure_values[-1] if failure_values else None,
}
required = ("opus", "pcmu", "pcma", "g722")
observed["required_codecs"] = {
    codec: (any(item.split("/", 1)[0].strip().lower() == codec for item in codecs)
            if codecs else None)
    for codec in required
}

def relative_or_original(value):
    if not value:
        return None
    path = pathlib.Path(value)
    try:
        return str(path.resolve().relative_to(root.resolve()))
    except (ValueError, OSError):
        return str(path)

artifacts = {
    "run_log": relative_or_original(out / "run.log"),
    "build_log": relative_or_original(out / "build.log"),
    "instrumentation_log": relative_or_original(out / "instrumentation.txt"),
    "logcat": relative_or_original(out / "logcat.txt"),
    "host_signature_report": relative_or_original(out / "host-apksigner-verify.txt"),
    "smoke_signature_report": relative_or_original(out / "native-smoke-apksigner-verify.txt"),
    "host_apk": relative_or_original(os.environ.get("PJSUA_HOST_APK")),
    "smoke_apk": relative_or_original(os.environ.get("PJSUA_SMOKE_APK")),
    "signing_keystore": relative_or_original(os.environ.get("PJSUA_KEYSTORE")),
}

result = {
    "schema_version": 1,
    "status": os.environ["PJSUA_STATUS"],
    "phase": os.environ["PJSUA_PHASE"],
    "exit_code": int(os.environ["PJSUA_EXIT_CODE"]),
    "started_at_utc": os.environ["PJSUA_STARTED_UTC"],
    "finished_at_utc": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
    "target_package": "com.callagent.host",
    "instrumentation": "com.callagent.host.pjsua2smoke/com.callagent.host.smoke.PjsuaSmokeInstrumentation",
    "device_serial": os.environ.get("PJSUA_DEVICE_SERIAL") or None,
    "observed": observed,
    "checks": checks,
    "artifacts": artifacts,
    "error": os.environ.get("PJSUA_ERROR") or None,
}
results_path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
PY
    else
        cat > "$RESULTS" <<EOF
{
  "schema_version": 1,
  "status": "FAIL",
  "phase": "reporting",
  "exit_code": $exit_code,
  "error": "python3 is required to write results.json"
}
EOF
    fi
}

on_exit() {
    local rc=$?
    trap - EXIT
    if (( rc != 0 )); then
        STATUS="FAIL"
        if [[ -n "$CURRENT_CHECK" ]]; then
            record "$CURRENT_CHECK" "FAIL" "${ERROR_MESSAGE:-command failed in phase $PHASE (exit $rc)}"
        fi
        if [[ -z "$ERROR_MESSAGE" ]]; then
            ERROR_MESSAGE="command failed in phase $PHASE (exit $rc)"
        fi
    fi
    write_results "$rc" || true
    printf '\nNative smoke status: %s (exit %s)\nResults: %s\nRun log: %s\n' \
        "$STATUS" "$rc" "$RESULTS" "$RUN_LOG"
    exit "$rc"
}
trap on_exit EXIT

for check in toolchain host_apk host_apk_package harness_build signer_match adb_device host_install harness_install \
    instrumentation_command instrumentation_result_code instrumentation_failure native_library null_audio \
    microphone_capture sip_call codec_opus codec_pcmu codec_pcma codec_g722; do
    record "$check" "NOT_RUN" "not reached"
done

fail_check() {
    local name="$1" detail="$2"
    record "$name" "FAIL" "$detail"
    CURRENT_CHECK=""
    die "$detail"
}

PHASE="toolchain"
CURRENT_CHECK="toolchain"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK_ROOT" ]] || fail_check toolchain "Set ANDROID_SDK_ROOT or ANDROID_HOME to the Android SDK directory"
[[ -d "$SDK_ROOT" ]] || fail_check toolchain "Android SDK directory does not exist: $SDK_ROOT"
SDK_ROOT="$(cd -- "$SDK_ROOT" && pwd)"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
PLATFORM="$SDK_ROOT/platforms/android-35"
for required in "$BUILD_TOOLS/aapt" "$BUILD_TOOLS/d8" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$PLATFORM/android.jar"; do
    [[ -x "$required" || -f "$required" ]] || fail_check toolchain "Missing required SDK component: $required"
done
JAVA="$(command -v java || true)"
JAVAC="$(command -v javac || true)"
ADB="$(command -v adb || true)"
TIMEOUT="$(command -v timeout || true)"
PYTHON="$(command -v python3 || true)"
[[ -n "$JAVA" && -n "$JAVAC" ]] || fail_check toolchain "java and javac must be available on PATH"
[[ -n "$ADB" ]] || fail_check toolchain "adb must be available on PATH"
[[ -n "$TIMEOUT" ]] || fail_check toolchain "GNU timeout must be available on PATH"
[[ -n "$PYTHON" ]] || fail_check toolchain "python3 must be available on PATH to package the APK and write results.json"
[[ "$ADB_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] && (( ADB_TIMEOUT_SECONDS >= 30 && ADB_TIMEOUT_SECONDS <= 60 )) \
    || fail_check toolchain "PJSUA_SMOKE_ADB_TIMEOUT_SECONDS must be between 30 and 60"
record toolchain PASS "SDK build-tools 35.0.0 and android-35; java=$JAVA; javac=$JAVAC; adb=$ADB"
CURRENT_CHECK=""

HOST_APK="${PJSUA_SMOKE_HOST_APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
if [[ "$HOST_APK" != /* ]]; then HOST_APK="$ROOT/$HOST_APK"; fi
KEYSTORE="${PJSUA_SMOKE_KEYSTORE:-}"
if [[ -z "$KEYSTORE" && -n "${ANDROID_USER_HOME:-}" && -f "$ANDROID_USER_HOME/debug.keystore" ]]; then
    KEYSTORE="$ANDROID_USER_HOME/debug.keystore"
fi
if [[ -z "$KEYSTORE" && -n "${ANDROID_SDK_HOME:-}" && -f "$ANDROID_SDK_HOME/.android/debug.keystore" ]]; then
    KEYSTORE="$ANDROID_SDK_HOME/.android/debug.keystore"
fi
if [[ -z "$KEYSTORE" && -n "${HOME:-}" && -f "$HOME/.android/debug.keystore" ]]; then
    KEYSTORE="$HOME/.android/debug.keystore"
fi
if [[ -z "$KEYSTORE" ]]; then KEYSTORE="${HOME:-}/.android/debug.keystore"; fi
if [[ "$KEYSTORE" != /* ]]; then KEYSTORE="$ROOT/$KEYSTORE"; fi
SMOKE_APK="$OUT/pjsua2-native-smoke.apk"
MANIFEST="$ROOT/scripts/native-smoke/AndroidManifest.xml"
SOURCE="$ROOT/scripts/native-smoke/src/com/callagent/host/smoke/PjsuaSmokeInstrumentation.java"

PHASE="host-apk"
CURRENT_CHECK="host_apk"
[[ -f "$HOST_APK" ]] || fail_check host_apk "Host debug APK not found: $HOST_APK"
[[ -f "$KEYSTORE" ]] || fail_check host_apk "Host debug keystore not found: $KEYSTORE"
record host_apk PASS "found $HOST_APK; signing key $KEYSTORE is available"
CURRENT_CHECK=""

PHASE="host-package"
CURRENT_CHECK="host_apk_package"
HOST_PACKAGE="$("$BUILD_TOOLS/aapt" dump badging "$HOST_APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
[[ "$HOST_PACKAGE" == "com.callagent.host" ]] || fail_check host_apk_package "Expected host package com.callagent.host, got '${HOST_PACKAGE:-unreadable}'"
record host_apk_package PASS "aapt reports package=$HOST_PACKAGE"
CURRENT_CHECK=""

PHASE="build-harness"
CURRENT_CHECK="harness_build"
WORK="$OUT/build"
CLASS_DIR="$WORK/classes"
DEX_DIR="$WORK/dex"
UNSIGNED_APK="$WORK/native-smoke-unsigned.apk"
ALIGNED_APK="$WORK/native-smoke-aligned.apk"
rm -rf "$WORK"
mkdir -p "$CLASS_DIR" "$DEX_DIR"
[[ -f "$MANIFEST" && -f "$SOURCE" ]] || fail_check harness_build "Smoke manifest or Java source is missing"
{
    echo "SDK=$SDK_ROOT" &&
    echo "BUILD_TOOLS=$BUILD_TOOLS" &&
    echo "PLATFORM=$PLATFORM" &&
    "$JAVAC" -version &&
    "$JAVA" -version &&
    "$JAVAC" -source 8 -target 8 -bootclasspath "$PLATFORM/android.jar" \
        -d "$CLASS_DIR" "$SOURCE" &&
    class_files=("$CLASS_DIR"/com/callagent/host/smoke/*.class)
    [[ -f "${class_files[0]}" ]] &&
    "$BUILD_TOOLS/d8" --lib "$PLATFORM/android.jar" --min-api 26 --output "$DEX_DIR" "${class_files[@]}" &&
    [[ -f "$DEX_DIR/classes.dex" ]] &&
    "$BUILD_TOOLS/aapt" package -f -M "$MANIFEST" -I "$PLATFORM/android.jar" \
        --min-sdk-version 26 --target-sdk-version 34 -F "$UNSIGNED_APK" &&
    python3 - "$UNSIGNED_APK" "$DEX_DIR/classes.dex" <<'PY'
import sys
import zipfile
apk, dex = sys.argv[1:]
with zipfile.ZipFile(apk, "a", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex, "classes.dex")
PY
    "$BUILD_TOOLS/aapt" dump xmltree "$UNSIGNED_APK" AndroidManifest.xml > "$WORK/manifest.xmltree" &&
    python3 - "$WORK/manifest.xmltree" <<'PY'
import re
import sys

lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
tree = "\n".join(lines)
manifest_indent = None
direct_instrumentation = 0
for line in lines:
    match = re.match(r"^(\s*)E: manifest(?:\s|$)", line)
    if match:
        manifest_indent = len(match.group(1))
        continue
    match = re.match(r"^(\s*)E: instrumentation(?:\s|$)", line)
    if match and manifest_indent is not None:
        if len(match.group(1)) == manifest_indent + 2:
            direct_instrumentation += 1

if direct_instrumentation != 1:
    raise SystemExit(
        "AAPT XML tree must contain one instrumentation element directly under manifest"
    )
if "com.callagent.host.smoke.PjsuaSmokeInstrumentation" not in tree or "com.callagent.host\"" not in tree:
    raise SystemExit("AAPT XML tree is missing the expected runner name or target package")
PY
    "$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
} > "$BUILD_LOG" 2>&1 || {
    cat "$BUILD_LOG"
    fail_check harness_build "Java/D8/AAPT/zipalign harness build failed; see $BUILD_LOG"
}
cat "$BUILD_LOG"
record harness_build PASS "javac, D8, AAPT, top-level instrumentation XML tree check, dex packaging, and zipalign succeeded"
CURRENT_CHECK=""

PHASE="sign-harness"
CURRENT_CHECK="signer_match"
export PJSUA_SMOKE_APK_SIGN_STOREPASS="${PJSUA_SMOKE_KEYSTORE_PASSWORD:-android}"
export PJSUA_SMOKE_APK_SIGN_KEYPASS="${PJSUA_SMOKE_KEY_PASSWORD:-$PJSUA_SMOKE_APK_SIGN_STOREPASS}"
KEY_ALIAS="${PJSUA_SMOKE_KEY_ALIAS:-androiddebugkey}"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:PJSUA_SMOKE_APK_SIGN_STOREPASS --key-pass env:PJSUA_SMOKE_APK_SIGN_KEYPASS \
    --out "$SMOKE_APK" "$ALIGNED_APK" > "$OUT/sign.log" 2>&1 || {
    cat "$OUT/sign.log"
    fail_check signer_match "Could not sign the native smoke APK with the host debug keystore"
}
"$BUILD_TOOLS/apksigner" verify --print-certs "$HOST_APK" > "$HOST_APK_VERIFY" 2>&1 \
    || fail_check signer_match "apksigner rejected the host APK; see $HOST_APK_VERIFY"
"$BUILD_TOOLS/apksigner" verify --print-certs "$SMOKE_APK" > "$SMOKE_APK_VERIFY" 2>&1 \
    || fail_check signer_match "apksigner rejected the signed harness; see $SMOKE_APK_VERIFY"
HOST_CERT="$(sed -n 's/.*certificate SHA-256 digest: //p' "$HOST_APK_VERIFY" | head -n 1 | tr '[:upper:]' '[:lower:]')"
SMOKE_CERT="$(sed -n 's/.*certificate SHA-256 digest: //p' "$SMOKE_APK_VERIFY" | head -n 1 | tr '[:upper:]' '[:lower:]')"
[[ -n "$HOST_CERT" && "$HOST_CERT" == "$SMOKE_CERT" ]] \
    || fail_check signer_match "Host and harness signer certificates differ (host=${HOST_CERT:-missing}, smoke=${SMOKE_CERT:-missing})"
record signer_match PASS "host and harness SHA-256 certificate match: $HOST_CERT"
CURRENT_CHECK=""

PHASE="adb-device"
CURRENT_CHECK="adb_device"
adb_cmd() {
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        "$TIMEOUT" --signal=TERM --kill-after=5s "${ADB_TIMEOUT_SECONDS}s" "$ADB" -s "$ANDROID_SERIAL" "$@"
    else
        "$TIMEOUT" --signal=TERM --kill-after=5s "${ADB_TIMEOUT_SECONDS}s" "$ADB" "$@"
    fi
}
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    if ! DEVICE_STATE="$(adb_cmd get-state 2>&1)" || [[ "$DEVICE_STATE" != "device" ]]; then
        fail_check adb_device "ANDROID_SERIAL=${ANDROID_SERIAL} is not ready: ${DEVICE_STATE:-no response}"
    fi
    DEVICE_SERIAL="$ANDROID_SERIAL"
else
    DEVICES_TEXT="$(adb_cmd devices -l 2>&1)" || fail_check adb_device "adb devices failed: $DEVICES_TEXT"
    mapfile -t DEVICE_LIST < <(printf '%s\n' "$DEVICES_TEXT" | awk 'NR > 1 && $2 == "device" {print $1}')
    if (( ${#DEVICE_LIST[@]} != 1 )); then
        fail_check adb_device "Expected exactly one ready adb device (set ANDROID_SERIAL when multiple are attached); found ${#DEVICE_LIST[@]}"
    fi
    DEVICE_SERIAL="${DEVICE_LIST[0]}"
fi
record adb_device PASS "selected device $DEVICE_SERIAL"
CURRENT_CHECK=""

PHASE="install-host"
CURRENT_CHECK="host_install"
adb_cmd install -r -d "$HOST_APK" > "$OUT/install-host.txt" 2>&1 \
    || { cat "$OUT/install-host.txt"; fail_check host_install "Could not install host debug APK"; }
cat "$OUT/install-host.txt"
record host_install PASS "host package installed from $HOST_APK"
CURRENT_CHECK=""

PHASE="install-harness"
CURRENT_CHECK="harness_install"
adb_cmd install -r "$SMOKE_APK" > "$OUT/install-harness.txt" 2>&1 \
    || { cat "$OUT/install-harness.txt"; fail_check harness_install "Could not install native smoke harness APK"; }
cat "$OUT/install-harness.txt"
record harness_install PASS "instrumentation package installed with matching signer"
CURRENT_CHECK=""

PHASE="run-instrumentation"
CURRENT_CHECK="instrumentation_command"
adb_cmd logcat -c > "$OUT/logcat-clear.txt" 2>&1 \
    || fail_check instrumentation_command "Could not clear logcat before the smoke run"
INSTRUMENTATION_ID="com.callagent.host.pjsua2smoke/com.callagent.host.smoke.PjsuaSmokeInstrumentation"
if adb_cmd shell am instrument -w -r "$INSTRUMENTATION_ID" > "$INSTRUMENTATION_LOG" 2>&1; then
    INSTRUMENTATION_EXIT_CODE=0
    record instrumentation_command PASS "adb shell am instrument completed"
else
    INSTRUMENTATION_EXIT_CODE=$?
    record instrumentation_command FAIL "adb shell am instrument exited $INSTRUMENTATION_EXIT_CODE"
fi
CURRENT_CHECK=""
adb_cmd logcat -d -v threadtime > "$LOGCAT_LOG" 2>&1 \
    || { CURRENT_CHECK="instrumentation_command"; fail_check instrumentation_command "Could not capture logcat after the smoke run"; }
CURRENT_CHECK=""
cat "$INSTRUMENTATION_LOG"

PHASE="assert-results"
CURRENT_CHECK="instrumentation_result_code"
RESULT_CODE="$(sed -n 's/^INSTRUMENTATION_CODE: *//p' "$INSTRUMENTATION_LOG" | tail -n 1 | tr -d '\r')"
if [[ "$RESULT_CODE" == "-1" ]]; then
    record instrumentation_result_code PASS "observed INSTRUMENTATION_CODE: -1"
else
    record instrumentation_result_code FAIL "expected INSTRUMENTATION_CODE: -1, observed '${RESULT_CODE:-missing}'"
fi
CURRENT_CHECK=""

CURRENT_CHECK="instrumentation_failure"
FAILURE_VALUE="$(sed -n 's/^INSTRUMENTATION_STATUS: failure=//p' "$INSTRUMENTATION_LOG" | tail -n 1 | tr -d '\r')"
INSTRUMENTATION_ERRORS="$(grep -hE '^(INSTRUMENTATION_(STATUS|RESULT): (failure|shortMsg|longMsg)=|.*FAIL: PJSUA2 native runtime smoke failed)' "$INSTRUMENTATION_LOG" "$LOGCAT_LOG" || true)"
if [[ -z "$FAILURE_VALUE" && -z "$INSTRUMENTATION_ERRORS" ]]; then
    record instrumentation_failure PASS "no failure status or PJSUA2 failure log was observed"
else
    record instrumentation_failure FAIL "instrumentation reported failure: ${FAILURE_VALUE:-${INSTRUMENTATION_ERRORS:-failure field found}}; see captured logs"
fi
CURRENT_CHECK=""

check_status_field() {
    local check="$1" field="$2" expected="$3" actual
    CURRENT_CHECK="$check"
    actual="$(sed -n "s/^INSTRUMENTATION_STATUS: ${field}=//p" "$INSTRUMENTATION_LOG" | tail -n 1 | tr -d '\r')"
    if [[ "$actual" == "$expected" ]]; then
        record "$check" PASS "observed $field=$actual"
    else
        record "$check" FAIL "expected $field=$expected, observed '${actual:-missing}'"
    fi
    CURRENT_CHECK=""
}
check_status_field native_library native_library pjsua2
check_status_field null_audio audio_device null
check_status_field microphone_capture microphone_capture 'not requested'
check_status_field sip_call sip_call 'not attempted'

CODECS="$(sed -n 's/^INSTRUMENTATION_STATUS: codecs=//p' "$INSTRUMENTATION_LOG" | tail -n 1 | tr -d '\r')"
codec_present() {
    local expected="$1" item base
    IFS=',' read -r -a CODEC_IDS <<< "$CODECS"
    for item in "${CODEC_IDS[@]}"; do
        item="${item,,}"
        base="${item%%/*}"
        [[ "$base" == "$expected" ]] && return 0
    done
    return 1
}
for codec in opus pcmu pcma g722; do
    check="codec_$codec"
    CURRENT_CHECK="$check"
    if codec_present "$codec"; then
        record "$check" PASS "codec reported: $codec"
    else
        record "$check" FAIL "required codec $codec missing from '${CODECS:-missing}'"
    fi
    CURRENT_CHECK=""
done

if [[ "$INSTRUMENTATION_EXIT_CODE" != "0" || "$RESULT_CODE" != "-1" || -n "$FAILURE_VALUE" ]]; then
    die "PJSUA2 instrumentation smoke did not satisfy every assertion"
fi
# Require every measured assertion recorded after instrumentation to pass.
if awk -F '\t' '$2 == "FAIL" { failed=1 } END { exit failed ? 0 : 1 }' "$CHECKS_FILE"; then
    die "One or more native smoke checks failed; see $RESULTS and captured logs"
fi

STATUS="PASS"
PHASE="complete"
echo "PJSUA2 JNI smoke passed: native_library=pjsua2 codecs=$CODECS"
