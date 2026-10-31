# H2 AVD startup risk record — before launch

Timestamp: 2026-10-29 19:12:41 UTC. This is a reversible test-environment action requested to complete B5/C; no root, Magisk, AVD config edit, disk cleanup, or product change is planned.

## Pre-start snapshot

- Host: `18.119.132.34`, 2 vCPU, 7.6 GiB RAM, root filesystem 61 GiB total / 7.6 GiB free.
- MemAvailable: 1620 MiB; SwapFree: 3210 MiB; memory PSI avg10/avg60/avg300 was 0.00; final two `vmstat` samples had `si=0, so=0`.
- Running AVDs: `emulator-5554` = GW `reception-r1-gw-api34`; `emulator-5556` = H1 `reception-r1-h1-api35-play`. H2 serial `emulator-5558` was absent.
- H2 AVD `reception-r1-h2-api35-play` exists with its existing userdata (3.3 GiB). It is API 35 Google Play, configured for 1024 MiB guest RAM and one vCPU. No config change or wipe is planned.
- At the preceding read-only snapshot, GW/H1 QEMU RSS was about 1.4/2.8 GiB. H2 startup can consume the remaining host headroom and increase swap use or delay GW/H1.

## Reversible effects and stop conditions

Starting H2 adds a normal emulator process and persistent Android runtime writes. Booted apps may reconnect to SoulGem and update H2's own endpoint generation/counters; those rows must stay separate from H1's device ID/generation. A clean emulator shutdown stops the added process and preserves userdata, but does not undo server/app state changes already made by a booted app.

Monitor host MemAvailable, SwapFree, `vmstat si/so`, load, and ADB responses during boot. Stop H2 cleanly and do not start B5/C if MemAvailable falls below 512 MiB, SwapFree below 1 GiB, swap-in/out persists across samples, GW/H1 ADB becomes unresponsive, or either existing AVD reports ANR/connection loss. Do not delete files or stop unrelated containers to make room. If H2 reaches a stable boot, record its actual role/API and reception/FCM state before scoring cases.

Recovery action: `adb -s emulator-5558 emu kill` (or normal window close), then verify only GW/H1 remain and their ADB/API health returns. No userdata restore is needed unless an application-level test explicitly changes it.
