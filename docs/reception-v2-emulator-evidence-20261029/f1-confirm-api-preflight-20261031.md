# F1 retained-SIM confirmation API preflight — 2026-10-31

**Result: stopped before any API write.** Kiri authorized one supported production propose/confirm operation, but only for the retained F1 SIM UUID and original pairing. The identity/session preflight did not match, so no proposal or confirmation request was sent.

## Read-only pre-state

- The private F1 app-data archive `gateway-appdata-before.tar.gz` has SHA-256 `a2671c8c2e8b8f5bcd96690d18a591c0649327670df44c1c4ba1ddf714ad266d`. Its retained local SIM is slot 0, `UNVERIFIED`, mapping revision 2, local revision barrier 1. A salted report identifier for its SIM UUID is `70e90a9060f8155c`; the original gateway-owner identifier hashes to `0f83efe84c6d`.
- The last read-only Node C audit, dated 2026-10-31, found the corresponding retained mapping at revision 2 with `identity_verified=false`. No fresh authenticated read was possible in this attempt.
- The archive contains an encrypted `session_v1` value (476 bytes; SHA-256 `9d0f1b9cd176` prefix only). It does not contain the Android Keystore private key needed to load that session. The original API 36 F1 AVD/full snapshot was not present in the available backup inventory.
- The currently running API 34 gateway AVD is a different pairing: its app is `0.4.2-dev`/versionCode `1000000000`, its owner identifier hashes to `26762fa7f4642cea`, and its local SIM UUID hashes to `463d8ed4c51ca599` at local revision 1 (`LOCAL_CONFIRMED`). Both identifiers differ from the F1 archive. Its session therefore cannot authenticate or confirm the retained F1 mapping without substituting another gateway identity/SIM.

## Stop condition and disposition

The official endpoint requires an authenticated gateway session. The matching F1 session cannot be loaded without its original Keystore, and the live gateway session belongs to a different owner and SIM. Restoring the raw app-data archive into the other AVD would not restore the missing key and would risk cross-identity state, so it was not attempted.

No propose/confirm POST was issued. No database, pairing, application data, SIM UUID, or revision was changed by this attempt. Because the operation stopped before mutation, there is no post-write value; the revision remains unmodified by this test. No manual SQL or direct database write was used.

F1 call/voice assertions were not rerun, and F2/F3 were not started because the F1 business baseline is still incomplete. The earlier F2 failure and historical F3 evidence remain unchanged and are not recast as current-head results. To resume, recover the original F1 API 36 AVD snapshot including its Android Keystore and pairing/session, or an officially supported full backup that preserves that key. Then repeat the read-only identity/revision check, perform exactly one retained-UUID confirmation, and verify the API result is `VERIFIED`; if it is not, stop without retrying.
