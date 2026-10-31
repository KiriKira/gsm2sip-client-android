# Reception v2 模拟器验收（2026-10-29）

## 被测提交与环境

2026-10-29 首轮结果对应以下仓库提交；后续代码复测使用的提交见下方 2026-10-30 清单：

| 组件 | PR | 实际测试提交 |
| --- | --- | --- |
| SoulGem | `KiriKira/gsm2sip-server#33` | `152821079f87af7958d30cebf0cf8c0c316ed8e0` |
| Kyubey | `KiriKira/Kyubey#1` | `1bb9a2bc976ace3ccedb3a95315e01713559bddf` |
| Homura | `KiriKira/gsm2sip#26` | `a41dd196aa4caa279cc28c688469c70ca4ba59f4` |
| Madoka | `KiriKira/gsm2sip-client-android#38` | `770fb716517aa7d4aa26b7ee32b297ea0a4c3c01` |

本轮分支均为 `claude/vibrant-babbage-ye0ey3`。2026-10-30 续测所用代码提交：SoulGem `152821079f87af7958d30cebf0cf8c0c316ed8e0`；Kyubey `1bb9a2bc976ace3ccedb3a95315e01713559bddf`；Homura `a41dd196aa4caa279cc28c688469c70ca4ba59f4`；Madoka 最新分支 HEAD `e25d9436c05f79d1e49cfb65360aea039a0846d7`。本轮 Madoka 产品提交为 B3 修复 `d60e3c9cac80389a42137e4da21c46420f27c13f`、C1 修复 `299213b3d9f8e12b16d309f9e87093030e436fa4`；测试 runner 提交 `5b6db6baedbcff637bfdf33d70feb9aab9fd6494`。B3、B6、C1 已完成模拟器端到端复测。

2026-10-30 F1 基线尝试也实际使用了以下代码提交：F1 基线 SoulGem `152821079f87af7958d30cebf0cf8c0c316ed8e0`、Kyubey `1bb9a2bc976ace3ccedb3a95315e01713559bddf`、Homura `31f65a7d5aa7fbca7df575d73a87fcf3b018b254`、Madoka `5b721c3b80763264a73dbe128364529e11801c97`。因此本报告记录的全部产品/测试代码提交为：SoulGem `1528210`；Kyubey `1bb9a2b`；Homura `a41dd19`、F1 基线 `31f65a7`；Madoka `e25d943`、B3 `d60e3c9`、C1 `299213b`、测试 runner `5b6db6b`、F1 基线 `5b721c3`。本次文档更新不含产品代码改动。

不同测试窗口使用的安装包不同，以下不是当前安装状态的概括。B3–B6/C1 复测窗口的 H1/H2 使用同签名 debug APK（`GSM_RELEASE_VERSION_CODE=1000000000`，SHA-256 `05d6cb74d8bd5e2f30edf6927679182218fbc17e8dcf8cf40fcb584ce0d91530`，产品提交 `d60e3c9`、`299213b`；`5b6db6b` 只增加测试 instrumentation）。F1 窗口改用 exact-main Homura/Madoka APK，具体提交和哈希见 `f1-preflight-20261030.md`、`f1-relay-diagnostic-20261030.md`。Gateway runner 通过单独测试包运行。10-30 续测窗口的 A/B/C 使用同一 PR #33 镜像 tag `soulgem-aio:reception-r1-1528210`，本机镜像 config ID 为 `sha256:1f6fd76b942af40b751ede535ade03604de858ca4a40054406d4afb56fa5c1d4`。H3 在最近一次空间检查时未运行：根盘可用约 6.0–6.3 GiB，启动 H3 约需 7.37 GiB；按磁盘约束没有继续清理。F1 runner 退出后 Homura 后台偏好虽已通过 UI 恢复，运行态没有恢复，详见 `f1-call-rerun-20261030.md`。

首轮（10-29）环境记录保留其当时状态：2 vCPU、约 8 GiB RAM、64 GiB 磁盘；GW API 34 普通镜像，H1/H2 API 35 Google Play 镜像，H3 API 34 普通镜像；H1/H2 未登录 Google 账号，未执行 Magisk/root 测试。Kyubey 使用本地 Wrangler 和临时 HTTPS 隧道，没有 Cloudflare `wrangler tail`。H3 清理前后磁盘证据仍见 `disk-space-cleanup-20261029.txt`。10-30 续测窗口中 A/B/C 使用上述 PR #33 镜像，Kyubey 由本地 Worker 和 HTTPS 隧道提供真实 FCM 路径；H3 因该次空间检查的可用空间不足而关闭。

证据目录为 `docs/reception-v2-emulator-evidence-20261029/`。脱敏执行记录在 `run-observations-20261029.md`；新增 B3–B6、C1、G1 证据在 `b3-call-readiness-20261029.txt`、`b4-reboot-status.txt`、`b4-reboot-retest-20261029.txt`、`b5-offline-sync-20261029.txt`、`b6-sms-sync-20261029.txt`、`b6-call-sms-retest-20261030.txt`、`c1-reject-retest-20261030.txt` 和 `g1-restart-20261029.txt`。H3 清理与补测数据见 `disk-space-cleanup-20261029.txt`、`h3-e1-diagnostics.json`、`h3-e4-segment.json`、`h3-e4-diagnostics.json` 和 `h3-recovery-20261029.md`。认证头、设备/会话标识、推送 endpoint、手机号和 Firebase 凭据没有放进仓库；本地 Kyubey redacted 日志留在 `/tmp`，只提交计数和结果摘要。

## 首轮及历史结果

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
| F1 | 阻塞（前置失败） | 2026-10-30 首次 exact-main 基线尝试的真实推送登记预检反复 `UnknownHostException`，Node C 没有 H1 endpoint，因此未执行任何 F1 业务断言。后续修正与复测结果见本节 2026-10-30 补充。 |
| F2 | 未运行（依赖 F1） | F1 的业务前置未通过，按顺序停止；没有升级 Homura 或宣称测试通过 |
| F3 | 未运行（依赖 F1/F2） | 没有进行该步骤升级；已恢复原 APK 与应用备份，但备份内会话凭据已不被 A/B/C 当前会话表或恢复窗口接受，详见 F1 追加记录 |
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

## 2026-10-30 Sol 复核与续测补充

本节追加在首轮报告之后，首轮表格与 2026-10-28 的 F1/F2 FAIL 历史原样保留。GPT-6.1 Sol 阅读了本报告、测试计划、协议、2026-10-28 升级报告及归档证据，并根据后续现场状态修正了初始判断。复核与更正分别存于 `sol-report-review-20261030.md`、`sol-review-actions-20261030.md` 和 `sol-final-followup-20261030.md`。B3/C1 两个已授权产品缺陷分别由 `d60e3c9`、`299213b` 修复并完成代码回归；SIM 恢复通过生产 API 完成，没有手工改数据库或用模拟推送代替 FCM。

