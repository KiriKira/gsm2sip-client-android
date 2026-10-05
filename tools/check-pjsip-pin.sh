#!/usr/bin/env bash
set -euo pipefail

readonly upstream="https://github.com/pjsip/pjproject.git"
readonly pin="a67b8e81b0024b993f47e463c01c67c25cda116f"
readonly dns_fix="d9514ce3ef56eb12ce59ec6148cd703ca9fae731"
readonly san_nul_fix="43d3bd77bb6833eab4c493503b8d564a754ddfdd"
readonly cn_san_fix="44869567853c2d368d8be5bd20fafdab0c3f1335"
readonly ip_san_fix="764d73433798ea61a5fb7c1e00440c11958444e3"
source_dir="${1:-third_party/pjproject}"

if [[ ! -d "$source_dir/.git" ]]; then
  mkdir -p "$(dirname "$source_dir")"
  git clone --filter=blob:none --no-checkout "$upstream" "$source_dir"
fi

git -C "$source_dir" fetch --filter=blob:none origin "$pin"
git -C "$source_dir" checkout --detach "$pin"
actual="$(git -C "$source_dir" rev-parse HEAD)"
if [[ "$actual" != "$pin" ]]; then
  echo "Expected pjproject $pin, got $actual" >&2
  exit 1
fi

for fix in "$dns_fix" "$san_nul_fix" "$cn_san_fix" "$ip_san_fix"; do
  git -C "$source_dir" cat-file -e "$fix^{commit}"
  if ! git -C "$source_dir" merge-base --is-ancestor "$fix" "$pin"; then
    echo "Expected upstream fix $fix to be an ancestor of $pin" >&2
    exit 1
  fi
done

echo "Verified official pjproject source pin $actual and all recorded TLS/DNS fix ancestors."
echo "This script does not build, package, or approve a SIP SDK binary."
