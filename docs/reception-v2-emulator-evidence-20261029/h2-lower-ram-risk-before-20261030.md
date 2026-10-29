# H2 constrained-memory launch risk record (before launch)

Recorded 2026-10-29 19:48:54 UTC, before the alternate H2 launch.

The first H2 launch used its unchanged 1024 MiB API 35 Google Play AVD configuration. Android booted, but sustained swap traffic reached the stop condition in `h2-start-risk-before-20261030.md`; H2 was then shut down cleanly. This is a different, one-time method: launch the same AVD with a **runtime-only 768 MiB RAM override**, leaving its `config.ini`, snapshot files, and userdata untouched. The override will disappear when the process stops.

Pre-launch host readings: 2958 MiB `MemAvailable`, 2185 MiB `SwapFree`, root disk 7.6 GiB free. GW and H1 are the only running AVDs. Existing H2 userdata is retained. A booted Madoka may refresh only H2's stale push registration; all server evidence will continue to separate H2 from H1 by device digest and endpoint generation.

This reduces available guest memory and can cause Android or app low-memory kills, so any affected case is invalid rather than a product pass. Stop H2 cleanly if `MemAvailable < 512 MiB`, `SwapFree < 1 GiB`, swap-in/out is non-zero in two consecutive 1-second samples after the clean pre-launch baseline, or GW/H1 loses ADB/API responsiveness. The original stricter stop rule from `h2-start-risk-before-20261030.md` remains in force; the numeric rate threshold considered during setup is not used. Do not change AVD config, wipe userdata, kill unrelated services, or retry a resource-triggered failure. Rollback is `adb -s emulator-5558 emu kill`; verify only GW/H1 remain and the persisted AVD config still says 1024 MiB.

The test-only runner remains the same debug build. Its synthetic PCM is not evidence of physical cellular HAL audio. No root, Magisk, product-code change, or database edit is involved.

## Launcher correction

The first 768 MiB command at 19:50:36 UTC exited before QEMU creation because the remote shell did not export `ANDROID_AVD_HOME=/home/ubuntu/gsm-webrtc-validation/avd`. ADB showed no H2 serial and the emulator log said the AVD name could not be resolved. No userdata/config file was opened for writing. This was a command-environment error, not a resource-triggered launch failure; one corrected invocation will pass the AVD directory explicitly. The no-retry rule above applies if that actual launch hits a resource stop condition.
