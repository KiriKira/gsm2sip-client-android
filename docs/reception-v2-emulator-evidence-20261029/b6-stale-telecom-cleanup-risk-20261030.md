# B6 failed-call environment cleanup risk — 2026-10-30

Before cleanup, the GW emulator had one stale Android Telecom call in `RINGING` (TelephonyConnectionService; first recorded at 2026-10-29 21:12Z). `adb emu gsm list` returned no active emulator modem call, and SoulGem had no call session created after the B6 attempt at 21:22:17Z. The new `adb emu gsm call` command returned `OK`, but it produced no new Gateway `Incoming GSM call`, SoulGem call session, or H1/H2 call push.

Planned cleanup: send one `KEYCODE_ENDCALL` to the GW emulator to clear only the stale Telecom call before independent call cases. This is reversible by a later test call and does not change app data, pairing, server state, system configuration, root, or Magisk. If the call remains, stop and preserve that environment limitation rather than escalating to reboot or system changes.
