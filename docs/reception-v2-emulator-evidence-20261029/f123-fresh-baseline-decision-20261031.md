# F1–F3 fresh legacy baseline: decision and artifact audit

This is a new, isolated F1–F3 execution plan and a read-only artifact audit. It does not claim that the fresh F1, F2, or F3 business assertions have run.

## Corrected scope and reversibility

F1 requires main-version Homura and Madoka to work against the R1 SoulGem/Kyubey deployment. It does **not** require the pairing used by an earlier attempt. Kiri has authorized a clean test AVD, or data clearing of the designated test AVD, same-signer historical APK installation, and fresh pairing. Therefore recovering the original historical Android Keystore is not a prerequisite for this fresh route.

The fresh route will create a new, clearly named test owner, short-lived gateway/client pairing codes, sessions, SIM proposal/confirmation, and normal reception/push records through `mahogsm-admin owner create`, `pairing-code create`, and production application APIs. There will be no manual SQL or direct database writes. Historical owners, identities, SIM mappings, and sessions will not be reused or changed.

This is isolated but not losslessly reversible: owner/device/SIM/audit rows and monotonic revisions can remain after a test. The supported `POST /v1/auth/revoke` can invalidate the new sessions and remove their push endpoints, but is not an owner/SIM deletion API. No unsupported deletion is promised. Device-local clean data is disposable only after the main thread explicitly hands over that designated AVD; current GW/H1 state needed by C3 must first be preserved. App-data tar files alone do not guarantee restoration of working authenticated sessions after refresh-token rotation.

## APK origin and independent verification

The legacy APKs are test-machine **Gradle debug builds**, not CI release downloads. The retained logs contain `:app:packageDebug`, `:app:assembleDebug`, and successful builds. The logs themselves do not retain the complete original shell command; the build commands below are reproducible commands for a new environment-config rebuild, not a claim to have recovered the old shell invocation.

Root for the following paths: `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/`.

| Component | Source | APK path under root | SHA-256 |
| --- | --- | --- | --- |
| F1 GW | `31f65a7d5aa7fbca7df575d73a87fcf3b018b254` | `private/f1-sim-confirm-20261031/gateway-main.apk` | `0698186ceeff6268482ae431da8ac4e10a02b530a2978119c8de3ba09894e836` |
| F1 Host, previous relay configuration | `5b721c3b80763264a73dbe128364529e11801c97` | `private/f123-fresh-20261031/host-main-previous-relay.apk` (preserved before rebuild; also `private/f2-20261031/host-installed-before.apk`) | `c4cf82013d2d4150857be46fa37389fa7f56168a7cdbd127a830b63856139a63` |
| F2 GW | `a41dd19` | `build/gateway-r1/a41/app-debug-same-key.apk` | `8cb9898986a80658852fed7ee622800640fedf99a9fb8a918338b66b44a959cf` |
| F3 Host, previous relay configuration | PR build; source must be revalidated for the next rebuild | `private/f3-relay-rebuild-20261031/host-pr-correct-relay.apk` | `dbc2ce502b0a0024fc423f3b5480195b89a37e0ca1d8001c0ac47d6b1c9f4205` |

All four APKs were rechecked with SDK 35.0.0 `aapt dump badging` and `apksigner verify --print-certs`: versionCode `1000000000`; GW versionName `0.4.2-dev`, Host `0.4.2`; signing certificate SHA-256 `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28`. The same certificate allows Android package replacement while retaining data. The previous Host relay origins must be replaced with the verified live HTTPS relay before the new F run.

Retained legacy build records:

| Log under root | Recorded result | Log SHA-256 |
| --- | --- | --- |
| `private/f-sequence-20261030/relay-java-diagnostic/gateway-main-corrected-build.private.log` | `BUILD SUCCESSFUL in 1m 24s` | `d252cbe35c5956e54065fed28854075d8aa620368fe189636635bcb9fc9bba38` |
| `private/f-sequence-20261030/relay-java-diagnostic/host-main-corrected-build.private.log` | `BUILD SUCCESSFUL in 1m 40s` | `820bab9e4e436192d838b1eafaf030b8d717f9c9d15eebf716ea9a8a14ab02cc` |

An independent comparison of **all archived `src/main` files** against the remote legacy build directories found GW 122 files and Host 153 files byte-identical, zero mismatches. This count includes non-Kotlin resources and therefore differs from earlier Kotlin-only counts. Source archives and hashes:

