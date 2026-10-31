# G1 post-restart call retest risk — before action

Recorded 2026-10-29 22:10 UTC, before restarting Node A.

The earlier G1 attempt restarted only test node A and confirmed its database and reception settings persisted, but the then-current ordinary-app GSM injection did not create a modem call. A later run with the established `GatewayJointInstrumentation` path did create a real emulator GSM call and SoulGem session. This retest changes the call injection/service harness while keeping the plan's real modem call; it is not a second attempt using the earlier failed ordinary-service setup.

Planned action: confirm no active Telecom/GSM call, restart only the R1 test container `reception-r1-a`, wait for its existing health check to return healthy, verify reception rows remain v2 with their prior settings, then inject one actual emulator GSM call and trace Homura → SoulGem → real Kyubey/FCM → Madoka. The call will be stopped as soon as the expected ringing window ends or a user-visible failure is confirmed. No API-generated call, synthetic push, source change, root, Magisk, database edit, or unrelated service restart is planned.

This temporarily interrupts only Node A's API/worker and its test clients. Docker's existing restart policy and persistent bind-mounted data provide recovery; do not touch B/C or their databases. If A does not become healthy, stop without injecting a call and record the node-health failure.
