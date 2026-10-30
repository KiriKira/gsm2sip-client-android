# F1 call-control rerun — 2026-10-30

This is a sanitized supplement to the F1 relay and SMS evidence. F1 used exact-main Homura `31f65a7d5aa7fbca7df575d73a87fcf3b018b254` and Madoka `5b721c3b80763264a73dbe128364529e11801c97`, with SoulGem PR #33 `152821079f87af7958d30cebf0cf8c0c316ed8e0` and Kyubey PR #1 `1bb9a2bc976ace3ccedb3a95315e01713559bddf`. The Android apps used the shared debug signer and release version code `1000000000`. GW was API 34 ordinary; H1 was API 35 Google Play.

The isolated F1 owner was on Node C, while the plan's default is Node A. This topology deviation was used to preserve the existing pairing and is recorded explicitly. Node C ran the same PR #33 image as A/B. The SMS subtest used the real HTTPS relay and FCM; its evidence is in `f1-sms-rerun-20261030.md`.

## Incoming call observation

- At `2026-10-30T15:17:07Z`, `adb emu gsm call` returned `OK`. Homura's Telecom observed the incoming call at `15:17:08.602Z`; its bridge entered `GSM_RINGING` at `15:17:08.610Z`.
- At `15:17:09.024Z`, Homura logged `Call not offered: SIM account mapping is unavailable`, then returned the bridge to `IDLE`. The GSM call remained in the emulator until the normal `adb emu gsm cancel` at `15:19:34Z`; Telecom then logged call removal. The modem reported idle afterward (`mCallState=0`).
- A read-only SQL query after the attempt found zero `call_sessions` created in the preceding ten minutes. H1 had no incoming-call UI and no `Incoming call ringing` record. Its only `HostPush: received priority=1` record in the capture was at `14:53:48Z`, during the earlier SMS window, not during this call.
- After the attempt, the local SIM registry showed one mapping with `identity_state=UNVERIFIED`, no saved phone-account key, `mapping_revision=2`, and `local_revision_barrier=1`. The standard Homura UI stated: “确认 SIM 卡：检测到 1 张 SIM，尚未绑定到服务器”. There was no separate pre-run snapshot of these fields, so this evidence cannot establish when the mapping became unverified.

GPT-6.1 Sol classified the call as **BLOCKED (environment/test pairing state), not product FAIL**: the call was rejected before the production offer path and no server session was created. The existing guard prohibited SIM re-confirmation or re-binding, so the call was not retried. Outgoing call was also not attempted because its production route depends on the same verified SIM/Telecom mapping. F1's call assertions remain incomplete; the passing SMS/real-FCM assertion does not make F1 complete. F2/F3 were not run because they depend on completing F1.

The exact-main `GatewayJointInstrumentation` test package was used only to substitute synthetic cellular PCM/audio preparation. It retained the production GSM, SIM resolution, Telecom, WebRTC, server control API, Madoka, and FCM paths. The call stopped at SIM resolution, before any media was prepared. The instrumentation reported an orderly finish (`status=PASS`, stage `commands`); this records harness shutdown only, not a call-test pass. No root/Magisk, synthetic push, API-created call, SQL edit, or product-code change was used.

Afterward, the call was cancelled and the server had zero recent/active sessions. The instrumentation had stopped `GatewayService`. The background preferences were restored through the normal Homura UI to `autoconnect=true`, `background_opted_in=true`, `background_user_stopped=false`, `background_task_manager_stopped=false`, with voice still disabled. The Homura UI showed background enabled but waiting for system start/paused; `GatewayService`/the call-control runtime was not running. This is recorded as settings restored, runtime not restored.

Restricted raw logcats and command outputs remain on the test host under `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f1-rerun-20261030/`; only the sanitized facts above are recorded in this evidence file.
