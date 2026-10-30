# Reception v2 模拟器验收（2026-10-29）

## 被测提交与环境

测试时四个仓库均在 `claude/vibrant-babbage-ye0ey3` 分支，使用以下 HEAD：

| 组件 | PR | 实际测试提交 |
| --- | --- | --- |
| SoulGem | `KiriKira/gsm2sip-server#33` | `152821079f87af7958d30cebf0cf8c0c316ed8e0` |
| Kyubey | `KiriKira/Kyubey#1` | `1bb9a2bc976ace3ccedb3a95315e01713559bddf` |
| Homura | `KiriKira/gsm2sip#26` | `a41dd196aa4caa279cc28c688469c70ca4ba59f4` |
| Madoka | `KiriKira/gsm2sip-client-android#38` | `770fb716517aa7d4aa26b7ee32b297ea0a4c3c01` |

2026-10-30 product follow-up commits: Madoka `d60e3c9` (B3 outgoing-call dispatch fix) and `299213b` (C1 incoming Telecom UI fix). Details and separate regression evidence are below; simulator end-to-end retests remain tracked separately.

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

## 2026-10-30 Sol 复核与续测补充

本节追加在首轮报告之后，首轮表格与 2026-10-28 的 F1/F2 FAIL 历史原样保留。GPT-6.1 Sol 阅读了本报告、测试计划、协议、2026-10-28 升级报告及归档证据，并根据后续现场状态修正了初始判断。复核与更正分别存于 `sol-report-review-20261030.md`、`sol-review-actions-20261030.md` 和 `sol-final-followup-20261030.md`。B3/C1 两个已授权产品缺陷分别由 `d60e3c9`、`299213b` 修复并完成代码回归；SIM 恢复通过生产 API 完成，没有手工改数据库或用模拟推送代替 FCM。

