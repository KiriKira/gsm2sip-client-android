# Protocol source pin

Authoritative implemented contract: `gsm2sip-server` commit `42f1b3c2c26a9eda018e7baa9d365f97f5bfcfec`.

- [Wire addendum](https://github.com/KiriKira/gsm2sip-server/blob/42f1b3c2c26a9eda018e7baa9d365f97f5bfcfec/docs/server-wire-addendum.md)
- [OpenAPI](https://github.com/KiriKira/gsm2sip-server/blob/42f1b3c2c26a9eda018e7baa9d365f97f5bfcfec/openapi/openapi.yaml)
- [Fixtures](https://github.com/KiriKira/gsm2sip-server/tree/42f1b3c2c26a9eda018e7baa9d365f97f5bfcfec/fixtures/api)

The client protocol implementation started against `gsm2sip-server` commit `f39b1b58020ebf14697ac84362199881075b2766` (`protocol-v1.md`). The server wire addendum dated 2026-10-03 and the matching OpenAPI shapes were reviewed for this implementation. The addendum completes the v1 pairing, token rotation, message, event, and call-unavailable response shapes without changing the protocol's owner, idempotency, or SIM-mapping rules.

When the server protocol changes, update this pin only after checking the corresponding server commit and fixtures. Client parsers currently consume the frozen server JSON shapes for gateway list, SIM list, event pages, and `MessageDetail`.
