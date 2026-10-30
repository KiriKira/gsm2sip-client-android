# C3 private TURN sidecar — risk and rollback before action

Recorded 2026-10-31 before starting a TURN listener.

## Scope

Prepare a separate coturn container on the R1 test machine for private-network connectivity checks. Bind only to the host VPC address `172.31.15.170`; use TCP/UDP listener port `13478` and UDP relay ports `49300-49399`. Use the shared TURN secret already configured on Node A without printing it or writing it to the repository. Pin the already-present coturn image by digest `sha256:c29135c08565810d37053f384584392b7c24b69570fc81a721b878d6fe63e04e`.

This preparation does not change Node A's `RTC_TURN_URLS`, recreate any SoulGem node, alter firewall/security-group rules, touch a database, or generate a call. It is only a private listener/credential smoke check; it cannot be counted as the C3 end-to-end result.

## Reversibility and risks

- The only intended persistent changes are one named test container (`reception-r1-private-turn`) and a mode-0600 config under the R1 private test directory on the test machine.
- Existing listeners on public port 3478 and relay ports 49160-49259 remain untouched. The candidate private ports were checked free immediately before this record.
- The secret is copied directly from Node A's runtime environment into the private config by a script; command output, logs, and Git must not contain its value.
- Host networking and private VPC binding make the service reachable to the test-machine VPC/AVDs; no new public port is opened.
- Roll back with `sudo docker rm -f reception-r1-private-turn` and remove only the generated private config. No Node A or database rollback is involved.

## Stop conditions

Stop without changing Node A if the candidate ports become occupied, coturn cannot bind exclusively to `172.31.15.170`, an authenticated private allocation fails, the container restarts/exits, or the machine shows resource pressure. Any later change to Node A's advertised TURN URL is a separate action and requires a fresh pre-change DB/config snapshot and an explicit rollback plan. Do not use this listener to claim a call or media test passed.

## Result and cleanup

The initial default-user container could not read the mode-0600 mounted config; a second invocation with `cap-drop ALL` could not execute the image binary. Both instances were removed. The working instance ran as UID 1000 with `no-new-privileges`, a 128 MiB memory limit, and a 0.5 CPU limit; `ss` showed only the VPC address on port 13478, with no CLI listener. An authenticated REST allocation smoke test succeeded and allocated a relay port from the configured range. This verified host-to-sidecar TURN authentication/allocation only, not AVD reachability or C3 media.

After the smoke test, the sidecar was removed to release its temporary relay allocations, the secret-bearing config was securely unlinked, and checks found no container, listener, or relay socket remaining. Node A's settings and databases were untouched. Detailed redacted measurements are in `c3-private-turn-preflight-20261031.txt`.
