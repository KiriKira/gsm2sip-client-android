# Reception v2 模拟器验收（2026-10-29）

## 被测提交与环境

测试时四个仓库均在 `claude/vibrant-babbage-ye0ey3` 分支，使用以下 HEAD：

| 组件 | PR | 实际测试提交 |
| --- | --- | --- |
| SoulGem | `KiriKira/gsm2sip-server#33` | `152821079f87af7958d30cebf0cf8c0c316ed8e0` |
| Kyubey | `KiriKira/Kyubey#1` | `1bb9a2bc976ace3ccedb3a95315e01713559bddf` |
| Homura | `KiriKira/gsm2sip#26` | `a41dd196aa4caa279cc28c688469c70ca4ba59f4` |
| Madoka | `KiriKira/gsm2sip-client-android#38` | `770fb716517aa7d4aa26b7ee32b297ea0a4c3c01` |

实验机为 2 vCPU、约 8 GiB RAM、64 GiB 磁盘的 KVM 主机。GW 是 API 34 普通镜像；H1/H2 是 API 35 Google Play 镜像；H3 是 API 34 普通镜像。H1/H2 未登录 Google 账号，未执行 Magisk/root 测试。SoulGem A 使用 PR #33 镜像；A/B/C 有各自数据库，但 B/C 仍是旧 joint-0.4 镜像，不符合本轮多节点测试要求。Kyubey 使用本地 Wrangler 和临时 HTTPS 隧道；本地 redacted worker 日志可用，但没有 Cloudflare `wrangler tail`。A1/A3 的 H1 收到真实 FCM 推送唤醒；本轮 G1 来电未到达 SoulGem，未触发 FCM，不能据此评价推送交付。H3 首次启动时因测试机根盘剩余 5.5G（AVD 要求约 7.37G）退出。清理三个历史、未运行的旧 AVD 后，H3 用原 userdata 成功启动；清理前后的 `df -h`/`du` 和资源数据见证据目录 `disk-space-cleanup-20261029.txt`。

证据目录为 `docs/reception-v2-emulator-evidence-20261029/`。脱敏执行记录在 `run-observations-20261029.md`；新增 B3–B6、G1 证据在 `b3-call-readiness-20261029.txt`、`b4-reboot-status.txt`、`b4-reboot-retest-20261029.txt`、`b5-offline-sync-20261029.txt`、`b6-sms-sync-20261029.txt` 和 `g1-restart-20261029.txt`。H3 清理与补测数据见 `disk-space-cleanup-20261029.txt`、`h3-e1-diagnostics.json`、`h3-e4-segment.json`、`h3-e4-diagnostics.json` 和 `h3-recovery-20261029.md`。认证头、设备/会话标识、推送 endpoint、手机号和 Firebase 凭据没有放进仓库；本地 Kyubey redacted 日志留在 `/tmp`，只提交计数和结果摘要。

## 结果

