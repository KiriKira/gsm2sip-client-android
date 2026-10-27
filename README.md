# Madoka

Madoka 是三端系统的主机 App：通过服务端 **SoulGem** 使用另一台旧手机（网关 App **Homura**）里的两张 SIM 打电话、收发短信。
（仓库、包名 `com.callagent.host` 和签名不变，可直接覆盖升级或回退。）

**0.4.0**：通话只走 WebRTC（服务端呼叫控制 + 原生 ICE/DTLS-SRTP/Opus），SIP/PJSUA2 已彻底移除，设置页也不再有“WebRTC 语音”开关。
建议先把服务端升级到 0.4（上一版服务端需开启 `RTC_MEDIA_ENABLED`/`RTC_CONTROL_ENABLED` 才能通话）；升级和回退方法见服务端的 [0.4 发布说明](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/release-0.4.md)，三端联合测试见 [0.4 联合测试计划](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/plan-0.4-joint-test.md)。
WebRTC 实现见 [docs/webrtc-implementation.md](docs/webrtc-implementation.md)；10-19 的联合验收记录保留为历史。

[自动构建与 Release、固定签名配置](docs/releases.md)：`main` 推送发布预览版，版本标签发布正式版；首次发布前运行签名 Secrets 配置脚本。

Android host app for controlling calls and SMS through the two SIMs in a separate Android gateway phone (SMS-only operation needs no root). The Material 3 Expressive interface follows the Google Phone / Messages look (search pill, rounded list tiles, monogram avatars, pill dial pad, round in-call controls) and has three bottom destinations: **电话 / 短信 / 设置**. Phone history opens a dedicated dial pad; SMS conversations show their remote SIM and open a message editor. Server pairing, permissions, paired devices and backups live in Settings. See [the host UI design](docs/host-ui-redesign.md).

The app includes server call control over HTTPS, native WebRTC audio, self-managed Telecom calls, CallStyle notifications, mute/DTMF/audio routing and server-authorized remote calls. Calling requires configured server/gateway voice capability; real dual-SIM audio remains a device acceptance gate. It does not read or send SMS through the host phone or fall back to the host carrier dialer.

Several hosts can pair to the same owner and gateway, using separate one-time codes and credentials. Settings lists paired hosts and preserves its last snapshot offline and through folding. Incoming calls select the first host that actually answers; other hosts show that another device answered. One host rejecting does not reject the call for everyone. See [multi-host operation](docs/multi-host.md); the shared protocol supports future platforms, while a Windows client is not yet implemented.

## 多服务器节点

下一阶段设计：[Reception v2 精简协议](docs/reception-v2-protocol.md)与[实施 PLAN](docs/reception-v2-PLAN.md)：复用现有事件和接口，解耦电话/短信、按需来电唤醒并支持无 FCM 降级。[与远期 mesh 的整合决定](docs/reception-mesh-decision.md)：先完成接收改造；出口故障先用受信 Kyubey 备用地址，必要时再做单跳代发；完整复制/控制路由暂不排期。当前均为待实施设计。

此前的[持续待机唤醒锁修复计划（2026-10-27）](docs/idle-wakelock-fix-plan-20261027.md)、[待机优化计划](docs/standby-optimization-plan-20261026.md)和[实施记录](docs/standby-optimization-implementation-20261026.md)保留供对照；新接收协议的实施顺序以 Reception v2 PLAN 为准。

设置里可以添加多个独立服务器节点并拖动排序：当前节点不通、旧手机不在线或延迟过高时自动切到下一个，
更高优先级节点稳定恢复后切回。同一条短信经过多个节点只存一份、只提醒一次；发出的短信由主手机铸造全局
`message_id`，跨节点重试时旧手机最多执行一次。见 [多服务器节点](docs/mesh-nodes.md)。

## Build

Requires JDK 17 and Android SDK platform 35. From this directory run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --max-workers=2
```

The checked-in Gradle wrapper is copied from the gateway project and pins Gradle 8.9 with its distribution checksum. CI runs the same build and unit-test tasks.

## Pair and use

Install the APK, open **设置**, and enter the server URL and a one-time pairing code created for a `client` role. HTTPS is recommended; `http://` server URLs are also supported for self-hosted nodes on a home LAN or a private VPN such as Tailscale, where you are responsible for network security. Call audio is always encrypted with DTLS-SRTP. New calls and messages use a selected server-confirmed remote SIM. Replies keep the incoming message's SIM. Messages whose SIM cannot be resolved remain readable without a send action. Offline, the app keeps the latest server snapshot, call/message history and drafts; it does not show a local SMS as submitted or delivered. Optional contacts permission reads names and phone numbers from this host phone for search and dialing.

If an SMS POST times out, its body, SIM ID, mapping revision and idempotency key are saved in SQLite. The app asks the user to check or continue that same submission. It never creates a replacement key for an uncertain task, and it queries an existing server message rather than POSTing it again.

Opening a paired app automatically enables background SMS sync using authenticated WSS wake hints plus HTTPS event-cursor polling, a visible foreground service, reboot recovery and deduplicated inbound notifications that show the sender and message (lock screen shows only “新短信”; verification codes get a copy button and can be copied automatically). Available server calling starts incoming-call signaling automatically from the visible app; microphone permissions are requested only when placing or answering an actual call. There are no sync or backfill switches. Notification and battery permissions remain Android-controlled. On devices with Google Play services, builds that carry a Firebase config and a [Kyubey](https://github.com/KiriKira/Kyubey) relay URL (`MAHOGSM_PUSH_RELAY_URL`) also get content-free high-priority wake-ups (0.4.1): the relay turns the FCM token into an endpoint that nodes post to, so nodes need no Firebase credentials. Without push, polling is as before. Physical-device sleep-state acceptance remains pending. FoldingFeature and window metrics adapt inner/outer screens, with saved navigation, drafts and system-bar/cutout/IME insets. See [Android UI and background operation](docs/android-ui-and-background.md) for permissions and limits.

## SMS backup and archive

The backup screen exports password-encrypted archives, JSON, or SMS Backup & Restore XML. Imports merge into a separate read-only history ledger and never enqueue sends or change the paired account. See [backup and restore instructions](docs/sms-backup.md) and [两端 KVM 验证与关键界面截图](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/ui-verification/README.md).

## Project notes

- [Implementation plan](PLAN.md)
- [Implementation and data-safety notes](docs/implementation-notes.md)
- [Protocol pin](docs/protocol-pin.md)
- [Three-party protocol](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/protocol-v1.md)
- [Joint roadmap](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/roadmap.md)
- [Android gateway](https://github.com/KiriKira/gsm2sip)

SMS cache merges, inbound alert journaling and event-cursor advances share a SQLite transaction;
foreground and background readers fence stale pages against the committed cursor. Pending alerts
are acknowledged after Android accepts the notification, so a process interruption may replay
a notification instead of silently losing it. First history remains silent; summaries are coalesced
and notification delivery still requires user opt-in, permissions and network/OS availability.
See the [feature gaps and weak-network audit](https://github.com/KiriKira/gsm2sip-server/blob/codex/control-plane-foundation/docs/network-and-feature-status.md).

主机 UI 重构的原始截图、验证状态和自动报告见 [截图与验证](docs/ui-redesign-verification/README.md)。

两端 UI 与 Magisk 测试所用 GitHub Actions + KVM Android Emulator 的启动、取消、截图下载、复用模板和费用说明，见 [Android 虚拟机使用指南](https://github.com/KiriKira/gsm2sip/blob/main/docs/android-virtual-machine.md)。
