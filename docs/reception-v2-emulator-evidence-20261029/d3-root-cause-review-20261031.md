# D3 stop-A 失败根因复核（2026-10-31）

**判定：保留 D3 FAIL（产品行为）；已确认接收状态的短暂关闭撤出了 B 上的来电参与者，但不能把某一种异常或 callback 声称为已经证实的触发根因。** 静态代码明确存在把运行时启动失败写成持久接收关闭的路径；本次日志与此路径吻合。新增全 buffer 证据证明 Android 实际启动过前台服务，不能把本次失败直接解释为“Android 禁止启动 FGS”。

本次只读审计没有运行新 D3，没有停止节点、操作 AVD、重置日志缓冲区、修改设置/配对/数据库，也没有修改产品或测试代码。仅新增本证据文件；主验收报告由主线程合并。任何 D3 产品修复及为此改动 APK 的取证都需要 **Kiri 另外授权**，不属于此前 B3/C1 修复授权。

## 范围与源码一致性

- 读取 `host`、`gateway`、`server` 的 `claude/vibrant-babbage-ye0ey3` 工作区，以及远端既有原始日志；不读取或输出认证 token、手机号、设备/呼叫 UUID、推送 endpoint。
- 本次读取时 host HEAD 为 `0245714ddfc00ec661f06c5bdd41e9a780616fd8`，server 为 `152821079f87af7958d30cebf0cf8c0c316ed8e0`。对 host 报告所列 `e25d9436c05f79d1e49cfb65360aea039a0846d7` 与当前 HEAD 比较，下面分析的 `CallRuntime`、`HostCallService`、`CallPreferences`、`ReceptionV2`、`NodeMonitor`、`PushWake` 产品文件无差异。
- 既有判定与数据库时间线取自 [有效重试记录](d3-stop-a-retry-result-20261030.txt)。第一次 stop-A 尝试受先前操作的延迟设置同步影响，按 [重试前风险记录](d3-stop-a-risk-before-20261030.md) 不评分；本次没有把那次无效尝试混入有效重试。

## 已证实事实

以下时间均为 2026-10-30 UTC；Android epoch 日志转换为 UTC 后与既有报告对齐。

| 时间 | 事实与证据边界 |
| --- | --- |
| 重试前 | 原执行记录确认 A/B 接收及 endpoint 健康、没有 live call，设置页面未操作且没有启动 instrumentation runner；A 被停止后 B/C 继续健康。这里保留当时的现场记录，不以今天的环境状态代替。 |
| 11:30:30.687 | H1 收到真实 `wire=2 kind=call` FCM，priority/original 均为 1。 |
| 11:30:30.693 | ActivityManager 允许启动 `HostCallService`，原因为 `PROC_STATE_TOP`，并有 FCM 临时 allowlist。 |
| 11:30:30.704 | events 日志出现该服务的 `am_foreground_service_start`。这是比“允许请求”更强的实际启动证据。 |
| 11:30:30.758 | events 日志出现该服务的 `am_foreground_service_stop`，原因为 `STOP_FOREGROUND`，本次持续 54 ms。该行没有给出哪个产品调用点执行了 stop。 |
| 11:30:30.851777 | 既有 B 数据库记录：H1 participant 以 `reception_disabled` 结束。 |
| 11:30:30.853859 | 既有 B reception 记录：`incoming=false`、revision 11。与源码的 false PUT/撤出事务一致；这些观察时间不能当成完整 HTTP/调用栈追踪。 |
| 11:30:31.813 / .908 | 再一次 LISTEN 启动被允许，随后 events 再次记录实际 FGS start。 |
| 11:30:31.878468 | 既有 B reception 记录恢复 `incoming=true`、revision 12；已经结束的 participant 没有因此重新加入该通电话。 |
| 11:30:32.244 | H1 记录 `Call control idle; the next call arrives by push`，随后 .255 记录 `STOP_FOREGROUND`。这次 idle 停服务本身按代码不会关闭持久接收偏好。 |
| 11:30:32.650 / .820 | 又一次真实 call FCM 到达；随后控制再次进入 idle。 |
| 有效重试窗口 | 没有 `Incoming call ringing`、没有 H1 来电页；原执行记录没有观察到 H1/H2 响铃。操作员未在窗口内切换设置。 |

新增原始证据来自远端：

`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f-sequence-20261030/h1-logcat-after-ui-retry.txt`