| 项目 | Sol 对可执行性/限制的判断 | 本轮最终处置与证据 |
| --- | --- | --- |
| B3 去电 | 初始拨号页提示本身不足以判环境阻塞；根因是 Madoka 出站控制在 incoming-disabled 时等待永不产生的 `READY`。 | **PASS（修复后端到端）**：`d60e3c9` 绕过该等待；在 H1 来电关闭、H2 来电开启时，从 H1 实际拨号页呼叫，SoulGem RTC 记录 `dispatched=true`，Homura Telecom 与 H1 Telecom 均进入 `ACTIVE`；H1 主动挂断后服务端为 `ended/cellular_ended`、Homura modem 空闲，H1 的 `incoming=false` 与 revision 35 保持不变。代码回归及本次设备证据见 `b3-fix-regression-20261030.md`、`b3-outgoing-retest-20261030.txt`。使用合成 PCM，未验证真实蜂窝 HAL 音频。 |
| B5 剩余来电断言 | 通过真实 modem→SoulGem→真实 FCM→H2 Telecom 的一次路径可执行。 | **PASS（H1/H2 断言）**：H1 `incoming=false`，H2 唯一在线手机进入 Telecom `RINGING`；H3 仍是离线但有效的服务器注册，作为拓扑偏差记录，不算第二台响铃手机。见 `b5-call-only-h2-20261030.txt`。 |
| B6 剩余断言 | SMS 开关/游标与来电路径均可由真实模拟器 SMS、GSM、FCM 和 Telecom 证据验证。 | **PASS**：H1 关闭短信同步时，真实模拟器 SMS 入库后 H1 的推送事件游标保持 8，H1 未收到 `kind=sms`；同一设置下真实 GSM 来电使 Homura Telecom 与 H1 Telecom 进入 `RINGING`，真实 FCM `kind=call` 到达，Madoka 来电界面启动并显示。返回 Madoka 主界面后后台同步恢复，H1 收到一次 SMS wake，服务端游标 8→9，本地消息表该 marker 恰好 1 行，通知为一个消息项及其系统分组摘要。真实调用时间线与状态见 `b6-call-sms-retest-20261030.txt`。本地 Wrangler Worker 没有 Cloudflare `wrangler tail`，因此以 H1 `HostPush`、SoulGem 游标和本地消息库作为该子项的可回溯证据；未用模拟推送。 |
| C1 | 来电已到 Telecom，但此前未响应 self-managed Telecom 的 UI 请求。 | **PASS（修复后端到端）**：`299213b` 显示既有接听/拒接 UI；H2 飞行模式期间，H1 Telecom 进入 `RINGING`，可见“拒接/接听”，点击拒接后先显示“已拒接，正在等待网关让其他主机停止响铃…”，再显示“已拒接，所有主机已停止响铃。”Homura 在点击后 0.905 秒断开；SoulGem 为 `incoming/ended/declined`，3 个参与端均 `ended/declined`。恢复 H2 网络后 8 秒内无 call push、Telecom `RINGING` 或迟到来电。设备时间线见 `c1-reject-retest-20261030.txt`；代码回归与保留 SIM 确认见 `c1-fix-regression-20261030.md`、`c1-sim-reconfirm-20261030.txt`。 |
| C2/C3 | C2 用四次真实来电验证接听/拒接竞争；C3 关闭 H1 接收后由 H2 继续响铃并尝试接听。 | **C2 PASS；C3 BLOCKED（环境媒体路径）**：C2 的四次有效终局满足竞争断言；C3 中 H1 停止响铃、H2 继续响铃并进入 Telecom `ACTIVE`，但没有形成 WebRTC candidate pair/媒体；实验环境未满足外部 TURN 可达性前置，Homura 未进入 `cellular_active`，因此不宣称完整接通。见 `c2-result-summary-20261030.md`、`c3-followup-20261030.md`。 |
| D1–D3 多节点 | D1 需 A 上无其他有效订阅者；D2/D3 使用生产多节点路径。 | **D1 BLOCKED（环境/磁盘），D2 PASS，D3 FAIL（产品行为）**：D1 时 A 仍有有效客户端订阅，且 H3 启动约需 7.37 GiB、可用约 6.3 GiB，按磁盘约束未继续清理。D2 已验证单次 Telecom 来电及 `answered_elsewhere`；D3 停止 A 后真实 FCM 到达，但 B participant 被 `reception_disabled` 结束，未响铃。分别见 `d-multinode-current-eligibility-20261030.txt`、`d2-multinode-retest-20261030.txt`、`d3-stop-a-retry-result-20261030.txt`。D3 不在当前授权的 B3/C1 修复范围内，未改代码。 |
| F1 | Exact-main 起点、PR #33 服务端及真实 relay/FCM。 | **未完成（SMS/真实 FCM PASS；来电、去电 BLOCKED）**：短信消息和真实 FCM 到达；来电时 Homura SIM registry 为 `UNVERIFIED`，无法解析有效 SIM/Telecom 映射，因此在 offer 前被拒绝，服务端未建 call session。无保存 PhoneAccount key 是诊断事实，不单独视为根因。Sol 判定测试配对状态前置不满足，不是产品 FAIL；未改变 SIM mapping。详见 `f1-call-rerun-20261030.md`、`f1-sms-rerun-20261030.md`。 |
| F2/F3 | 依次升级 Homura、Madoka 并重复 F1。 | **未运行（依赖 F1 未完成）**：归档中已找到 exact-main APK/镜像来源，但没有以当前验证通过的完整 F1 业务基线开始升级；未通过覆盖安装伪造基线。来源审计见 `f-baseline-artifact-audit-20261030.md`。 |
| G1 重启后来电 | 原先“ADB 来电未到 modem/Telecom”的总结过宽，重新逐层采集已证明注入和交付可用。 | **PASS**：A 重启后等待 4 分 33 秒，再用真实模拟器 GSM call；GW modem +2 秒检测，H2 真实 FCM +3 秒，Telecom +4 秒进入 `RINGING`。未接听后约 25 秒结束；不声称 HAL 音频通过。见 `g1-restart-retest-20261030.txt`。 |
| G2 中继暂时不可达 | 先确认恢复阶段没有新 modem edge 的根因，再在服务有效时执行 outage/恢复两通真实来电。 | **PASS（恢复送达）**：节点 A 的可逆 relay 出口拦截期间，真实 GSM 来电进入 Homura 编排，H1/H2 均无新 call push；generation 6 `call_failures` 0→4。撤销规则后下一通真实来电经真实 FCM 到达 H1 Telecom `RINGING`，generation 6 计数恢复为 0。该通在响铃窗口结束后未接听，不作接通/音频断言。详见 `g2-retest-20261030.txt` 和操作前风险记录 `g2-retry-risk-before-20261030.md`。 |
| E2/E3/E4 | E2 需 H3 与有效来电；E3 需计划指定 AVD 上的双向网络切换；E4 为 30 分钟现象记录。 | **E2 BLOCKED（H3/有效语音前置），E3 BLOCKED（API 34 AVD 未建立 Wi-Fi transport），E4 仅记录**：H3 当前因磁盘空间不足关闭；E3 若换 AVD 会形成计划偏差；E4 仅有既有分段观察，不外推为稳定性结论。详见 `disk-space-cleanup-20261029.txt`、`h3-recovery-20261029.md`。 |
| H1 一小时 push | G2 后的历史 generation 4 endpoint 曾健康；计划标准为连续墙钟观察后的 App 诊断、服务、HTTP 和 alarm 证据。加速时钟会破坏计时语义。 | **未开始，等待用户决定是否真等**：计划本身不要求 `failures=8` 或 event cursor 固定。旧 `failures=8,event_cursor=4` 来自失效 endpoint，不能作本轮证据；用户提出的 8/游标断言另列为未验证补充项，不能 SQL 造数或用模拟推送。G2 后曾有 generation 4 的 `failures=0, call_failures=3, event_cursor=8`，这不是当前 H1 的状态。F1 后当前 H1 使用 exact-main Madoka，Homura runtime 未恢复；开始长观察前需重新建立真实 FCM 与 gateway 运行前置并保持无业务。
| H3 一小时 stream | 方法/时间限制：之前 43 分 21 秒不可外推；QEMU `-icount` 或 guest clock 倍速会让 Android timer 与真实网络、服务器和 FCM 脱节。 | **未重跑，等待用户决定是否真等**：必须 fresh、连续 60 分钟墙钟；diagnostics/deviceidle/alarm/logcat/ping-pong 只能佐证实际墙钟时长。H3 需重新启动并确认 stream live、屏幕关闭、deep idle。最近一次磁盘检查可用约 6.0–6.3 GiB，而启动 H3 约需 7.37 GiB；先按既定磁盘约束解决空间前置，不能靠继续删文件。空间满足后，H1 与 H3 可在短用例结束后共享一段 60 分钟窗口，另需约 10–15 分钟准备和前后导出。 |

G2 有效复测之前的中间状态：当时未出现新 modem edge 的原因已查明，旧 GSM 来电仍在 modem `INCOMING` 与 Telecom `RINGING`，而非 relay 恢复失败；旧来电已明确取消并验证空闲。其后 B6 清理后注入产生了新 modem edge，但当时 Homura 的 call-control runtime、SIM 绑定和后台启动均未就绪，不能把旧的 G2 session 当作有效证据。随后仅通过普通 UI 确认 AVD SIM 并启用 control-only 后台服务；远程语音仍关闭。由于计划明确不测 root/Magisk，当时没有再注入来电或开启语音诊断。`DOCKER-USER` 没有本轮规则。Sol 没有判定 G2、D1、F、C2/C3、E2/E3 等项目本质上做不到；后续有效 G2 复测见下文，当前 B3/C1 已修复并通过，长观察保持暂停。

上述为 G2 有效复测之前的中间状态；下文 `G2 中继隔离与恢复来电复测` 记录了随后使用真实 modem/FCM 完成的有效 outage/恢复测试，结果为 PASS。

### B3 修复后模拟器端到端复测（2026-10-30）

