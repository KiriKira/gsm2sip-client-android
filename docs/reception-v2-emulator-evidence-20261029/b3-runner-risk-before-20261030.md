# B3 instrumentation follow-up risk record — 2026-10-30

The earlier Madoka UI attempt is already recorded as a failure and will not be repeated in the same setup. This follow-up uses the review-recommended existing `GatewayJointInstrumentation` backend. It temporarily stops Homura's ordinary `GatewayService`, reuses its existing primary-node credentials and confirmed SIM mapping, then starts the actual Gateway service with only the physical cellular PCM implementation replaced by the existing synthetic test backend. Madoka remains on its production UI path with incoming reception disabled. No database rows, pairing, SIM mapping, default-dialer role, application data, root/Magisk state, or product source will be changed by setup.

Rollback: send the runner `stop`, wait for `joint-finished.json`, then reopen Homura and verify its normal service/heartbeat is restored. The fixture contains only the existing local API base and `reuse_pairing=true`; remove that fixture after the run. If runner readiness, server connectivity, or test call state is not as expected, stop the runner and do not place a call.

The test backend does not validate modem audio HAL. The assertion is limited to outbound call control, Telecom/modem state, server session lifecycle, and preservation of the disabled incoming preference.