该存档包含 `system`、`radio`、`crash`、`events`、`main` 的 buffer 起始标记，且覆盖 D3。提取 epoch 范围 **[1793359825, 1793359840]**（11:30:25–11:30:40，保留原行换行、顺序）得到 378 行。该窗口没有匹配到 host crash/ANR/进程死亡、`ForegroundServiceStartNotAllowedException`、`MissingForegroundServiceTypeException`、`SessionChanged`/`SESSION_CHANGED`、`UnknownHostException` 或 `ConnectException`；也没有绑定此次关闭偏好的异常栈。**未匹配不是证明不存在异常**：关键产品 catch 本来就不输出异常。

在附近可见的 `ForegroundServiceTypeLoggerModule` “does not have any types”警告没有 accompanying 拒绝/崩溃记录，且 events 已证实 start 成功；不能单凭该警告判定 FGS 类型异常是本次根因。其他进程的 `NullBinder`/`TransactionTooLargeException` 同样没有与 host 关闭偏好建立因果关系。

## 原始日志完整性

本次发现专用采集文件在原记录后仍有追加内容。今天整文件哈希不同，不应替换或否认原报告的快照哈希；对每个原文件按行前缀增量计算 SHA-256，原报告哈希均能精确复现。

| 文件 | 可复现原快照 | 本次整文件 SHA-256 |
| --- | --- | --- |
| `/tmp/reception-r1-private-20261030/d3-retry-host-ui.log` | 前 **1744 bytes / 18 行**：`851f93bba3a93cfc806e1529ccd829241508a1b47b11f78e90f92e8b62d2ed25` | `a271876b278a49628e1eff630385774f248ed9b774e601ad7c555693fd159b84` |
| `/tmp/reception-r1-private-20261030/d3-retry-gw-ui.log` | 前 **7183 bytes / 61 行**：`297f50b8b7f452332a103cb8e562a0c11d9e3d31e2e70dd2ecdbb78cadae02e2` | `0736c4d82e7b45a775b779776b8320323373ee2c552f725cc541284492332afb` |
| 上述 `h1-logcat-after-ui-retry.txt` | 本次读取完整存档，24,257,402 bytes | `350b425206a7cf9b6a79f56e026ad46f2b0c6d845c9500731406fbda6219fa55` |

相同 [1793359825, 1793359840] epoch 窗口、以原始 bytes 拼接后的 SHA-256：

- 专用 host：4 行，`15aa0715296494214fae1c05e0a081d057efd2f1587e8364b7a8414044c675fc`。
- 专用 gateway：12 行，`e0b88f1abf8acfd65e81b4aa9e3bfad6e10b5665b13d0ed5abd4687c6a2db0a1`。
- 全 buffer host：378 行，`08fbdd9f80d89c07c0c7e7d741507f98f85c5bedcdc4b5a51952de23fc38240e`。

原始日志留在受限目录，未复制进 Git。此次只读调用当前 A/B/C 容器的 `docker logs --since 11:30:20Z --until 11:30:40Z` 均无输出，因此不能声称已由当前容器日志补齐当时的 reception HTTP 请求、主体或调用链。

## 具体代码路径与判断

