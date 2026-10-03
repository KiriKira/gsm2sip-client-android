# gsm2sip-client-android

Android host app for controlling SMS through the two SIMs in a separate rooted gateway phone. The first implementation provides HTTPS pairing and token refresh, a cached two-SIM status screen, per-line inboxes and replies, SMS submission and delivery status, and a local task ledger for safe recovery after timeouts.

Calls stay visibly unavailable while gateway audio and server call routing are being verified. The app does not read or send SMS through the host phone, request the default SMS or dialer role, or invoke the system dialer.

## Build

Requires JDK 17 and Android SDK platform 34. From this directory run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --max-workers=2
```

The checked-in Gradle wrapper is copied from the gateway project and pins Gradle 8.5 with its distribution checksum. CI runs the same build and unit-test tasks.

## Pair and use

Install the debug APK, enter the server HTTPS URL and a one-time pairing code created for a `client` role, then choose one of the server-confirmed remote SIMs. Replies keep the incoming message's SIM. An incoming message whose SIM cannot be resolved has no reply shortcut. Offline, the app keeps the latest server snapshot, message cache and per-SIM drafts; it does not show a local SMS as submitted or delivered.

If an SMS POST times out, its body, SIM ID, mapping revision and idempotency key are saved in SQLite. The app asks the user to check or continue that same submission. It never creates a replacement key for an uncertain task, and it queries an existing server message rather than POSTing it again.

M2 receives updates through authenticated HTTPS event-cursor polling while the app is open. WSS, FCM/background wakeups, SMS notifications, and physical-device end-to-end acceptance are still pending. Calling remains disabled until the SDK, gateway audio, and server call route pass the separate probe gate.

## Project notes

- [Implementation plan](PLAN.md)
- [Implementation and data-safety notes](docs/implementation-notes.md)
- [Protocol pin](docs/protocol-pin.md)
- [SIP SDK probe gate](docs/sdk-probe.md)
- [Three-party protocol](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/protocol-v1.md)
- [Joint roadmap](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/roadmap.md)
- [Rooted gateway](https://github.com/KiriKira/gsm2sip)