| 项目 | Sol 对可执行性/限制的判断 | 本轮最终处置与证据 |
| --- | --- | --- |
| B3 去电 | 初始拨号页提示本身不足以判环境阻塞；后续有效尝试定位为 Madoka 出站控制逻辑未能 dispatch。 | **产品缺陷已修复；相关回归 PASS，模拟器端到端复测待补**：提交 `d60e3c9` 绕过 incoming-disabled 时等待 `READY` 的死锁，同时保持接收关闭。主线程相关 5 类 25/25 通过；代理全量 258/258、lint 0 errors、debug assemble 成功。失败证据、根因、改动范围和验证详情见 `b3-fix-regression-20261030.md`。 |
| B5 剩余来电断言 | 通过真实 modem→SoulGem→真实 FCM→H2 Telecom 的一次路径可执行。 | **PASS（H1/H2 断言）**：H1 `incoming=false`，H2 唯一在线手机进入 Telecom `RINGING`；H3 仍是离线但有效的服务器注册，作为拓扑偏差记录，不算第二台响铃手机。见 `b5-call-only-h2-20261030.txt`。 |
| B6 剩余断言 | SMS 开关/游标子项已可由真实模拟器 SMS 与真实 FCM 验证；来电必须有活动的 Homura call-control runtime。 | **SMS 子项部分通过；来电 BLOCKED（GW runtime/setup）**：清除旧 modem/Telecom 状态后，新的 ADB GSM 注入确实进入 GW modem 并触发 `GsmCallManager`，但没有 `GatewayService`/`CallOrchestrator`/`GsmRtc` offer、SoulGem 新 session 或 H1/H2 call push。Sol 核对确认此前引用的 `734201fb` 是上一晚 G2 的历史 session。Homura 当时 `autoconnect=false`、`background_user_stopped=true`、`background_service_running=false`、`background_voice_enabled=false`、SIM 未绑定；该 attempt 不能评价 relay/FCM。后来通过普通 UI 确认 SIM 并启用 control-only 后台服务；语音 runtime 仍关闭。按计划不做 Magisk/root 测试，因此本项不重复注入。见 `b6-sms-independence-20261030.txt`、`b6-call-retry-setup-state-20261030.txt`。 |
| C1 | 来电已到 Telecom，但此前未响应 self-managed Telecom 的 UI 请求。 | **产品缺陷已修复；代码回归 PASS；SIM 前置已恢复，端到端复测待补**：提交 `299213b` 仅在当前有效的 incoming/ringing 调用上启动既有接听/拒接界面，不会自动接听。主线程相关 5 类 27/27 通过；代理全量 260/260、lint 0 errors、debug assemble 成功。保留同一 SIM UUID 的生产确认已将本地与服务器 mapping revision 从 12 一致推进到 13 并 verified；未清数据或重配对。详见 `c1-fix-regression-20261030.md` 和 `c1-sim-reconfirm-20261030.txt`。 |
| C2/C3 | C2 可用设备端 runner 做协议层竞争验证，必须量取两动作约 0.5 秒间隔；C3 必须通过 Madoka Settings 实际关闭接收，runner 的 reject/hangup 不能代替。完整可见 UI 结论依赖 C1。 | **未完成（停止条件）**：Sol 认为协议层路径可执行，但 G2 恢复来电未进入 modem/GW 后按正常使用失败规则停止，本轮未继续触发 C2/C3。它们不是已证明不可做，也不记环境 PASS。 |
| D1–D3 多节点 | 复核确认 B/C 的当前 Homura 优先级 1/2 行健康；`SIM_ID_CONFLICT` 只留在已移除的历史优先级 3/4 行。可用 H1 端 `HostJointInstrumentation mesh_add` 调用生产 `HostNodes.add()`，保留 H1 的 A 会话，但须先有 H1 runner。 | **D1 BLOCKED（工具本轮不可用），D2/D3 未运行**：只读检查发现 H1 仅装有 `R1DiagnosticInstrumentation`，H2 没有 instrumentation；没有 H1 的 `HostJointInstrumentation` 控制入口。此前 Settings/加入链接入口已触发 ANR，按要求未重复。ANR 截图含测试号码，留在本地未上传。未来可先构建/安装受控的 test-only runner 或修复可见加入路径；没有删除节点、改 SQL 或恢复旧快照。见 `d1-control-path-status-20261030.txt` 及 `d-multinode-current-baseline-20261030.md`。 |
| F1–F3 升级顺序 | 属于起始状态和 APK provenance 前置，不是证明产品做不到。将旧 APK 覆盖安装到当前 v2 数据不会生成真实 v1 起点。 | **BLOCKED（缺有效基线）**：归档中没有可验证的 pre-R1 server/device snapshot 和对应同签名旧版 APK。需先取得真实快照，或用旧 main 与已验证签名重新建立计划要求的初态，再按原顺序覆盖升级；不临时降级/改状态。10 月 28 日 FAIL 行不变。 |
| G1 重启后来电 | 原先“ADB 来电未到 modem/Telecom”的总结过宽，重新逐层采集已证明注入和交付可用。 | **PASS**：A 重启后等待 4 分 33 秒，再用真实模拟器 GSM call；GW modem +2 秒检测，H2 真实 FCM +3 秒，Telecom +4 秒进入 `RINGING`。未接听后约 25 秒结束；不声称 HAL 音频通过。见 `g1-restart-retest-20261030.txt`。 |
| G2 中继暂时不可达 | 先确认恢复阶段没有新 modem edge 的根因，再在服务有效时执行 outage/恢复两通真实来电。 | **根因已确认；重跑 BLOCKED（GW runtime/setup）**：前一次恢复注入时，旧来电仍留在 modem `INCOMING`/Telecom `RINGING`，所以新的 `emu gsm call` 没有新边沿。已用匹配号码执行 `emu gsm cancel`，确认 modem idle、Telecom 无活动呼叫。随后 B6 的清理后注入证明新的 modem edge 可以产生，但 Homura 当时 call-control runtime 未运行，因此没有新 offer/session；该注入不是 G2 复测，也未重新启用/拦截 relay。计划禁止 root/Magisk 测试，本轮不再造来电。见 `g2-outage-recovery-20261030.txt`、`g2-stuck-modem-cleanup-risk-before-20261030.md`、`b6-call-retry-setup-state-20261030.txt`。 |
| E2/E3/E4 | E2 可用 runner/真实通话短测；E3 当前 API 34 H3 没有已连接 Wi-Fi transport，换 AVD 可验证网络能力但不再是计划指定镜像；E4 是 30 分钟仅记录项。 | **未在本轮继续**：G2 恢复段触发停止条件后未再发起测试来电或扩展 AVD。E3 当前镜像是环境能力限制；替代 AVD 会形成计划偏差。E4 30 分钟仍需新窗口，仅记录、不判定。 |
| H1 一小时 push | 真实 FCM/当前 endpoint 可运行；计划标准为连续墙钟观察后的 App 诊断、服务、HTTP 和 alarm 证据。加速时钟会破坏计时语义。 | **未开始，等待用户决定是否真等**：计划本身不要求 `failures=8` 或 event cursor 固定。旧 `failures=8,event_cursor=4` 来自失效 endpoint，不能作本轮证据；用户提出的 8/游标断言另列为未验证补充项，不能 SQL 造数或用模拟推送。G2 后 generation 4 的 `failures=0, call_failures=3, event_cursor=8`；需先具备真实 FCM 健康前置并保持无业务。 |
| H3 一小时 stream | 方法/时间限制：之前 43 分 21 秒不可外推；QEMU `-icount` 或 guest clock 倍速会让 Android timer 与真实网络、服务器和 FCM 脱节。 | **未重跑，等待用户决定是否真等**：必须 fresh、连续 60 分钟墙钟；diagnostics/deviceidle/alarm/logcat/ping-pong 只能佐证实际墙钟时长。H3 需重新启动并确认 stream live、屏幕关闭、deep idle。H1 与 H3 可在短用例结束后共享一段 60 分钟窗口，另需约 10–15 分钟准备和前后导出。 |

