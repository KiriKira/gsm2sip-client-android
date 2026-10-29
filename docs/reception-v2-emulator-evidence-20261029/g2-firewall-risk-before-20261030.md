# G2 temporary relay isolation risk record (before change)

Recorded 2026-10-29 19:45:19 UTC, before adding any firewall rule.

The active H1 relay endpoint was read from Node A only to identify its HTTPS authority. The sealed endpoint URL was not printed or saved. Its SHA-256 is `e3c1812c2e55d4b382ba95cdcff7a57e0d3d969e91bd22f625b1103ccee89984`; the hostname SHA-256 is `15541eb754c329a03475da30ee6f65ceea34912b0e0182437d82dc9e3dd2cc15`. DNS returned IPv4 addresses `104.16.230.132` and `104.16.231.132`. Node A is `172.30.100.2` on its dedicated Docker network. `DOCKER-USER` exists and had no rules.

Planned temporary effect: reject only new outbound TCP/443 packets whose source is Node A's container address and whose destination is one of those two currently resolved relay addresses. Device-to-Node-A and Node-A-to-database traffic stays available. The destination addresses belong to a shared CDN edge, so an unrelated Node A TLS connection to the same address could also be affected during the short test; remove the rules immediately after one blocked call and verify the chain is clean.

Exact rollback commands:

```sh
sudo iptables -D DOCKER-USER -s 172.30.100.2/32 -d 104.16.230.132/32 -p tcp --dport 443 -m comment --comment reception-v2-g2-20261030 -j REJECT
sudo iptables -D DOCKER-USER -s 172.30.100.2/32 -d 104.16.231.132/32 -p tcp --dport 443 -m comment --comment reception-v2-g2-20261030 -j REJECT
```

This is a reversible host firewall change scoped to the test container and relay edge. It uses no root access inside either emulator, no Magisk, no product-code change, and no database mutation. The rollback will be run in the same SSH session immediately after the blocked-call observation, and the rule list will be checked both before and after.

## Refreshed preflight — 2026-10-29 22:30 UTC

Immediately before the planned retry, all three dedicated test containers were `running/healthy`. Node A remained `172.30.100.2`; its own container resolver still returned exactly the two relay-edge IPv4 addresses above for the same endpoint-host hash. The local host resolver returned a lab-only proxy address, so the firewall target is deliberately taken from Node A's resolver, which is the resolver used by the server container. `DOCKER-USER` had no rules. Node A had 65 call sessions and no `ringing` or `active` session; its two current endpoint generations were 4 and 2, both `failures=0`, `call_failures=0`, and event cursor 8. The real emulator modem call list was empty. The previous G1 real-FCM delivery had reached H2 Telecom RINGING.

For this attempt, insert only the two rules shown above, observe one real emulator GSM call and the resulting counters, then delete those exact rules immediately. Keep the block only long enough for the single call's push-attempt window to complete. The second real GSM call is permitted only after the rules are absent and the first call has ended; it verifies recovery through real FCM. No retry of the blocked call is allowed.
