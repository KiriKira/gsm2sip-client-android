# GPT-6.1 Sol report review — 2026-10-30

Read-only review of the 2026-10-29 validation report, test plan, archived evidence, 2026-10-28 upgrade report, and the retained-SIM recovery evidence. This is an assessment of unfinished work, not a replacement for the original PASS/FAIL rows. No product source or test environment was changed during the review.

| Item | Sol classification | Feasible path and this-round judgment |
| --- | --- | --- |
| B3 | Method/setup incomplete; the dialer warning alone does not prove that outgoing calls cannot work. | Reuse the existing `GatewayJointInstrumentation`, keep H1 incoming reception disabled, place the call through Madoka UI, then verify Telecom and server active→ended state and that reception remains disabled. Feasible. Synthetic PCM does not establish cellular HAL audio. |
| B5 remaining assertion | Its offline setting sync passed; H2 being off is a recoverable environment setup gap. | Start the existing H2 API 35 Google Play AVD, restore its real relay registration, set H1 incoming off/H2 on, inject a real emulator GSM call, and verify only H2 rings. Feasible if monitored startup remains healthy. |
| B6 remaining assertions | The previous relay endpoint had expired; the current true-FCM relay has been restored. Prior SMS/cursor evidence alone did not prove relay suppression or the call assertion. | With H1 incoming on and SMS sync off, use real `emu sms send`, check the current H1 device/generation in redacted Kyubey logs, verify the call still arrives, then re-enable sync and verify one backlog hint. Feasible; do not infer PASS from the cursor or message list alone. |
| C1–C3 | H2/Telecom readiness and operation method, not an inherent emulator limitation. | Run with GW/H1/H2 online. C1 requires restoring H2 after its airplane-mode leg. For C2 race the actions from the devices with a measured ~0.5 s interval; two sequential host ADB commands are not valid race timing. C3 verifies H1 disable while H2 continues. Feasible subject to monitored resource headroom. |
| D1–D3 | Current B/C images are an explicit but reversible environment mismatch. | Back up B/C databases, image IDs, and container arguments; bring test-only B/C nodes to the same PR #33 image while retaining independent DBs/networks, then use the runner's `mesh_add` in the planned A/B/C order. Feasible if backup/rollback and disk checks pass. Full-DB restore rolls back later test/audit events. |
| F1–F3 | Baseline prerequisite/method issue, not proven impossible. | Installing old `main` over the current H1 cannot produce v1: the protocol says a server that has already recorded v2 stays v2. Need a real pre-R1 server/device snapshot or a fresh old-main pairing, exact main source SHA and same-signed APKs, then the ordered old GW/H1 → new GW → new H1 installs without clearing data or manually reconfirming SIM. No verified pre-R1 snapshot is present in the reviewed evidence; do not call an ad-hoc downgrade F1. Feasibility depends on recovering that baseline and APK provenance. Preserve the 2026-10-28 F1/F2 FAIL rows unchanged. |
| G1 incoming leg | The earlier “did not reach modem/Telecom” summary is too broad: archived `gw-runner-preflight-20261030.txt` records a matching Telecom RINGING call. The retained SIM is now active/verified. | Restart A, wait one minute, inject a real emulator GSM call, and trace GW→SoulGem→real Kyubey→H1 under one call ID. Feasible; prior node-restart/settings subtest PASS does not complete the incoming leg. |
| G2 | Relay outage had an unmet successful-call/real-FCM prerequisite; it is not inherently untestable. | First prove a normal real call. Then block only node A's egress to the active relay endpoint, leave device/API routes available, verify `call_failures` increments once without repeat pushes, remove the exact temporary rule, and verify the next call succeeds and the counter recovers. Feasible with an explicit cleanup/rollback command. |
| H1 push 60 min | Old relay failure was environmental. `failures=8` and cursor 4 describe the old endpoint, not the plan's pass criterion. | Current H1 generation 4 has a healthy true-FCM delivery and a fresh baseline; run the full quiet interval, comparing only H1 device ID + generation before/after and excluding the stale H2 endpoint. Do not recreate failure count 8 by editing SQL or forcing fake pushes. Feasible for 60 real minutes. |
| H3 stream 60 min | Method/time limitation only; the prior 43m21s cannot be extrapolated or accumulated after idle settings were restored. | Start a fresh uninterrupted 60-minute deep-idle interval with the stream live. Accelerated clocks change Android timer semantics relative to the real server/network and cannot substitute. Feasible for 60 real minutes. |

## Evidence completeness caveat

The original report itself notes that the archived A1–A4/B1–B2 PASS entries mostly have redacted summaries rather than per-attempt diagnostic JSON and original log excerpts. Keep those historical PASS labels intact, but do not describe their evidence as independently complete. New attempts in this addendum use per-case redacted evidence and source/hash references.

## Resource and timing guidance

At 2026-10-29 19:12:41 UTC, the remote host reported MemAvailable 1620 MiB, SwapFree 3210 MiB, root free 7.6 GiB, and no swap-in/out in the final two `vmstat` samples. The H2 API 35 Play AVD already exists with a 1024 MiB guest, one vCPU, and 3.3 GiB userdata. Sol judged a monitored original-userdata boot feasible, without changing AVD settings. Stop H2 cleanly if memory becomes critically low, swap-in/out persists, or ADB becomes unresponsive; do not delete data.

The two final idle observations can share one uninterrupted 60-minute wall-clock window after all short cases, with H1 and H3 idle concurrently. Allow another 10–15 minutes for pre/post diagnostics. Do not accelerate either guest clock.

## Sources reviewed

- `../reception-v2-emulator-validation-20261029.md`
- `../reception-v2-emulator-test-plan.md`
- `../../../server/docs/reception-v2.md`
- `gw-runner-preflight-20261030.txt`
- `sim-reconfirmation-20261030.txt`
- `sim-reconfirmation-risk-before-20261030.md`
- `../reception-v2-emulator-validation-20261028.md`
- `b3-call-readiness-20261029.txt`, `b5-offline-sync-20261029.txt`, `b6-sms-sync-20261029.txt`, `g1-restart-20261029.txt`, `h3-recovery-20261029.md`, and `push-endpoint-rebuild-baseline-20261030.txt`
