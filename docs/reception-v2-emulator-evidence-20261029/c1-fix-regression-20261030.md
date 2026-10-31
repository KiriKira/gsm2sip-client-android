# C1 incoming Telecom UI fix — 2026-10-30

Product commit: `299213b` (`fix: show incoming call controls when Telecom requests UI`).

## Failure and root cause

The prior real FCM call reached Telecom on H2 and transitioned `NEW → RINGING`, but Madoka did not open its visible answer/reject screen; see `g1-restart-retest-20261030.txt`. A focused pre-fix regression then invoked `HostCallConnection.onShowIncomingCallUi()` and failed because no activity was launched. The private XML is retained outside the repository at `/tmp/reception-r1-c1-before.xml`, SHA-256 `9d6d5b17c0df6c69b7ea4098f24c888488a7e40e1d7c03fd867a98ffbfc86ce6`.

The root cause was that the self-managed Telecom `Connection` inherited the default no-op callback. A `RINGING` Telecom state did not open the app's own UI.

## Change and impact

`HostCallConnection.onShowIncomingCallUi()` now opens the existing `CallActionActivity` only when the connection is incoming, Telecom reports `STATE_RINGING`, and the current call snapshot remains `INCOMING_RINGING`. This uses the existing answer/reject screen and does not answer automatically. Only `CallTelecomController.kt` and `CallTelecomControllerTest.kt` changed; no call protocol or server behavior changed.

## Regression

- Main-thread rerun: `CallTelecomControllerTest`, `CallActionActivityTest`, `CallAlertingPolicyTest`, `CallSessionCoordinatorTest`, and `HostCallServiceStartupTest`: **27 tests, 0 failures/errors/skips**.
- Full delegated Android check: **260 unit tests, 0 failures/errors/skips**; `lintDebug` **0 errors, 58 warnings**; `assembleDebug` succeeded. Log SHA-256 `7df836522ef2f4783048fbd356eb89ad4b3bf93141139052a97911ab99b03e28`.
- The incoming-call UI still needs a post-fix real-FCM emulator retest; the unit regression is not reported as an end-to-end pass.
