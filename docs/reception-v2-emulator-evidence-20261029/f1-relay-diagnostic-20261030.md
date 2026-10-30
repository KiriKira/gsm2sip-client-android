# F1 relay diagnosis and baseline configuration repair — 2026-10-30

This record supplements `f1-preflight-20261030.md`. It does not count a relay registration probe as an F1 incoming-call, outgoing-call, SMS, or wire1 assertion.

## Diagnostic boundary

A separate Java diagnostic ran under the existing H1 app UID through `run-as app_process`. It loaded `BuildConfig` and the production `KyubeyClient` from the APK under inspection. It did not install that APK, initialize the target Application or ContentProviders, call `SessionStore`, fetch or rotate an FCM token, store a returned endpoint, or call a node API. It read the existing real token inside the device and sent it only to the configured HTTPS relay registration route. No token, URL, endpoint, device identifier, or raw exception message was exported in the result.

The main and current `KyubeyClient.kt` files are byte-identical. Source inspection confirms `/v1/register` only seals the token with a random IV and returns an endpoint: it does not send FCM, write a node endpoint, or persist Worker application state. The seal-key object can be cached in Worker memory; repeated replies differ because of the random IV. All shared-preferences file hashes remained unchanged across the initial diagnostic runs. The diagnostic is separate from a real FCM delivery test.

## Confirmed relay configuration defect

The original F1 APK was verified against SHA-256 `315d42f1f87d0157aee9671e92983cd0cbe218863fb3aaa99ed8b23b9194d4c0`. Its original token was still present and compared equal to the token captured during the failed preflight, without printing either value.

For that APK's configured relay, the new Java probe resolved both IPv4 and IPv6 addresses through both the default resolver and the active network. A Java GET returned HTTP 200. The production registration request reached `KyubeyClient.kt:28`: HTTP 200 had been received, but the endpoint failed the required HTTPS check. A separate real POST confirmed HTTP 200 with an `http` endpoint whose host matched the configured host. Madoka correctly rejected that endpoint. This is a confirmed current deployment configuration problem; it does not establish the original trigger of the earlier `UnknownHostException`.

The original tunnel forwarded HTTP to a local HTTP Wrangler listener. An existing alternative tunnel forwards HTTPS to an HTTPS Wrangler listener and returns HTTPS endpoints. Both relays have all four `src` files byte-identical to Kyubey PR #1 commit `1bb9a2bc976ace3ccedb3a95315e01713559bddf`, with `src/index.js` as entrypoint. Their Firebase project and full service-account configuration compare equal, and the project matches Host's Firebase configuration. Their seal keys differ, so endpoints must be registered anew for the chosen origin rather than copied between relays.

Using the alternative origin with the current APK's identical client returned HTTP 200 and an HTTPS endpoint. No Madoka DNS retry change or HTTP-endpoint exemption is justified by this evidence. The environment repair is to build exact main with the verified HTTPS relay origin, keeping product source and signing identity unchanged.

The app-process harness could obtain the active network and perform its DNS lookup, but `getNetworkCapabilities` encountered a package-attribution `SecurityException`. That is a limitation of the standalone diagnostic context; the production registration and DNS results above completed independently. It is not classified as an app registration defect.

## Device and authentication correction

The precise text `配对会话已过期或被撤销，请重新配对此手机。` belongs to Madoka. The earlier report attributed it to Homura incorrectly. The read-only device check found the expected MainActivity package on each device: Homura `0.4.2-dev` with GatewayService and a primary credential blob; Madoka `0.4.2` with two registry nodes, no active credential blob, and no HostCallService.

Separate server checks found one original active gateway device and one session within its refresh expiry on each of A/B/C. None had a recent heartbeat. Each Homura backup refresh token matched a nonexpired recovery old hash, but not the current refresh hash, and each current access token was expired. The recovery implementation also requires a valid current access token, so an old-hash match alone does not prove recoverability. These are independent reasons to treat the old Homura credential snapshot as stale; the Madoka UI text is not Homura authentication evidence. The prior H1 backup comparison found no matching current or eligible recovery token. No original pairing was revoked or repaired, and no SQL state was changed.

A final pure read-only decryption probe used Homura's existing Android Keystore key directly, without calling `CredentialStore.load`, generating keys, or making authenticated API calls. All three current credential blobs decrypted. Their refresh and access fingerprints matched no current session hashes; each refresh matched a recovery old hash, while current access validity was zero on A/B/C. All Homura shared-preferences hashes stayed unchanged. Thus this authentication finding applies to the currently restored credential blobs as well as the backup snapshot. Fingerprints and device identifiers were compared only in private process memory and were not printed or saved.

## Changes and residual state

- Only diagnostic/build artifacts were created; no diagnostic APK was installed. Read-only DEX artifacts were placed in the device temporary directory.
- The old F1 APK was retained privately. A first environment-repair build candidate failed post-build checks because its relay setting was empty and its version code was not the required baseline value. It was rejected without installation. A corrected exact-main build must pass configuration, version, signer and real-registration checks before use.
- Existing Worker processes and original node pairings were left intact. Node endpoint totals after diagnosis were A=2, B=1, C=0.
- The previous isolated Node C owner/gateway session remains a recorded residual. It was not deleted, reused, or revoked through SQL.
- Current data and encrypted backups must remain protected before a new clean-main baseline is installed. Restoring an archive is not a promise that a rotated session will authenticate again.

Private source, build logs and raw diagnostic process output remain under the existing remote `f-sequence-20261030/relay-java-diagnostic/` directory. Business assertions and subsequent version transitions must be recorded separately.

## Corrected build gate

The corrected build passed all required gates before installation:

| Component | Exact-main source files compared with archive | APK SHA-256 | Version / version code | Signer |
| --- | ---: | --- | --- | --- |
| Madoka | 151, all matching | `c4cf82013d2d4150857be46fa37389fa7f56168a7cdbd127a830b63856139a63` | `0.4.2` / `1000000000` | Original debug certificate |
| Homura | 116, all matching | `0698186ceeff6268482ae431da8ac4e10a02b530a2978119c8de3ba09894e836` | `0.4.2-dev` / `1000000000` | Original debug certificate |

Loading the corrected Madoka APK without installing it confirmed its relay setting matched the verified HTTPS origin. Its actual production client returned HTTP 200 and an HTTPS endpoint using the same real H1 token. A separate POST response inspection confirmed the endpoint's scheme was HTTPS and its origin matched the configured origin. Default and active-network Java DNS lookups succeeded; Java GET returned HTTP 200; push preferences remained unchanged. These gates permit a fresh normal-pairing F1 attempt after backup protection, but do not count as its business assertions.

## Handoff boundary

Completed: relay/provenance/Firebase comparison; real registration diagnostics; exact-main builds and source, version, signer checks; read-only authentication checks; preservation of the previous APK and all prior encrypted backups. No product source changed.

Pending: a new export/stage validation of the currently restored app data; installation or clearing of designated apps; new isolated-owner normal pairing; all F1 business assertions. The earlier encrypted backups passed built-in staging and were restored in the preceding attempt, but their expired authentication is recorded above. No new current-state backup stage is claimed here. The diagnostic agent stopped all device-changing operations before handing UI control to the main thread. Temporary diagnostic DEX/APK files remain under the device temporary directory; no diagnostic APK was installed. The original isolated Node C owner/session remains untouched.
