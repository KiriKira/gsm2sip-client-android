# Sol 复核与续测操作记录（2026-10-30）

GPT-6.1 Sol 阅读了 `reception-v2-emulator-validation-20261029.md`、2026-10-29 证据目录内的文本/JSON/截图，以及 2026-10-28 的升级报告与归档证据。复核认为，普通 ADB GSM 注入此前的失败不足以证明 API 34 AVD 无法产生来电；Homura 源码已有 `GatewayJointInstrumentation`，可沿用 0.4 联测计划重新验证。

## 未完成项的可执行性判断

| 用例 | Sol 判断 | 替代/补测路径 | 本轮可做性与预计用时 |
| --- | --- | --- | --- |
| B3 | 目前是方法未走完，不是已证实环境限制。拨号页的旧手机提示只表示标准音频前置未完成 | 使用已有 `GatewayJointInstrumentation`；H1 来电保持关闭，以 UI 主动拨号，核对 Telecom、服务端终态及开关状态 | 可做，约 10–20 分钟；需要 GW、H1 |
| B5 剩余来电断言 | 同步断言已通过，只有来电未验证 | 恢复测试 runner 与真实 FCM，启动 H2，补测只有 H2 响铃 | 可做，约 5–10 分钟 |
| B6 剩余断言 | SMS/cursor 子项有部分证据；缺真实 relay 抑制证据与来电断言 | H1 开启来电、仅关闭短信同步；记录开关后注入一个 SMS marker 和一通电话，检查真实 Kyubey 日志与来电 | 可做，约 10–15 分钟 |
| C1–C3 | 尚未用计划内 runner 验证，不是已证实环境阻塞 | 使用 GW+H1/H2 的真实 Telecom/RTC/API 路径；C2 用设备端脚本控制 0.5 秒操作间隔 | 可做，约 20–35 分钟 |
| D1–D3 | 当前 B/C 镜像过旧是明确环境限制；可逆升级可解除 | 备份 B/C 各自数据库后，将测试专用节点升级到 A 同一 PR #33 镜像，保留独立数据库和网络；确认容器实际 image ID 后执行 | 可做，约 20–40 分钟；操作前需记录镜像回退和数据库恢复办法 |
| F1–F3 | 本轮尚未执行；10 月 28 日旧目标 F1/F2 的 FAIL 必须原样保留，不能充作当前结果 | 按计划固定旧 main 与本轮 PR 分支构建产物，严格按旧 GW/H1 → 新 GW → 新 H1 顺序覆盖安装；用历史证据只作参考 | 可做，约 45–90 分钟；取决于对应 APK 是否可复用 |
| G1 来电 | A 重启/设置保留已通过；来电子项定位不足。归档截图曾显示 Android 系统 Incoming call UI | 重新走一次 ADB GSM 注入并连续采集 radio、Telecom、Homura、SQL、FCM 证据，逐层确定断点 | 可做，约 5–10 分钟 |
| G2 | 前置缺失，不是本质不可做 | G1/真实 FCM 恢复后，只隔离测试节点 A 到 relay 的出口，记录并恢复临时规则；再验证下一通成功 | 可做，约 10–20 分钟；限定节点出口，规则可恢复 |
| H1 push 一小时 | 旧 `failures=8` 不足以证明来电 FCM 故障；`call_failures` 独立 | 先用生产 push test endpoint 发一次真实、静默的 SMS hint，并以 H1 `HostPush` 或诊断 `last_fcm_received_at` 证明送达；通过后再做完整 60 分钟墙钟观察 | 条件可做；端到端预检约数分钟，合格后需连续 60 分钟 |
| H3 stream 一小时 | 43 分 21 秒不能外推；模拟器时钟加速会破坏与真实网络/服务端/FCM 的计时语义 | 重新执行完整、连续的 force-idle 60 分钟观察；dumpsys、logcat、ping/pong 时间戳只能佐证，不代替墙钟时间 | 可做；需完整 60 分钟 |
| E2/E3/E4 | E2 可随 runner 补做；E3 的 API 34 AVD 未提供计划所需的已连接 Wi-Fi 传输；E4 现有累计快照有重连，且窗口证据不足 | E2 用 runner；E3 可另建 API/网络配置不同的控制 AVD，但不能冒充计划镜像；E4 需新建完整 30 分钟窗口及前后诊断 | E2 可做约 5–10 分钟；E3 当前镜像仍受环境限制；E4 可做约 30 分钟并仅记录 |

