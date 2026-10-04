#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_ONLY="${1:-}"
[[ -z "$BUILD_ONLY" || "$BUILD_ONLY" == '--build-only' ]] || {
    echo 'Usage: run-paired-ui-fixture.sh [--build-only]' >&2
    exit 2
}
OUT="${PAIRED_UI_FIXTURE_OUT_DIR:-$ROOT/artifacts/android-ui-smoke/paired-fixture}"
if [[ "$OUT" != /* ]]; then OUT="$ROOT/$OUT"; fi
mkdir -p "$OUT"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]] || { echo 'Set ANDROID_SDK_ROOT or ANDROID_HOME to the Android SDK directory' >&2; exit 1; }
SDK_ROOT="$(cd -- "$SDK_ROOT" && pwd)"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
PLATFORM="$SDK_ROOT/platforms/android-35"
for required in "$BUILD_TOOLS/aapt" "$BUILD_TOOLS/d8" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$PLATFORM/android.jar"; do
    [[ -x "$required" || -f "$required" ]] || { echo "Missing required SDK component: $required" >&2; exit 1; }
done

HOST_APK="${PJSUA_SMOKE_HOST_APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
if [[ "$HOST_APK" != /* ]]; then HOST_APK="$ROOT/$HOST_APK"; fi
[[ -f "$HOST_APK" ]] || { echo "Host debug APK not found: $HOST_APK" >&2; exit 1; }

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
[[ -f "$KEYSTORE" ]] || { echo "Host debug keystore not found: $KEYSTORE" >&2; exit 1; }

WORK="$OUT/build"
CLASS_DIR="$WORK/classes"
DEX_DIR="$WORK/dex"
UNSIGNED_APK="$WORK/paired-ui-fixture-unsigned.apk"
ALIGNED_APK="$WORK/paired-ui-fixture-aligned.apk"
FIXTURE_APK="$OUT/paired-ui-fixture.apk"
MANIFEST="$ROOT/scripts/paired-ui-fixture/AndroidManifest.xml"
SOURCE="$ROOT/scripts/paired-ui-fixture/src/com/callagent/host/smoke/PairedUiFixtureInstrumentation.java"
cleanup_build_outputs() {
    rm -f "$FIXTURE_APK" "$FIXTURE_APK.idsig" "$UNSIGNED_APK" "$ALIGNED_APK"
    rm -rf "$WORK"
}
trap cleanup_build_outputs EXIT
rm -rf "$WORK"
mkdir -p "$CLASS_DIR" "$DEX_DIR"
[[ -f "$MANIFEST" && -f "$SOURCE" ]] || { echo 'Fixture manifest or Java source is missing' >&2; exit 1; }

build_fixture() {
    javac -source 8 -target 8 -bootclasspath "$PLATFORM/android.jar" -d "$CLASS_DIR" "$SOURCE" || return 1
    class_files=("$CLASS_DIR"/com/callagent/host/smoke/*.class)
    [[ -f "${class_files[0]}" ]] || return 1
    "$BUILD_TOOLS/d8" --lib "$PLATFORM/android.jar" --min-api 26 --output "$DEX_DIR" "${class_files[@]}" || return 1
    "$BUILD_TOOLS/aapt" package -f -M "$MANIFEST" -I "$PLATFORM/android.jar" \
        --min-sdk-version 26 --target-sdk-version 34 -F "$UNSIGNED_APK" || return 1
    "$BUILD_TOOLS/aapt" dump xmltree "$UNSIGNED_APK" AndroidManifest.xml > "$WORK/manifest.xmltree" || return 1
    python3 - "$WORK/manifest.xmltree" <<'PY' || return 1
import re
import sys

lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
tree = "\n".join(lines)
manifest_indent = None
instrumentation = 0
for line in lines:
    match = re.match(r"^(\s*)E: manifest(?:\s|$)", line)
    if match:
        manifest_indent = len(match.group(1))
        continue
    match = re.match(r"^(\s*)E: instrumentation(?:\s|$)", line)
    if match and manifest_indent is not None and len(match.group(1)) == manifest_indent + 2:
        instrumentation += 1

if instrumentation != 1:
    raise SystemExit("expected one instrumentation element directly under manifest")
if ("com.callagent.host.smoke.PairedUiFixtureInstrumentation" not in tree or
        'package="com.callagent.host.paireduismoke"' not in tree or
        not re.search(r'android:targetPackage\(0x[0-9a-fA-F]+\)="com.callagent.host"', tree)):
    raise SystemExit("fixture runner name, instrumentation package, or host target is missing")
PY
    python3 - "$UNSIGNED_APK" "$DEX_DIR/classes.dex" <<'PY' || return 1
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "a", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(sys.argv[2], "classes.dex")
PY
    "$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED_APK" "$ALIGNED_APK" || return 1
}
build_fixture > "$OUT/build.log" 2>&1 || {
    cat "$OUT/build.log"
    echo "Fixture instrumentation build failed; see $OUT/build.log" >&2
    exit 1
}
cat "$OUT/build.log"

export PAIRED_UI_FIXTURE_STOREPASS="${PJSUA_SMOKE_KEYSTORE_PASSWORD:-android}"
export PAIRED_UI_FIXTURE_KEYPASS="${PJSUA_SMOKE_KEY_PASSWORD:-$PAIRED_UI_FIXTURE_STOREPASS}"
KEY_ALIAS="${PJSUA_SMOKE_KEY_ALIAS:-androiddebugkey}"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:PAIRED_UI_FIXTURE_STOREPASS --key-pass env:PAIRED_UI_FIXTURE_KEYPASS \
    --out "$FIXTURE_APK" "$ALIGNED_APK"
"$BUILD_TOOLS/apksigner" verify --print-certs "$HOST_APK" > "$OUT/host-apksigner-verify.txt" 2>&1
"$BUILD_TOOLS/apksigner" verify --print-certs "$FIXTURE_APK" > "$OUT/fixture-apksigner-verify.txt" 2>&1
HOST_CERT="$(sed -n 's/.*certificate SHA-256 digest: //p' "$OUT/host-apksigner-verify.txt" | head -n 1 | tr '[:upper:]' '[:lower:]')"
FIXTURE_CERT="$(sed -n 's/.*certificate SHA-256 digest: //p' "$OUT/fixture-apksigner-verify.txt" | head -n 1 | tr '[:upper:]' '[:lower:]')"
[[ -n "$HOST_CERT" && "$HOST_CERT" == "$FIXTURE_CERT" ]] || {
    echo 'Host and fixture instrumentation signer certificates differ' >&2
    exit 1
}
if [[ "$BUILD_ONLY" == '--build-only' ]]; then
    echo "Fixture instrumentation build, signing, and host certificate match succeeded: $OUT"
    exit 0
fi

ADB="$(command -v adb || true)"
[[ -n "$ADB" ]] || { echo 'adb must be available on PATH' >&2; exit 1; }
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB_ARGS=(-s "$ANDROID_SERIAL")
    DEVICE_SERIAL="$ANDROID_SERIAL"
else
    ADB_ARGS=()
    mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1}')
    (( ${#DEVICES[@]} == 1 )) || { echo 'Expected one ready adb device or set ANDROID_SERIAL' >&2; exit 1; }
    DEVICE_SERIAL="${DEVICES[0]}"
fi
adb_cmd() { "$ADB" "${ADB_ARGS[@]}" "$@"; }
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    [[ "$(adb_cmd get-state)" == 'device' ]] || { echo "ADB device is unavailable: $DEVICE_SERIAL" >&2; exit 1; }
fi
python3 "$ROOT/scripts/paired-ui-fixture.py" \
    --apk "$HOST_APK" \
    --package com.callagent.host \
    --device-profile '7.6in Foldable' \
    --artifact-dir "$OUT" \
    --serial "$DEVICE_SERIAL" \
    --fixture-apk "$FIXTURE_APK" \
    --fixture-component 'com.callagent.host.paireduismoke/com.callagent.host.smoke.PairedUiFixtureInstrumentation'