H1 API 35 Google Play 上安装当前同签名 Madoka debug APK；Homura API 34 普通镜像上运行真实 `GatewayJointInstrumentation`，仅将物理蜂窝 PCM 替换为合成 PCM。未使用 H1 Host runner，避免它的启动流程将 H1 来电开关主动打开。H1 的 v2 设置为 `incoming=false, sms=true, revision=35`，H2 为 `incoming=true, sms=true, revision=10`；点击 H1 设置页关闭来电接收后，SoulGem SQL 快照亦显示 H1 对应行 `incoming_enabled=false, sms_enabled=true, revision=35`。拨号按钮在该状态可用。

H1 用户界面发起去电：07:10:42.218Z；Homura Telecom 在 07:10:56.192Z 进入 `DIALING`、07:10:57.689Z 进入 `ACTIVE`；Madoka Telecom 于 07:10:58.975Z 进入 `ACTIVE`。H1 用户于 07:11:51.765Z 挂断；Madoka 于 07:11:52.492Z 断开，Homura 于 07:11:53.103Z 断开。SoulGem 最新 RTC 去电记录为 `direction=outgoing, state=ended, reason=cellular_ended, control_transport=rtc, dispatched=true`。挂断后 Homura `emu gsm list` 为空、`mCallState=0`、runner bridge 为 `IDLE`；H1 的 `HostCallService` 已停止，`incoming=false, sms=true, revision=35` 仍保持。全过程没有 root/Magisk 或真实蜂窝 HAL 音频验证。脱敏证据：`b3-outgoing-retest-20261030.txt`。

### B6 修复后来电与短信同步复测（2026-10-30）

H1 来电保持开启、后台短信同步关闭（SoulGem v2 reception `incoming=true, sms=false, revision=37`；endpoint generation 6、失败计数 0、推送事件游标 8）。07:22Z 左右通过 `adb emu sms send` 向 Homura AVD 注入一条真实模拟器 SMS；SoulGem 存储一条 `inbound/received`，短信关闭期间 H1 游标仍为 8，H1 的 `HostPush` 没有 SMS wake。07:23:26.748Z 再发起真实 GSM 模拟来电，Homura `GsmCallService`、`GsmCallManager` 和 `CallOrchestrator` 均记录新来电；H1 于 07:23:29.294Z 收到真实 FCM `wire=2 kind=call`，Android Telecom 进入 `RINGING`，Madoka 的 `CallActionActivity` 于 07:23:31.312Z 启动、07:23:32.171Z 显示。没有接听，Homura 在剩余响铃窗口结束时记录 `Leaving the GSM call unanswered`。这证明短信同步关闭没有关闭来电接收。

随后通过来电结束页返回 Madoka 主界面；主界面恢复后台同步。H1 于 07:26:28.545Z 收到一条 `kind=sms` wake，SoulGem reception 恢复为 `incoming=true, sms=true, revision=38`，endpoint 推送事件游标从 8 增至 9；仅有一个对应 marker 的服务端 inbound message 和一个 H1 本地 Room message row。系统通知是一个消息项及其通知分组摘要，没有重复 SMS 记录。返回主界面触发恢复，不是我显式点击短信开关；报告按实记录该路径。未捕获 Cloudflare `wrangler tail`，但本地 Worker/真实 FCM 路径实际送达，H1 `HostPush` 日志、服务端游标和本地消息行相互印证。原始日志、通知截图和设备标识未提交。详见 `b6-call-sms-retest-20261030.txt`。

### C1 修复后拒接与多节点确认复测（2026-10-30）

H1/H2 的 incoming reception 均保持开启；测试期间 H2 用 Android 飞行模式断网（Wi-Fi 关闭、默认网络为 none）。第一次操作漏过按钮时，来电在响铃窗口结束后按正常未接处理，不计为产品失败或 C1 完成。随后两次均通过真实 Homura GSM 来电完成 H1 的可见拒接；最后一次留存了计划要求的完整界面顺序。

最后一次来电中，H1 Telecom 于 07:48:50.771Z 记录 `SET_RINGING`；Madoka 来电页显示“来电”“拒接”“接听”。07:49:04.905Z 点击“拒接”；07:49:09.051Z 页面仍显示“已拒接，正在等待网关让其他主机停止响铃…”，之后显示“通话已结束”及“已拒接，所有主机已停止响铃。”Homura 于 07:49:05.406Z 记录 `declined_for_every_host`，07:49:05.810Z 进入 `DISCONNECTED`，即点击后 0.905 秒；`emu gsm list` 为空。SoulGem 最新来电为 `ended/declined`，3 个 call participants 均为 `ended/declined`。H2 恢复网络后观察 8 秒：Wi-Fi/default network 恢复，`HostPush kind=call` 计数未增加，Telecom 没有 `RINGING`，前台仍是 MainActivity。详见 `c1-reject-retest-20261030.txt`。没有执行真实蜂窝 HAL 音频断言。

### 2026-10-30 follow-up — D1 runner installed; short retests pending

H1's API 35 Google Play AVD now has the test-only `MeshSetupInstrumentation` and `HostJointInstrumentation` registered. The installation used the existing R1 debug certificate (`2c613d…845f28`) and left the product app/data untouched; evidence and the exact test APK hash are in `d1-runner-install-20261030.txt`. This clears the missing-runner tool issue only. D1 still has not run: its required topology is not established, and no pairing code or call has been consumed. The previous ANR path remains unused.

The 04:37Z B6 follow-up was not a valid fresh-call attempt: Sol's read-only radio timeline showed incoming/ringing markers throughout the preceding minute, while Telecom call handles in that window were first seen at 04:25Z. Cleanup afterward left modem and live Telecom idle. The runner is now installed on H1, but the product APK remains the older `0.4.2` install and Homura call-control readiness must be freshly established before a single B6 call retry. No retry is counted yet. D1 and B6 remain pending with their environment/method prerequisites identified, rather than marked impossible.

### B6 retry trigger review (2026-10-30 05:53Z)

The later one-shot `adb emu gsm call` command did not preserve its command result because the remote shell consumed the remaining script input. It therefore is not a verified modem-call attempt and is not a product FAIL. The emulator was subsequently idle (`gsm list=OK`, `mCallState=0`), with no new gateway callback or downstream call event in the captured window. Sol advised using Extended Controls → Phone → Call Device as a distinct supported trigger; the existing GW AVD is headless (`-no-window`) and the remote session has no display server, so that path cannot be used without restarting/reconfiguring the emulator. The same GSM console command was not repeated. B6's call assertion remains BLOCKED (method/tool), with the sanitized evidence in `b6-call-retry-trigger-20261030.txt`.

The D1 H1 instrumentation runner installation is complete, but D1 itself has not run and no join code or topology change was made. The gateway-side joint runner was stopped cleanly after the idle check; its result was `PASS` at stage `commands`, which records only orderly runner shutdown, not a call-test pass.

### D1–D3 and E2–E4 follow-up (2026-10-30)

Sol rechecked the runner and topology. A read-only SQL snapshot found three A-side active client refresh sessions, all with v2 incoming enabled (two currently have push endpoints); B/C each have zero client sessions. The prior inventory maps the three A clients to H1/H2/H3, so stopping H3 did not remove its still-valid incoming subscription. The current server call-selection query treats a v2 client with `incoming_enabled=true` and a valid refresh session as eligible even without a push endpoint. Exact D1 therefore cannot run: A is not subscriber-free, and H3 needs roughly 7.37 GiB to start while the remote root currently has 6.3 GiB available. The earlier disk instruction forbids further cleanup and requires a user disk decision if H3 still cannot start. No join code, pairing, SQL row, or server node was changed. Evidence: `d-multinode-current-eligibility-20261030.txt`.

Sol judged that adding B to H1 with `MeshSetupInstrumentation` is feasible as D2 setup only, but it consumes a B one-time pairing code, adds a production pairing, catches up message history, and registers push. With no verified fresh-call trigger available, this preparation would change state without completing D2, so it was deferred. D2's Telecom merge/`answered_elsewhere` assertion and D3's A-stopped delivery through B both remain unverified and require a new real call; stopping A without that call would not test either assertion. B6 trigger limitations are recorded in `b6-call-retry-trigger-20261030.txt`.

E2/E3 were not run because their H3 prerequisite is unavailable under the disk constraint. E4 and the H1/H3 long observations remain paused by the user's instruction; no waiting interval was started.

### F1–F3 artifact re-audit (2026-10-30)

