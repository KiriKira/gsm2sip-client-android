# Android 界面与后台运行

本次两端采用原生 Kotlin Views + **Material 3 Expressive**，依赖 Material Components **1.14.0**（实施时核对 Google Maven 的最新稳定版），使用 `Theme.Material3Expressive.DayNight.NoActionBar`。支持明暗主题、Android 12+ 动态取色，较早系统使用语义色板。Material 卡片、按钮、输入框、对话框及线路选择保留现有明确选卡、草稿与安全重试语义。按窗口宽度控制内容和系统栏/键盘 insets，不以机型判断布局。

构建使用 compileSdk 35、AGP 8.7.2、Kotlin 2.1.20、Gradle 8.9；minSdk 26、targetSdk 34。升级编译 SDK 用于满足新组件依赖，不把旧手机按型号排除。Compose 并非采用 Material 3 的必要条件；当前在现有 Views 上实现该设计体系。

## 后台同步与操作入口

用户可以查看后台运行、通知、电池优化、连接状态和最后同步时间，并进入系统通知或电池优化设置。前台服务显示常驻通知及停止按钮；应用内停止会持久保存，开机和系统重建不会自行覆盖。Android 13+ 任务管理器主动停止通过最近 `ApplicationExitInfo` 的用户停止记录阻止自动恢复，用户明确再次开启后才恢复。

WSS `/v1/ws` 只接收 `sync_required` 提示，不承载短信正文、执行命令或通话信令；随后用已认证 HTTPS 拉取权威数据。Bearer 仅放请求头，使用系统 CA 与 hostname 校验。断开后退避重连，并保留 HTTPS 定时补齐；回调必须符合当前配对身份和连接代次。网络切换时重新同步，工作唤醒锁有时间上限并在结束后释放。

主机默认关闭后台同步；用户在可见页面开启后运行 `remoteMessaging` 前台服务（Android 14+），用于跨设备短信，不作为空闲 SIP 保活。首次历史回填不提醒，后续入站按服务器消息 ID 去重、合并通知，锁屏隐藏号码和正文。后台只查询消息与任务状态，不重发不确定的短信 POST。缓存仍按配对账户和设备隔离。

## 需要允许的权限与真实限制

1. 两端允许通知；主机开启后台同步后才能提供后台新短信提示。
2. 对需要持续接收的应用允许电池优化豁免，并在系统提供该功能时允许后台运行/自启动。只从用户操作打开系统授权入口，不自动授权。
3. 无障碍不是本项目后台同步的前提。无障碍服务不提供 Doze 网络豁免，也不能保证进程不被结束，暂不为保活添加空服务。

普通前台服务不能消除 Doze 网络限制；电池豁免有助于无 GMS 的 WSS 路线，但网络故障、系统/厂商策略仍可能延迟。系统设置中的强行停止会阻止正常后台启动，需要用户重新打开并明确开启。已停止的服务不会靠短信广播或开机广播绕过用户选择。

**当前实现是短信/状态后台同步，尚未实现主机 SIP SDK、完整 Asterisk 呼叫路由、Core-Telecom、CallStyle 通话通知及锁屏接听。** 不把 WSS 连通或进程常驻当作来电验收完成。后续实际通话使用对应 Telecom/phoneCall 与麦克风权限；不使用空闲 microphone/phoneCall FGS 保活，也不开机录音。

当前无 GMS 路线不依赖 Firebase。GMS 设备可后续增加 FCM 高优先级呼入唤醒，校验时效并通过 HTTPS 查询呼叫状态；FCM 的注册、推送凭据和呼叫流程尚未实现。两台真机仍需测试熄屏、Doze、重启、Wi-Fi/移动网络切换、通知拒绝、用户停止以及两卡短信；本机测试不能证明真实锁屏来电及时性。

## 官方依据

- [Material Components 发布](https://github.com/material-components/material-components-android/releases) / [稳定版本元数据](https://dl.google.com/dl/android/maven2/com/google/android/material/material/maven-metadata.xml)
- [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Doze 与电池优化](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [用户停止前台服务](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
- [Android 通知权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Core-Telecom](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom)
- [FCM Android 前提](https://firebase.google.com/docs/cloud-messaging/android/client)