## Homura runner 的风险、回退与证据

本轮拟使用已存在的 `GatewayJointInstrumentation`，仅限测试包。runner 会通过 Android instrumentation 的 shell permission identity 读取 Telecom/SIM 状态，启动实际 `GatewayService`、`CallOrchestrator`、Telecom、服务器 API 与 WebRTC；只将物理蜂窝 PCM 替换为合成音。它不请求 root、不安装 Magisk、不更改默认拨号应用。使用 `reuse_pairing: true` 且不含 `reconfirm_retained_emulator_sim` / `reconfirm_operation_id`，只复用现有 gateway credential、SIM UUID 与 revision，不执行 pair 或 SIM propose/confirm；正常服务心跳仍会更新。

可逆副作用：启动时会短暂停止正在运行的 `GatewayService`，结束时 `finally` 会再次停止它；这会短暂中断 GW 心跳/短信控制。runner 会在测试包私有目录写入 `joint-*` 文件。计划在完成后通过文件协议发 `stop`、等 `joint-finished`，从 UI 恢复普通 Homura 后台服务，确认状态恢复，再卸载测试包；主包数据、配对、SIM 映射和默认拨号角色保留。若无法恢复普通服务，将停止后续依赖用例并记录现场，不扩大到系统权限改动。

该 runner 的 PCM 是合成的，不能作为真实蜂窝 HAL 音频通过证据；其余 Telecom、RTC、WebRTC、SoulGem 与真实 FCM 链路仍按各用例要求取证。真实推送只能经 SoulGem → Kyubey → Firebase Cloud Messaging；`sent=true` 只代表 relay 接受，必须另有 H1 侧收到证据。

## 真实 FCM relay 恢复的风险记录（操作前）

2026-10-29T18:20Z 的只读快照显示：H1/H2 的 endpoint 均指向已无法解析的临时 `trycloudflare.com` 主机；实验机没有 Wrangler、Cloudflare Tunnel 或 Node relay 进程。SoulGem A 中两组 endpoint 的短信 `failures` 分别为 30、26，`call_failures=0`，`pushed_event_cursor=4`；尚未为此恢复动作发推送。Madoka 的 Google 配置与服务账号 JSON 的 Firebase `project_id` 匹配；服务账号仅从用户先前提供的本地附件读取。

可执行恢复路线：在 `/tmp` 生成 Kyubey 当前分支的隔离工作副本，用本机 Wrangler `dev` 和临时 HTTPS Quick Tunnel 提供真实 relay；密钥文件 `.dev.vars` 权限限制为 0600，含新随机 `SEAL_KEY` 与服务账号 JSON，不写入仓库。新 Quick Tunnel 的域名是临时公网地址，relay 会暴露在公网，直到本轮结束时停止；Kyubey 只处理不含号码/短信内容的 FCM wake metadata。安装新 Madoka APK 前，先私下保存 H1 当前 APK 与 SHA-256，按同一远程 debug keystore（证书 SHA-256 `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28`）构建，设置 `MAHOGSM_PUSH_RELAY_URL` 为新 HTTPS 地址，并以 `adb install -r` 覆盖，保留配对和用户数据。正常注册会把 H1 endpoint 换成新 sealed endpoint、更新 generation 并重置失败退避，这是预期的应用协议行为，不手工改数据库。回退路径是安装事先保存的旧 APK；但旧 endpoint 仍指向已失效 tunnel，回退后真实 FCM 不会工作。

若 relay 预检通过，H1 将暂时运行该临时 relay 配置直到 H1 一小时观察完成；观察结束后停止 tunnel/worker 并清理临时密钥与构建目录。若在覆盖安装前无法证明签名、配置或 API 可达，本次不安装新 APK，也不改 endpoint。该动作只改测试模拟器 APK/服务，不改产品源码、服务器镜像、系统权限、默认拨号应用或 root 状态。

## 本轮续测状态

本节在操作前创建。实际执行结果、临时改动、回退检查和每个用例最终状态追加在下方；此文件不包含 token、凭据、号码、设备 ID 或 push endpoint。
