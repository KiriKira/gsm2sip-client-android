# Reception v2 模拟器验收计划

对象：R1 的四个 PR，三端加中继同一批提交。

| 仓库 | PR | 分支提交 |
| --- | --- | --- |
| SoulGem | [gsm2sip-server#33](https://github.com/KiriKira/gsm2sip-server/pull/33) | `f055aa3` |
| Kyubey | [Kyubey#1](https://github.com/KiriKira/Kyubey/pull/1) | `1bb9a2b` |
| Homura | [gsm2sip#26](https://github.com/KiriKira/gsm2sip/pull/26) | `ef2c283` |
| Madoka | [gsm2sip-client-android#38](https://github.com/KiriKira/gsm2sip-client-android/pull/38) | `5f553ce` |

分支之后若有新提交，以测试时的 HEAD 为准并记入结果。协议见 [reception-v2.md](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/reception-v2.md) 第 0 节。

## 范围

**要验证的是：**
- 待机时不续约、不持锁、不常驻，推送能把来电送到。
- 关闭来电后短信和主动拨号照常。
- 整通拒接。
- 多节点分叉。
- 无推送时的 WebSocket。
- 真实的升级顺序。

**不在本计划内：**
- Magisk 模块与 root 相关路径（模拟器装不了）。
- 真实蜂窝音频、厂商 ROM 后台限制、8 小时耗电对比：留给真机阶段。
- 三端任意版本错配的组合：只测部署时真实会出现的一种，见 F1。
- 已有单元或集成测试覆盖的纯逻辑：账本竞态、推送水位、TTL 计算等，不在模拟器上重复。

## 环境

沿用 [0.4 三端联合测试计划](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/plan-0.4-joint-test.md) 的 KVM 模拟器和 Docker 节点搭建方式。

**服务端：** 三个独立 SoulGem 节点 A、B、C，各自有 PostgreSQL，使用 PR #33 构建的镜像。默认只用 A；D 组用到 B、C。

**Kyubey：** 把 PR 分支部署成测试 Worker（例如 `npx wrangler deploy --name kyubey-r1-test`），使用与 Madoka 构建相同的 Firebase 项目凭据。Madoka 构建时把 `MAHOGSM_PUSH_RELAY_URL` 指向这个 Worker。
- 如果拿不到 FCM 凭据，推送相关用例（A、C1、C2、D3、F1 的推送部分）记为“阻塞”，不要用别的方式模拟推送后算作通过。

**模拟器：**

| 名称 | 镜像 | 用途 |
| --- | --- | --- |
| GW | API 34 普通镜像 | Homura。来电用 `adb emu gsm call <号码>`，主叫挂断用 `emu gsm cancel <号码>`，来短信用 `emu sms send <号码> <内容>` |
| H1、H2 | API 34 或 35，带 Google Play 的镜像，已登录 Google 账号（否则没有 FCM） | Madoka |
| H3 | 无 Google Play 的镜像 | 只用于 E 组（无推送） |

**常用命令：**

| 用途 | 命令 |
| --- | --- |
| 熄屏 | `adb shell input keyevent 223` |
| 强制 Doze | `adb shell dumpsys deviceidle force-idle`（结束用 `unforce`） |
| 待机桶 | `adb shell am set-standby-bucket com.callagent.host rare` 或 `restricted` |
| 后台杀进程 | `adb shell am kill com.callagent.host`（注意 `force-stop` 会让 FCM 停止投递，是另一回事） |
| 断网 | `adb shell cmd connectivity airplane-mode enable`（恢复用 `disable`） |
| 切换 Wi-Fi 和数据 | `adb shell svc wifi disable` / `svc data enable` |

**每个用例都要收集的证据：**
- Madoka 的“待机统计”导出 JSON，重点看 `reception` 节点模式和 `wake_locks`。
- `adb logcat` 中 `GsmRtc`、`HostPush`、`HostCallService` 标签的日志。
- 服务端 SQL：`device_reception`、`push_endpoints`（`generation`、`failures`、`call_failures`）、`call_sessions` 与 `call_participants` 的状态和 reason。
- Kyubey 的 `wrangler tail`，看 kind、TTL、优先级。
- 测时延的用例记录 GW 日志里的振铃时刻和 H 上 `Incoming call ringing` 的时刻。

## 准备（P0）

1. H1、H2 配对节点 A，打开“后台来电接收”和“后台同步”。GW 配对节点 A。
2. 通过标准：
   - SQL 里 H1、H2 的 `device_reception` 都是 `version=2, incoming_enabled=true, sms_enabled=true`。
   - 诊断导出里节点模式是 `push`。
   - 通话页显示“接收设置已同步到 1 个节点”。

## A. 推送模式下的来电

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| A1 | 只用 H1（H2 开飞行模式）。H1 熄屏并 `force-idle`，等 1 分钟后 GW 来电，接听，10 秒后挂断。重复 3 次 | 3 次都响铃并接通。GW 振铃到 H1 响铃的时间逐次记录，目标 ≤5 秒，任何一次都不超过 25 秒 |
| A2 | A1 结束 60 秒后检查 H1 | `dumpsys activity services com.callagent.host` 里没有 `HostCallService`；没有信令通知；`dumpsys power` 里没有持有中的 `host:rtc-signaling`；诊断中没有 `rtc-compat-idle` 和 `rtc_capability` 的新增 |
| A3 | H1 `am kill` 后熄屏、`force-idle`，GW 来电 | 响铃并能接听 |
| A4 | 分两次：GW 来电后 1 秒内 `gsm cancel`（H1 还没响）；GW 来电，等 H1 响铃后再 `gsm cancel` | 第一次 H1 不响或只短暂响后停止；第二次 H1 在几秒内停止响铃；两次之后都不再出现迟到的响铃 |
| A5 | 待机桶设为 `restricted` 后重复 A1 一次 | **只记录不判失败**：是否响铃、时延、FCM 实际优先级（诊断里的 `fcm_priority`）。这是现实中 FCM 被降级的情况；如果不响铃，单独报告，作为真机阶段的重点 |

## B. 关闭来电与短信独立

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| B1 | H1 点“关闭后台来电接收”，H2 保持开启。GW 来电 | 只有 H2 响铃；H1 的 SQL 显示 `incoming_enabled=false`；H1 没有启动 `HostCallService` |
| B2 | B1 状态下 GW 收一条短信 | H1、H2 都收到并提醒，各只提醒一次 |
| B3 | B1 状态下 H1 主动拨出，通话后挂断 | 拨出成功；挂断后 H1 仍是关闭状态，服务端仍是 `incoming_enabled=false` |
| B4 | H1 从后台划掉应用再打开；再 `adb reboot` 后打开 | 两次都仍是关闭，不会被自动重新开启 |
| B5 | H1 先开启来电，再开飞行模式，在离线状态下关闭“后台来电接收”，然后关闭飞行模式 | 离线期间通话页显示“待同步”；恢复网络 1 分钟内 SQL 变为 `incoming_enabled=false`，通话页显示“已同步”；之后来电只响 H2 |
| B6 | H1 关闭“后台同步”，GW 收短信，再来一通电话 | `wrangler tail` 里没有发给 H1 的 sms kind；电话照常响铃。重新开启后台同步后，积压短信只补一次提示 |

## C. 拒接

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| C1 | H1、H2 都开启来电，H2 开飞行模式。GW 来电，H1 点拒接 | GW 上的来电在约 3 秒内结束（`emu gsm list` 为空）；H1 先显示等待确认，随后显示“所有主机已停止响铃”；H2 恢复网络后不响铃，记录里原因是 declined |
| C2 | H1、H2 都在线。GW 来电，两台同时响，用脚本让 H1 接听、H2 拒接，间隔约 0.5 秒，交换先后各做 2 次 | 每次只有一个结果：要么接通且不会被迟到的拒接挂断，要么整通结束、两台都停止。不允许出现一台通话中而 GW 已挂断 |
| C3 | H1、H2 都在响铃时，H1 关闭“后台来电接收” | H1 停止响铃；H2 继续响铃且能接听；来电没有被挂断 |

## D. 多节点

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| D1 | GW 再配对节点 B、C（GW 侧按 A、B、C 顺序）。H1 只配对 B；A、C 上没有主机。GW 来电 | H1 响铃；GW 日志显示 A、C 返回 `NO_INCOMING_SUBSCRIBERS` 后继续 offer；整通在剩余窗口内完成 |
| D2 | H1 配对 A、B 两个节点，GW 来电 | H1 只出现一次 Telecom 来电（两个节点的推送合并）；接听后另一节点显示 answered_elsewhere |
| D3 | D2 状态下 `docker stop` 节点 A，GW 来电 | 来电经 B 送达并能接听 |

## E. 无推送（H3）

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| E1 | H3 配对 A，开启来电与后台同步 | 诊断模式为 `stream`，心跳 120 秒；节点 A 日志或抓包显示约 120 秒一次服务端 ping，客户端不再每 25 秒 ping |
| E2 | H3 熄屏（不 `force-idle`）后 GW 来电 | 响铃并能接听；空闲期间没有 `rtc-compat-idle` 锁，也没有每几秒一次的 calls 轮询 |
| E3 | H3 在空闲时 Wi-Fi 切到移动数据，再切回 | 每次切换只重连一次（日志里 `wss_open` 一次）；之后来电正常 |
| E4 | H3 保持 30 分钟不动 | 诊断里心跳升到 180 秒或以上，期间连接没有异常断开（只记录，不严格判定数值） |

## F. 真实的升级顺序（唯一的版本组合）

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| F1 | 先让 GW、H1 运行当前 `main`，服务端与 Kyubey 已是本批。做一次来电、一次去电、一条短信 | 都正常（旧手机走原来的 capability 方式）；H1 收到的推送是 wire1 |
| F2 | 升级 GW 到本批，重复 F1 | 正常；GW 日志出现 `remaining_ring_ms` |
| F3 | 升级 H1 到本批（覆盖安装，不清数据），等 1 分钟后重复 F1 | 自动切到 v2（SQL 出现 `version=2`，保留原来的开关状态）；升级前已提醒过的短信不重复提醒；来电走推送模式 |

## G. 服务端异常

| 编号 | 步骤 | 通过标准 |
| --- | --- | --- |
| G1 | 手机空闲时 `docker restart` 节点 A，1 分钟后 GW 来电 | 来电正常送达，接收设置仍在（没有回到 v1） |
| G2 | 节点 A 运行中临时把 Kyubey 地址改成不可达（例如防火墙拦截），GW 来电；然后恢复 | 不可达期间 `call_failures` 增加，来电不会重复推送刷屏；恢复后下一通来电正常，`call_failures` 归零 |

## 短时待机观察（不代替真机 8 小时）

在 H1（push 模式）和 H3（stream 模式）各做一段 1 小时观察：熄屏、`force-idle`，期间无业务。结束后导出诊断。

| 模式 | 期望 |
| --- | --- |
| push | `rtc-compat-idle`=0，没有 `HostCallService`，calls 列表 HTTP 请求接近 0，没有 `RtcSleepyListening` 精确闹钟（`dumpsys alarm` 中没有 `RtcRetryReceiver`） |
| stream | 只有 WebSocket 心跳和必要的重连；同样没有 `rtc-compat-idle` 和精确闹钟 |

## 结果记录

- 每个用例写“通过 / 失败 / 阻塞 / 仅记录”，并附证据文件名。
- 失败的用例保留三端日志与 SQL 快照，写明复现步骤。修复后只重跑相关用例和受影响的 A1，不重跑全表。
- 结果文档放在 `docs/reception-v2-emulator-validation-<日期>.md`，开头写测得的四个提交。