G2 恢复阶段最初未出现新 modem edge 的原因已查明：旧 GSM 来电仍在 modem `INCOMING` 与 Telecom `RINGING`，而非 relay 恢复失败。旧来电已明确取消并验证空闲。其后 B6 清理后注入产生了新 modem edge，但该时刻 Homura 的 call-control runtime、SIM 绑定和后台启动均未就绪，不能把旧的 G2 session 当成本次证据。随后仅通过普通 UI 确认 AVD SIM 并启用 control-only 后台服务；远程语音仍关闭。由于计划明确不测 root/Magisk，本轮不再重复 call 注入或开启语音诊断。`DOCKER-USER` 没有本轮规则；G2 的真实 outage/恢复测试需等非 root 约束下可用的 call-control runtime。Sol 没有判定 G2、D1、F、C2/C3、E2/E3 等项目本质上做不到；当前明确的产品缺陷仍是 B3 和 C1。长观察保持暂停。

### C1 复测前 SIM 映射恢复风险与前置检查（2026-10-30）

本次 C1 端到端重测尝试无效：2026-10-30T03:59:11Z 的真实模拟器 GSM 来电进入 Homura modem，`GsmCallManager` 检测到来电，但随后因本地 SIM 映射为 `UNVERIFIED` 而在 Telecom offer 前结束；SoulGem 没有新 session，Madoka 没有收到 call push。它不构成 C1 修复的通过或失败，旧报告中的 C1 FAIL 仍保留。脱敏逐层证据见 `c1-retest-sim-precondition-20261030.txt`。

为验证修复，下一步只使用已有 `GatewayJointInstrumentation` 的 `reconfirm_retained_emulator_sim` 路径：保留当前 gateway pairing 和原 SIM UUID，调用生产 SIM propose/confirm API，并要求 `same_sim_verified=true`；不直接写 SQL，不清应用数据、不卸载、不重启模拟器、不更换 SIM、不重新配对。操作会推进节点 A 的 SIM mapping revision，并改变该 AVD 的本地 SIM 身份状态；若服务端向 B/C 同步，revision 也会随之更新。它不会删除 pairing、设备、会话或短信数据。操作成功后不能无损恢复到之前的 `UNVERIFIED` 状态，只能通过同一保留 SIM 的生产确认流程再次校正；因此先保存配套本地快照、核对当前无 modem/Telecom/Host 活动呼叫，并验证 SIM UUID、slot 和 subscription 一致。任一不一致、确认后仍 `UNVERIFIED`、revision 不一致或 PhoneAccount 正反向关联不唯一时立即停止，不再注入来电。此操作不启用 root/Magisk/CAPTURE_AUDIO_OUTPUT，也不改变语音开关。

### C1 SIM 前置恢复结果（2026-10-30）

通过上述 guarded retained-SIM 路径完成生产确认。相同 SIM UUID 保留；revision 由本地/服务器 12 一致推进到 13，本地身份为 `FINGERPRINT_VERIFIED`，服务器行是 `active/identity_verified=true`，slot 匹配。Telecom 预检中只有一个 call-capable SIM PhoneAccount；其反查 subscription ID 匹配该 SIM，`createForSubscriptionId(subId).phoneAccountHandle` 也回指同一账户。当前 mapping 没有保存单独的 PhoneAccount handle pin；`SimRegistry` 允许该字段为空并在运行时按已验证 SIM 的 subscription 解析，因此真实来电 offer 仍需实测。操作没有清数据、卸载、重启 AVD、重新配对、手改 SQL 或注入电话；结束时 modem 空闲、SoulGem 活动 session 数为 0。

Instrumentation 返回 `READY` 并完成受控确认；首次启动时旧 `stop` mailbox 命令仍在，因此 runner 随后按该命令正常结束。它没有执行来电，也未影响数据。发起 C1 前会先写入新的 `status` mailbox，再启动 runner。脱敏证据和本地私有快照哈希见 `c1-sim-reconfirm-20261030.txt`。
