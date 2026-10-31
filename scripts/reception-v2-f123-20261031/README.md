# reception-v2 F1–F3 / C3 脚本（2026-10-31）

在 lab（`ssh gsm-lab`）的 `~/gsm-webrtc-validation/reception-v2-r1/private/f123-fresh-20261031/`
目录下就地运行；本目录只是副本，便于复现与审阅。

- `f1-incoming.py` — 来电：注入 GSM 来电 → 等 `call_participants.state=ringing` → 真实点击接听
  （R1 构建点 `CallActionActivity` 的「接听」，main 构建点通知的 `Answer`）→ 断言
  `call active / participant accepted / cellular_active=t` / Host Telecom `ACTIVE`。
- `f1-sms.py` — 短信：`emu sms send` → 断言服务端 `messages` 恰好 1 行、Host 短信页显示 1 次、settle 后仍 1 行。
- `c3-reception-off-v2.py` — C3：两台 Host 同时响铃时，在 H1 关闭「后台来电接收」，
  断言 H1 `ended|reception_disabled`、H2 仍 `ringing`、通话未被挂断、H2 仍可接听。
- `b12.py` — B1/B2：H1 关闭来电时只有 H2 响铃、短信两台各收一次（含 `dismiss_call_screen`，
  残留的 CallActionActivity 会吞掉 tab 点击）。
- `c1-decline.py` — C1：H2 飞行模式下 H1 拒接，量蜂窝通话结束时延。
- `c2-race.py` — C2：两台同时响铃后接听一台、0.5 s 后拒接另一台，两种顺序各 2 次。
  接听/拒接按钮在通知栏，先唤醒屏幕并 `expand-notifications`，再按固定坐标点。
- `b5.py` — B5：飞行模式下关闭来电接收，联网后 9.5 s 完成提交，之后只响 H2。
- `b6.py` — B6：关闭后台短信同步后不再收 sms 推送但来电照响，重开后积压短信补发一次。
- `mknode.py` — 从节点 B 克隆出新的 all-in-one 节点容器（自带网络/子网/端口）。
- `addnode.py` — 在网关 App 里「添加节点」（只用 UI，不 force-stop，避免 SIM 身份重估）。
- `f123-reconfirm.py` — 网关冷启动后若 `sim_bindings` 变 unverified，用 fixture 重新确认保留的 SIM。
- `d13.py` — D1/D3：三节点下 host-less 节点返回 `NO_INCOMING_SUBSCRIBER` 后继续 offer；
  停掉 D/E 后来电仍经 B 送达并接通。
- `d2-multinode-host.py` — D2：主机同时配两个节点，两个节点各有一通 ringing，
  主手机只有 `TC@…` 一个来电，接听后另一节点 `answered_elsewhere`。
- `f1-probe.py` — 单次取证的只读探针（1 Hz 采样 DB + Host Telecom）。
- `labq.py` — 在节点 B 上执行只读 SQL 的小工具（SQL 走 stdin）。

测试号码不在仓库里：脚本从私有的 `f1-number.private` 读取。

已知的 harness 坑与修法见 `docs/reception-v2-emulator-evidence-20261029/f123-c3-results-20261031.md`。
- `export-stats.py` — 导出 App「待机统计 → 分享统计」到 `com.callagent.lab.capture` 的
  `files/export.json`，打印 `events`/`wake_locks`/`wss_live`/`reception`。用于网络切换前后
  的 `wss_open`/`wss_closed` 计数对比（E3 重试的代理观察即用它取值）。
