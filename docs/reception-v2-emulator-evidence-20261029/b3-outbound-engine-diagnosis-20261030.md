# B3 outbound failure diagnosis (2026-10-30)

This is a read-only follow-up to the single B3 outgoing attempt. No call was repeated and no source file was changed.

## Server timeline

Node A SQL was queried by the session hash only; the raw call ID and destination are intentionally omitted. Session key `b78141c66b22fcee53361675995bf213` had:

| Event/field | UTC | Meaning |
| --- | --- | --- |
| `call.reserved` / session created | 20:13:24.795211 | The outgoing authorization was created. |
| `client_seen_at` | 20:13:24.799708 | Four milliseconds after create; not treated as proof of an active client poll because it can be initialized with the row. |
| Session ended | 20:13:46.042565 | `state=ended`, `reason=control_expired`; about 21.25 seconds after creation. |
| `gateway_seen_at` | 20:16:50.316376 | After the session ended; not evidence that GW polled during the valid control window. |

`dispatched=false`, `end_requested=true`; the only call events were `call.reserved` and `call.ended`. There was no `connecting`, `ringing`, media setup, or cellular dispatch. The existing archived `b3-outbound-attempt-20261030.txt` records H1 Telecom's self-managed connection as DIALING and GW/H1 modem state idle. A self-managed Telecom connection enters DIALING when created, before RTC engine readiness or cellular dispatch.

## Source-level cause

On the tested Madoka commit `770fb716517aa7d4aa26b7ee32b297ea0a4c3c01`:

- `HostCallService.kt:480-481` starts the engine and calls `startAuthorizedOutbound()` only if the engine is already `READY`.
- `RtcCallEngine.kt:165-170` returns early when incoming listening is disabled and there is no live call. A newly authorized outgoing call has not reached media/delivered state, so it does not satisfy `hasLiveCall()`.
- The successful READY notification is later in `RtcCallEngine.kt:202`, after the early return.
- `CallTelecomController.kt:175-177` puts the self-managed Telecom connection into DIALING before that engine handshake; therefore Telecom DIALING is not evidence that an outgoing RTC dial was dispatched.

With incoming reception disabled, the new engine waits for READY before dialing, while the engine declines to poll until there is a live call. This accounts for the reserved-only session timing out before the GW receives active control. The failure is in Madoka's call-start logic, not an AVD/modem/PCM limitation.

## Result and handling

B3 outbound-success assertion is **FAIL (product logic)**. The “incoming remains disabled” assertion is still **PASS**: the UI and Node A retained `incoming_enabled=false` after the attempt. Product source changes are out of scope for this continuation, so no patch or rerun was made. This diagnosis supersedes any earlier B3 environment-blocked interpretation while preserving the original attempt evidence.
