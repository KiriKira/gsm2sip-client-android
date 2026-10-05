# Signed Android releases

The release workflow builds the host app with one persistent Android signing
identity. It validates the APK signature, package name, version, native ABI and
16 KB ZIP alignment before it makes a release payload available. It does not
generate or substitute a development key during a build.

## Release triggers

| Trigger | Android version name | GitHub release |
|---|---|---|
| Push to `main` | `0.3.0-build-${run_number}-${sha7}` | Prerelease tagged `build-${run_number}-${sha7}` |
| Push of a `v*` tag | The exact tag, such as `v0.4.0` | Stable release under that tag |
| Manual run on `main`, `publish=true` | `0.3.0-build-${run_number}-${sha7}` | Prerelease |
| Manual run on `main`, `publish=false` | `0.3.0-build-${run_number}-${sha7}` | Signed build and validation only |
| Manual run on another branch or any tag | `0.3.0-build-${run_number}-${sha7}` | Build and validation only; publication is disabled |

Manual runs default `publish` to true. The publisher job also checks the event
and ref independently: manual publication is permitted only from `main`, and
`v*` tags publish only when pushed. A manual run on another ref cannot publish,
even if its input is true. Manual runs on any non-main ref, including tags, do
not receive the release signing secrets; they run the debug assemble, unit
tests and lint for build validation.

The Android `versionCode` is `10000 + GITHUB_RUN_NUMBER` and must be within
`1..2100000000`. The current app version code is 4. Retrying the same workflow
run preserves its version code; a new workflow run receives a new run number.
The stable tag itself supplies `versionName` for a pushed version tag.

## Configure the permanent signing identity

Use Python 3.10 or newer, JDK 17 or newer with `keytool`, and GitHub CLI
(`gh`). Authenticate with permission to manage Actions secrets and variables
for this repository, then run the repository helper:

```sh
gh auth login
python3 scripts/configure-release-signing.py --repo KiriKira/gsm2sip-client-android
```

The helper creates one permanent signing keystore, asks GitHub to store its
credentials as Actions secrets, and pins the public certificate fingerprint
as an Actions variable. By default its private backup is stored under
`~/.local/share/gsm2sip-release-signing/KiriKira--gsm2sip-client-android`.
Repeated runs reuse that identity; they do not rotate the key. Keep this
private backup and an offline backup of the keystore and passwords. Never
commit that backup or copy it into the release artifact. The helper refuses to
silently replace an existing signing identity.

To import an existing signing identity instead of creating one, pass its path
and alias. The helper prompts for the passwords without putting them on the
command line:

```sh
python3 scripts/configure-release-signing.py \
  --repo KiriKira/gsm2sip-client-android \
  --keystore /private/path/release.keystore \
  --alias release
```

The workflow requires these exact Actions secrets:

- `GSM_RELEASE_KEYSTORE_BASE64`
- `GSM_RELEASE_STORE_PASSWORD`
- `GSM_RELEASE_KEY_ALIAS`
- `GSM_RELEASE_KEY_PASSWORD`

It also requires the public Actions variable `GSM_RELEASE_CERT_SHA256`. The
fingerprint may be 64 hexadecimal characters, optionally colon-separated. The
build fails with the missing setting's name if a required secret or variable
is absent. The secret keystore is decoded into the runner's temporary
directory with owner-only permissions and is not uploaded.

The `release` Gradle variant reads `GSM_RELEASE_STORE_FILE`,
`GSM_RELEASE_STORE_PASSWORD`, `GSM_RELEASE_KEY_ALIAS`, and
`GSM_RELEASE_KEY_PASSWORD`. It also reads `GSM_RELEASE_VERSION_CODE` and
`GSM_RELEASE_VERSION_NAME` from the workflow. A release packaging task fails
clearly if these values or the keystore file are missing.

## Release assets and verification

The workflow publishes these payload files:

- `gsm2sip-host.apk`
- `release-manifest.json`
- `SHA256SUMS.txt`

The manifest records the source commit, version name and code, package name,
release tag, certificate SHA-256, native ABIs, and the APK's SHA-256 and size.
The checksum file covers the APK and manifest. From the downloaded asset
directory, verify those checksums with:

```sh
sha256sum -c SHA256SUMS.txt
```

The builder also runs `apksigner verify`, compares the APK certificate to
`GSM_RELEASE_CERT_SHA256`, confirms package `com.callagent.host` and matching
version metadata, checks that the APK is non-debuggable, and runs
`zipalign -c -P 16 4`. The pinned PJSUA2 AAR currently supplies only
`arm64-v8a`; the release verifier requires that ABI and `libpjsua2.so` in the
APK. If the AAR's native ABI set changes, update the release verifier and this
documentation to match the newly inspected artifact.

The publisher has `contents: write` permission and downloads the verified
bundle without checking out source. Its helper checks the tag, source commit,
manifest, checksums and asset digests. Published releases are immutable; an
identical rerun is a no-op, while conflicting assets or a tag targeting another
commit fail without overwriting the published release.

## Move from a locally installed debug build

Android will not install a release-signed APK over an app signed by a different
debug certificate. Export any SMS archive you need to keep before uninstalling
the debug app. Then uninstall it, install the first release APK, and pair the
host again. Later releases signed with the configured permanent key can update
that installation in place.
