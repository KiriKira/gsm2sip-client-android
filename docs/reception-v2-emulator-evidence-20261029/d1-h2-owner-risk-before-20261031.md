# D1 H2 isolated-owner route — preflight and risk record

Recorded before changing a pairing, owner, SIM mapping, or reception setting.

## Planned route

Use the already-running API 35 Google Play AVD at `127.0.0.1:15559` as the sole D1 host, preserving H1's current app state. Keep the existing API 34 Homura AVD. Do not start H3 or create another AVD. Create a fresh isolated owner through `mahogsm-admin` on the PR #33 A/B/C nodes, pair Homura to A then B/C through the supported app/admin flows, and pair H2 only to B. This avoids treating the old H3 session on the previous owner as a subscriber in the new D1 topology.

## Reversibility and scope

- Before changing app state, export and validate password-protected full-app backups for Homura on the same API 34 AVD and Madoka H2 on the same API 35 AVD. Keep them and the password file only in the remote test host's mode-0700 private R1 directory; never copy them to the repository.
- Do not touch H1, the old owner, A/B/C existing rows, or the old sessions. Do not clear app data, uninstall, revoke, or re-pair the existing H1. The H2 test pairing uses its isolated AVD and the fresh test owner. No direct SQL edits are allowed.
- The new owner's pairing/session/SIM rows will be created through supported admin and production pairing/SIM APIs. These are test records and will remain after the test; do not delete them as cleanup.
- Homura's existing local mesh-node list may need to be isolated from the old owner. If so, stop `GatewayService` and use only `GatewayDatabase.removeMeshNode()` on the local test AVD; preserve its `SessionVault` contents. Do not call `MeshPairing.remove()`, revoke a session, or clear vaults.
- After D1, restore the same-device full-app backups through the app's full-backup flow and verify the original session identities, node list, SIM state, reception settings, and idle runtime before any other call test. App restore will re-encrypt pairing data with the same AVD Keystore.

## Stop conditions

Stop before creating/consuming codes if either backup cannot be staged, the backup manifest does not include the current pairing, or any modem/Telecom call is active. Stop before a test call unless the new owner has the intended A/B/C nodes in order, Homura's SIM is confirmed by the supported flow and consistent across those nodes, A and C have zero eligible incoming clients, B has exactly H2, and H2 has a healthy real FCM endpoint. Stop on any identity, install-ID, or mapping mismatch; do not repair it with SQL, data clearing, or re-pairing of an existing owner.

The route is reversible at the app/AVD level but deliberately leaves isolated test rows in the PR test databases. H3's disk requirement remains out of scope; if this alternative fails a stated stop condition, record the exact evidence and use no additional cleanup or AVD boot attempt.
