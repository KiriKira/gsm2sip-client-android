# SIP SDK probe gate

The host app's M1/M2 build intentionally contains no SIP native library. Its call screen reports that calling is not enabled until the gateway audio path and server call routing have passed the M3 probe.

## PJSUA2 source status

PJSIP 2.17 and earlier are not acceptable release pins for this app. The upstream advisories identify DNS response validation and TLS certificate-name validation problems. The upstream `pjproject` commit currently recorded for the probe is `a67b8e81b0024b993f47e463c01c67c25cda116f`; the official upstream history contains fixes at or before that commit:

| Issue | Upstream fix commit |
|---|---|
| DNS response validation (GHSA-pvmg-ph43-54r2) | `d9514ce3ef56eb12ce59ec6148cd703ca9fae731` |
| Embedded NUL in TLS SubjectAltName (GHSA-382p-87mh-r3q8) | `43d3bd77bb6833eab4c493503b8d564a754ddfdd` |
| TLS CN/SAN name validation correction | `44869567853c2d368d8be5bd20fafdab0c3f1335` |
| IP SubjectAltName validation correction | `764d73433798ea61a5fb7c1e00440c11958444e3` |

`tools/check-pjsip-pin.sh` fetches the official repository at the pinned commit, checks the exact source SHA, and verifies that each listed fix is an ancestor of the pin. This is source provenance checking only. It does not claim that TLS is correctly configured in the app, build a native library, or pass the device/security probe.

## M3 release gate

Before enabling calls or packaging PJSUA2, the probe must also establish all of the following on the selected Android NDK and a real target device:

- Build arm64-v8a from the pinned official source and record compiler versions, source SHA, build flags, dependency licenses, and native library SHA-256 values. Check Android 16 KB page-size loading where required.
- Configure server certificate validation explicitly, verify CA chain and SIP hostname/SNI, and prove that an untrusted CA, wrong hostname, expired certificate, and failed handshake all stop registration.
- Require TLS for SIP signalling and mandatory SDES-SRTP for media. A server offering plain RTP or no supported SRTP suite must fail the call without downgrade.
- Interoperate with the pinned Asterisk configuration and verify two-way audio, Telecom audio endpoints, lock/unlock, app process recovery, and host cellular-call interruptions.
- Review the exact PJSIP license path and every native dependency before distributing a release APK.

Until those checks have recorded results, the SIP capability remains unavailable. No PJSIP 2.17 binary or other unverified native artifact is included in the host app.
# 网关实现方向补充

旧机端采用 Magisk 模块生命周期、priv-app 权限和受限账户 broker，按实际
系统能力确认订阅与 PhoneAccount；不要求特定机型或 API 31 才开始语音
实现。主机保持未 root，无需 Magisk。通用网关适配与主机 SIP SDK、服务端
ARI 是独立实现项；本端在完整链路就绪前继续清楚报告通话不可用。
