# Protocol source pin

Authoritative implemented contract: `gsm2sip-server` commit `8b83ae8f613fd7f27ea8e4f3aa1b2148f22c1606`.

- [Wire addendum](https://github.com/KiriKira/gsm2sip-server/blob/8b83ae8f613fd7f27ea8e4f3aa1b2148f22c1606/docs/server-wire-addendum.md)
- [OpenAPI](https://github.com/KiriKira/gsm2sip-server/blob/8b83ae8f613fd7f27ea8e4f3aa1b2148f22c1606/openapi/openapi.yaml)
- [Fixtures](https://github.com/KiriKira/gsm2sip-server/tree/8b83ae8f613fd7f27ea8e4f3aa1b2148f22c1606/fixtures/api)

The client protocol implementation started against `gsm2sip-server` commit `f39b1b58020ebf14697ac84362199881075b2766` (`protocol-v1.md`). The current wire addendum and OpenAPI define pairing and platform metadata, owner-scoped paired client listing, independent incoming call participants, refresh recovery, message/event synchronization, durable client receipts and call history. From 0.4 calls use RTC call control (`docs/rtc-call-control-v1.md` in the server repository); the SIP configuration, credential, call-intent and call-readiness endpoints were removed. The owner, idempotency, and SIM-mapping rules remain enforced across these operations.

When the server protocol changes, update this pin only after checking the corresponding server commit and fixtures. Client parsers consume the pinned gateway, SIM, message/event, call-state and RTC control JSON shapes.

## Optional extensions

- `sim_radio` (server commit `f7a5e6fd66f6036fe09761290f97cd1b806eaad4`, branch `claude/dual-sim-remote-control-82i2og`): `GET /gateways/{id}/sims` may include a nullable `radio` object per SIM (`signal_level` 0–4, `network_type`, `network_operator`, `roaming`, `updated_at`). The client parses it defensively, caches it in `sim_lines.radio_json` (database version 2), and only shows it while the gateway heartbeat is younger than 120 seconds. Older servers omit the field and the UI falls back to carrier name and service state.
