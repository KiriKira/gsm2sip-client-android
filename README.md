# gsm2sip-client-android

本分支的设置页 **WebRTC 语音** 开关已接入独立 Go/HTTPS 呼叫控制和原生 WebRTC 音频；纯 RTC 节点不读取 SIP 凭据或初始化 PJSUA2。迁移阶段保留可选 SIP 兼容，拨号页继续显示联系人建议；每通电话显示并保留实际媒体/控制协议。[部署与控制协议](https://github.com/KiriKira/gsm2sip-server/blob/ci/webrtc-sip-debug-20261017/docs/rtc-call-control-v1.md)。独立控制 KVM 联合验收已通过并发布原签名 Debug；[独立控制验收与下载](docs/webrtc-independent-validation-20261019.md)。[旧过渡版本记录](docs/webrtc-joint-validation-20261019.md) 仅保留历史；物理蜂窝音频最后由真机验证。

[自动构建与 Release、固定签名配置](docs/releases.md)：`main` 推送发布预览版，版本标签发布正式版；首次发布前运行签名 Secrets 配置脚本。

Android host app for controlling calls and SMS through the two SIMs in a separate Android gateway phone (SMS-only operation needs no root). The Material 3 Expressive interface follows the Google Phone / Messages look (search pill, rounded list tiles, monogram avatars, pill dial pad, round in-call controls) and has three bottom destinations: **电话 / 短信 / 设置**. Phone history opens a dedicated dial pad; SMS conversations show their remote SIM and open a message editor. Server pairing, permissions, paired devices and backups live in Settings. See [the host UI design](docs/host-ui-redesign.md).

The app includes independent Go/HTTPS call control, native WebRTC, optional PJSUA2 SIP compatibility, self-managed Telecom calls, CallStyle notifications, mute/DTMF/audio routing and server-authorized remote calls. Calling requires configured server/gateway voice capability; real dual-SIM audio remains a device acceptance gate. It does not read or send SMS through the host phone or fall back to the host carrier dialer.

Several hosts can pair to the same owner and gateway, using separate one-time codes and credentials. Settings lists paired hosts and preserves its last snapshot offline and through folding. Incoming calls select the first host that actually answers; other hosts show that another device answered. One host rejecting does not reject the call for everyone. See [multi-host operation](docs/multi-host.md); the shared protocol supports future platforms, while a Windows client is not yet implemented.

## 多服务器节点

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

Install the APK, open **设置**, and enter the server URL and a one-time pairing code created for a `client` role. HTTPS (with SIP over TLS and SDES-SRTP) is recommended; `http://` server URLs and plain SIP over UDP (the server's `transport`/`media_encryption` SIP settings) are also supported for self-hosted nodes on a home LAN or a private VPN such as Tailscale, where you are responsible for network security. New calls and messages use a selected server-confirmed remote SIM. Replies keep the incoming message's SIM. Messages whose SIM cannot be resolved remain readable without a send action. Offline, the app keeps the latest server snapshot, call/message history and drafts; it does not show a local SMS as submitted or delivered. Optional contacts permission reads names and phone numbers from this host phone for search and dialing.

If an SMS POST times out, its body, SIM ID, mapping revision and idempotency key are saved in SQLite. The app asks the user to check or continue that same submission. It never creates a replacement key for an uncertain task, and it queries an existing server message rather than POSTing it again.

Opening a paired app automatically enables background SMS sync using authenticated WSS wake hints plus HTTPS event-cursor polling, a visible foreground service, reboot recovery and deduplicated inbound notifications that show the sender and message (lock screen shows only “新短信”; verification codes get a copy button and can be copied automatically). Available server calling starts incoming SIP signaling automatically from the visible app; microphone permissions are requested only when placing or answering an actual call. There are no sync or backfill switches. Notification and battery permissions remain Android-controlled; FCM and physical-device sleep-state acceptance remain pending. FoldingFeature and window metrics adapt inner/outer screens, with saved navigation, drafts and system-bar/cutout/IME insets. See [Android UI and background operation](docs/android-ui-and-background.md) for permissions and limits.

## SMS backup and archive

The backup screen exports password-encrypted archives, JSON, or SMS Backup & Restore XML. Imports merge into a separate read-only history ledger and never enqueue sends or change the paired account. See [backup and restore instructions](docs/sms-backup.md) and [两端 KVM 验证与关键界面截图](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/ui-verification/README.md).

## Project notes

- [Implementation plan](PLAN.md)
- [Implementation and data-safety notes](docs/implementation-notes.md)
- [Protocol pin](docs/protocol-pin.md)
- [SIP SDK probe gate](docs/sdk-probe.md)
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