- `reception-v2-gateway-main-31f65a7.tar.gz`: `6c3bbff8ce35ca82f480a71a4e6cf60c43140812263cb1a2e9eae46d7a0ce28d`.
- `reception-v2-host-main-5b721c3.tar.gz`: `5066bc53d5d97e988722396fecebc493321bf78ce75e36445f6ed7161b8b0efe`.

Rebuild in the corresponding already verified source directory, with the existing debug signing key and existing Firebase configuration, after setting `ANDROID_HOME`/`ANDROID_SDK_ROOT` to the test SDK and setting `MAHOGSM_PUSH_RELAY_URL` from a private, verified relay configuration for Host:

```bash
GSM_RELEASE_VERSION_CODE=1000000000 ./gradlew --no-daemon :app:assembleDebug
```

Before use, recheck source contents, package/version, signer, APK hash, x86_64 native libraries, embedded HTTPS relay, Firebase project match, and production registration success. An HTTP registration response alone does not establish real FCM delivery.

## Android Keystore versus signing key

Homura's `CredentialStore` uses Android Keystore alias `gsm2sip.control.session.v1` for an **AES SecretKey**, with AES/GCM encryption of session blobs. Earlier references to that alias as a “private key” were imprecise. It is device/app credential encryption material. A raw shared-preferences tar contains the encrypted blob, not an exported copy of that key.

The build-machine `debug.keystore` holds the separate APK-signing private key. Its certificate is the fingerprint listed above. The matching signatures and existing builds establish that this signing identity remains usable; they say nothing about decryption of an older AVD's session blob.

**Retraction of the earlier round mix-up:** the missing historical API36 AVD belongs to the **2026-10-21** 0.4 round. The **2026-10-30 F1** GW was already API34, and that named API34 AVD still exists with a usable current session. The prior `0245714`/`f1-confirm-api-preflight-20261031.md` wording that treated “original F1 API36 snapshot missing” as proof of a missing F1 encryption key conflated those rounds and is withdrawn. Different current pairing/owner/SIM values do not establish a different AES key.

The raw app-data tar does not include an exported Android Keystore key, but that alone does **not** prove the still-existing API34 AVD cannot decrypt its old blob. This attempt has not tested decryption of that old blob. The current device's usable session establishes working current credential encryption/decryption; the signing key also remains usable. There is presently **no evidence that any key was lost, nor an identified step at which a key was lost**. The missing API36 AVD proves only that the inspected inventory did not contain that separate old full-device state.

Read-only inspection of both main and current `CredentialStore.kt` gives the exact lifecycle: `key()` reuses the existing AES alias when available and generates only when absent (or after a `getKey` exception, where it attempts deletion); normal `SessionVault.clear()` removes the credential blob and cache, **not the key**; normal re-pairing saves a replacement blob with that same key. `persist()` attempts key deletion/recreation only after an encryption exception. No observed log or artifact ties either exceptional branch, app-data clearing, uninstall, or AVD wiping to a lost historical F1 key. Source code showing an exceptional deletion path is not evidence that it ran.

The built-in full-backup flow is also distinct from a raw tar: it exports readable session JSON into the password-encrypted backup and re-encrypts imported sessions under the destination key. Its usability still depends on valid server authentication after restoration. The fresh isolated route avoids spending time recovering an old session; a genuinely new F AVD obtains its own normal credential encryption key during pairing.

## SIM states and historical rounds

Local `SimRegistry.identityState` and server `sim_mappings.identity_verified` are distinct checks. A local confirmation can make the same slot/subscription locally usable without changing a server row. Conversely a verified server row does not prove that the local app can currently resolve the SIM's subscription/PhoneAccount. Neither state is evidence that the other layer passed.

The prior evidence identifies three different states, not one SIM that can be freely substituted:

1. The 2026-10-21 historical 0.4 round has a matching app-data/SQL pair with an active, verified revision-1 mapping, but no matching currently verified mapping on R1 A/B/C. It is historical provenance, not the fresh F baseline.
2. The 2026-10-30 isolated Node C F1 attempt left its own local revision-2 mapping `UNVERIFIED`; the server mapping was revision 2 with `identity_verified=false`. Its SMS/FCM subtest passed, but call offers stopped before business assertions. This is an unmet call prerequisite, not a demonstrated product call failure.
3. The currently running API34 GW belongs to the 2026-10-31 isolated D1 setup: local revision 1, `LOCAL_CONFIRMED`, with a different owner/SIM from the earlier F1 archive. Its working session cannot be substituted for the old F1 identity. It must be preserved until C3 no longer needs it.