The earlier “missing baseline artifacts” assessment is corrected. The required exact `main` source commits, APKs, and source archives were found; both main APKs verify against the shared debug certificate. Historical 0.4/0.4.1 APKs and image archives were also located, but they are not substitutes for the plan's `main` start or current R1 server. The archived main Madoka APK embeds an expired temporary relay hostname, so the F1 attempt rebuilt the exact source with the active relay and same signer. F1 then cleared/rebased only the designated GW/H1 test apps, created an isolated owner on C, and paired the two test devices. The real Madoka push-registration preflight failed before any F1 business assertion; the current attempt and restoration are documented below. Artifact provenance and hashes remain in `f-baseline-artifact-audit-20261030.md`.

### C1 复测前 SIM 映射恢复风险与前置检查（2026-10-30）

本次 C1 端到端重测尝试无效：2026-10-30T03:59:11Z 的真实模拟器 GSM 来电进入 Homura modem，`GsmCallManager` 检测到来电，但随后因本地 SIM 映射为 `UNVERIFIED` 而在 Telecom offer 前结束；SoulGem 没有新 session，Madoka 没有收到 call push。它不构成 C1 修复的通过或失败，旧报告中的 C1 FAIL 仍保留。脱敏逐层证据见 `c1-retest-sim-precondition-20261030.txt`。

为验证修复，下一步只使用已有 `GatewayJointInstrumentation` 的 `reconfirm_retained_emulator_sim` 路径：保留当前 gateway pairing 和原 SIM UUID，调用生产 SIM propose/confirm API，并要求 `same_sim_verified=true`；不直接写 SQL，不清应用数据、不卸载、不重启模拟器、不更换 SIM、不重新配对。操作会推进节点 A 的 SIM mapping revision，并改变该 AVD 的本地 SIM 身份状态；若服务端向 B/C 同步，revision 也会随之更新。它不会删除 pairing、设备、会话或短信数据。操作成功后不能无损恢复到之前的 `UNVERIFIED` 状态，只能通过同一保留 SIM 的生产确认流程再次校正；因此先保存配套本地快照、核对当前无 modem/Telecom/Host 活动呼叫，并验证 SIM UUID、slot 和 subscription 一致。任一不一致、确认后仍 `UNVERIFIED`、revision 不一致或 PhoneAccount 正反向关联不唯一时立即停止，不再注入来电。此操作不启用 root/Magisk/CAPTURE_AUDIO_OUTPUT，也不改变语音开关。

### C1 SIM 前置恢复结果（2026-10-30）

通过上述 guarded retained-SIM 路径完成生产确认。相同 SIM UUID 保留；revision 由本地/服务器 12 一致推进到 13，本地身份为 `FINGERPRINT_VERIFIED`，服务器行是 `active/identity_verified=true`，slot 匹配。Telecom 预检中只有一个 call-capable SIM PhoneAccount；其反查 subscription ID 匹配该 SIM，`createForSubscriptionId(subId).phoneAccountHandle` 也回指同一账户。当前 mapping 没有保存单独的 PhoneAccount handle pin；`SimRegistry` 允许该字段为空并在运行时按已验证 SIM 的 subscription 解析，因此真实来电 offer 仍需实测。操作没有清数据、卸载、重启 AVD、重新配对、手改 SQL 或注入电话；结束时 modem 空闲、SoulGem 活动 session 数为 0。

Instrumentation 返回 `READY` 并完成受控确认；首次启动时旧 `stop` mailbox 命令仍在，因此 runner 随后按该命令正常结束。它没有执行来电，也未影响数据。发起 C1 前会先写入新的 `status` mailbox，再启动 runner。脱敏证据和本地私有快照哈希见 `c1-sim-reconfirm-20261030.txt`。

### C1 修复后即时来电复测（2026-10-30 04:37Z）

本次 `adb emu gsm call` 返回 `OK`，但 8.222 秒后 GW modem 已空闲；该窗口内没有新的 Homura `GsmCallService.onCallAdded`/incoming callback，也没有 RTC offer、Madoka 新 call push 或 `CallActionActivity`。按停止规则在 8 秒处取消，没有点击拒接。H2 当时处于飞行模式；恢复网络后观察 8 秒无迟到响铃。故这次不能判定 C1 接听/拒接 UI 修复通过或失败，C1 的端到端拒接断言仍未完成，停止在 GW 注入/回调边界。脱敏记录与原始日志哈希见 `c1-fast-real-ui-20261030.txt`；原始日志和截图未上传。

### Homura retained-SIM 与后台状态恢复（2026-10-30）

Sol 复核了 C1 复测后的状态差异：普通 `MainActivity` 调用 `SimRegistry.snapshot()` 时，Android app UID 读不到 ICCID fingerprint，会按保护逻辑把 `FINGERPRINT_VERIFIED` 本地映射改为 `UNVERIFIED`；runner 的 `finally` 调用 `GatewayService.stop()`，会把后台开关持久化为关闭并保留 `voice=false`。本次恢复前只读核对了本地与 Node A 的活动 SIM UUID，一致；两边 revision 均为 13、slot 均为 0，本地 subscription 为 1；Node A 行仍是 `active/identity_verified=true`，modem 空闲且 Telecom 无响铃。

为保留原 UUID 且不产生新服务器 revision，使用一次性 test-only instrumentation（目标 app UID，无 shell permission adoption）调用现有生产 `SimRegistry.confirmMapping()`。结果 `PASS`：本地状态成为 `LOCAL_CONFIRMED`，SIM 映射与 revision 13 保持不变；没有调用 server API、手改偏好或写 SQL。临时 helper 源文件在使用后已从远端构建目录删除，原 `GatewayJointInstrumentation` 测试 APK 已恢复。随后通过普通 UI 的“启用后台运行”恢复 control/sync：`autoconnect=true`、`background_opted_in=true`、`background_user_stopped=false`、`background_task_manager_stopped=false`、服务运行、`background_voice_enabled=false`。没有执行语音诊断、root/Magisk 或系统权限改动。脱敏证据：`gw-local-sim-recovery-20261030.txt`。这只恢复测试前置状态，不构成 B6、G1、G2 或 C1 来电断言通过；后续短用例待继续。

### G2 中继隔离与恢复来电复测（2026-10-30）

按操作前风险记录只临时阻断 Node A 到 relay 的 TCP/443，使用真实 GSM modem 来电。Homura 于 08:08:18.243Z 检测并编排来电；拦截期间 H1/H2 没有收到 call push，generation 6 的 `call_failures` 在单个响铃窗口内由 0 增至 4。删除规则后检查标签规则数为 0，再发一通真实来电；H1 于 08:09:50.149Z 收到真实 FCM，Telecom 于 08:09:49.022Z 进入 `RINGING`，服务端该 endpoint 的 `call_failures` 回到 0。来电在正常响铃窗口结束时未接听，因此只判定计划要求的恢复送达和计数重置，不宣称通话接通或音频通过。最终 modem idle、无活动 GSM call、无临时规则，A/B/C 全部健康。脱敏证据见 `g2-retest-20261030.txt`；没有模拟推送、API 造呼叫、root/Magisk 或产品代码修改。

### C2 多主机接听/拒接竞争复测（2026-10-30）

**结果：通过。** 按计划各完成两次顺序：H1 接听后 H2 拒接，以及 H2 拒接后 H1 接听。每次都先确认 H1/H2 runner 返回同一个新 call ID、均为 `INCOMING_RINGING`，并在各自本轮 logcat 中确认 `HostCall: Incoming call ringing`；服务端最新会话 ID 与两端一致。所有呼叫都由 Homura `adb emu gsm call` 产生，推送走真实 FCM，未用 API 造呼叫或模拟推送。

前两次顺序中，两次最终均由 H1 成为唯一赢家：H1 `ACTIVE`、H2 `ENDED/answered_elsewhere`，Homura modem 保持 `mCallState=2`；随后由 H1 测试挂断，两端及服务器会话都结束。反向顺序中，两次都以整通 `ended/declined` 收尾，H1/H2 均为 `ENDED`，Homura `mCallState=0`。没有出现“主机仍在通话而网关已挂断”。服务器控制事件显示两次接听优先的 `accept→decline_all` 间隔为 0.845 秒和 0.905 秒；两次拒接优先的 `decline_all→accept` 间隔为 0.272 秒和 0.479 秒。脚本每次目标间隔约 0.5 秒，考虑了 runner 200 ms 轮询和 H2 ADB 触发耗时；反向第二次将目标调至 0.75 秒，使服务端实际控制间隔回到约 0.5 秒。该数据验证的是竞态顺序和稳定终局，不把异步控制提交时间误称为精确 UI 点击时间。

