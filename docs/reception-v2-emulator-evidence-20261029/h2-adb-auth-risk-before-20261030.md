# Local H2 ADB authorization workaround — pre-action record (2026-10-30)

The local H2 AVD is a copy of the stopped remote API 35 Play AVD. Its saved Android state trusts the remote host's ADB public key, so the local ADB host reports the copied emulator as `unauthorized`. The emulator was shut down cleanly through its console; app data and the remote source AVD remain untouched.

## First attempt and result

The local copy was started once with `-skip-adb-auth`; its emulator ports were confirmed bound only to `127.0.0.1`/`::1`. The guest still reported the local ADB client as unauthorized. It was not used for app testing, and the emulator will be shut down cleanly before the next approach. This process option changed no app data or persistent Android settings.

## Planned replacement

Do not copy the remote ADB private key and do not change the guest's saved ADB keys. Run the local copied AVD normally, then create one temporary SSH reverse forward from remote loopback port 15559 to local loopback port 5559. The remote ADB server already has the matching key trusted by this cloned guest. Verify the remote port is loopback-only and the remote ADB state is `device` before any test operation.

## Risk and rollback

The failed `-skip-adb-auth` process would have allowed local processes to control the copied test device without key approval; its ports were loopback-only and it will be stopped. The replacement exposes only a temporary remote-loopback SSH listener on port 15559, forwarding to local loopback port 5559; it is not exposed to the remote LAN or internet. Rollback is to disconnect the remote ADB transport, stop the SSH session, and shut down the local emulator. The remote AVD remains unchanged and is the recovery copy. No remote private key is copied; no root, Magisk, writable-system, app-data reset, product-code change, or host-wide setting is involved.

## Verification before test use

After startup, record `ss -lntp` for both emulator and remote-forward ports, confirm each binds only to loopback, wait for `sys.boot_completed=1`, verify the retained app/pairing state and current FCM registration through normal app behavior and server SQL. Do not run B5/C unless real FCM delivery is independently evidenced on the handset.
