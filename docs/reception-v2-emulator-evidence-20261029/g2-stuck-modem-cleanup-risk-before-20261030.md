# G2 stale modem call cleanup risk — before change (2026-10-30)

Read-only preflight found the single G2 test call still active in the GW emulator after its business ring window had expired: radio `mCallState=1`, Telecom `TC@69` in `RINGING`, and RILJ polling the same incoming call. The server-side G2 session was already ended. `adb emu gsm list` returned only `OK` on this AVD and is not a reliable idle check.

The private GW log identifies the exact incoming number as the same test call; the value is not copied here. Planned action is one `adb emu gsm cancel <verified-current-number>` to simulate the caller hanging up. This affects only the stale synthetic call on the GW test emulator. Verify every radio call state is `0` and Telecom has no calls before sending any new test call. If either check fails, stop without another injection. No app data, pairing, SIM mapping, server row, firewall rule, root state, or unrelated service will be changed.
