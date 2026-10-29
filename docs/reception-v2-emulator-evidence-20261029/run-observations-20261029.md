# 2026-10-29 执行观察（脱敏摘要）

本文件是当轮执行记录的脱敏摘要，不替代设备原始诊断 JSON、完整 logcat、数据库导出或 Wrangler tail。完整原始输出留在实验机私有目录，未复制到仓库，以免带入设备标识、会话标识、电话号码、推送 endpoint 或凭据。

## 环境与版本

- 分支：四个仓库均为 `claude/vibrant-babbage-ye0ey3`。
- SoulGem：`152821079f87af7958d30cebf0cf8c0c316ed8e0`，镜像 `soulgem-aio:reception-r1-1528210`。
- Kyubey：`1bb9a2bc976ace3ccedb3a95315e01713559bddf`。
- Homura：`a41dd196aa4caa279cc28c688469c70ca4ba59f4`。
- Madoka：`770fb716517aa7d4aa26b7ee32b297ea0a4c3c01`。
- GW 为 API 34 普通镜像；H1/H2 为 API 35 Google Play 镜像；H3 为 API 34 普通镜像。H1/H2 未登录 Google 账号。未执行 Magisk/root 测试。
- Kyubey 由本机 Wrangler 本地运行并经临时 HTTPS 隧道供实验机访问；没有部署成 Cloudflare Worker，因此没有 `wrangler tail` 部署日志。A1/A3 实际收到了推送唤醒；H2 的 G1 未确认收到 FCM。
- 实验机 2 vCPU、约 8 GiB RAM、64 GiB 磁盘。测试后可用磁盘约 5.6 GB；H3 启动时 Android Emulator 将 RAM 提高到 2.5 GiB，并因 userdata 分区空间不足而退出（可用 5661.45 MB，要求 7372.80 MB）。

## 推送来电与待机

- A1 三次来电都响铃并接听。GW 首次振铃到 Madoka `Incoming call ringing`：
  1. `07:08:46.001Z` → `07:08:47.696Z`，`1.695 s`；
  2. `07:21:14.153Z` → `07:21:16.272Z`，`2.119 s`；
  3. `07:33:29.651Z` → `07:33:31.957Z`，`2.306 s`。
  三次均在计划的 5 秒目标内并低于 25 秒上限；后两次接听后保持通话至少 10 秒。
- A2：A1 后等待 60 秒检查，未见 `HostCallService`、信令通知、持有中的 `host:rtc-signaling`，也未见兼容续约/闹钟新增。
- A3：杀掉 Madoka 进程、熄屏并强制 Doze 后，来电由推送唤醒并成功接听。
- A4：首次快速取消时 H1 在服务器建呼约 0.71 秒后短暂响铃，取消后停止；第二次在 H1 响铃后取消，也在数秒内停止。两次均未观察到迟到或重复响铃。
- A5：restricted 待机桶下观察到 H1 响铃并接听、通话至少 10 秒；记录为现象，不判通过/失败。该轮未取得可提交的 FCM 实际优先级原始诊断值。

## 接收开关、短信和去电

- P0：切换来电和短信开关后，UI 开关状态与 SQL `device_reception` 一致；最终两个设备均为 v2、来电与短信开启。push endpoint 计数为 2，`failures=0`、`call_failures=0`。UI/SQL 摘要见 `p0-ui-switches.txt`。
- B1：H1 关闭来电、H2 保持开启。SQL 为一个 `incoming=false,sms=true` 和一个 `incoming=true,sms=true`；测试来电只有 H2 出现振铃记录，H1 未见来电服务启动。
- B2：合成短信 marker 只发出一条。H1/H2 各观察到一条短信事件；服务端消息数增加 1。每台手机通知栏可见线程通知和分组摘要各一条记录，未观察到重复短信事件。
- B3：H1 来电关闭时主动拨号未完成。UI 保持“正在呼叫…”；服务端 outgoing session 约在创建后 20.5 秒以 `control_expired` 结束，`dispatched=false`，并记录 `end_requested=true`。网关 instrumentation 进程当时仍在运行并处于 `commands` 阶段；网关另有通用 MESH `NETWORK_ERROR`。现有证据不能确定是产品缺陷还是实验环境/出站媒体路径故障，故 BLOCKED（环境，原因未定位）；没有归因于 SIM 或默认拨号器。
- B4：划掉应用并重新打开后，来电关闭、短信开启仍然保留，UI 显示已同步。计划中的重启后检查遇到 Android “Madoka isn't responding”，last-ANR 为 `Input dispatching timed out (Application does not have a focused window)`；只读本地状态与 SQL 仍为来电关闭、短信开启，但重启后 UI 验证未完成。证据见 `b4-reboot-status.txt` 和 `b4-post-reboot-anr.png`。因设备内存紧张、归因不明，整体 BLOCKED（环境），不是产品 FAIL。

## 后续用例与环境阻塞

- B5、B6、C1–C3、D1–D3、F1–F3：H1 重启后处于 ANR，不能完成其 UI/通话步骤；B/C 的依赖节点也未处于可用于多节点测试的运行状态，因此未继续，记 BLOCKED（环境）。F1/F2 在 2026-10-28 旧目标上的 FAIL 仍只记在上一份报告，本轮没有重跑，也不把旧结果挪作本轮结果。
- G1：节点 A 重启后恢复健康；接收 SQL 状态保留。GW synthetic call 得到 `OK` 并开始振铃，但 H2 没有 `HostCall`、`HostPush` 或 `GsmRtc` 收到来电的记录，最终参与方为 `declined`，因此端到端推送交付未证实。H2 未登录 Google 账号，BLOCKED（环境），不判产品 FAIL。
- G2：基线 G1 的 H2 推送交付未确认，依计划不做 Kyubey 不可达注入，BLOCKED（环境/前置未满足）。
- E1–E4 与 H3 一小时 stream 观察：H3 因 userdata 分区空间不足未启动，均 BLOCKED（环境）；没有伪造 stream 观测结果。
- H1 一小时 push 观察：H1 在 B4 重启后 ANR，BLOCKED（环境）。

## 证据完整性

本目录保留 P0 UI/SQL 摘要和 B4 重启后状态/截图。A1–A5、B1–B3、G1 的完整 logcat、数据库快照和诊断导出只在实验机私有证据目录；它们含有必须脱敏的标识，尚未形成可提交的逐用例原始摘录。Kyubey 使用本地 Wrangler，故没有 Cloudflare `wrangler tail`。结果状态基于现场观察和实验机记录；上述证据缺口在验证报告中明示。