| 用例 | 结果 | 关键证据/说明 |
| --- | --- | --- |
| P0 | 通过 | `p0-ui-switches.txt`：UI 开关与 SQL 一致，两个设备均为 v2；push endpoint 2 个，failures 均为 0 |
| A1 | 通过 | `run-observations-20261029.md`：3 次时延分别 1.695、2.119、2.306 秒；均响铃并接听 |
| A2 | 通过 | 同上：等待 60 秒后无 HostCallService、信令通知、持锁或兼容续约/闹钟新增 |
| A3 | 通过 | 同上：杀进程、熄屏、强制 Doze 后由推送唤醒并接听 |
| A4 | 通过 | 同上：两种取消时机均停止响铃，无迟到或重复响铃；第一次短暂振铃约 0.71 秒 |
| A5 | 仅记录 | 同上：restricted 桶下响铃并接听，保持通话至少 10 秒；未取得可提交的 FCM 优先级值 |
| B1 | 通过 | 同上：H1 关闭、H2 开启；只有 H2 响铃，SQL 开关状态正确 |
| B2 | 通过 | 同上：两台各一条短信事件，服务端增加一条消息，未见重复事件 |
| B3 | 阻塞（环境） | `b3-call-readiness-20261029.txt`：H1 重启后稳定，但 Madoka 拨号页明确提示旧手机远程通话尚未就绪；普通 Homura 镜像无 Magisk/root，未继续制造预期会失败的去电。早先 `control_expired` 约 20.5 秒的尝试一并保留 |
| B4 | 通过 | `b4-reboot-status.txt`、`b4-reboot-retest-20261029.txt`：划掉/重开与重启后重开均保持来电关闭、短信同步开启；重启复测无 ANR，页面显示接收设置已同步 |
| B5 | 阻塞（环境；同步子项通过） | `b5-offline-sync-20261029.txt`：离线切换、待同步提示及联网后 3.4 秒内 SQL/UI 同步均通过；仅 H2 来电断言因 G1 的 Homura 模拟器来电注入阻塞 |
| B6 | 阻塞（环境；短信同步子项已观察） | `b6-sms-sync-20261029.txt`：真实模拟器 SMS 入库；关闭时 H1 endpoint cursor 不变，恢复同步后 marker 在消息线程中出现一次；本地 Kyubey 无新请求，FCM relay 与后续来电断言受环境阻塞 |
| C1 | 阻塞（环境） | GW 的 ADB GSM 来电未产生可由 Homura 处理的 modem/Telecom 来电；拒接链路无法开始 |
| C2 | 阻塞（环境） | 同上；无法形成两端同时响铃供竞争测试 |
| C3 | 阻塞（环境） | 同上；无法形成两端响铃供切换接收开关测试 |
| D1 | 阻塞（环境） | GW 来电注入不可用，且 B/C 仍使用旧 joint-0.4 镜像，不符合 PR #33 多节点环境 |
| D2 | 阻塞（环境） | 同上；未在不合格的 B/C 镜像上执行 |
| D3 | 阻塞（环境） | 同上；当前网关无法产生进入 SoulGem 的来电 |
| E1 | 通过 | `h3-e1-diagnostics.json`、`h3-recovery-20261029.md`：v2 stream、120 秒心跳、开关同步；抓包有三次间隔 120 秒的服务端 ping 和对应 pong，H3 流无客户端 25 秒 ping |
| E2 | 阻塞（环境） | `h3-recovery-20261029.md`：GW 无 root/Magisk 语音能力；注入未形成 modem call、服务器 session 或 H3 ringing |
| E3 | 阻塞（环境） | `h3-recovery-20261029.md`：API 34 AVD 没有已连接 Wi-Fi 传输；开关 Wi-Fi 未形成计划要求的双向网络切换 |
| E4 | 仅记录 | `h3-e4-segment.json`、`h3-e4-diagnostics.json`、`h3-recovery-20261029.md`：14:44:32–15:14:32 UTC；15:16 快照心跳 270 秒、无当前持锁；15:22 观察后导出仍为 `wss_live=true`；累计 `wss_open/closed` 从 E3 末的 9/8 增至 14/13（五组重连），仅记录不判定 |
| F1 | 阻塞（环境） | 本轮未执行；当前 GW 远程通话未就绪，无法完成计划要求的通话顺序。10 月 28 日旧目标上的 FAIL 仍只保留在旧报告 |
| F2 | 阻塞（环境） | 同上；不以不完整通话替代计划用例 |
| F3 | 阻塞（环境） | 同上；H1 当前稳定，但升级后的通话验收仍依赖未就绪的 GW |
| G1 | 阻塞（环境；服务恢复子项通过） | `g1-restart-20261029.txt`：节点 A 重启后健康，三台设备仍为 v2 且设置保留；Homura 的计划 ADB 来电命令没有产生 modem call、新的 `call_sessions` 或 H1 推送/响铃日志，停在模拟器蜂窝/Telecom 注入层 |
| G2 | 阻塞（环境/前置未满足） | G1 未到达 SoulGem，且本地 Kyubey 未收到本轮 wake 请求，未执行不可达注入 |
| H1 一小时 push 观察 | 阻塞（环境；本轮未开始） | `push-environment-status-20261029.txt`：两条 push endpoint 的失败计数均为 8、事件游标仍为 4；本地 Kyubey 在 G1/B6 期间没有新 wake 请求，push 环境尚未恢复 |
| H3 一小时 stream 观察 | 阻塞（方法；保留部分记录） | 15:25:27.975–16:08:49.185 UTC，连续 43 分 21.210 秒；未满计划要求的一小时。idle 设置随后恢复且 H3 已关闭，若要获得完整结果需重新进行连续 60 分钟观察；不外推为通过 |

## A1 逐次时延

| 次数 | GW 首次振铃（UTC） | Madoka `Incoming call ringing`（UTC） | 时延 | 结果 |
| --- | --- | --- | --- | --- |
| 1 | 07:08:46.001Z | 07:08:47.696Z | 1.695 秒 | 接听，通话约 16 秒 |
| 2 | 07:21:14.153Z | 07:21:16.272Z | 2.119 秒 | 接听并保持至少 10 秒 |
| 3 | 07:33:29.651Z | 07:33:31.957Z | 2.306 秒 | 接听并保持至少 10 秒 |

三次都低于 5 秒目标和 25 秒上限。逐次原始 logcat/诊断导出未随报告提交；脱敏的现场摘录见证据目录中的执行观察文件。

## 一小时观察

| 模式 | 结果 | 原因 |
| --- | --- | --- |
| H1 push | 阻塞（环境；本轮未开始） | 两条 endpoint 组的失败计数均为 8、event cursor 均为 4；本地 Kyubey 在 G1/B6 期间没有新 wake 请求，无法形成真实推送观察 |
| H3 stream | 阻塞（方法；部分记录 43 分 21 秒） | 15:25:27.975–16:08:49.185 UTC，未达到一小时；按用户要求停止实时等待，不能用加速时钟或累计计数替代。由于随后恢复 idle 初始配置并关闭 H3，完整结果需重新连续观察 60 分钟 |

## 缺陷与真机阶段结论

本轮没有确认可归因于产品代码的缺陷，因此没有修改或推送产品代码，也没有以环境故障为由放宽验收标准。B3 拨号页明确提示 Homura 远程通话未就绪；G1 的 ADB GSM 命令未形成 modem/Telecom 来电或 SoulGem session。B4 初次重启时的 ANR 在关闭 H3、释放内存后未复现，开关持久化检查通过。H1 push 观察未开始，因为本地 Worker 在本轮没有收到 wake 请求。

A1–A4、B1–B2 和 B4 在模拟器上通过；B5 的离线设置同步子项及 B6 的短信积压展示也已观察，但不代表相关来电或 FCM relay 子项通过。基本推送唤醒、通话取消、多设备开关隔离与短信路径可进入真机阶段验证。真实蜂窝 HAL 音频、厂商后台限制、A5 的 FCM 降级行为和长时间耗电仍需真机确认。B3、B5/B6 的剩余断言、C/D 多节点、F 升级顺序和 G2 尚未验证；H3 小时观察只有 43 分 21 秒，不能视为通过。GPT-6.1 Sol 评估认为，`-icount` 或调整模拟器时钟会让 guest 计时与真实网络、FCM、服务端时钟脱节，不能代替连续一小时；batterystats、deviceidle、调度记录、logcat 和 ping/pong 时间戳只能佐证实际经过的墙钟时长。
