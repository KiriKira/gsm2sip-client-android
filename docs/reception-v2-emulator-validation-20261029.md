# Reception v2 模拟器验收（2026-10-29）

## 被测提交与环境

测试时四个仓库均在 `claude/vibrant-babbage-ye0ey3` 分支，使用以下 HEAD：

| 组件 | PR | 实际测试提交 |
| --- | --- | --- |
| SoulGem | `KiriKira/gsm2sip-server#33` | `152821079f87af7958d30cebf0cf8c0c316ed8e0` |
| Kyubey | `KiriKira/Kyubey#1` | `1bb9a2bc976ace3ccedb3a95315e01713559bddf` |
| Homura | `KiriKira/gsm2sip#26` | `a41dd196aa4caa279cc28c688469c70ca4ba59f4` |
| Madoka | `KiriKira/gsm2sip-client-android#38` | `770fb716517aa7d4aa26b7ee32b297ea0a4c3c01` |

实验机为 2 vCPU、约 8 GiB RAM、64 GiB 磁盘的 KVM 主机。GW 是 API 34 普通镜像；H1/H2 是 API 35 Google Play 镜像；H3 是 API 34 普通镜像。H1/H2 未登录 Google 账号，未执行 Magisk/root 测试。SoulGem A 使用 PR #33 镜像；A/B/C 有各自数据库，但 B/C 未处于本轮多节点用例所需的可用状态。Kyubey 使用本地 Wrangler 运行并经临时 HTTPS 隧道供实验机访问，没有部署成 Worker，因此没有 `wrangler tail`。A1/A3 的 H1 收到真实 FCM 推送唤醒；G1 的 H2 未证实收到 FCM。H3 无法启动，启动器将 RAM 配置提高到 2.5 GiB 后，因 userdata 空间不足退出（可用 5661.45 MB、要求 7372.80 MB）。

证据目录为 `docs/reception-v2-emulator-evidence-20261029/`。其中 `run-observations-20261029.md` 是本轮脱敏执行记录，`p0-ui-switches.txt`、`b4-reboot-status.txt` 和 `b4-post-reboot-anr.png` 是保留的本地证据。完整原始日志、诊断导出、SQL 和推送 endpoint 含设备/会话标识，未复制进仓库；Kyubey 的本地 Wrangler 也没有 Cloudflare `wrangler tail`。此证据完整性限制不改变下表中的环境阻塞判定。

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
| B3 | 阻塞（环境，原因未定位） | 同上：outgoing session 约 20.5 秒后 `control_expired`、`dispatched=false`；instrumentation 仍运行，不能确认产品或环境原因 |
| B4 | 阻塞（环境，原因未定位） | `b4-reboot-status.txt`、`b4-post-reboot-anr.png`：划掉/重开通过；重启后 ANR，持久化状态仍正确但 UI 检查未完成 |
| B5 | 阻塞（环境） | H1 重启后 ANR，离线开关 UI 步骤未执行 |
| B6 | 阻塞（环境） | H1 重启后 ANR，短信/开关步骤未执行 |
| C1 | 阻塞（环境） | H1 重启后 ANR，依赖 UI/拒接交互未执行 |
| C2 | 阻塞（环境） | H1 重启后 ANR，双端竞争用例未执行 |
| C3 | 阻塞（环境） | H1 重启后 ANR，切换来电开关用例未执行 |
| D1 | 阻塞（环境） | H1 不可用，多节点 B/C 未处于可测试状态 |
| D2 | 阻塞（环境） | H1 不可用，多节点 B/C 未处于可测试状态 |
| D3 | 阻塞（环境） | H1 不可用，多节点 B/C 未处于可测试状态 |
| E1 | 阻塞（环境） | H3 未能创建 userdata 分区，未启动 |
| E2 | 阻塞（环境） | H3 未启动 |
| E3 | 阻塞（环境） | H3 未启动 |
| E4 | 阻塞（环境） | H3 未启动，无法做 30 分钟观察 |
| F1 | 阻塞（环境） | 本轮未执行；H1 在前序 B4 后 ANR。10 月 28 日旧目标上的 FAIL 只保留在旧报告 |
| F2 | 阻塞（环境） | 本轮未执行；H1 在前序 B4 后 ANR。10 月 28 日旧目标上的 FAIL 只保留在旧报告 |
| F3 | 阻塞（环境） | 本轮未执行；H1 在前序 B4 后 ANR |
| G1 | 阻塞（环境） | 节点 A 重启后健康且接收 SQL 保留；GW 开始振铃，但 H2 无来电/推送日志，无法证实真实推送交付 |
| G2 | 阻塞（环境/前置未满足） | G1 的 H2 推送交付未确认，未执行 Kyubey 不可达注入 |
| H1 一小时 push 观察 | 阻塞（环境） | H1 重启后 ANR |
| H3 一小时 stream 观察 | 阻塞（环境） | H3 未启动，磁盘空间不足 |

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
| H1 push | 阻塞（环境） | B4 重启后 Madoka ANR，无法进行健康待机观察 |
| H3 stream | 阻塞（环境） | H3 因 userdata 分区所需磁盘空间大于可用空间而未能启动 |

## 缺陷与真机阶段结论

本轮没有确认可归因于产品代码的缺陷，因此没有修改或推送产品代码，也没有以环境故障为由放宽验收标准。B3 的出站控制在约 20.5 秒后过期，但现有证据不足以判定产品回归；B4 的 ANR 也缺少应用侧故障栈，实验机当时仅约 409 MiB 可用内存且 swap 已用约 4 GiB，故按计划归为环境阻塞。

A1–A4、B1–B2 在模拟器上通过，表明基本推送唤醒、通话取消、多设备开关隔离与短信路径可进入真机阶段验证。真实蜂窝 HAL 音频、厂商后台限制、A5 的 FCM 降级行为和长时间耗电仍需真机确认。B3 未完成；B4 重启后 UI、B5/B6、C/D 多节点、E/H3 stream、F 升级顺序以及 G2 故障恢复尚未验证，不应视为可交付通过。
