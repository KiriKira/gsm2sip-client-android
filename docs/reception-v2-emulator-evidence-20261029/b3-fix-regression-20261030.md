# B3 outgoing-call fix — 2026-10-30

Product commit: `d60e3c9` (`fix: start authorized outgoing calls with reception disabled`).

## Failure and root cause

The hardware-level B3 attempt on 2026-10-29 created a server call reservation, then ended about 22 seconds later with `control_expired`; there was no gateway cellular dispatch, and H1's incoming setting remained disabled. A separate SQL-backed diagnosis on 2026-10-30 recorded `dispatched=false`, only `call.reserved` and `call.ended`, and a 21.25-second expiry. These are separate attempts and are not merged into one timeline. Redacted source records: `b3-outbound-attempt-20261030.txt` and `b3-outbound-engine-diagnosis-20261030.md`.

The pre-fix regression reproduced the deadlock deterministically: with incoming reception disabled and an authorized outbound call, `startEngine()` returned without emitting `READY`, while the old caller waited for `READY` before dispatching the authorized dial. `HostCallServiceStartupTest.authorizedOutboundStartsWithoutAnIncomingPollAndKeepsReceptionOff` failed with expected dial count 1, actual 0. The private XML is retained outside the repository at `/tmp/reception-r1-b3-before.xml`, SHA-256 `264d089a5eb7f7b1079b2cdc1fbd1f758472a193a293b0a88486f38ce7908822`.

## Change and impact

The fix starts an authorized outbound dial from engine startup rather than waiting for an incoming-listener `READY` event. It leaves reception disabled and keeps dialing idempotent. Only `HostCallService.kt` and `HostCallServiceStartupTest.kt` changed; no protocol or server behavior changed.

## Regression

- Main-thread rerun: `HostCallServiceStartupTest`, `RtcCallEngineStandbyTest`, `ReceptionV2Test`, `CallPreferencesTest`, and `CallSessionCoordinatorTest`: **25 tests, 0 failures/errors/skips**.
- Full delegated Android check: **258 unit tests, 0 failures/errors/skips**; `lintDebug` **0 errors, 58 warnings**; `assembleDebug` succeeded. Log SHA-256 `53dfb7700954b3eb4243e6720afe23298d77645367c2947ffaeb0978e6859b17`.
- The repaired end-to-end emulator call is tracked separately from this source regression; this unit result does not claim cellular HAL audio validation.
