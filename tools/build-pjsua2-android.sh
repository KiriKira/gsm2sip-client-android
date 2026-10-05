#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
readonly pin=a67b8e81b0024b993f47e463c01c67c25cda116f
source_dir=${PJPROJECT_DIR:?Set PJPROJECT_DIR to a clean official pjproject checkout}
out_dir=${OUTDIR:?Set OUTDIR to a build directory outside this repository}
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT}"
export ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
: "${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK with javac and jar}"

mkdir -p "$out_dir"
out_dir=$(cd "$out_dir" && pwd)
case "$out_dir/" in
    "$repo_root/"*) echo "OUTDIR must be outside the repository" >&2; exit 2 ;;
esac

if [[ -n "$(git -C "$source_dir" status --porcelain)" ]]; then
    echo "PJPROJECT_DIR must be clean; refusing to change a worktree with local edits" >&2
    exit 2
fi

"$repo_root/tools/check-pjsip-pin.sh" "$source_dir"
[[ "$(git -C "$source_dir" rev-parse HEAD)" == "$pin" ]]

ndk_host=linux-x86_64
case "$(uname -s)" in
    Darwin) ndk_host=darwin-x86_64 ;;
    Linux) ;;
    *) echo "Unsupported native build host" >&2; exit 2 ;;
esac

cmake_bin=${CMAKE_BIN:-"$ANDROID_HOME/cmake/3.30.5/bin"}
export ANDROID_API=26
export ABIS=arm64-v8a
export OUTDIR="$out_dir"
export PATH="$JAVA_HOME/bin:$cmake_bin:$PATH"

"$source_dir/build/android/build-aar.sh"

aar="$out_dir/dist/pjsua2-2.17-dev.aar"
[[ -s "$aar" ]] || { echo "Upstream build did not produce $aar" >&2; exit 1; }
stage=$(mktemp -d)
cleanup_stage() {
    python3 -c 'import shutil, sys; shutil.rmtree(sys.argv[1], ignore_errors=True)' "$stage"
}
trap cleanup_stage EXIT
unzip -q "$aar" -d "$stage"
[[ -s "$stage/classes.jar" && -s "$stage/jni/arm64-v8a/libpjsua2.so" ]]
"$JAVA_HOME/bin/jar" tf "$stage/classes.jar" | grep -qx 'org/pjsip/pjsua2/Endpoint.class'

readelf_bin="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$ndk_host/bin/llvm-readelf"
so="$stage/jni/arm64-v8a/libpjsua2.so"
loads=$("$readelf_bin" -l "$so" | awk '$1 == "LOAD" { print $NF }')
[[ -n "$loads" ]] || { echo "No ELF LOAD segments found" >&2; exit 1; }
while IFS= read -r alignment; do
    (( alignment >= 16384 )) || { echo "ELF LOAD alignment is below 16 KB: $alignment" >&2; exit 1; }
done <<<"$loads"

mkdir -p "$repo_root/app/libs"
cp "$aar" "$repo_root/app/libs/pjsua2-2.17-dev.aar"
license_dir="$repo_root/app/src/main/assets/licenses/pjsua2"
mkdir -p "$license_dir"
while IFS= read -r entry; do
    case "$entry" in
        META-INF/NOTICE) license_name=NOTICE ;;
        META-INF/licenses/*) license_name=${entry##*/} ;;
        *) continue ;;
    esac
    [[ -n "$license_name" ]] || continue
    unzip -p "$aar" "$entry" > "$license_dir/$license_name"
done < <(unzip -Z1 "$aar")
printf 'PJSIP source: %s\n' "$pin"
sha256sum "$repo_root/app/libs/pjsua2-2.17-dev.aar" "$so"
"$readelf_bin" -d "$so" | sed -n 's/.*Shared library: \[\(.*\)\]/DT_NEEDED \1/p'
echo "Verified generated PJSUA2 Java wrapper and arm64-v8a 16 KB ELF load segments."
