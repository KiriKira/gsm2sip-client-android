# C3 Node B private TURN route — risk and rollback before action

Recorded 2026-10-31 before any Node B change.

## Scope and observed state

The C3 B-node test setup now has H1 and H2 paired to the same Node B owner through the supported Add Node flow. Both devices must still pass a read-only preflight (healthy, incoming enabled, valid real FCM endpoint, no active call) before the call. Node B is healthy on image `sha256:1f6fd76b942af40b751ede535ade03604de858ca4a40054406d4afb56fa5c1d4`, with its existing `/data` bind, network, restart policy, and loopback API port unchanged. It advertises the public TURN listener on `18.119.132.34:3478`, which is not reachable from the emulator network. Earlier C3 work proved a private authenticated TURN data-channel path for both AVDs, but did not run a GSM call or the C3 reception-switch assertion.

## Reversible change

For this C3 run only, start the already-reviewed, digest-pinned coturn sidecar on the test host's private VPC address `172.31.15.170:13478`, limited to 128 MiB and 0.5 CPU, with relay ports `49300-49399`. Read the existing Node B TURN shared secret directly into a mode-0600 private config; never print it, store it in the repository, or include it in logs. After an authenticated allocation probe succeeds, preserve Node B's exact container configuration and a private database recovery dump, then recreate only Node B using the same image, `/data` mount, network, port binding, restart policy, and every existing environment value except `RTC_TURN_URLS`. Keep the original stopped container as the rollback copy. AVD TURN access uses the already-probed private route and temporary local TCP forward; no public firewall/security-group changes are made.

Rollback restores the retained original Node B container against the same `/data` directory and verifies health, image, environment, mount, network, port binding, and read-only owner/device/reception/endpoint state. It does not restore an old database dump over runtime writes. Then remove only the test sidecar, temporary TURN config, local forward, and private backup files after successful verification. A/B/C remain otherwise untouched. This action performs no manual SQL write, pairing change, SIM change, or reception setting update.

## Stop conditions

Do not start if the call/modem is active, any A/B/C node is unhealthy, H1/H2 owner or gateway install identity differs, either endpoint is unhealthy, Node B's current image/config/mount no longer matches the recorded values, or root free space is below 3 GiB. Keep at least 1 GiB `MemAvailable`; the current preflight is 1.3 GiB, so stop if it falls below that floor before or during sidecar startup. Stop and roll back if the private allocation fails, the sidecar binds outside the VPC address or outside the isolated relay range, B fails health within 120 seconds, any non-TURN runtime configuration differs, B owner/reception/endpoint rows change unexpectedly, or an Android ANR/OOM appears. Do not inject a call until both H1 and H2 gather the private relay candidate and Node B passes all read-only checks. After the one scored C3 call, restore Node B's original TURN configuration before any F test.

This changes a test-only server runtime setting and temporarily restarts Node B; it can interrupt B during the bounded restart window. It is reversible at the container/configuration level. Normal runtime requests may append ordinary server call/audit rows; no database contents are manually edited or rolled back.
