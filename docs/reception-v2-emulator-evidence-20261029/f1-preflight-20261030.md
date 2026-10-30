# F1 real-push preflight and rollback — 2026-10-30

## Tested sources and environment

- SoulGem: PR #33 source `152821079f87af7958d30cebf0cf8c0c316ed8e0`, image `soulgem-aio:reception-r1-1528210`, image config `sha256:1f6fd76b942af40b751ede535ade03604de858ca4a40054406d4afb56fa5c1d4` on A/B/C.
- Kyubey: PR #1 source `1bb9a2bc976ace3ccedb3a95315e01713559bddf`.
- Homura F1 baseline: `main` source `31f65a7d5aa7fbca7df575d73a87fcf3b018b254`; APK SHA-256 `c0042dd263184c6e7c83d8bacb45122d60b835f3cf221fa659983aae5e865e21`.
- Madoka F1 baseline: `main` source `5b721c3b80763264a73dbe128364529e11801c97`; rebuilt with the active relay setting; APK SHA-256 `315d42f1f87d0157aee9671e92983cd0cbe218863fb3aaa99ed8b23b9194d4c0`.
- Both APKs use release version code `1000000000` and the same debug certificate, SHA-256 `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28`.
- GW: API 34 ordinary emulator. H1: API 35 Google Play emulator. Both were online; H1 was configured for real FCM.

## F1 boundary and result

The designated GW/H1 test apps were rebased to the exact main builds and an isolated test owner was created on Node C. Homura joined the owner, its emulator SIM was confirmed, and its service was started; H1 then joined the same owner. A/B/C were healthy.

Before any call or SMS assertion, Madoka's real push-registration preflight repeatedly failed with `UnknownHostException`. The configured relay was the active Worker. Both the shell and the app UID resolved the same relay hostname to the same address, but the registration request still failed. A controlled retry after restarting the app process at approximately 13:00Z failed the same way. The app's diagnostic export did not expose a more specific nested error, and Node C had no endpoint for H1.

**F1 result: BLOCKED (push-registration prerequisite failed).** The registration failure is recorded as the failed prerequisite; it does not establish a failure in the F1 call/SMS business assertions. Following the stop rule, no incoming call, outgoing call, SMS, or `wire=1` assertion was attempted. No synthetic push, API-created call, DNS override, or product change was used. **F2 and F3 were not run** because they depend on completing F1.

The private raw captures remain on the test host under `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f-sequence-20261030/`. Relevant SHA-256 values are `h1-logcat-after-ui-retry.txt` `350b425206a7cf9b6a79f56e026ad46f2b0c6d845c9500731406fbda6219fa55`, `h1-push-retry.log` `118f560894446078a2edc29a6a4881fac4da5456088036fdc97f641bbd391638`, `host-push-after-restart-before-retry.log` `61f749147889494d8a12d32fdee3cf58719f73d74b2a7bd5dc431c8a67ed306d`, and `h1-push-debug.log` `b62e4aff8d937d5f1f94fea9fe9ff7aeaa53237c62db4bf6712e99ab52c5a665`. Raw logs, screenshots, endpoint values, and credentials are not included in this repository.

## Rollback and residual state

The original Homura and H1 APKs were reinstalled, then each encrypted full-app backup was restored through the app's built-in restore flow:

| Device | Restored app version | Pairings in restore manifest | Backup SHA-256 |
| --- | --- | ---: | --- |
| Homura | `0.4.2-dev` | 3 | `24ada5818c834a5f74446c1f56ad4dc6de69b5f71ba3f2e2f6a94b18e66f9150` |
| H1 | `0.4.2` | 2 | `b31b01cd84138977d35b580bbfc53cf71d4735f5ec7ac0f55df4f0b6ae9b0a1d` |

H1's recorded runtime permissions were restored (`POST_NOTIFICATIONS=true`, `RECORD_AUDIO=true`, `READ_CONTACTS=false`); default dialer remained `com.callagent.gateway`. A/B/C were healthy and the GW/H1/H2 modems were idle at the end.

The restore recovered app data, but did not recover working server authentication. Homura's settings UI showed: `配对会话已过期或被撤销，请重新配对此手机。` A read-only comparison of the two H1 backup refresh-token fingerprints against current `sessions.refresh_hash` and valid `refresh_recoveries.old_refresh_hash` on A/B/C found no matches. Fingerprints and token values were not printed or committed; the one-time private fingerprint file was deleted after comparison. No database edit or re-pair was performed.

The isolated temporary Node C owner/gateway pairing from this attempt was not revoked through a supported UI: Homura has no primary pairing removal control. Restoring its backup removed the usable local temporary session, but the server-side temporary owner/session may remain. No SQL cleanup was performed. Encrypted backups and their passwords remain in the remote private test directory.

This evidence supersedes the pre-run status in `f-sequence-risk-before-20261030.md`: F1 was attempted and stopped at real push registration; the installed packages and app data were restored, but original authentication functionality remains unverified and currently appears stale.
