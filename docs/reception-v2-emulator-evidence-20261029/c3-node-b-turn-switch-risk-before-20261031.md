# C3 Node B private-TURN switch — risk and rollback before action

This record covers only the planned temporary change to SoulGem Node B's TURN URLs. It does not authorize database edits, pairing changes, app-data clearing, or product-code changes.

## Current state and reversible scope

- The original B container is still running with its original environment, image, network, loopback API binding, restart policy, and `/data` bind mount. Its immutable ID and environment are saved in a mode-0600 private file on the test host; a private PostgreSQL dump is retained but will not be restored over newer writes.
- A/B/C are healthy, have zero active calls, and the C3 owner still has the expected H1/H2 reception and verified Homura SIM mapping. H1 and H2 have real FCM endpoints. The private coturn sidecar binds only to the test host's VPC address and the isolated relay range; H2 reaches its loopback route through `adb reverse`.
- The reversible change recreates only B from the same image and bind-mounted data, preserving its network, port, hostname, restart policy, and environment while changing only `RTC_TURN_URLS`. A and C and their containers remain untouched. No schema or row is edited.
- The first `docker create` attempt was stopped before original B was stopped: Docker's CLI representation differed only in attach flags and `OomKillDisable` null/false defaults. The exact created replacement was removed without `-f` or `-v`; the original B remained healthy and unchanged. This was a harness guard mismatch, not a C3 product result.

## Stop conditions

Before switching, require: exact original container ID and saved environment; healthy A/B/C; zero active calls; exact H1/H2 owner rows and verified SIM binding; running sidecar with zero restarts; expected VPC/loopback listeners and H2 reverse; at least 3 GiB free on `/` and 1 GiB `MemAvailable`. Do not stop original B if any precondition fails.

After startup, require the exact replacement ID, same image/data mount/runtime configuration, only the expected TURN URL difference, B healthy, the H1/H2 owner and SIM state unchanged, and zero active calls. Stop the test if any identity, mount, environment, peer health, call count, or resource guard changes.

## Rollback

Run the script's `--restore-original` mode while A/B/C have zero active calls and the resource floor remains satisfied. It restores the retained original container by exact ID, waits for its bounded health transition without requiring sidecar/ADB/Host test gates, and independently verifies B identity/config/health plus A/C health and all-node zero-call state. The replaced private-TURN container is retained stopped with `restart=no`; it is not deleted until the original is fully audited. If original startup fails, the script may return the exact previously verified private-TURN B as a separately audited fallback. Unknown container IDs are never renamed or removed. Any final state it cannot independently prove is reported as `restore_recovery_unverified`, never PASS.

The restore state machine was reviewed by GPT-6.1 Sol after static syntax and mutually exclusive CLI checks. No container state was changed during that review.
