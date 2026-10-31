# Reception v2 模拟器验收（2026-10-28）

## 被测提交与环境

测试开始时从 `claude/vibrant-babbage-ye0ey3` 读取的分支 HEAD：

| 组件 | PR | 提交 |
| --- | --- | --- |
| SoulGem | `KiriKira/gsm2sip-server#33` | `152821079f87af7958d30cebf0cf8c0c316ed8e0` |
| Kyubey | `KiriKira/Kyubey#1` | `1bb9a2bc976ace3ccedb3a95315e01713559bddf` |
| Homura | `KiriKira/gsm2sip#26` | `ef2c28306cffac90bba15adfb3b161fa52c05a01` |
| Madoka | `KiriKira/gsm2sip-client-android#38` | `7c7d5c0fd76791917a8b76f6644f595ae290083e` |

测试中修复 Madoka 拨出 Telecom 元数据问题，后续目标版使用 `fec36366248f3c454ae0d39bfe2cdca7fcd4c6d6`；修复 Homura 旧心跳恢复问题后使用 `f39b4304108848469fa4f9f31ac65f90466cbe02`。修复前的结果保留。

实验机：远程 KVM 测试主机（2 vCPU、约 8 GiB RAM、64 GiB 磁盘）。目标模拟器镜像为 Homura API 34 普通 x86_64、H1/H2 API 35 Google Play x86_64、H3 API 34 普通 x86_64；逐台登录/FCM 可用状态、Android build fingerprint 与 AVD 配置见本地证据归档。SoulGem A/B/C 均使用上述服务端提交构建，独立网络与 PostgreSQL。

Kyubey 使用上述提交在本机 Wrangler 本地运行，通过无账号的 Cloudflare 临时 HTTPS 隧道供实验机访问，使用与 Madoka 相同 Firebase 项目的凭据；已观察到真实 FCM 投递。实验副本只增加了外部入口地址配置和不包含标识符的 kind、wire、TTL、priority 日志；对应证据是 Wrangler 本地日志，而非部署后 `wrangler tail`。H1/H2 使用 Google Play 镜像，但没有登录 Google 账号，是计划环境的偏差，不能以此代替真机后台表现。所有用例均不做 Magisk/root 测试；模拟 PCM 结果不代表真实蜂窝 HAL 音频。

证据目录：`docs/reception-v2-emulator-evidence-20261028/`。密钥、token、Firebase 凭据、手机号与未脱敏的密封 endpoint 不进入仓库。

## 结果

| 用例 | 结果 | 关键证据 |
| --- | --- | --- |
| P0 | 待执行 | — |
| A1 | 待执行 | — |
| A2 | 待执行 | — |
| A3 | 待执行 | — |
| A4 | 待执行 | — |
| A5 | 仅记录 | — |
| B1 | 待执行 | — |
| B2 | 待执行 | — |
| B3 | 待执行 | — |
| B4 | 待执行 | — |
| B5 | 待执行 | — |
| B6 | 待执行 | — |
| C1 | 待执行 | — |
| C2 | 待执行 | — |
| C3 | 待执行 | — |
| D1 | 待执行 | — |
| D2 | 待执行 | — |
| D3 | 待执行 | — |
| E1 | 待执行 | — |
| E2 | 待执行 | — |
| E3 | 待执行 | — |
| E4 | 仅记录 | — |
| F1 | 失败 | 旧版来电、短信和 RTC 去电链路可用，但去电 Telecom 返回 `Missing call details`；见 `f1-telecom-error.txt`、`f1-host-logcat.txt`、`f1-server-sql.txt` |
| F2 | 失败 | 更新 Homura 后来电响铃接通、短信送达；旧版 Madoka 去电仍报 `Missing call details`，GW 日志缺少计划要求的 `remaining_ring_ms`。见 `f2-logs-sql.txt`、`f2-telecom-error.txt`、`f2-kyubey.txt` |
| F3 | 待执行 | — |
| G1 | 待执行 | — |
| G2 | 待执行 | — |
| H1 一小时 push 待机 | 待执行 | — |
| H3 一小时 stream 待机 | 待执行 | — |

## A1 逐次时延

| 次数 | GW 首次 RINGING | Madoka `HostCall: Incoming call ringing` | 时延 | 接听/结束 |
| --- | --- | --- | --- | --- |
| 1 | 待执行 | 待执行 | — | — |
| 2 | 待执行 | 待执行 | — | — |
| 3 | 待执行 | 待执行 | — | — |

## 一小时观察

| 模式 | 起止时间 | 诊断/系统快照 | 结论 |
| --- | --- | --- | --- |
| H1 push | 待执行 | — | — |
| H3 stream | 待执行 | — | — |

## 缺陷与修复

- F1 旧版 Madoka 主动拨号时，Telecom 拒绝连接并报告 `Missing call details`；目标分支提交 `fec36366248f3c454ae0d39bfe2cdca7fcd4c6d6` 修正 outgoing extras 的传递，增加回归测试，目标版仍待重测。旧版 main 本身未修改。
- F2 在仅节点 A 在线时，旧版 Madoka 的来电经真实 FCM 唤醒并接通，测试短信显示在短信列表；去电在服务器进入 active，但 Android Telecom 同时以 `Missing call details` 断开连接。GW 本次 offer 日志未输出计划要求的 `remaining_ring_ms`。此前有一次操作者未及时接听，另有两次通知操作未命中，均保留在 `f2-attempt-*`、`f2-second-*` 和 `f2-third-*`，不作为产品判定；最终接听使用设备端自动点击完成。
- 准备多节点时发现 Homura 会将新节点合法的空 SIM 心跳 revision 0 判为无效，并持续重放旧心跳；主节点 SIM revision 更新后也有旧心跳卡住。修复前证据见 `setup-heartbeat-defect.txt`。Homura `f39b4304108848469fa4f9f31ac65f90466cbe02` 增加心跳和持久化回归；177 项单测、lint 和构建通过。覆盖安装后保留原数据，A/B/C 自动发出新心跳，缓存 revision 与当前映射一致，旧 pending 清空，见 `setup-heartbeat-recovery.txt`；相关联调用例与 A1 仍待重测。

## 真机阶段结论

待模拟器用例完成后填写；真实蜂窝 HAL 音频、真实手机厂商后台限制和长时耗电只能在真机阶段判断。

## 归档说明

本报告保留 F1/F2 的原始失败结论。详细原始证据仍在本地 `docs/reception-v2-emulator-evidence-20261028/`；其中含通话标识、设备标识或号码等信息，因此本次只发布脱敏报告，不上传该目录中的原始日志与诊断 JSON。
