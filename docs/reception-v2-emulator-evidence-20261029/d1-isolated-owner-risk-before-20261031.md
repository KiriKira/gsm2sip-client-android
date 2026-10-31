# D1 isolated-owner setup risk — before action

Recorded 2026-10-31 before changing any pairing or reception setting.

## Scope

Use a fresh, isolated owner on the existing PR #33 nodes A/B/C so the offline H3 registration under the old owner cannot be an incoming subscriber. Homura joins A, then B and C; the sole D1 test host joins only B. This preserves the D1 assertion without booting H3 or editing SQL. Use normal admin join-code creation, normal device pairing and the production SIM-confirmation flow.

## Preservation and reversibility

- Before pairing, export password-protected full-app backups for the current Homura and H1 on their own AVDs using each app's built-in backup flow. Keep them mode 0600 in the test host's private R1 directory; do not commit backup files or passwords.
- Do not revoke the old owner sessions, clear app data, uninstall either product, change the old owner's reception, or edit A/B/C SQL. The test creates new owner/session rows only.
- After D1, force-stop both apps and restore each backup on the same AVD/Keystore. Reopen each app and verify the prior session identity, node list, SIM mapping, reception settings, modem idle state and service state before any further call test.
- The database additions for the isolated owner are intentionally retained. Do not delete them as cleanup; report their owner scope and IDs only in the private evidence, not the public report.

## Stop conditions

Stop before consuming a join code if either backup manifest does not include the existing pairing(s), calls are not idle, or the owner/session identities do not match the saved baseline. Stop before call injection if any new node reports a different gateway install ID, SIM UUID/slot mismatch, SIM identity remains `UNVERIFIED`, A or C has an incoming subscriber for the new owner, B has other than the single test host, or real FCM registration is not healthy. If restoring a backup fails or the old session has been revoked/rotated unexpectedly, do not clear or re-pair; retain the new owner state and report the exact recovery requirement.

The host emulator used for D1 must be API 35 with Google Play. If using the second already-running API 35 emulator rather than the current H1 serial, record that substitution and validate GMS/FCM before pairing. No additional AVD, root/Magisk or host-disk cleanup is in scope.
