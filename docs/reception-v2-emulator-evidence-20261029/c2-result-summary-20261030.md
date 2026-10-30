# C2 multi-host answer/reject race retest (2026-10-30)

All four scored calls were real `adb emu gsm call` injections. H1/H2 each reported the same fresh SoulGem call ID in `INCOMING_RINGING`, with a new `HostCall: Incoming call ringing` log before commands were released. No synthetic push or API-created call was used. `HostJointInstrumentation` accepted commands; its `OK` response was treated only as command handling evidence. Telecom phases, modem state, call participants, and SoulGem control events determined outcomes.

| Trial | Command order | Timing evidence | Final result |
| --- | --- | --- | --- |
| 2b | H1 answer, H2 reject | Harness target 0.5 s; SoulGem `accept` → `decline_all` 0.845 s | **PASS**: H1 was the sole accepted/active participant; H2 ended `answered_elsewhere`; GW modem remained active (`mCallState=2`). H1 hangup then ended both hosts and the session. |
| 2c | H1 answer, H2 reject | Host monotonic bound for command-file commits 0.484–0.758 s (target 0.5 s); server `accept` → `decline_all` 0.905 s | **PASS**: same stable single-winner result; GW stayed active until H1 hangup, then both hosts ended. |
| 3a | H2 reject, H1 answer | Host monotonic command-commit bound 0.240–0.516 s (target 0.5 s); server `decline_all` → `accept` 0.272 s | **PASS**: server finalized `ended/declined`; both hosts were `ENDED`, GW `mCallState=0`. |
| 3b | H2 reject, H1 answer | Host monotonic command-commit bound 0.511–0.768 s (target adjusted to 0.75 s to compensate for H2 persistent-shell dispatch; server event gap 0.479 s) | **PASS**: server finalized `ended/declined`; both hosts were `ENDED`, GW `mCallState=0`. |

The runner polls its mailbox every 200 ms. Commands were prewritten and released through persistent device-side shells; command-commit bounds use the test host's monotonic clock from trigger write to the device-side atomic rename acknowledgement. The server event timestamps provide an independent record of accepted control operations. They show that the command-to-server path adds variable delay; therefore the report does not claim an exact 500 ms UI-handler interval. The required race order and approximately half-second command spacing were exercised in both directions.

Two extra exploratory trials were excluded from the four scored runs: `c2-unscored-timing-trial-1b.txt` had a wide timing interval; `c2-trial-2a.txt` used an earlier cross-device clock-bound implementation that was later corrected. Both ended stably, but their incomplete timing evidence was not used to satisfy the planned repeats. The earlier false precondition abort caused by capped Telecom event history is documented in `c2-runner-risk-before-20261030.md`; it issued no answer/reject action.

Per-trial sanitized runner output is in `c2-trial-2b.txt`, `c2-trial-2c.txt`, `c2-trial-3a.txt`, and `c2-trial-3b.txt`; server control timestamps are in `c2-server-control-events-20261030.txt`. The reproducible harness is `c2-four-trials-20261030.py`. No product source or test data was changed.
