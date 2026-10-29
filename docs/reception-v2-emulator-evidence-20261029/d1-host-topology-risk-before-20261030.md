# D1 host-topology change risk — before action

Recorded 2026-10-29 22:12 UTC, before changing any Madoka pairing or reception setting.

The original D1 topology is not currently present: Node A has three active client registrations, all with incoming enabled; B and C currently have the original Homura gateway sessions only. Sol confirmed the original Homura B/C sessions are intact after removing the duplicate rows created during this round. Homura's A/B/C configuration is now back to two healthy secondary-node rows (priorities 1/2, mapping revision 2, no sync error).

Planned D1 setup, with app UI and production pairing APIs only:

1. Save private, mode-0600 app-data archives and redacted SQL baselines for H1, H2, H3, and A/B/C. Create a 15-minute, client-role pairing code on B for the existing owner; keep the one-time code out of logs and evidence.
2. Add B to H1 using Settings → Add server node (API base `http://10.0.2.2:18111`, without `/v1`), wait for identity validation and node health, activate B, then remove inactive A from H1. Stop if B's owner/install identity does not match or H1 data/history is not retained.
3. Through H2 and H3 settings, turn off incoming reception on A. H3 must be started temporarily to change its setting. Leave the H2/H3 registrations in place, but verify A and C have zero incoming subscribers; this is the plan-equivalent behavior and will be documented as disabled registrations if the literal “no host rows” interpretation matters.
4. Use the already installed HostJointInstrumentation in `reuse_pairing=true` mode for D1's single real incoming call and controlled answer. The test runner uses synthetic PCM, so it can prove call-control/fork behavior but not cellular HAL audio or a normal user-facing CallActionActivity.

All changes are confined to the existing R1 test emulators and A/B/C. Pairing, server membership, and reception settings are reversible by re-adding A and restoring the saved switch states. H3 will be stopped after the short case if it is no longer needed. Do not clear app data, revoke existing accounts, edit SQL, change product source, use root/Magisk, or start long observations within this step. If a saved baseline or node identity does not match, stop before the pairing changes.