早期测时方法较宽的额外来电保留为未计分记录，没有替代四次有效复测。首次使用 Telecom 历史计数的误判也没有执行任何动作；已在前置风险记录中更正。逐次结果、脱敏服务端事件和 harness 见证据目录中的 `c2-result-summary-20261030.md`、`c2-trial-2b.txt`、`c2-trial-2c.txt`、`c2-trial-3a.txt`、`c2-trial-3b.txt`、`c2-server-control-events-20261030.txt`、`c2-four-trials-20261030.py`。本项没有产品代码改动或产品 commit。

### C3 接收切换与媒体路径复核（2026-10-30）

**结果：未通过；媒体路径受测试环境 TURN 可达性限制。** H1 关闭接收后停止振铃；H2 继续振铃，runner 应答后 Android Telecom 进入 `ACTIVE`（09:16:52.982Z）。但 H2 的两次 WebRTC 统计均为 `selected_pair=null/null`、收发包为 0、候选仅为 host；Homura 有 `answer_committed` 与 `cellular_answer_requested`，没有进入 `cellular_active`。来电在正常振铃窗口结束时结束，因此不把 Telecom 接听动作当作已接通，也不判 C3 完整通过。

对 TURN 的只读检查发现，Node A 使用外部 TURN 配置；测试机 3478 和 UDP relay 端口确有监听/转发，但对应的是仍在运行的旧容器，不能证明它使用 Node A 的共享密钥。从开发机到测试机的 TCP/22 成功，而 TCP/3478 超时；有效 UDP STUN Binding 请求也没有响应。测试机本机输入策略为 accept 且有本地转发规则，故问题位于外部入口路径或其后端目标。当前没有 AWS CLI/凭据读取云安全组，未改任何防火墙、容器、节点配置或产品代码。完成媒体复测需要可从 H2 网络到达、并与 Node A 共享 TURN 密钥的 listener，以及 UDP 49160–49259 relay 可达；现有旧容器占用相同端口，不能未经评估直接覆盖。证据摘要见 `c3-followup-20261030.md`。

### D2 多节点复测（2026-10-30）

**最终结果：PASS。** 本节覆盖旧矩阵中 D2 的 BLOCKED／未运行状态；旧矩阵、首轮 FAIL 及其他历史原文保留，D1、D3 不因本次 D2 通过而改变结论。H1 保留 A 配对并增加 B，节点数为 2。复测前 A、B 的健康 push endpoint 分别为 2/2、1/1；v2 reception 的 incoming 与 sms 均开启，A 为 3/3，B 为 1/1。

10:58:22.790Z 的首次真实 GSM 注入未计分：runner 先报告 `INCOMING_RINGING`，脚本随即检查 Telecom 得到 0 个 connection 并取消来电；后续 Telecom history 中可见 10:58:27.643Z 的 `SET_RINGING`。Sol 核对源码与时序后确认为状态观察竞态：本地 phase 先更新，Telecom connection 随后异步创建。该次是测试方法误差，不作产品 FAIL。离线验证日志 parser 后才进行下一次复测。

11:02:03.533Z 通过真实 `adb emu gsm call` 再次注入来电，同一个 fresh call 在 A、B 均产生 session。H1 实时日志中，首条 `HostPush: received wire=2 kind=call` 为 11:02:05.205Z，本次窗口共 5 条；首条 `HostCall: Incoming call ringing` 为 11:02:05.903Z，共 1 条。Telecom 仅有 1 个属于 `HostCallConnectionService` 的 `RINGING` connection。推送经过真实 FCM；日志条数仅按实记录，不将 5 条消息解读为 5 通来电或逐条归因到节点。A 的 2/2 个端点、B 的 1/1 个端点的 call cursor 均前进。

通过 `HostJointInstrumentation` 的生产 `answer` action 接听后，Madoka 为 `ACTIVE`、GW 为 `mCallState=2`，A session 为 `active/winner=true`；B 的同一 call 为 `ended/answered_elsewhere`，跨节点共有 3 个 participant 记录为 `answered_elsewhere`。这满足 D2 的单次 Telecom 来电合并与接听后其他分支结束断言。

脚本随后因额外、未列入计划的 `active_participant_count` 检查误报，使用 GSM caller cancel 清理该测试通话；该检查不影响已满足的 D2 计划断言。清理后 A 为 `ended/cellular_ended`，B 保持 `ended/answered_elsewhere`，Telecom 与 GSM 均空闲。本次不声称真实蜂窝 HAL 音频通过。脱敏证据见 [d2-multinode-retest-20261030.txt](reception-v2-emulator-evidence-20261029/d2-multinode-retest-20261030.txt)；不包含号码、call ID、Worker URL、凭据或原始日志／截图。

### B6 清理旧 Telecom 状态后的来电确认（2026-10-30 11:39Z）

先取消 D3 前一轮遗留的精确测试呼叫，确认 GW、H1、H2 的 modem 均 idle、H1/H2 无活动 Telecom 呼叫，并将 H2 旧的已结束来电页退出。H1 设置页确认“后台来电接收”开启、“后台短信同步”关闭。随后只注入一通真实 GSM 来电：GW 于 11:39:39.868Z 检测到 modem 来电；H1 于 11:39:40.959Z/11:39:41.122Z 收到真实 FCM `wire=2 kind=call`，11:39:42.104Z 记录 `Incoming call ringing`，11:39:45Z 前台为 `CallActionActivity`。确认响铃后取消该测试来电，modem 恢复 idle、活动 Telecom ringing 归零。此项确认 B6 的“短信同步关闭时电话照常响铃”断言；不声称接听、媒体或蜂窝 HAL 音频通过。返回应用后短信同步恢复开启，H1 原设置恢复。脱敏证据见 `b6-call-clean-telecom-retest-20261030.txt`；使用真实 FCM，未用模拟推送。

### G2 恢复阶段未产生新 modem event 的根因（2026-10-30）

Sol 对私有 RILJ/Gateway 日志做只读复核后确认：第一次恢复注入前 `adb emu gsm list=OK` 造成了错误的 idle 判断；日志直到 22:33:36.424Z 仍显示同一旧测试来电处于 `INCOMING` 且 connection 未变化，因此后续 `gsm call` 没有产生新 edge。03:06 的清理验证了 `cancel` 后 radio/Telecom 才真正 idle，并出现新 incoming 与 `GsmCallManager` callback；但当时 Homura 的 call-control `GatewayService` 已停止，所以那次没有 offer，也不计入通过。随后 08:09 的正式 G2 重测在 modem idle、GatewayService running 前置下通过真实 FCM 恢复送达，相关 `call_failures` 归零。该项按原 G2 结果 **PASS**，根因和各次尝试区分见 `g2-recovery-trigger-root-cause-20261030.txt`、`b6-call-retry-setup-state-20261030.txt` 与 `g2-retest-20261030.txt`。

### D3 stop-A failover retry（2026-10-30 11:30Z）

D3 第二次尝试满足有效前置：此前先停掉并清理了一个因延迟旧设置更新而无效的尝试；重试前 A/B/C、A/B reception 和 endpoints 健康，设置 UI 未触碰，未启动 instrumentation runner，Homura 旧 modem call 已显式 cancel。停止 A、保留 B/C 后只注入一通真实 GSM 来电。H1 于 11:30:30.687Z 收到真实 `wire=2 kind=call` FCM，Android 允许 call-listening foreground service 启动；但没有 `Incoming call ringing` 或来电页。B 的 participant 于 11:30:30.851777Z 以 `reception_disabled` 结束，B 的 reception 行在 11:30:30.853859Z 短暂变成 `incoming=false` revision 11，随后 11:30:31.878468Z 恢复为 true revision 12，已经结束的 participant 没有复活。无操作员在该窗口切换设置。故 D3 **FAIL（产品行为）**：A 不可达时，B 分支未能保持接收并响铃。

代码中 `HostCallService.setForegroundListening()` 的启动异常处理会调用 `CallRuntime.onListeningFailed()`，后者持久化关闭当前来电接收；这与观察到的短暂 false update 相符，但本次原始日志没有异常栈把该呼叫直接绑定到这一 catch，故具体异常来源仍属有代码依据的假设。用户当前授权的产品修复范围只包括 B3/C1，本轮未改这条额外产品路径，也未重复 D3。A 已恢复健康，旧 modem call 已清理，GW/H1/H2 modem idle。脱敏证据和私有日志哈希见 `d3-stop-a-retry-result-20261030.txt`；未使用模拟推送、API 造呼叫、手改 SQL 或 root/Magisk。

