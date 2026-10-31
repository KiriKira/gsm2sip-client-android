# G2 relay isolation retry risk record

Preflight completed at 2026-10-30 07:59:09 UTC, before any firewall change.

Node A/B/C containers were all running and healthy. Node A is `172.30.100.2`; its `DOCKER-USER` chain had only the default chain declaration and no test rules. The two active A-side push endpoint generations (2 and 6) resolve to the same two IPv4 relay-edge addresses, `104.16.230.132` and `104.16.231.132`; HTTPS GET `/` returned 200 from both endpoint authorities. Their hostname hashes are `15541eb754c329a03475da30ee6f65ceea34912b0e0182437d82dc9e3dd2cc15` and `856fb1e66aad797e1cda9632012798a3b3f3e7b623dfee67abca7e92a870b710`. The current tunnel-host file has hash `7136a1438a29fc9f28ee2fe926e3cc12393b6b1721b60cb2b1fa99315f763fa6`; all three authorities resolve to the same relay-edge IP set. No raw endpoint URL or tunnel hostname was printed or saved.

Before the test, endpoint generations 2 and 6 had `failures=0`, `call_failures=0`, and event cursor 9; call cursors were 389 and 388. SoulGem had zero live call sessions. The Homura modem had `mCallState=0` and no active Telecom ringing call. H1 and H2 were online with incoming reception enabled.

Planned temporary effect: reject only outbound TCP/443 packets from Node A's container address to those two resolved relay-edge addresses. Node B/C, emulator networking, inbound calls, PostgreSQL traffic, and all other container addresses are outside the rule. Because the destination IPs belong to a shared CDN, unrelated Node A TLS traffic to those same IPs may also fail during the brief test window. The rules will be removed immediately after one blocked call window and the `DOCKER-USER` chain will be verified clean before the recovery call.

Exact rollback commands:

```sh
sudo iptables -D DOCKER-USER -s 172.30.100.2/32 -d 104.16.230.132/32 -p tcp --dport 443 -m comment --comment reception-v2-g2-retry-20261030 -j REJECT
sudo iptables -D DOCKER-USER -s 172.30.100.2/32 -d 104.16.231.132/32 -p tcp --dport 443 -m comment --comment reception-v2-g2-retry-20261030 -j REJECT
```

This is a reversible host firewall-only change. It uses no emulator root/Magisk, writes no database rows, changes no pairing/SIM state, and modifies no product code. The only test call will be a real `adb emu gsm call`; no API-generated or synthetic push will be used.
