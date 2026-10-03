# Protocol source pin

Authoritative implemented contract: `gsm2sip-server` commit `56f55f77a4840f0e6797a1765165db601f7c08b4`.

- [Wire addendum](https://github.com/KiriKira/gsm2sip-server/blob/56f55f77a4840f0e6797a1765165db601f7c08b4/docs/server-wire-addendum.md)
- [OpenAPI](https://github.com/KiriKira/gsm2sip-server/blob/56f55f77a4840f0e6797a1765165db601f7c08b4/openapi/openapi.yaml)
- [Fixtures](https://github.com/KiriKira/gsm2sip-server/tree/56f55f77a4840f0e6797a1765165db601f7c08b4/fixtures/api)

The client protocol implementation started against `gsm2sip-server` commit `f39b1b58020ebf14697ac84362199881075b2766` (`protocol-v1.md`). The server wire addendum dated 2026-10-03 and the matching OpenAPI shapes were reviewed for this implementation. The addendum completes the v1 pairing, token rotation, message, event, and call-unavailable response shapes without changing the protocol's owner, idempotency, or SIM-mapping rules.

When the server protocol changes, update this pin only after checking the corresponding server commit and fixtures. Client parsers currently consume the frozen server JSON shapes for gateway list, SIM list, event pages, and `MessageDetail`.