### F1 首次 exact-main 基线尝试与回滚（2026-10-30）

**结果：F1 阻塞（前置失败）；F2/F3 未运行。** 按计划用 main 版本启动 F1：Homura 源码 `31f65a7`，Madoka 源码 `5b721c3`（使用有效 relay 地址重新构建），SoulGem 镜像仍为 PR #33 的 `soulgem-aio:reception-r1-1528210`（image config `sha256:1f6fd76b942af40b751ede535ade03604de858ca4a40054406d4afb56fa5c1d4`），Kyubey 为 `1bb9a2b`。Homura 是 API 34 普通镜像；H1 是 API 35 Google Play 镜像。APK 与原 debug keystore signer 一致，release version code 为 `1000000000`。

H1 的真实 push 注册预检多次失败，Madoka 日志报告 `UnknownHostException`；即便 shell 与 app UID 对同一 relay hostname 的解析得到相同地址，应用注册仍失败。诊断导出隐藏了更深层错误，Node C 的 `push_endpoints` 中没有 H1 endpoint。一次进程重启后的受控 UI 重试仍失败。按“正常使用失败即停止”规则，在 F1 任何业务断言前停止：没有触发来电、去电或 SMS，也没有验证 `wire=1`。没有模拟推送或 API 造呼叫。F2/F3 依赖 F1 基线业务正常，故未启动。

停止后恢复了原安装 APK、Homura 与 H1 的加密 full-app 备份、H1 原记录的运行时权限以及默认拨号角色；A/B/C 均健康，GW/H1/H2 modem idle。备份解密检查表明 Homura 备份 manifest 为 `0.4.2-dev`、3 个配对，H1 为 `0.4.2`、2 个配对。**应用数据文件已恢复，但认证状态没有恢复成功**：Madoka 设置页提示“配对会话已过期或被撤销，请重新配对此手机。”；只读地将备份中的两个 H1 refresh-token 指纹与 A/B/C 的当前 session hash 和有效 recovery hash 比较，均无匹配，这项比较只支持 H1 凭据 stale。Homura 自身的 stale 结论来自后续当前凭据的 refresh/access/recovery 比较，未独立记录其 UI 过期提示。未打印或提交 token/fingerprint，也未手改数据库或重新配对。H1 权限恢复为 `POST_NOTIFICATIONS=true`、`RECORD_AUDIO=true`、`READ_CONTACTS=false`，默认拨号应用仍为 `com.callagent.gateway`。

本轮在 Node C 创建的隔离测试 owner/临时 gateway pairing 未通过受支持的 Homura UI 撤销：Homura 没有主配对移除入口，恢复应用备份后本地临时会话不再可用，但服务端临时 owner/session 可能仍存在。没有直接删除 SQL 行。私有加密备份和原始日志留在测试机权限受限目录；一次性 refresh-token 指纹临时文件已删除。脱敏证据和摘要见 `f1-preflight-20261030.md`。这次回滚恢复了安装包及应用数据，但由于会话认证已失效，不能声称原配对可正常工作。

### F1 relay 继续定位与文案更正（2026-10-30）

上述“配对会话已过期或被撤销，请重新配对此手机。”的精确文案来自 **Madoka**，此前误写为 Homura。独立只读检查显示 Homura 的原 A/B/C gateway/session 行仍存在且 refresh 未过期；但备份 token 仅匹配 recovery old hash、不匹配当前 refresh hash，且当前 access 已过期，不能满足服务端完整的 recovery 条件。因此 Homura 旧凭据快照的 stale 判断来自 token/access 校验，不来自那句 Madoka 文案，也不能仅凭 GatewayService 存在认定认证健康。

Sol 随后在 H1 app UID 中只读加载实际 APK 的生产 `KyubeyClient`，使用现存真实 FCM token 定位，未启动 Application/provider、未改 prefs/登录状态、未登记节点 endpoint。原 F1 relay 当前已可由 Java 解析，但真实 POST 返回 HTTP 200 后给出 **http endpoint**，被 Madoka 的 HTTPS 校验正确拒绝。另一已核验为同一 Kyubey PR #1 源码、同 Firebase 项目及 service account 的现有 HTTPS origin 则登记成功并返回 HTTPS endpoint；旧 `UnknownHostException` 的原始触发仍未被这次结果证明。此处确认的是临时 Worker/tunnel origin 配置问题，不应修改 Madoka 放行 HTTP。

因此当时的 BLOCKED 只是推送前置失败记录，不代表 F1 无法继续。当时下一步计划是在保护现有数据后，用已核验 HTTPS origin 重建 exact-main Madoka 并完成真实业务断言；该计划本身不计作真实 FCM 送达或 F1 PASS。后续实际续测结果见下节。详细边界、构建哈希、认证检查和残留见 `f1-relay-diagnostic-20261030.md`。

### F1 HTTPS relay 修复后的 SMS 与通话复测（2026-10-30）

经 HTTPS origin 重建 exact-main Madoka 后，H1 的真实 push endpoint 已登记，SMS/真实 FCM 子项通过，详见 `f1-sms-rerun-20261030.md`。随后在 Homura 使用受审的测试 PCM runner 执行真实 GSM 来电：modem/Telecom 确认来电，Homura 于 `15:17:09.024Z` 因本地 SIM account mapping 不可用、状态为 `UNVERIFIED` 而在 offer 前拒绝；H1 没有 call push 或 Incoming call UI，Node C 在该窗口没有新 call session。Sol 判定这是测试配对状态前置问题，记 BLOCKED（环境），不作为产品 FAIL；按未改变 SIM mapping 的约束停止重试。去电同样依赖该 SIM/Telecom 前置而未尝试。F1 仍不完整，因此 F2/F3 未运行。报告也记录了计划默认节点 A 与本次隔离节点 C 的方法偏差，以及 runner 退出后恢复后台偏好的结果；运行态仍未恢复。脱敏证据见 `f1-call-rerun-20261030.md`。

### Kiri follow-up: paused-item audit and next gates (2026-10-31)

“尚未通过”与“无法继续”分开记录如下；本次只读复核及 H3 磁盘复试没有更改产品代码、数据库、配对或应用数据。

| 项目 | 当前判定与继续条件 |
| --- | --- |
| B6 来电断言 | **PASS**：清理旧 Telecom 状态后，短信同步关闭时真实 FCM 来电响铃，Modem/Telecom 收尾为空闲。 |
| G2 | **PASS**：恢复 relay 后下一通真实来电送达且 `call_failures` 归零；此前“无新 modem event”由旧来电仍处于 INCOMING、而非产品未恢复导致。 |
| D1 | **未完成；可继续**：H1 的 `HostJointInstrumentation` runner 已安装，先前称 runner 不可用已过时；准确拓扑还需让 A/C 无来电订阅，当前 H3 仍在 A 且无法启动，测试机需先扩盘约 8 GiB 才能重设 H3 状态。 |
| D2 | **PASS**：已按 A/B 双节点完成单一 Telecom 来电合并、接听赢家及另一节点 `answered_elsewhere` 断言。 |
| D3 | **FAIL（产品行为）**：有效 stop-A 重试中 B 分支因 `reception_disabled` 结束且没有响铃；原始日志缺少直接证明具体异常来源的栈，本轮授权不含该缺陷修复。 |
| C2 | **PASS**：两种接听/拒接顺序各两次，终局一致且无网关先挂断、主机仍通话。 |
| C3 | **未完成；当前有两个可解除前置**：外网 TURN 不通；两个远端 AVD 可达私网 TURN 地址，但完整媒体呼叫还需一条与现有网关 SIM 映射一致的有效呼叫路径，且目前映射为 `UNVERIFIED`。 |
| E2 | **BLOCKED（环境）**：H3 不能启动；远端根盘 61G 总量、56G 已用、5.4G 可用，AVD 需要 7.37G，差 1.94G，按停止规则需扩远端盘约 8 GiB 后重试。 |
| E3 | **BLOCKED（环境）**：同样需要 H3 在线执行 Wi-Fi/移动数据切换；扩盘前不再尝试启动或清理。 |
| E4 | **仅记录**：已有 30 分钟现象记录；计划不严格判定该项，不将其记作失败；H1/H3 各一小时观察按 Kiri 指示暂缓。 |
| F1 | **部分通过，通话未判定**：SMS/真实 FCM 子项 PASS；来电和去电都未执行成功，因为当前 Homura SIM UUID 在 A/B/C 上没有匹配的已验证映射。相同签名 main APK 已找到；在“不改数据库/不清数据/不重新配对”限制下，需要恢复配套完整 Homura AVD/Keystore 快照，或 Kiri 明确允许对隔离节点走生产 SIM propose/confirm API（保留 UUID，但会写映射并推进 revision）。 |
| F2 | **可继续的部分尚未执行**：旧 Gateway main APK 和同签名 PR 构建都可用；实际去电/来电断言仍受 F1 的 SIM 映射限制，不能把只完成 `install -r` 当成 F2 PASS。 |
| F3 | **尚未执行；当前缺有效升级起点**：现有 H1 的服务端接收状态已是 v2，无法用它证明 main/v1 → PR/v2 迁移；需要原始 main 状态快照或一台按计划建立的 main 版本测试主机，保持同一 H1 数据和配对后覆盖升级。 |

