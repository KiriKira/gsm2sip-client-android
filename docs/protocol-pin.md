# Protocol source pin

Authoritative implemented contract: `gsm2sip-server` commit `77a79f95961d3a8aba0645bbd0c8062ed158a653`.

- [Wire addendum](https://github.com/KiriKira/gsm2sip-server/blob/77a79f95961d3a8aba0645bbd0c8062ed158a653/docs/server-wire-addendum.md)
- [OpenAPI](https://github.com/KiriKira/gsm2sip-server/blob/77a79f95961d3a8aba0645bbd0c8062ed158a653/openapi/openapi.yaml)
- [Fixtures](https://github.com/KiriKira/gsm2sip-server/tree/77a79f95961d3a8aba0645bbd0c8062ed158a653/fixtures/api)

The client protocol implementation started against `gsm2sip-server` commit `f39b1b58020ebf14697ac84362199881075b2766` (`protocol-v1.md`). The current wire addendum and OpenAPI define pairing, refresh recovery, message/event synchronization, durable client receipts, SIP configuration and credential replay, call intents, call history, and incoming call readiness. The owner, idempotency, and SIM-mapping rules remain enforced across these operations.

When the server protocol changes, update this pin only after checking the corresponding server commit and fixtures. Client parsers consume the pinned gateway, SIM, message/event, SIP, call-intent, call-state, and pending-call JSON shapes.
