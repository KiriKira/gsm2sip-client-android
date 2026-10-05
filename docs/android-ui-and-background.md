# Android 界面与后台运行

本次两端采用原生 Kotlin Views + **Material 3 Expressive**，依赖 Material Components **1.14.0**（实施时核对 Google Maven 的最新稳定版），使用 `Theme.Material3Expressive.DayNight.NoActionBar`。支持明暗主题、Android 12+ 动态取色，较早系统使用语义色板。Material 卡片、按钮、输入框、对话框及线路选择保留现有明确选卡、草稿与安全重试语义。按窗口宽度控制内容和系统栏/键盘 insets，不以机型判断布局。

## 折叠屏、窗口与安全区域

主机使用稳定版 AndroidX WindowManager `1.5.1` 的 `WindowMetrics` 和 `FoldingFeature`，按当前 Activity 窗口实时排版，不判断 Galaxy 型号或假定某个内外屏分辨率。主界面使用“电话 / 短信 / 设置”三个底栏目的地；拨号键盘与短信对话各自位于独立页面。合拢、展开、旋转、横屏和键盘显示时都要检查页面内容与系统安全区，并保留号码和短信草稿。

窗口使用 edge-to-edge 布局，并同时避开系统栏、显示 cutout 与键盘 IME。合拢、展开、旋转及窗口尺寸变化时重新计算安全区和页面内容，摄像头 cutout 与折痕区域不覆盖可点按控件。窗口变化时保留输入、焦点和滚动位置；短信收件人与正文写入所选 SIM 的本地草稿。未完成的配对表单由 ViewModel 保留在配置变更期间，进程恢复只保存服务器地址和设备名，配对码只留在内存中且不写入 Bundle 或磁盘；配对请求也由该 ViewModel 持有，因此折叠屏重建 Activity 时不会取消请求或重复提交。通话会话继续由现有通话 runtime/service 持有，不依赖 Activity 是否重建。

构建使用 compileSdk 35、AGP 8.7.2、Kotlin 2.1.20、Gradle 8.9；minSdk 26、targetSdk 34。升级编译 SDK 用于满足新组件依赖，不把旧手机按型号排除。Compose 并非采用 Material 3 的必要条件；当前在现有 Views 上实现该设计体系。

## 后台同步与操作入口

配对后后台短信同步自动运行并显示常驻通知。Android 通知授权与电池策略由系统设置管理；系统或用户停止服务后，不靠短信广播或开机广播自行恢复。Android 13+ 任务管理器主动停止通过最近 `ApplicationExitInfo` 的用户停止记录阻止自动恢复；恢复需要用户重新从桌面打开应用。

WSS `/v1/ws` 只接收 `sync_required` 提示，不承载短信正文、执行命令或通话信令；随后用已认证 HTTPS 拉取权威数据。Bearer 仅放请求头，使用系统 CA 与 hostname 校验。断开后退避重连，并保留 HTTPS 定时补齐；回调必须符合当前配对身份和连接代次。网络切换时重新同步，工作唤醒锁有时间上限并在结束后释放。

主机配对后自动运行 `remoteMessaging` 前台服务（Android 14+），用于跨设备短信，不作为空闲 SIP 保活。服务状态与后台接收设置仍可查看；用户不需要另行开启自动同步开关。首次历史回填不提醒，后续入站按服务器消息 ID 去重、合并通知，锁屏隐藏号码和正文。后台只查询消息与任务状态，不重发不确定的短信 POST。缓存仍按配对账户和设备隔离。

电话页显示通话记录，拨号键盘从单独入口打开。系统联系人权限只用于查找候选姓名和号码；未授权时仍可直接输入号码。短信页先显示按会话分组的历史记录及 SIM 标记；打开会话或新建短信后，在独立编辑页中选择 SIM 和填写正文。设置保留服务器与配对表单、已配对设备管理和“短信备份与归档”入口。

## 需要允许的权限与真实限制

1. 主机配对后自动启动后台短信同步；Android 通知权限仍由系统控制，未允许通知时应用会显示相应状态。
2. 对需要持续接收的应用允许电池优化豁免，并在系统提供该功能时允许后台运行/自启动。只从用户操作打开系统授权入口，不自动授权。
3. 无障碍不是本项目后台同步的前提。无障碍服务不提供 Doze 网络豁免，也不能保证进程不被结束，暂不为保活添加空服务。
4. 远程语音使用 self-managed Telecom，需要 `MANAGE_OWN_CALLS`；实际通话才启用带 `phoneCall` 类型的前台服务，麦克风类型只在用户接听或已接通的出站呼叫中开启，并要求 `RECORD_AUDIO` 运行时权限。配对会话进入前台且服务器提供 SIP 服务时，客户端自动启动来电信令服务；空闲信令不使用麦克风。Android 14 的全屏来电权限被拒绝时，客户端保留 CallStyle 通知和普通来电界面入口；不会自动接听。

普通前台服务不能消除 Doze 网络限制；电池豁免有助于无 GMS 的 WSS 路线，但网络故障、系统/厂商策略仍可能延迟。系统设置中的强行停止会阻止正常后台启动；用户重新从桌面打开应用后，配对会话会重新校验并按服务器能力恢复服务。服务不会靠短信广播或开机广播绕过系统的停止状态。

主机已集成固定版本的 PJSUA2 SIP 引擎、已验证的 TLS 信令和强制 SDES-SRTP。独立的 `HostCallService` 在配对会话可用且应用页面处于前台时自动保持 SIP 来电信令；不依赖短信 `remoteMessaging` 服务保活。客户端只接受真实 SIP INVITE，并以 INVITE 内业务 call UUID 向认证 API 核对后再提交 self-managed Telecom 呼入；接听仍须用户操作。真实通话使用 CallStyle 通知和 `phoneCall` 前台服务，麦克风服务类型只在用户接听或出站 SIP 呼叫确认后启动。服务监测默认网络变化；网络恢复时先让 PJSUA2 更新 SIP transport，再从认证 API 重新核对当前通话，确认仍有效后才恢复本地媒体。默认网络中断或 SIP 注册持续失败超过 30 秒会结束当前通话；SIP 注册的临时错误由 SDK 自动重试，凭据拒绝会关闭来电接收并提示检查账号配置。拒绝、挂断、静音、DTMF 和 Telecom 音频路由都由通话服务处理，不调用外部 SIP 拨号器或 Android SIM 拨号器。

此实现不代表端到端来电时效或媒体互通已验收：服务器 SIP/Asterisk 路由、网关接听、真实 TLS/SRTP 双向音频、耳机切换、进程恢复、Doze、蜂窝来电打断和真机锁屏通知仍需设备验证。WSS 连通或后台进程存活不能替代这些检查。

当前无 GMS 路线不依赖 Firebase。GMS 设备可后续增加 FCM 高优先级呼入唤醒，校验时效并通过 HTTPS 查询呼叫状态；FCM 的注册、推送凭据和呼叫流程尚未实现。两台真机仍需测试熄屏、Doze、重启、Wi-Fi/移动网络切换、通知拒绝、用户停止以及两卡短信；本机测试不能证明真实锁屏来电及时性。

## 官方依据

- [Material Components 发布](https://github.com/material-components/material-components-android/releases) / [稳定版本元数据](https://dl.google.com/dl/android/maven2/com/google/android/material/material/maven-metadata.xml)
- [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Doze 与电池优化](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [用户停止前台服务](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
- [Android 通知权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Core-Telecom](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom)
- [FCM Android 前提](https://firebase.google.com/docs/cloud-messaging/android/client)