| 路径 | 可以确认的代码行为 | 对本次故障的判断 |
| --- | --- | --- |
| `host/.../push/PushWake.kt:40`、`:64`、`:73` → `calls/CallRuntime.kt:357` | call hint 先唤醒**当前 active session** 的控制；来源是另一节点时，随后异步启动 `NodeMonitor.evaluate(force=true)`。 | A 已停时，存在先启动旧 active 节点控制、再故障转移的时序；本次具体 active/session 切换时间没有被记录，不能断言走了哪条竞态。 |
| `host/.../calls/HostCallService.kt:209`，尤其 `:239`、`:253` | 启动过程中依次读取 session、`prepareCallControl(api)`、校验 session、启动 engine；catch **任意 Exception** 后调用 `CallRuntime.onListeningFailed`，再停 FGS/engine/service；catch 没有 `Log`。`prepareCallControl`（`:822`）包含 capabilities 请求、原生引擎可用性检查及 capability 写入。 | **最强的写入端候选**。网络不可达、session changed、服务器拒绝等不同原因被同一 catch 合并；现存日志无法进一步指定异常种类。 |
| `host/.../calls/CallRuntime.kt:329` → `CallPreferences.kt:21` | `onListeningFailed` 重新读当前 session，无条件 `setEnabled(session,false)`；后者同步 commit 偏好并 `NodeMonitor.maintain(force=true)`。 | 代码明确把运行失败变成持久 incoming 关闭。它使用 profile 范围的偏好，可以影响同 profile 的健康 B；这是已确认的静态设计缺陷，是否正是本次实际调用点仍缺调用栈。 |
| `host/.../calls/CallRuntime.kt:124`、`:161` | 页面确保监听时，若服务启动返回 false，也会 `setEnabled(session,false)`。 | 另一个自动关闭入口，不能仅凭 false 行排除；但本次首个 Android LISTEN/FGS 已启动成功，不能把它直接定为本次触发入口。 |
| `host/.../mesh/NodeMonitor.kt:145`、`:181` → `calls/ReceptionV2.kt:77`、`:86` | 逐节点读取 `CallPreferences.enabledFor(session)`，将当前 choice 经 GET/PUT 同步到节点；冲突后重读并重试，成功后更新该节点的确认缓存。 | **同步的是偏好，不是主动判断用户意图**。节点同步本身不调用 `CallPreferences.setEnabled(false)`；它能把上游短暂 false 扩散到 B，也可能提交先前已采样的 choice。没有请求/偏好审计，不能宣称已排除所有延迟同步。 |
| `server/internal/httpapi/reception.go:69`、`:117`、`:124`、`:170` | 仅 client 角色的 reception PUT 更新 revision；`incoming=false` 在同事务撤出仍 ringing、尚未成为 candidate 的本设备 participant，reason 为 `reception_disabled`。true 更新没有复活 participant 的逻辑。 | 与观察到的 false/rev11 → participant 结束 → true/rev12 无法复活完全一致。仓库静态路径中，`reception_disabled` 的写入位于这里；没有证据表明是网关或服务端自主关闭接收。 |
| `host/.../calls/CallPreferences.kt:45`、`:52`、`:38`；`MainActivity.kt:1251`、`:2436` | UI 关闭通过 `setUserChoice(false)` 写入 `user_turned_off`；普通页面自动启用受此标记阻止。`onListeningFailed` 只写 enabled false，**不写 user-off 标记**，因此可被后续页面自动启用恢复 true。 | 无操作员切换且约 1 秒后自动恢复 true，更符合自动失败/恢复，而非用户明确关闭。但本次没有对应时刻的 user-off、enabled 和 profile 快照，所以仍是有代码依据的推断。 |
| `host/.../calls/CallRuntime.kt:48`、`:65`；`mesh/HostNodes.kt:229`；`data/Models.kt:32` | 同 profile 节点切换保留用户偏好，只有 profile 不同时才 clear；节点切换停止旧控制并尝试启动新控制。service 的 listening generation 也会阻止部分旧启动任务继续提交失败。 | 正常 A→B 同 profile 切换本身不应写 incoming=false。不能忽略已有 generation 防护而直接宣称“旧 session callback 清了新 session”；需要记录 generation/session 的实际顺序。 |
| `host/.../calls/HostCallService.kt:188` | v2 + push 健康时 idle 停控制，仅清运行态与 engine，不修改 `CallPreferences`。 | `.244` 和 `.820` 的 idle 日志不等于用户关闭，也不能解释之前的 false。 |
| `host/.../calls/CallActionReceiver.kt:8` | 信令通知的 STOP 操作也能调用非 `byUser` 的 disable 路径。 | 存在另一显式操作入口，但现场未操作、窗口也没有给出点击证据；未将其定为根因。 |

上表的 `host/.../` 均相对于 `host/app/src/main/java/com/callagent/host/`。gateway 的 `app/src/main/java/com/callagent/gateway/rtc/GatewayCallControl.kt:111` 负责逐节点 offer，`:149` 记录 offered 数量；本次 gateway 保存日志已有 `incoming_offered nodes=1`。它没有向 client reception PUT 写入 false 的路径，本次也没有证据要求修改 gateway。

**根因可确认到的层级：** B 收到了关闭来电接收的状态更新，按协议撤出了尚在 ringing 的 participant；随后恢复 true 无法挽救已结束的本次参与。**最可能的上游机制：** 临时呼叫控制启动失败被 Madoka 持久化为关闭，并同步到健康节点；页面自动启用稍后将其恢复。不能把“最可能”改写为已证实的 `ConnectException`、A DNS 故障、`SessionChanged` 或某个特定 callback。