只读检查还确认：历史 2026-10-21 SQL 快照确有与当时 Homura 完全相同的已验证 SIM UUID/slot，但归档 app-data 中的 session blob 依赖缺失的 Android Keystore 私钥，且原 API36 Homura AVD 未找到，故它不能直接恢复为无写入 F1 路径。另一个实用路径是对隔离 Node C 调用受支持的生产 SIM 确认 API；该请求不会清数据或重配对，但会改动服务器映射并单调推进 revision，所以本次没有越过既有“不改数据库”约束。详细可复核的脱敏证据、哈希与具体剩余前置见 `followup-20261031-precondition-audit.txt`。

### F1 判断纠正及逐项继续条件（2026-10-31，Sol 复核）

Sol 明确复核后，F1 的 `UNVERIFIED` **不是产品缺陷证据，也不是整个验收的终点**；它是尚未满足的呼叫前置。F1 的目标仍是 exact-main Homura/H1 完成真实来电、去电和短信，并确认旧 capability/wire1 路径。短信/真实 FCM 已通过；来电、去电没有进入有效业务判定，因此既不能记通过，也不能记产品失败。保留当前 SIM UUID 的生产 propose/confirm 是最小有效恢复路径，它不清数据、不重新配对，但会通过正式 API 写入 Node C 的 SIM 映射并推进 revision。当前 Kiri 明确要求不改数据库，因此本轮没有执行该 API 写入；Sol 没有找到已验证的无写入替代路径。继续 F1 需要二选一：允许只对隔离 Node C 做一次受支持 API 确认（不手写 SQL、不动 A/B、不重配对），或恢复与历史 verified SQL 配套的 Homura AVD/Keystore/session。若两者都不允许/无法提供，F1 的呼叫断言才可记为受限未完成；不能据此推断需要修产品。

截至本次复核，各暂停项逐项判定：

| 项目 | 当前结论、是否能继续及所需前置 |
| --- | --- |
| B6 来电断言 | **PASS**：已清理旧 Telecom 状态后重试，真实 FCM 来电响铃；无需继续。 |
| G2 | **PASS**：旧来电残留导致首次无 modem edge 的根因已复现；真实 relay 恢复后的下一通也已送达。 |
| D1 | **可继续，但当前受硬环境资源限制**：H1 runner 已安装；准确拓扑要求 A/C 无来电订阅、B 仅 H1，旧 owner 下 H3 仍在 A。安全替代需要独立 Homura AVD或可认证的 H3 会话；测试机根盘仅余 5.4 GiB、AVD 预检需 7.37 GiB。按磁盘停止规则，需要把测试机扩盘至少 8 GiB 后再建干净 AVD，不能用旧 Homura 重配 primary 污染 A/B/C。 |
| D2 | **PASS**：A/B 双节点单一 Telecom 来电与 `answered_elsewhere` 断言已有证据。 |
| D3 | **FAIL（产品行为，已有复现）**：A 停止后 B participant 以 `reception_disabled` 结束且没有响铃。原因写入端尚未由原始异常栈证实；此前授权修复范围只含 B3/C1，故本轮没有改 D3 产品代码。 |
| C2 | **PASS**：接听/拒接竞态四次均完成。 |
| C3 | **未完成，可继续的私网 TURN 前置已验证**：临时私网 sidecar 在 VPC `172.31.15.170` 上完成两次 authenticated allocation smoke，验证了共享密钥和 relay 端口范围；它不是 AVD 呼叫/媒体测试，随后已清理。端到端仍需一通真实 RTC 呼叫及 Node A 宣告可达 TURN 地址，当前呼叫受 F1 SIM 映射约束。详见 `c3-private-turn-preflight-20261031.txt` 与风险记录。 |
| D1/D2/D3 runner | **D1 runner 缺失已解决**：runner 已在 H1。D2 已 PASS；D3 为实际 FAIL，不是 runner/环境阻塞。 |
| E2 | **当前硬环境阻塞**：H3 无法启动，缺少约 1.94 GiB 预检空间；测试机扩盘至少 8 GiB 后才能按计划熄屏来电。 |
| E3 | **当前硬环境阻塞**：同一 H3 启动空间限制；另外原 API 34 AVD 没有 Wi-Fi transport，换机需在扩盘后按计划镜像重建，不能以不同用例替代。 |
| E4 | **仅记录**：计划要求 30 分钟现象观察，已有 30 分钟记录；用户暂停的一小时观察不在本轮执行。 |
| F1 | **部分通过、无产品缺陷证据**：短信/FCM PASS；通话需受支持的 retained-SIM API 写入或可恢复的旧 AVD/Keystore/session。严格不写数据库的限制下无已验证替代路径。 |
| F2 | **尚未通过但可继续部分升级验证**：从 exact-main Homura 覆盖升级到 PR 可验证安装/数据保持及短信；要满足计划中“重复 F1”的通话断言，仍需先解除同一 SIM 映射前置。 |
| F3 | **尚未通过，不等于无法继续**：当前 H1 的 server reception 已是 v2，不能证明 v1→v2 迁移。需要保留有效 pairing/session 的 main/v1 server state 快照，或建立独立的 main/v1 Host 基线；不能把已是 v2 的状态当成迁移验证。 |

在制作一致性备份时，Homura 与 H1 进程曾被 force-stop；备份完成后已重新打开两端，复核 GW/H1 modem 均 idle、Homura `GatewayService` 与 Madoka `HostCallService` 均运行。没有更改应用数据、配对、数据库或产品代码。Sol 的完整路径/风险评审保留在会话交接记录；磁盘数字与 H3 启动证据见 `followup-20261031-precondition-audit.txt`。

### D1 用 H2 替代 H1 完成端到端复测（2026-10-31）

计划中的 H1 当前无法与保留的 Homura owner 组成无额外配对的 A/B/C 拓扑，因此按 Sol 复核的最小替代路径，使用同为 API 35、已登录 Google Play 的 H2 作为唯一接收主机；该角色偏差已明确保留。开始前保留原 SIM UUID、配对和数据，仅对测试设备 UID 的本地 `SimRegistry` 做受控 `LOCAL_CONFIRMED` 修正；本地 revision 仍为 1，未调用服务器 API、未改 SQL、未清数据或重新配对。Homura、SoulGem A/B/C 与 H2 的 FCM endpoint 均通过前置检查，隔离 owner 拓扑为 A=0、B=1、C=0，无活动呼叫。

通过真实模拟器 GSM 来电、Homura RTC/Gateway、SoulGem 和真实 FCM/Kyubey，H2 收到正常 Telecom 来电界面并由可见“接听”按钮接听。脚本记录的注入时刻为 23:05:46.144Z，接听点击为 23:05:55.746Z；随后同时观察到服务端 `active`、蜂窝模拟器 `ACTIVE`、Host Telecom `ACTIVE` 和 Homura modem `OFFHOOK`。再由正常 UI 挂断，服务端终态为 `ended/cellular_ended`。未使用模拟推送。音频仍由测试 PCM 后端提供，因此本项不证明蜂窝 HAL 音频。首次尝试因 H2 尚停留在 Android USB 调试授权对话框而在注入前停止，不计为测试失败；授权仅加入测试机的 ADB key，可从开发者选项撤销。

