# 2026-10-29 执行观察（脱敏摘要）

本文件是当轮执行记录的脱敏摘要，不替代设备原始诊断 JSON、完整 logcat、数据库导出或 Wrangler tail。完整原始输出留在实验机私有目录，未复制到仓库，以免带入设备标识、会话标识、电话号码、推送 endpoint 或凭据。

## 环境与版本

- 分支：四个仓库均为 `claude/vibrant-babbage-ye0ey3`。
- SoulGem：`152821079f87af7958d30cebf0cf8c0c316ed8e0`，镜像 `soulgem-aio:reception-r1-1528210`。
- Kyubey：`1bb9a2bc976ace3ccedb3a95315e01713559bddf`。
- Homura：`a41dd196aa4caa279cc28c688469c70ca4ba59f4`。
- Madoka：`770fb716517aa7d4aa26b7ee32b297ea0a4c3c01`。
- GW 为 API 34 普通镜像；H1/H2 为 API 35 Google Play 镜像；H3 为 API 34 普通镜像。H1/H2 未登录 Google 账号。未执行 Magisk/root 测试。
- Kyubey 由本机 Wrangler 本地运行并经临时 HTTPS 隧道供实验机访问；没有部署成 Cloudflare Worker，因此本轮没有 Cloudflare `wrangler tail`。A1/A3 的 H1 收到过真实 FCM 唤醒；G1 未产生 SoulGem 来电 session，因此本轮没有 G1 FCM 结果。B6 期间本地 redacted worker 日志没有新增 wake 请求。
- 实验机 2 vCPU、约 8 GiB RAM、64 GiB 磁盘。初始根盘可用 5.5 GiB，H3 需要约 7.37 GiB，首次启动退出。只清理三个历史、未运行的 AVD 后可用空间升至 8.7 GiB；H3 随后用原 userdata 成功启动。清理前后 `df -h`、目录 `du` 和启动后资源值见 `disk-space-cleanup-20261029.txt`。

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
- B3：H1 重启后稳定且来电关闭、短信开启。拨号页当前明确显示“旧手机通话尚未就绪，呼叫可能失败；请在旧手机完成‘远程通话’设置。”因此本轮没有再次发起预期会失败的去电，按 GW 环境前置不满足记 BLOCKED。前次 `control_expired` 记录保留在原证据中；新证据见 `b3-call-readiness-20261029.txt`。
- B4：划掉应用并重新打开后，来电关闭、短信开启仍然保留，UI 显示已同步。H3 关闭后在原 H1 userdata 上重启，H1 无 ANR；Settings 开关仍为来电关闭、短信开启，Phone 页显示已同步。B4 两个子步骤现均 PASS；见 `b4-reboot-status.txt`、`b4-reboot-retest-20261029.txt`。
- B5：H1 临时开启来电后切到 airplane mode，离线关闭来电。Phone 页显示“1 个节点的接收设置待同步…”，服务端仍保持旧值；恢复网络后约 3.4 秒内 SQL 变为关闭且页面显示已同步。只响 H2 的后续来电断言因 G1 的 GW 注入阻塞；记录为 BLOCKED（环境，离线同步子项通过）。
- B6：H1 关闭后台同步后，用 `adb emu sms send` 向 GW 注入一条真实模拟器 SMS，SoulGem 增加一条 inbound/received 记录，H1 endpoint cursor 未前进。重新开启同步后，标记短信在 H1 消息线程中出现一次，通知列表出现线程通知及其分组摘要。Kyubey 本地 redacted 日志未增加本轮 wake 请求，故 push/relay 与来电断言 BLOCKED（环境）；见 `b6-sms-sync-20261029.txt`。

## 后续用例与环境阻塞

- C1–C3：Homura 的标准 ADB GSM 命令返回 `OK`，但未产生可处理的 modem/Telecom 来电，也没有新 SoulGem `call_sessions`。拒接、并发接听和来电开关切换均无法开始，BLOCKED（环境）。
- D1–D3：除 GW 来电注入不可用外，现有 B/C 容器仍来自旧 `joint-0.4` 镜像，不是 PR #33 的本轮目标；未在混合镜像上执行多节点用例，BLOCKED（环境）。
- F1–F3：H1 已恢复，但 Homura 拨号页报告远程通话未就绪，通话顺序无法完成；本轮未开始升级组合。2026-10-28 旧目标的 FAIL 仍只保留在上一份报告。
- G1：节点 A 于 16:25:03 UTC 重启，16:26:03 UTC 健康；SQL 仍为三台 v2 设备且设置保留。81 秒后及其后一次 canonical test-number 注入均没有新 modem call、Telecom current call 或 server session；无 FCM 结果。G1 服务恢复子项通过，来电子项 BLOCKED（环境），详见 `g1-restart-20261029.txt`。
- G2：G1 未到达 SoulGem，且本地 Kyubey 未收到本轮 wake 请求，依计划不做不可达注入，BLOCKED（环境/前置未满足）。
- E1：H3 已启动并通过 stream 心跳与设置同步验证。E2 因 GW 语音前置、E3 因 AVD 无可用 Wi-Fi 网络而 BLOCKED；E4 的 30 分钟数据只记录，不判定。详见 `h3-recovery-20261029.md`。
- H3 一小时 stream：force-idle 连续观察了 43 分 21.210 秒后按用户要求停止，并恢复 AVD 原先关闭的 idle 设置。该段未达到一小时，保留为 BLOCKED（方法）的部分证据，不作通过结论。
- H1 一小时 push：本轮未开始。最新 SQL 快照中两个 endpoint 组均为 `failures=8`、`event_cursor=4`；Kyubey 本地 worker 在 G1/B6 期间没有本轮 wake 请求，push 前置环境未恢复。详见 `push-environment-status-20261029.txt`。

## 证据完整性

本目录保留 P0 UI/SQL 摘要、B3–B6 与 G1 的脱敏关键证据，以及 H3 的诊断导出和时间片。完整原始 logcat、SQL 行和 pcap 留在实验机私有目录，因为包含必须脱敏的标识。Kyubey 使用本地 Wrangler；本轮记录了本地 redacted log 的计数，没有 Cloudflare `wrangler tail`。所有阻塞都保留环境或方法原因，没有把模拟推送当作真实 FCM。
