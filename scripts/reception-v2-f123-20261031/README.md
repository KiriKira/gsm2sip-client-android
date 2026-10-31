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
- `f1-probe.py` — 单次取证的只读探针（1 Hz 采样 DB + Host Telecom）。
- `labq.py` — 在节点 B 上执行只读 SQL 的小工具（SQL 走 stdin）。

测试号码不在仓库里：脚本从私有的 `f1-number.private` 读取。

已知的 harness 坑与修法见 `docs/reception-v2-emulator-evidence-20261029/f123-c3-results-20261031.md`。