**D1：通过（H2 替代 H1，按已披露偏差判定）**。来电和状态机断言满足；不声明 H1 本机结果或真实蜂窝音频结果。完成后 Node B 原 TURN 配置已恢复，A/B/C 健康、owner 拓扑未变、活动呼叫为 0；临时私网 TURN sidecar、停止的临时 B 容器和仅供 D1 更新/回滚的密钥、inspect、数据库转储已删除。全程未修改产品代码。脱敏计时、状态、日志哈希与清理证据见 `d1-result-summary-20261031.txt`、`d1-sim-confirm-result-20261031.txt`、`d1-cleanup-result-20261031.txt`；端到端执行脚本为 `d1-ui-call-20261031.py`。原始日志与截图只保留在测试机私有证据目录，不进入仓库。

### 2026-10-31 当前未完成项逐项状态（取代更早的暂缓结论）

| 项目 | 当前状态；硬阻塞与下一步 |
| --- | --- |
| B6 | **PASS**；无剩余工作。 |
| G2 | **PASS**；旧来电状态是首次没有新 modem edge 的原因，relay 恢复后真实下一通已送达。 |
| D1 | **PASS（H2 替代 H1）**；计划角色偏差与合成 PCM 限定已记录。 |
| D2 | **PASS**；无剩余工作。 |
| D3 | **FAIL（可复现产品行为）**；停止 A 后 B 分支为 `reception_disabled` 且未响铃。本轮获准的代码修复范围不含该缺陷；保留复现证据，不扩大修改范围。 |
| C2 | **PASS**；无剩余工作。 |
| C3 | **可继续，尚未跑接收切换断言**；TURN 数据通道已通过。下一步只读核对 H1 是否已有与 H2 同节点、同 owner 的有效身份/session；若有则经普通 UI 开启接收并跑 C3。若没有匹配身份且禁止重配对/改库，才构成该约束下的具体硬阻塞。 |
| E2 | **硬阻塞（测试机磁盘）**；远端 `/` 为 61 GiB 总量、57 GiB 已用、4.1 GiB 可用；H3 AVD 启动预检需约 7.37 GiB，短约 3.3 GiB。按 Kiri 的停止规则不再清理；需要把 `18.119.132.34` 根盘扩至少 8 GiB 后重试。 |
| E3 | **硬阻塞（同一磁盘前置）**；需要启动带移动数据 transport 的 H3，当前空间不足；扩盘后继续切换与来电复测。 |
| E4 | **仅记录**；30 分钟现象已记录；一小时长观察依 Kiri 指示暂缓，不阻塞任何其他用例。 |
| F1 | **部分通过，可继续**；短信/真实 FCM 已通过。D1 的 retained-SIM 本地确认证明同类 UUID 可保留，但它只适用于 D1 隔离 owner；F1 原 main pairing 的来电/去电是否能在不写映射的条件下恢复，继续查对应 AVD/配对快照和同签名旧版 app 状态，并由 Sol 评估是否应对隔离节点执行受支持的 SIM 确认 API。 |
| F2 | **可独立继续其升级断言**；旧 Gateway main APK 与同签名 PR APK 均已找到。先验证覆盖安装、数据/设置保留及 `remaining_ring_ms` 日志；重复 F1 的通话断言单独标注其 SIM 前置，不以此暂停安装与非通话断言。 |
| F3 | **可独立继续其迁移断言**；先从归档恢复或建立 main/v1 Host 的有效测试状态，再覆盖安装 PR APK，验证版本 2、接收开关保留与短信去重。当前 H1 已是 v2，不能把现状冒充 v1 升级结果；旧 main APK、签名和主源代码已齐，继续检查可恢复的完整状态快照。 |

### D1 H2 替代拓扑风险登记（2026-10-31）

Sol 复核后，D1 不需要等待 H3 启动：保留 H1，改用已运行的 API 35 Google Play AVD H2 作为唯一测试主机，并为 A/B/C 建立隔离测试 owner，可避开旧 owner 下 H3 仍有效订阅的问题。此替代需要通过支持的管理/配对/SIM API 创建隔离测试记录，不改现有 owner 的 SQL 行，也不直接写 SQL。执行前置条件、备份方案、回滚边界与停止条件已记录在 `d1-h2-owner-risk-before-20261031.md`；本记录只登记方案，**尚未改变配对、SIM 映射或 reception 状态，也不代表 D1 已运行**。

### D1 same-AVD recovery preflight（2026-10-31）

Homura API 34 与 H2 API 35 Google Play 均已通过应用内全量备份导出，并在各自原 AVD 上用恢复流程完成解密、数据库校验和配对数核验；两份清单各含 1 个配对，均在“恢复并重启”前取消，没有覆盖数据。备份 SHA、私有路径和 APK 回滚校验见 `d1-backup-preflight-20261031.md`。Homura 当前安装包已是目标 R1 同签名构建；H2 将覆盖安装同签名、versionCode 1000000000 的 FCM PR APK。此处只完成可恢复性核验，尚未创建 owner、消费配对码或改任何服务器/设备配对状态。

继续做只读 API 预检时发现：A/B/C 在测试机本机 `127.0.0.1:18110`–`:18112` 均返回 HTTP 200，但 H2 直连 `10.0.2.2` 的对应端口返回 connection refused；Homura 仅验证了原 A 路径可达。节点 API 容器端口只绑定宿主机 loopback。为避免对外开放端口，风险记录已增加可逆的逐 AVD `adb reverse` 路由，并规定发码前必须验证每台设备对 `http://127.0.0.1:<port>/v1/capabilities` 返回 HTTP 200；该预检尚未设置任何 reverse 或产生服务器写入。

### C3 双端私网 TURN 数据通道预检（2026-10-30 21:46Z）

Sol 复核认为上一轮 `wait_answer` 结果同时可能代表答案文件缺失或 SDP 应用失败，不能判成产品缺陷。只读核对发现 H1 已生成答案，而 H2 没有收到相同答案文件；上一轮未完成独立 AVD 之间的 SDP 搬运，故该次不计分。修正搬运并校验两端文件字节一致后，H1/H2 均收集到一个私网 relay candidate，TURN/TCP/UDP 握手和双向数据通道通过：H1 收发 `4/4` 条，H2 收发 `5/4` 条。测试只用 instrumentation 数据通道，没有真实来电、FCM 或 C3 的接收切换断言，因此 **C3 仍未完成**；本次证明的是私网 TURN relay media route 可用。脱敏证据见 `c3-turn-data-probe-20261031.txt`。H1/H2 临时探针配置与 SDP、工作站 loopback proxy 和 SSH forward 已清理；私网 TURN sidecar 按 D1 恢复路径保留。

### C3 Node B TURN 切换安全检查续记（2026-10-31）

本轮继续前的远端只读核对显示：Node B 仍是原容器且健康，Node A/C、三个 AVD 均在线；根盘可用 `4,219,984 KiB`、`MemAvailable=1,968,548 KiB`。私网 TURN sidecar 运行约 37 分钟、未重启。首次替换容器创建因 Docker inspect 的 CLI 默认字段表示差异被安全护栏拦截，**原 B 未停止**；仅移除了精确名称的未启动临时容器，无 `-f/-v`，原 `/data` 与数据库未改。脱敏记录见 `c3-node-b-turn-create-mismatch-20261031.txt`。

随后修正并加强恢复状态机：恢复命令与只读预检参数互斥；恢复期间屏蔽终端断连信号；恢复门槛不依赖 sidecar、ADB reverse、Host endpoint 或 H1/H2 在线状态；新 B 有有界启动等待；停止/改名任一步异常后按不可变容器 ID 对账，先尝试恢复原 B，再只在原 B 未健康时考虑私网配置回退。原 B 已健康而 A/C 或资源审计不完整时会保留原 B、明确报告审计未完成，不会为追求绿灯切回测试配置。最终 PASS 前验证原容器 ID/配置/健康、A/B/C 全部零活动呼叫，以及保留的私网容器已停止且 `restart=no`。GPT-6.1 Sol 对当前脚本做了只读复核并接受恢复路径；本地 `py_compile`、CLI 冲突参数检查及 `git diff --check` 通过。风险边界与停止条件见 `c3-node-b-turn-switch-risk-before-20261031.md`。

这一步只是安全护栏修正，不是 C3 用例结果，也尚未切换 Node B。下一步按该风险记录运行远端只读预检，满足全部条件后再换 B 的 TURN URL；C3 仍需真实 GSM→RTC→FCM/Telecom 路径完成 H1 关闭接收、H2 继续响铃并接听的断言。真实蜂窝 HAL 音频不在模拟器结论内。