这一结果违反 `server/docs/reception-v2.md:104` 的约束：“临时失败或休眠不自动取消”接收偏好。因此无需把 D3 改记为环境阻塞，也无需依赖新 D3 才能维持原产品 FAIL；新取证只用于最终定位上游写入点和验证修复。

## 最小修复建议及影响面（未执行，须 Kiri 授权）

1. 把持久用户接收选择与当前控制启动/连接结果分开。临时网络失败、旧 session 任务、控制初始化失败应更新运行状态/提示，触发有界重试或节点故障转移，保留 incoming opt-in。首先审查 `onListeningFailed` 及启动返回 false 的两处写入，避免只删一个 catch 后另一路继续关闭。用户主动关闭仍按现有生产 PUT 撤出尚在响铃的 participant。
2. 失败处理绑定启动时的 session/profile/generation，避免读取“当前 session”后把旧任务结果套给新的 active 节点；保留已有 generation 失效机制。该项是需要验证的保护，不是声称本次已证明发生 session race。
3. 保留服务端关闭接收时立即撤出与不复活已终止 participant 的语义。不要用服务端忽略 false、延迟撤出、重开已结束 participant 来掩盖客户端偏好污染，也不要修改来电授权、赢家裁决或网关分叉规则。
4. 补充脱敏原因记录：写入来源（UI/通知/自动恢复/失败）、启用前后值、用户关闭标记是否存在、匿名 node/session 代号和 generation、异常类型/受审错误码。不能直接输出任意 exception message、URL、token 或调用中的手机号。

最小产品改动预计集中在 host 的 `CallRuntime` / `HostCallService` / 偏好状态边界；尚无理由要求改 server 或 gateway。实际修复应兼顾旧 v1 节点的 capability 撤出、v2 push idle 行为、无 push stream、用户关闭后开页面保持关闭、session/profile 切换和主动呼出。主要风险是把“保持选择”误实现为无限重启 FGS/重连耗电，或让旧 session 重试影响新节点；需要有界调度与现有代际取消规则。修复仍须验证接收关闭的隐私行为和 C3 不受回归。

## 若需精确归因的下一次取证方案（主线程协调，未执行）

当前正在推进 E2/E3、F123/C3；不借用共享 AVD/节点重测 D3。等主线程安排独占窗口，并在 Kiri 授权诊断 APK/修复范围后，才执行以下一次受控验证：

1. 固定 APK/source 哈希和 active/profile 拓扑；只读记录 enabled、user-off 是否存在、每节点 reception revision/choice 和健康状态。预检无 live modem/Telecom/call participant，确认旧设置同步已稳定。不得 clear data、重配对、手写 SQL 或清空 logcat。
2. 所有关键自动关闭/恢复入口加上述脱敏结构日志，包含 `onListeningFailed` 调用来源与启动捕获的 generation/session，以及 `ReceptionV2.sync` 每次 PUT 的 desired/expected_revision。只开 `adb logcat` 无法产生被源码吞掉的异常栈，不能把同一采集办法当成能补齐根因。若不允许任何 APK 改动，可用现有 debuggable 包的非暂停 tracepoint/方法跟踪捕获这些入口；先确认不会暂停进程或改变超时语义，否则该次只能作诊断、不能评分。
3. 在主线程确认串行资源窗口后，对 GW/H1/H2 分别保存 `adb -s "$SERIAL" logcat -b main -b system -b crash -b events -v epoch`，从新文件开始采集，权限 0700/0600；另存节点 reception 更新及参加者时间线的只读查询。记录采集 PID，收尾仅停本次采集，绝不杀共享采集器或清缓冲区。
4. 仅停止 A，走一次真实 GSM → gateway → 生产多节点 → 真实 FCM，期间不动设置。关联“来源节点/active 节点、启动失败/节点切换、偏好 commit、PUT false、participant 撤出”的先后关系。保留原调用者测试号码与所有真实路径限制，不造 API call/模拟 push。
5. 恢复 A 并只清本次 modem call；核查设置、节点健康和 modem/Telecom 空闲。停止本次采集后立即固定整文件哈希，导出脱敏摘要。若出现前置失败则停止该轮，不能重复注入来电直到偶然通过。

修复后的业务评分还需证明 A 停止时健康 B 分支持续 eligible 并真正响铃/可处理，并验证显式 user-off 仍正确撤出且不会被页面或 FCM 自动重开。上述方案没有在本次运行，不能当成新增 PASS 证据。