Source inspection explains a previously observed downgrade: when an ordinary app UID can no longer obtain the ICCID fingerprint, `SimRegistry.snapshot()` can invalidate a formerly `FINGERPRINT_VERIFIED` binding; server state is unchanged by that local read. The runner's shell-permission adoption is process-scoped and dropped in `finally`. The next F sequence must record both local identity resolution and the authenticated server mapping before calls and after upgrade, rather than relying on a prior UI label.

## Executable sequence and stop conditions

1. Obtain the main thread's AVD handover; preserve current C3 state. Use separate clean F GW/API34 and Host/API35 Play devices where space permits, otherwise use only explicitly handed-over designated test devices. No historical identity restoration is needed.
2. Verify a live HTTPS Kyubey PR source with the real Firebase credentials; rebuild main and PR Host packages against that same relay. Record hashes and signer before installing.
3. Install exact-main GW/Host on clean data, create one new isolated Node B owner and short-lived pairing codes, and pair through the production clients. Use the existing emulator-only GatewayJointInstrumentation solely for cellular PCM substitution and supported pairing/SIM APIs. Verify one active SIM, consistent local/server UUID/revision/slot, and a unique reversible subscription/PhoneAccount resolution. Stop before calls on any mismatch or unverified mapping.
4. F1: execute a real emulator GSM incoming call, normal Host outgoing dial, and emulator SMS. Check the corresponding server state and actual Host UI, and confirm the legacy version-1 path plus real FCM receipt. The exact-main Host logger prints priority rather than a literal `wire=1` field: establish wire1 from the server's effective protocol, its legacy payload path, and the old callback's accepted schema, recording that evidence boundary. The PCM substitution means no physical cellular/HAL audio claim.
5. Record every F1 subtest independently, then replace GW with `adb install -r` using the same signer/versionCode, keeping GW data, owner, SIM, and settings. Repeat all F1 assertions; capture `remaining_ring_ms` for F2. Under Kiri's latest instruction, a reproduced old-main business failure (including the known outgoing `Missing call details` bug) is retained as a baseline failure and stops that subtest, not the subsequent supported upgrades. It is not relabeled as an environment blocker; neither old-main source nor timeout is modified to make it pass.
6. Keep that same Host pairing/data and replace Host with the PR APK via `adb install -r`. Wait one minute. Verify reception protocol version 2 and preserved switch values; verify a previously notified SMS produces no duplicate notification; repeat incoming/outgoing/SMS with real incoming push delivery for F3.
7. Each call starts with modem/Telecom/server idle and is explicitly ended. Preserve private raw logs and sanitized hashes. No synthetic pushes, API-created calls, root/Magisk, product changes, or manual database writes.

F2 still uses the old Host and can retain its known outgoing limitation. F3 uses the current Host containing that fix; the result must distinguish the three version combinations and must not call the whole sequence PASS if a required baseline/F2 subtest failed.

Current status at this document's creation: artifact/source audit completed; **no fresh owner/API write, AVD reset, installation, F1 call/SMS assertion, F2 upgrade, or F3 migration has been performed by this new attempt**.

## Preparation resumed after root-disk expansion

The test root filesystem is now 92 GiB, with approximately 35 GiB available; no cleanup was necessary. Two independent, clean AVD configurations were created with `avdmanager create avd`, using already installed official images and distinct userdata paths:

- `reception-f1-gw-api34`: `system-images;android-34;default;x86_64`.
- `reception-f1-h1-api35-play`: `system-images;android-35;google_apis_playstore;x86_64`.

They have not yet been started. The running C3 GW/H1 and E2/E3 H3 are untouched. To limit memory pressure while E2/E3 run, the F AVDs will only start after handover.

The main thread approved **Node B** for the new isolated F owner, reusing B's already configured reachable private TURN sidecar. This explicitly differs from the test plan's default A and the older attempt's C. It avoids changing A/C's TURN configuration or any historical owner's records. B's current TURN configuration is to remain until both F and C3 are finished; a successful TURN smoke alone will not count as a call result.

The recovered real Kyubey TLS worker, SSH reverse forward, and temporary HTTPS origin were checked: remote public GET returned HTTP 200. A prior C3 rebuild had failed because shell quoting removed Groovy string quotes in the init script; its arm64-only stale output was not accepted as a new build. A corrected **external** Gradle init script now overrides only emulator ABI packaging to `x86_64`; archived product `src/main` files remain unchanged. Old and new Host builds use the same verified current relay, existing Firebase configuration, versionCode, and signing identity, with one Gradle worker and a 768 MiB JVM. No relay URL or credential is included here.
