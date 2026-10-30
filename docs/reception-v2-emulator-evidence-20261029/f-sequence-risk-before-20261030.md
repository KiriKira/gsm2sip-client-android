# F1–F3 baseline reconstruction risk record (2026-10-30)

## Scope

Reconstruct only the planned version sequence on the designated GW and H1 AVDs: exact main Homura (`31f65a7d5aa7fbca7df575d73a87fcf3b018b254`) and main Madoka (`5b721c3b80763264a73dbe128364529e11801c97`), then current Homura, then current Madoka. Use the existing R1 SoulGem/Kyubey deployment. The archived APKs share the installed debug signing certificate; rebuild the exact main Madoka source with the currently reachable relay configuration so FCM remains real.

## Reversible state changes

- Before any package/data change, export and validate encrypted full-app backups from the installed Homura and H1 versions. Preserve copies of both currently installed APKs and the exact main APKs in the remote private test directory; verify package IDs and signer digests. Record runtime permissions and the default-dialer role so they can be restored if needed.
- The installed AVD snapshots are not used: the two AVD directories occupy 1.3 GiB and 3.9 GiB, and the test machine has 5.7 GiB free. Snapshot allocation could consume the H3 boot margin. No files are deleted to make room. App backups plus exact APK copies cover the app-level rollback; no OS-level setting is intentionally changed.
- Node C already contains an unrelated owner/device history (counts only: 1 owner, 2 devices, 1 session, 2 gateway records, no call sessions or push endpoints). Do not reuse or mutate that owner. Create one isolated F-sequence owner with `mahogsm-admin`, pair through the app's supported join flow, and remove both temporary device pairings through the app before restoring the original app backups. This may leave an empty owner row; do not delete or edit it through SQL.
- Preserve H1's existing registrations in the encrypted backup. The test pairing is additive and uses Node C's API at `http://10.0.2.2:18112`; A and B stay untouched.
- Perform the planned package transitions in order: exact main Homura + exact main Madoka; update Homura only; update Madoka only. The two upgrades use `adb install -r`, retaining app data. App-data clearing is allowed only after the encrypted rollback backup has passed restore staging, and only if required to establish the clean main-version pairing.
- No root/Magisk, OS changes, database edits, product-source edits, synthetic push, or unrelated service/container changes are in scope. All calls/SMS use the plan's emulator paths and real SoulGem → Kyubey → FCM delivery.

## Stop and rollback conditions

Abort before the first install if either full-app backup cannot be restore-staged, either current APK copy/signature is invalid, disk free space falls below 2 GiB, any modem/Telecom call is active, or A/B/C are not healthy. Stop the affected F item on a normal user-visible product failure and retain the evidence. After F3 or an abort, revoke temporary pairings through app UI, restore the exact saved APKs and encrypted app backups, recheck the recorded permission/role state, and verify all three nodes are healthy. Keep encrypted backups and passwords private; never copy them into the repository.

## Outcome

F1 was started on 2026-10-30 after both backups passed restore staging. The encrypted H1 backup (`13,577` bytes, SHA-256 `b31b01cd84138977d35b580bbfc53cf71d4735f5ec7ac0f55df4f0b6ae9b0a1d`) and Homura backup (`19,822` bytes, SHA-256 `24ada5818c834a5f74446c1f56ad4dc6de69b5f71ba3f2e2f6a94b18e66f9150`) were later restored through the built-in restore flow. Their staged manifests showed H1 version `0.4.2` with 2 pairings and Homura `0.4.2-dev` with 3 pairings. The exact main Homura APK (`c0042dd…e865e21`) and exact-source main Madoka rebuild with active relay (`315d42f1f87d0157aee9671e92983cd0cbe218863fb3aaa99ed8b23b9194d4c0`) share signer SHA-256 `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28`.

The F1 push-registration preflight failed repeatedly with `UnknownHostException`, and Node C had no H1 push endpoint. No call, outgoing call, SMS, or wire1 assertion ran; F2/F3 were stopped as dependent steps. The app packages and encrypted app data were restored, but the restored authentication state is not usable: the UI reports the pairing session expired or revoked, and read-only refresh-token fingerprint checks found no match in current sessions or valid recovery windows on A/B/C. Original H1 runtime permissions and the default-dialer role were restored. The isolated temporary C gateway pairing was not revoked because Homura has no supported primary-pairing removal UI; the restored Homura app no longer has its usable local temporary session, but the remote temporary owner/session may remain. No SQL rows were edited. See `f1-preflight-20261030.md` and the report's F1 addendum for the exact boundary and recovery evidence.
