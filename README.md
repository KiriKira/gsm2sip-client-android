# gsm2sip-client-android

Android host app for controlling SMS through the two SIMs in a separate Android gateway phone (SMS-only operation needs no root). The first implementation provides HTTPS pairing and token refresh, a Material 3 Expressive UI, a cached two-SIM status screen, per-line inboxes and replies, SMS submission and delivery status, and a local task ledger for safe recovery after timeouts.

The app now includes a pinned PJSUA2 SIP SDK, self-managed Telecom calls, CallStyle notifications, mute/DTMF/audio routing and server-authorized remote call intents. Calling requires configured server/gateway voice capability; real dual-SIM audio remains a device acceptance gate. It does not read or send SMS through the host phone or fall back to the host carrier dialer.

Several hosts can pair to the same owner and gateway, using separate one-time codes and credentials. The overview lists paired hosts and platform labels, preserves its last snapshot offline and through folding, and distinguishes pairing from online presence. Incoming calls select the first host that actually answers; other hosts show that another device answered. One host rejecting does not reject the call for everyone. See [multi-host operation](docs/multi-host.md); the shared protocol supports future platforms, while a Windows client is not yet implemented.

## Build

Requires JDK 17 and Android SDK platform 35. From this directory run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --max-workers=2
```

The checked-in Gradle wrapper is copied from the gateway project and pins Gradle 8.9 with its distribution checksum. CI runs the same build and unit-test tasks.

## Pair and use

Install the debug APK, enter the server HTTPS URL and a one-time pairing code created for a `client` role, then choose one of the server-confirmed remote SIMs. Replies keep the incoming message's SIM. An incoming message whose SIM cannot be resolved has no reply shortcut. Offline, the app keeps the latest server snapshot, message cache and per-SIM drafts; it does not show a local SMS as submitted or delivered.

If an SMS POST times out, its body, SIM ID, mapping revision and idempotency key are saved in SQLite. The app asks the user to check or continue that same submission. It never creates a replacement key for an uncertain task, and it queries an existing server message rather than POSTing it again.

Optional user-enabled background SMS sync now uses an authenticated WSS wake channel plus HTTPS event-cursor polling, a visible foreground service, reboot recovery and private, deduplicated inbound SMS notifications. FCM and physical-device sleep-state acceptance remain pending. See [Android UI and background operation](docs/android-ui-and-background.md) for permissions and limits. Background calling is a separate opt-in SIP service; microphone/phoneCall foreground service permissions are used only for an actual call. FoldingFeature and window metrics adapt inner/outer screens, with saved Activity state and system-bar/cutout/IME insets.

## Project notes

- [Implementation plan](PLAN.md)
- [Implementation and data-safety notes](docs/implementation-notes.md)
- [Protocol pin](docs/protocol-pin.md)
- [SIP SDK probe gate](docs/sdk-probe.md)
- [Three-party protocol](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/protocol-v1.md)
- [Joint roadmap](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/roadmap.md)
- [Android gateway](https://github.com/KiriKira/gsm2sip)

SMS cache merges, inbound alert journaling and event-cursor advances share a SQLite transaction;
foreground and background readers fence stale pages against the committed cursor. Pending alerts
are acknowledged after Android accepts the notification, so a process interruption may replay
a notification instead of silently losing it. First history remains silent; summaries are coalesced
and notification delivery still requires user opt-in, permissions and network/OS availability.
See the [feature gaps and weak-network audit](https://github.com/KiriKira/gsm2sip-server/blob/codex/control-plane-foundation/docs/network-and-feature-status.md).
