# H2 local KVM copy — preflight and risk record (2026-10-30)

## Purpose and planned scope

Use a private local copy of the stopped API 35 Google Play H2 AVD to resume only B5's incoming-call assertion and C1–C3. Keep the existing remote GW and H1 running. No product source, server image, database rows, pairing records, root/Magisk state, or global system configuration will be changed. No synthetic push will be used; call assertions require the normal SoulGem → Kyubey → FCM path and device-side evidence.

## Preflight evidence before copying

- The local KVM machine reports 31,833 MiB RAM total and 16,872 MiB `MemAvailable`; `/data0` has 1.1 TiB free; `/dev/kvm` exists and is accessible. There is no local Android emulator process and local TCP 18110 is free.
- The local Android SDK originally had command-line tools and platform tools but no emulator package or API 35 system image. Both have now been installed under a task-private SDK directory, not into the shared SDK. Emulator preflight then found the host library `libxkbfile.so.1` is absent on this Arch Linux machine.
- The remote test host is still running only GW (`emulator-5554`) and H1 (`emulator-5556`); H2 (`emulator-5558`) is stopped. Its original AVD is `/home/ubuntu/gsm-webrtc-validation/avd/reception-r1-h2-api35-play.avd`, API 35 Google Play, with a 6 GiB virtual data partition and about 3.3 GiB allocated directory content. The remote source will remain untouched.
- The remote host has about 2.8 GiB `MemAvailable`, 2.2 GiB free swap, and 7.5 GiB free root disk. H2 will not be restarted there.
- Current SQL has H1 at protocol v2, incoming disabled, SMS enabled, revision 18; its current endpoint generation is 4 with zero failures and call failures. H2's old endpoint generation is 1 and has accumulated failures; it must not count as a working FCM endpoint. Values are captured without publishing endpoint strings or device IDs.

## Risks and controls

- The H2 userdata contains app pairing credentials, Google Play state, and an FCM token. The copied AVD and SDK will live only under a task-specific directory with mode 0700. Nothing from that directory will be staged or committed.
- The remote and local H2 copies represent the same Android identity. Never run both at once. The remote emulator list is checked immediately before local startup; the remote H2 remains stopped for the full local run. Only normal app registration may rotate/re-register its real FCM endpoint.
- The local SDK install and copied userdata consume several GiB but are reversible. To avoid installing an OS package, the missing `libxkbfile` runtime will be unpacked into a task-private compatibility directory and passed through `LD_LIBRARY_PATH`; this does not alter the system package database or other applications. No daemon, global SDK, or unrelated service is changed. Cleanup removes only this task's private directory after evidence is archived.
- The API tunnel will bind only `127.0.0.1:18110` and forward to Node A's existing `127.0.0.1:18110` on the remote host. It will be stopped after use. If that port is occupied, do not use a wider bind or alter another service; stop and reassess.
- Start locally with the copied AVD's saved configuration and original guest RAM setting. Monitor host `MemAvailable`, swap activity, ADB responsiveness, app state, and remote health. If the local host becomes resource constrained or FCM is not demonstrably real, stop and record a block instead of lowering guest RAM or substituting synthetic push.

## Rollback

Stop the local H2 emulator, stop the SSH tunnel, verify remote H2 is still off and remote GW/H1 plus SoulGem A remain healthy, then remove only the task-private AVD/SDK copy if no further rerun is needed. The untouched remote H2 AVD is the recovery source. Any H2 endpoint generation change caused by a normal registration remains an application-produced test event and is recorded; no SQL repair is permitted.
