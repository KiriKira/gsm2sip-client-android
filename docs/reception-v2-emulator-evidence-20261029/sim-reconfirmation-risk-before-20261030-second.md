# Retained SIM confirmation — second operation pre-action record

Recorded 2026-10-30 UTC before any new propose/confirm call.

## Guard result

- The current Homura AVD was not rebuilt or rebooted during the local H2 work. The old standalone probe was stopped; telephony call state is idle, `emu gsm list` is empty, and the standard `GatewayJointInstrumentation` APK signed by the existing test-host debug certificate has been restored.
- The read-only same-salt probe returned the existing SIM digest `e8929d4f6127114e7d66c2edd2c8795f`, Gateway digest `3e1e720d9e8b3feac035419ae8a4d16d`, local revision 9/barrier 5, subId 1/slot 0, stored pin present, live fingerprint readable and matching, one SIM PhoneAccount, forward Telephony mapping true, default dialer true, and no Telecom call.
- Node A's current row has the same SIM/Gateway digests, Gateway and binding owner digest `c66592cc36beba342952a82010ef8f69`, active binding, `identity_verified=true`, slot 0, and revision 9. No active call is present. The mismatch is local verification state only (`UNVERIFIED`).

## Planned write and side effects

Use the branch's existing guarded `reconfirm_retained_emulator_sim` path exactly once with a newly generated operation ID and the original SIM UUID. It calls production `propose` and `confirm` and then `SimRegistry.confirmMappings`; expected change is only mapping revision/state/local verification barrier. It must return the same SIM UUID and Gateway, slot 0, and a valid increased revision. Any new UUID, different owner/gateway/slot, unreadable or changed fingerprint, failed guard, or API/client divergence means stop immediately and do not retry.

The call creates persistent API operation/audit records and updates the SIM mapping revision. It does not change the default dialer role, install root/Magisk, modify product code, clear application data, stop/recreate a node, or change the Firebase/FCM setup. The normal instrumentation runner starts the real GatewayService and stops it on exit; restore/verify ordinary Homura operation afterward. The runner's `READY` alone is not accepted as proof of identity.

## Backup and rollback plan

Immediately before writing, create a new private, mode-0600 full PostgreSQL dump of Node A and a private app-state tar of Homura's `shared_prefs`, `databases`, and `files`, then record sizes and SHA-256. The private directory is mode 0700. These backups are paired recovery points; they are not committed. A full Node A restore discards later calls/SMS/FCM/audit events. A Homura app-state restore alone is prohibited. If state diverges, stop calls and the runner, preserve live evidence, compare both sides, and only perform a paired restore after deciding that rolling back all post-backup events is acceptable.

The existing fixture will be retained privately and temporarily augmented only with the retained SIM ID and new operation ID. Restore the ordinary `reuse_pairing=true` fixture immediately after the guarded runner exits. Never tap the normal UI SIM-sync control while the registry is `UNVERIFIED`.

## Paired backups completed before write

Private directory `/tmp/reception-r1-private-20261030/` is mode 0700; both files are mode 0600. The Node A custom-format dump is 183,852 bytes, SHA-256 `fc17f63be762a9182656e85f04fd29fafa9f999caeda8e81d49faaae5a58e9be`; `pg_restore --list` parsed 267 TOC entries. The Homura app-state tar is 330,240 bytes, SHA-256 `f75bc249731af1c6c2ef948131e07d5d9872ecc3d0c209c59ed0a69590681049`; `tar -tf` parsed 21 entries. The dumps contain app credentials and personal test state and are not release evidence or repository files.
