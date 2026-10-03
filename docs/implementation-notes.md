# M1/M2 implementation notes

The app uses a single Kotlin Android module and a small native View UI. `HttpsURLConnection` accepts HTTPS only, follows no redirects, sends bearer credentials only in the authorization header, and uses the platform certificate trust store. The pairing claim body contains only `pairing_code` and `device_name`. Pair and refresh responses are encrypted as one AES-GCM blob using an Android Keystore key; that blob includes the server API base URL, owner/device IDs, and both token values and expiries. The server origin therefore cannot be changed independently of the stored credentials.

SQLite stores the gateway snapshot, SIM mappings, cached server messages, per-SIM drafts, opaque event cursor, and each outbound task's exact request JSON and key. Database filenames are derived from normalized API origin, `owner_id`, and client `device_id`, so a different account or server cannot see or retry the previous account's local work. Explicit unpair first asks the server to revoke the refresh-token family, then clears that scoped cache.

An outbound task is written to SQLite before the network request. A response loss leaves it `unknown`; app restart converts a `submitting` task with no server message ID to `unknown`. The periodic foreground sync only reads snapshots, events and known message IDs; it never automatically replays a POST. A user-initiated retry with no message ID uses the saved key and byte-for-byte-equivalent payload fields. If the server message ID is known, the UI only fetches its current status. If the SIM mapping changed, the user must refresh and choose a line again; an existing task still retains the SIM ID and revision captured at submission.

Messages are cached and rendered by stable `sim_id` and peer address. A null `sim_id` is rendered in its own unknown-SIM section with no original-line reply action. The compose flow requires a verified active remote line and shows the selected line before creating a task. Server `queued`, gateway `accepted_by_gateway`, radio `submitted`, carrier `delivered`, `failed`, `expired`, and `unknown` values remain distinct. `delivered` is shown only when the server reports it.

M2 inbound synchronization uses authenticated HTTPS `/events` cursor polling while the app is open and `/messages` snapshot backfill after first pairing or a resync request. WSS event subscriptions, FCM registration, SMS notifications, and reliable sleep-state delivery are not implemented yet. The app does not promise background inbox updates while asleep. No local telephony, SMS, contacts, call, root, or default-app permission is declared.

Calling remains disabled. The UI explains that call service is waiting for gateway audio and server calling setup verification. There is no SIP stack, `ACTION_DIAL`, or carrier-call fallback in this build.

## Current verification limits

CI and the local Android build verify debug APK assembly, retry decisions, and SQLite persistence, merge, and account-isolation behavior under Robolectric. They do not validate a running server, rooted gateway handset, second physical Android device, carrier delivery receipts, or real two-SIM operation. The APK remains a debug artifact until device acceptance and release signing are completed.
