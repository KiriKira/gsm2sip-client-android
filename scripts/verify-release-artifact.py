#!/usr/bin/env python3
"""Validate a signed host release APK and assemble its public release payload."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Sequence


PACKAGE_NAME = "com.callagent.host"
EXPECTED_ABIS = {"arm64-v8a"}
REQUIRED_NATIVE_LIBRARY = "libpjsua2.so"
APK_NAME = "gsm2sip-host.apk"


class ReleaseValidationError(RuntimeError):
    pass


def run_output(command: Sequence[str], label: str) -> str:
    try:
        completed = subprocess.run(
            list(command),
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as exc:
        raise ReleaseValidationError(f"Could not run {label}: {exc}") from exc
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout).strip()
        raise ReleaseValidationError(
            f"{label} failed with exit code {completed.returncode}"
            + (f": {detail}" if detail else ".")
        )
    return completed.stdout


def normalize_fingerprint(value: str) -> str:
    normalized = value.strip().lower().replace(":", "")
    if not re.fullmatch(r"[0-9a-f]{64}", normalized):
        raise ReleaseValidationError(
            "GSM_RELEASE_CERT_SHA256 must be a 64-character SHA-256 fingerprint "
            "(lowercase hex, optionally separated by colons)."
        )
    return normalized


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def package_metadata(aapt_output: str) -> tuple[str, int, str]:
    package_line = next(
        (line for line in aapt_output.splitlines() if line.startswith("package:")), None
    )
    if package_line is None:
        raise ReleaseValidationError("aapt did not report APK package metadata.")
    values = dict(re.findall(r"([A-Za-z]+)='([^']*)'", package_line))
    try:
        return values["name"], int(values["versionCode"]), values["versionName"]
    except (KeyError, ValueError) as exc:
        raise ReleaseValidationError(f"Could not parse aapt package line: {package_line}") from exc


def native_abis(apk: Path) -> tuple[set[str], set[str]]:
    try:
        with zipfile.ZipFile(apk) as archive:
            entries = archive.namelist()
    except (OSError, zipfile.BadZipFile) as exc:
        raise ReleaseValidationError(f"APK is not a readable ZIP archive: {exc}") from exc

    native_entries = [name for name in entries if name.startswith("lib/") and name.endswith(".so")]
    abis = {parts[1] for name in native_entries if len(parts := name.split("/")) == 3}
    return abis, set(native_entries)


def validate_non_debuggable(apkanalyzer: str, apk: Path) -> None:
    output = run_output(
        [apkanalyzer, "manifest", "debuggable", str(apk)], "apkanalyzer debuggable check"
    ).strip().lower()
    if output != "false":
        raise ReleaseValidationError(
            "Release APK must be non-debuggable; apkanalyzer reported "
            + (repr(output) if output else "no debuggable value")
            + "."
        )


def validate_certificate(apksigner: str, apk: Path, expected: str) -> str:
    output = run_output(
        [apksigner, "verify", "--verbose", "--print-certs", str(apk)],
        "apksigner verification",
    )
    fingerprints = re.findall(
        r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", output, flags=re.IGNORECASE
    )
    if len(fingerprints) != 1:
        raise ReleaseValidationError(
            f"Expected exactly one APK signing certificate SHA-256 digest; found {len(fingerprints)}."
        )
    actual = normalize_fingerprint(fingerprints[0])
    if actual != expected:
        raise ReleaseValidationError(
            "APK signing certificate does not match GSM_RELEASE_CERT_SHA256 "
            f"(expected {expected}, got {actual})."
        )
    return actual


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path, help="Signed release APK to validate")
    parser.add_argument("--aapt", required=True, help="Path to Android build-tools aapt")
    parser.add_argument("--apkanalyzer", required=True, help="Path to Android SDK apkanalyzer")
    parser.add_argument("--apksigner", required=True, help="Path to Android build-tools apksigner")
    parser.add_argument("--zipalign", required=True, help="Path to Android build-tools zipalign")
    parser.add_argument("--expected-cert-sha256", required=True, help="Expected release certificate fingerprint")
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--release-kind", required=True, choices=("prerelease", "stable"))
    parser.add_argument("--output-dir", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if not args.apk.is_file():
            raise ReleaseValidationError(f"Release APK does not exist: {args.apk}")
        if args.version_code not in range(1, 2_100_000_001):
            raise ReleaseValidationError("--version-code must be between 1 and 2100000000.")
        if not re.fullmatch(r"[0-9a-fA-F]{40}", args.source_commit):
            raise ReleaseValidationError("--source-commit must be a full 40-character Git commit SHA.")
        if not args.version_name.strip() or not args.release_tag.strip():
            raise ReleaseValidationError("--version-name and --release-tag must not be empty.")

        expected_cert = normalize_fingerprint(args.expected_cert_sha256)
        actual_cert = validate_certificate(args.apksigner, args.apk, expected_cert)
        package, version_code, version_name = package_metadata(
            run_output([args.aapt, "dump", "badging", str(args.apk)], "aapt package metadata check")
        )
        if package != PACKAGE_NAME:
            raise ReleaseValidationError(f"Expected package {PACKAGE_NAME}, found {package}.")
        if version_code != args.version_code:
            raise ReleaseValidationError(
                f"APK versionCode {version_code} does not match expected {args.version_code}."
            )
        if version_name != args.version_name:
            raise ReleaseValidationError(
                f"APK versionName {version_name!r} does not match expected {args.version_name!r}."
            )

        validate_non_debuggable(args.apkanalyzer, args.apk)
        run_output(
            [args.zipalign, "-c", "-P", "16", "4", str(args.apk)],
            "16 KB zipalign verification",
        )
        abis, native_entries = native_abis(args.apk)
        if abis != EXPECTED_ABIS:
            raise ReleaseValidationError(
                "Unexpected native APK ABIs: expected exactly "
                f"{sorted(EXPECTED_ABIS)}, found {sorted(abis)}."
            )
        required_library_entry = f"lib/arm64-v8a/{REQUIRED_NATIVE_LIBRARY}"
        if required_library_entry not in native_entries:
            raise ReleaseValidationError(f"APK is missing required native library {required_library_entry}.")

        args.output_dir.mkdir(parents=True, exist_ok=True)
        output_apk = args.output_dir / APK_NAME
        shutil.copyfile(args.apk, output_apk)
        apk_digest = sha256_file(output_apk)
        manifest = {
            "source_commit": args.source_commit.lower(),
            "version": version_name,
            "version_code": version_code,
            "certificate_sha256": actual_cert,
            "package_name": package,
            "release_tag": args.release_tag,
            "release_kind": args.release_kind,
            "native_abis": sorted(abis),
            "artifacts": [
                {
                    "name": APK_NAME,
                    "sha256": apk_digest,
                    "size": output_apk.stat().st_size,
                }
            ],
        }
        manifest_path = args.output_dir / "release-manifest.json"
        manifest_path.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        sums = [
            f"{apk_digest}  {APK_NAME}",
            f"{sha256_file(manifest_path)}  release-manifest.json",
        ]
        (args.output_dir / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="ascii")
    except ReleaseValidationError as exc:
        print(f"release validation failed: {exc}", file=sys.stderr)
        return 1

    print(
        "Validated signed release APK: "
        f"package={PACKAGE_NAME} version={version_name} code={version_code} "
        f"certificate_sha256={actual_cert} native_abis={','.join(sorted(abis))} "
        f"apk_sha256={apk_digest}"
    )
    print(f"Release payload: {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
