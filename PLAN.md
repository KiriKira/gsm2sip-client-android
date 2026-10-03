# PLAN — gsm2sip-client-android 主机 App v1

状态：实施计划；审查时仓库为空（无实现代码），功能尚未实现，SDK pin 待 M0 验证  
日期：2026-10-03  
目标仓库：`KiriKira/gsm2sip-client-android`

## 1. 目标与完成定义

主机是一台未 root 的 Android 手机；两张实际承载号码的 SIM 留在已 root 的旧 Android 网关手机中。主机端由本仓库实现一个完整的用户 App，用来查看与发送两张远端 SIM 的短信，并通过内置 SIP 引擎接听和拨打这两张 SIM 的蜂窝电话。

此仓库最终交付必须包含自己的短信、通话、通话通知和通话中界面。外部 Linphone 等 SIP 客户端只用于联调协议、呼叫和音频的诊断，不计入本项目完成度。主机无需 root，不接入 ADB/root 控制面，也不取代系统默认电话或短信应用。

网关、服务器与主机 App 三端共同实现。服务端协议以主仓库的 [protocol-v1.md](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/protocol-v1.md) 为唯一权威，开发次序以 [roadmap.md](https://github.com/KiriKira/gsm2sip-server/blob/main/docs/roadmap.md) 为准；本计划只写客户端必须遵守的行为，不复制接口字段或定义另一套状态。

V1 的完成标准：

- App 能分别选择两张远端 SIM，收发中文、多段短信，并显示服务端确认的提交、送达或失败状态。
- App 内能拨出选定远端 SIM 的号码，并接听呼入两张远端 SIM 的电话；双向语音可用。
- SIP 信令使用 TLS，媒体使用必需的 SDES-SRTP；不能降级为明文 SIP/RTP。
- Telecom 系统通话通知、锁屏接听入口、耳机/蓝牙和音频焦点可用。
- 在线状态、短信、通话及网关状态在断网、进程重建、重复事件、过期事件后能与服务端重新对齐。
- 一台网关同一时刻最多一通通话；服务端负责对所有控制端执行全局占用检查。
- 在目标 Samsung 折叠屏的外屏、内屏及折叠/展开过程中，操作布局和通话状态保持正确。
- 实际端到端验收覆盖两个 SIM 的收发短信、呼入、呼出和断线恢复；只在模拟器中验证不足以完成验收。

## 2. 系统边界与职责

| 部件 | 职责 |
|---|---|
| 本仓库 App | 账号配对；远端 SIM 选择；短信、通话历史与网关状态 UI；HTTPS/WSS API；FCM 唤醒；PJSUA2 SIP 媒体与信令；Android Telecom 接入 |
| rooted gateway | 读取/发送两张实体 SIM 的短信；接听/拨打蜂窝电话；将网关当前 SIM 映射和状态上报服务端 |
| 自建 server | 认证与授权；维护稳定 SIM 身份和映射修订号；短信及状态 API；WSS 事件；Asterisk PJSIP；推送分发；呼叫路由、占用和幂等 |

主机自己的实体 SIM 可继续用于原有蜂窝通话、短信及数据连接。它不决定远端短信或远端蜂窝号码，也不作为呼出 SIM。App 不读取主机本地短信、不申请默认短信应用角色，也不申请默认拨号器角色。VoIP 通话通过 Android Telecom 与主机自己的蜂窝通话、蓝牙耳机和音频焦点协调。

客户端的短信、网关状态、配对及呼叫控制全部走 HTTPS/WSS；只有通话信令与媒体走 SIP。网关目前的 SIP MESSAGE 行为可作为服务端过渡基线，但客户端不能直接连网关，也不应把 SIP MESSAGE 当作客户端短信 API。

## 3. 双 SIM 身份与选择规则

- 界面使用服务端提供的稳定 `sim_id` 标识远端线路，并向用户显示可区分的号码、运营商或自定义名称。
- Android `subId` 与卡槽号只作为网关观测值；不得作为客户端持久身份，也不得把卡槽 0 或系统“默认 SIM”当作兜底线路。
- 每次发短信或创建呼叫意图时，客户端提交当前的 `sim_id` 与 `mapping_revision`。服务端和网关任一方发现该修订已过期或映射不成立，必须拒绝该动作并要求刷新/重选。
- 失效时禁止静默切换到另一张卡。拒绝结果应明确指出线路映射已变化，不得让用户误以为仍在使用原号码。
- 两张远端 SIM 的收件箱和会话归属不能混淆。回复短信默认沿用该消息的 `sim_id`，发送前仍校验映射修订号。
- 每条短信提交采用服务器协议规定的幂等键；超时重试不能造成重复发送。
- UI 可在两张远端 SIM 间切换短信与拨号线路，但 V1 明确只允许网关级一通活动蜂窝通话。

## 4. 呼叫与短信流程

### 呼出

1. 用户选定远端 SIM 并输入号码。
2. App 按 `protocol-v1.md` 创建短时、单次的呼叫意图。该 API 只授权后续 SIP 拨号，本身不得触发网关打蜂窝电话。
3. 服务端返回短时授权后，App 通过已认证的 SIP/TLS 连接使用协议指定的 SIP 呼叫目标；只有服务端验证一次性授权后，才可向网关发起蜂窝呼叫。
4. 客户端内部以服务端的 `call_id` 对齐 SIP 会话、Telecom 通话、通知及历史；token 不写入普通日志、分析事件或通知文本。
5. 只有服务端确认对端应答后，客户端才向 Telecom 报告活动通话；失败、超时及用户取消均按服务端状态收敛。

### 呼入

1. 网关报告蜂窝呼入，服务端创建待接听状态；网关保持振铃，不因收到推送或客户端 SIP 注册就自动接听 SIM。
2. 服务端按 `protocol-v1.md` 向 FCM 发送高优先级、短 TTL 通知；只携带协议规定的最少不透明呼叫标识与过期信息，不含号码、短信内容、认证令牌或 SIP 凭证。
3. App 收到推送后检查系统实际给到的消息优先级，校验本地已配对身份，并通过已认证 HTTPS 查询该呼入当前权威状态。
4. 状态有效时先建立/恢复 SIP REGISTER，再按协议提交具体 call_id 和 wake_nonce 的 ready；收到匹配该 call_id 的实际 SIP INVITE 后才建立一次 Telecom 呼入 session。推送阶段可显示准备中的通知。服务端等待窗口目标不超过25秒；超时立即取消，不能让迟到事件继续响铃。
5. 用户点“接听”时由 SIP SDK 对实际 INVITE 发 200；拒接发送相应 SIP 失败响应，已建立通话挂断发 BYE。服务端据此应答/结束网关 leg，网关才接听/结束实体 SIM，不另造 HTTP answer 接口。SIP 收到的重复通知、已取消或过期呼入必须无声丢弃并幂等结束。
6. 接听、拒接、超时、远端挂断、主机本地蜂窝通话占用等竞态由一个 call session coordinator 串行处理，按服务端当前状态更新 Telecom。不能因重复回调或重连而重复 answer、重复挂断或产生第二个 SIM 呼叫。

### 短信

- 入站短信从服务端同步，按稳定 `sim_id` 分组并缓存；重复 WSS/推送事件用服务器消息 ID 去重。
- 出站短信按用户显式选择的 `sim_id` 提交；遵守统一协议中的长度、多段、编码和状态定义。
- App 使用 Room 保存会话列表、未发送草稿和已确认的历史快照；断网时可读本地缓存。发送失败的待发项需显示“待发送/失败”，网络恢复后只有用户明确重试或协议明确允许自动重试时才继续，并使用同一个幂等键。
- FCM 不携带短信正文。客户端收到新短信提示后通过已认证 API 取正文；通知默认隐藏敏感正文，可由用户设置。
- 短信正文、OTP、认证头、intent token 和 SIP 凭证不写入崩溃日志或普通调试日志。

## 5. Android 通话集成

采用 `androidx.core:core-telecom` 的 `CallsManager` 作为通话系统边界，不自行抢占音频路由，不要求成为默认电话应用。

平台版本要求与实现约束：

- Core-Telecom 的 `CallsManager` 要求 API 26 及以上；本 App 的通话支持最低 API 26。API 26 起自管 VoIP 通话能力可用。
- Core-Telecom 在 Android 13/API 33 及以下通过兼容路径使用 `ConnectionService`；Android 14/API 34 及以上使用前台服务类型支持。
- 面向 API 34+ 时按官方要求声明 phone-call foreground service 类型和权限；使用该类型时满足 `MANAGE_OWN_CALLS` 或默认拨号器角色条件。保持 `MANAGE_OWN_CALLS`，不申请默认拨号器角色。
- API 37 开始，旧的 `PhoneAccount.CAPABILITY_SELF_MANAGED` 被标记弃用；新实现使用 Core-Telecom，避免把该旧标志作为直接平台 API 的长期架构。
- `phoneCall` FGS 表示正在进行的通话，不能用来长期保活空闲 SIP socket。Android 15 起也不能从 `BOOT_COMPLETED` 广播启动此类 FGS。
- 呼入通过 Core-Telecom 登记，App 按官方指南实现对应 CallStyle 通话通知和自有通话 UI。Android 14+ 对全屏通知权限有单独限制；检测权限/设置状态，权限不允许时仍保留可操作的 CallStyle/高优先级通知，不能假设必然弹出全屏 Activity。
- 以 Core-Telecom 管理音频端点和系统音频焦点；不同时调用 `AudioManager#setCommunicationDevice` 或手动蓝牙 SCO 管理，以免与 Telecom 冲突。

### 后台与唤醒

后台呼入采用推送唤醒，不以永久后台 SIP 注册或自建 TCP/WebSocket 心跳作为可靠方案：

- GMS Android 用 FCM 处理休眠态呼入。仅对确实需要响铃的入站呼叫使用高优先级推送；收到消息后检查其最终优先级并快速展示/注册，处理前台服务启动失败和超时。
- Android Doze 会暂停常规网络；高优先级 FCM 可以尝试唤醒并给出短暂处理窗口，但 Google 可能将未对应用户可见通知的高优先级消息降为普通优先级。因此实现必须处理推送迟到、丢失、重复和降级。
- FCM 是外部推送传输依赖，即使 SIP/API/数据库全部自建，也不能称“完全不依赖第三方”。只发送随机 call_id 和 expiry，权威号码及通话状态由客户端登录后从自建 server 获取。
- Firebase Cloud Messaging 的 Android 客户端要求 Google Play 服务。没有 GMS 的设备上，前台运行时仍可使用 HTTPS/WSS；休眠态的可靠来电唤醒没有通用自建 socket 替代方案。若后续要求支持无 GMS，需增加对应厂商推送适配并逐台验收，或明确作为尽力而为模式；不能承诺一般 Android 上无条件可靠后台响铃。
- 用户强制停止 App、关闭通知、撤销系统允许、厂商深度休眠或网络断开时，来电可能错过。App 应暴露推送/通知/电池限制诊断状态及最后同步时间，不谎报在线可达。

## 6. SIP SDK 选择与安全门槛

首个技术验证以 **PJSUA2** 为优先；最终二进制只选一个 SIP 引擎。SDK 是 SIP/RTP 引擎，不替代 Android Telecom、推送、呼叫 UI、认证或协议状态机。

| 候选 | 有依据的优势 | 主要代价与决策 |
|---|---|---|
| PJSUA2（先验证） | 官方提供 Android Java/Kotlin 示例、SWIG/JNI 与 NDK 构建指南；提供 TLS、SRTP、SDES，以及 `PJMEDIA_SRTP_MANDATORY` 和要求 TLS 信令的设置，能匹配本项目的 Asterisk 路由 | 需要自行封装 native build、调用生命周期、音频与 Telecom；需按选定发行版/提交做安全公告复核，并证明服务端证书链、hostname/SNI 和 TLS 失败策略正确；PJSIP 是 GPL-2.0-or-later 或商业授权双许可，必须确定本仓库发行方式与全部依赖许可兼容 |
| Liblinphone（备选） | 高层 Java API 和完整 SIP 通话/媒体 API，Android SDK 由 Linphone 自己使用 | 官方资料对 SDK 许可表述不一致：Java API 文档标 GPLv3，FAQ/Liblinphone 项目资料标 AGPLv3；采用前须对准确版本、构建产物和依赖拿到明确许可结论，必要时联系厂商取得商业授权。只在 PJSUA2 的安全/构建/Telecom probe 失败或许可证不合适时单独评估 |
| baresip / libbaresip（不作首选） | 当前 core 项目为 BSD-3-Clause，列出 Android 8+、TLS、SDES-SRTP 和可嵌入 `libbaresip` | 官方 `baresip-android` 集成示例仍引用 NDK r14-r17、baresip 0.5.9 和 OpenSSL 1.1.0h，不能直接作为现代 Android SDK。其模块化 toolkit 给 App 留下较多 JNI/模块/生命周期工作。若前两者受阻，可单独做小型 modern NDK spike；星数不作为准入依据 |

当前文档核查不构成最终 SDK pin。实施时需锁定**已经审查和测试的发行版/commit、NDK、OpenSSL、SDES/SRTP 与 codec 配置**；CI 必须可从 pin 重建 arm64-v8a 原生库并记录来源、许可证和 SHA-256。不得在正式版本依赖 `latest` 或未锁定的 master。

PJSIP 作为首个 probe 有几项必须先解决的安全问题：

- PJSIP 官方 TLS 指南指出客户端 `verify_server` 默认是 false；生产配置必须显式为 true，检查 CA 链和 SIP URI hostname，确保 SNI 为服务器域名；无效 CA、过期证书、hostname 错配均必须导致连接失败。
- 官方公告报告了 <=2.17 DNS 响应验证不足，以及 2.17 及更早版本的 TLS SubjectAltName 内嵌 NUL 导致 hostname 验证绕过问题。正式 pin 必须审查官方后续修复状态；未找到可信修复时不得把“TLS 已启用”当作安全验收通过。若官方建议改用系统 resolver，应核对 PJSIP 实际配置确实绕过 bundled async resolver，并对 DNS/SRV 行为做回归测试。
- 对上述漏洞的修复只能采用 pjproject 官方发布的版本或官方上游提交，并锁定准确 pin 与来源；不引入第三方 fork 或未核实的外部补丁。若官方修复尚不可用，且未有上游确认的有效规避措施时，该 SDK 暂不能通过发布门槛。
- PJSIP Android 官方指南说明 Android 15 的 16 KB 页设备需采用支持方式构建 native libraries；打包产物必须检查 16 KB ELF 对齐/运行加载。
- 生产媒体只开放 SDES；设 `SRTP_MANDATORY`、SDES keying、TLS 安全信令。对协商到 plain RTP 或不支持 SDES 的服务端必须失败，不允许静默降级。
- PJSIP 通话中用 Call-ID 与服务端 `call_id` 建立唯一映射。SIP 事件桥接到 Kotlin 层时只发送序列化事件，所有 native 回调需线程安全，并避免在回调内阻塞网络、Room 或 Compose 主线程。

### 必做 SDK probe

在开始完整 UI 前做一个可重复的最小 Android probe，证明候选 SDK 在真实设备和自建 Asterisk 上能：

1. 以 `arm64-v8a` 构建并运行在目标 Fold 的 Android/One UI 版本上，包括该系统版本及 16 KB 页模式的 native loader 检查。
2. SIP/TLS 注册及呼叫成功；CA 不受信、域名不匹配、证书过期和握手失败均正确失败，确认 SNI。
3. SDES-SRTP 强制协商成功；抓取 SDP 证明有 `a=crypto` 且使用服务端要求的 cipher；plain RTP 服务端必须拒绝。
4. 双向收发音频、蓝牙耳机与扬声器切换、屏幕锁定、主机蜂窝电话插入/结束时遵守 Telecom 音频端点回调。
5. App 进入后台、进程被回收、收到 FCM 后恢复到期的通话，且不存在未授权时长的永久 SIP socket。
6. 核对依赖清单、license 文件及构建许可证；选择一个 SDK，不在最终 App 同时打包两套 SIP 引擎。

probe 失败时再评估 Linphone；不得同时引入 PJSUA2 和 Linphone 以“以后再选”，避免双重 native stack 与媒体/许可维护负担。

## 7. UI、数据与 Samsung 折叠屏

建议 Kotlin、Jetpack Compose、Room；所有网络和 SIP 回调经单一 repository/state reducer 汇入可恢复的界面状态，通话活动状态保存在长生命周期 call session owner 中，不由 Activity 持有。

- 依据当前 App window 的宽度与姿态做自适应布局，不按机型硬编码“外屏/内屏分支”尺寸。外屏用紧凑布局；内屏可在短信会话列表/内容或网关/状态页采用双栏。
- 支持折叠/展开、半开姿态、横竖屏、分屏/窗口缩放。状态变化不应重新创建 SIP account 或丢失通话 session。
- 遵守 display-cutout 与 edge-to-edge insets；通话按钮、短信发送键和拨号键不能被摄像头孔、系统栏、键盘、折痕/铰链遮住。
- 目标设备测试 Samsung Fold 内屏和外屏：收到来电时合盖/开盖、通话中切屏、旋转、展开/合上、接蓝牙耳机时来电 UI 和接听/挂断操作仍正确。
- 可读性：远端 SIM 的名称/末四位在短信发送页和呼叫确认页常驻显示，避免用户看错号码；过期映射时强制重选。

## 8. 离线恢复、幂等和故障呈现

- HTTP/WSS 重连后先请求服务端当前快照，再消费增量事件；WSS 事件视为提示，可重复、乱序或缺失，不能覆盖服务端当前权威状态。
- 每个通话以 `call_id` 为唯一 session key，保存服务器状态修订/版本；同一 call_id 的重复 FCM、SIP INVITE、Telecom answer/disconnect callback 不重复执行动作。
- 操作超时不代表服务器未执行。客户端按幂等键查询当前结果后再重试，不得新造幂等键造成重复短信或重复呼叫。
- 网络切换、SIP 断线、服务器不可达时，活动通话显示真实的重连/失败状态；清理失效 native transport。来电已过期或服务端撤销则取消 Telecom UI。
- 如果同一账号在多个 App 实例触发呼出，或已有网关通话占用，服务端拒绝新呼叫；App 显示忙线原因，不在另一张 SIM 上自动重试。
- 断网时允许读缓存和撰写草稿；不展示未经服务器确认的“已送达”或“正在呼叫”。
- 认证凭据保存在 Android Keystore 保护的存储中，支持撤销单一主机 App 实例；SIP 密码/短期凭证不写入日志，不用相同长期凭证复用管理 API 与 SIP。

## 9. 里程碑

执行次序与三端共用的 `plans/server/roadmap.md` 保持一致。

### M0 — 风险验证

- 先用最小 Android probe 验证唯一候选 SDK 的 TLS/SRTP、证书失败路径、锁屏呼入、Core-Telecom、音频路由和 native 构建。
- 核对候选 SDK 的官方安全修复状态和许可证；只锁定经过验证的官方 release/commit，不同时集成两套 SDK。

验收：目标设备上 SDK 可构建；不受信 CA/hostname 错误被拒绝；SRTP 强制生效；Telecom 与锁屏呼入的最小纵向切片可运行。M0 无法通过时先报告平台或 SDK 阻塞，不绕过安全检查。

### M1 — 契约和基础

- 初始化 Kotlin/Gradle 工程、CI、静态检查、测试分层、版本与依赖清单。
- 按 `protocol-v1.md` 实现配对、凭据保护和远端两卡状态页；mock fixtures 以主仓库为准，不另造客户端契约。
- 展示网关健康、最后心跳、两张稳定 SIM 身份、当前映射修订、网关联网与注册状态；支持断网状态和快照重同步。

验收：三端使用相同协议版本；远端映射变化后 App 要求刷新，旧修订请求被拒绝且不会切换到另一张卡。

### M2 — 双向短信

- 实现会话分组、收件箱、中文/emoji/多段消息、回复、发送状态、搜索、通知隐私和 OTP 复制。
- 使用服务端 HTTPS/WSS 消息协议；Room 去重、离线缓存与幂等重试。

验收：两张 SIM 分别收发短信；响应丢失后不会重复发送；网关断线恢复后状态一致。

### M3 — 前台完整通话

- 通过 M0 安全与许可门槛后集成唯一 SIP 引擎。
- 打通协议定义的出站呼叫授权、SIP/TLS、mandatory SDES-SRTP、Asterisk 和 Core-Telecom。
- 接入 CallStyle 通知、锁屏操作、全屏权限状态、系统音频端点。

验收：App 内完成两张远端 SIM 的呼出、接听、挂断、DTMF、蓝牙/Telecom 流程；无需外部 SIP 客户端。

### M4 — 后台和恢复

- 实现 FCM 注册、token 更新/撤销、高优先级呼入通知、authenticated fetch，并严格遵守协议的呼入期限。
- 建立服务端 call_id 与 SIP call、Telecom 通知的唯一状态协调器。
- 处理推送降级/重复/延迟/过期、通知操作竞态与网络丢失。

验收：休眠态真实来电可以唤醒并响铃；只有点接听才触发远端 SIM answer；迟到推送不响；重复事件不重复答接；无法唤醒时有明确诊断。

### M5 — 持续运行与发布

- 在目标 Samsung Fold 上测试封面屏、内屏、折叠姿态、蓝牙/扬声器、系统蜂窝电话打断、横竖屏、Doze、网络切换。
- 和网关/服务器组合跑短信及语音故障测试，覆盖 24 小时熄屏及 72 小时压力/掉线运行、端点重启与证书轮换。
- 更新 GMS/FCM、通知、全屏通知状态、省电策略和无 GMS 限制说明。
- Release 构建只包含经过验证的 SDK pin、arm64-v8a native libraries、来源和 license notice；检查构建可复现、16 KB page-size 兼容、签名与依赖校验。
- 完成 TLS/SRTP 失败用例、日志敏感字段扫描和整套端到端验收。

验收：Release APK 在目标 Samsung Fold 真实安装；两张远端 SIM 分别完成短信和 App 内通话闭环；App 进程重建/切网后状态可恢复；不存在需要外部 SIP App 才能达标的功能。

## 10. 关键测试清单

### SIP 与安全

- CA 不受信、hostname 错误、过期证书、错误 SNI、DNS 解析失败。
- Asterisk 只接受 TLS+SDES-SRTP；无 SRTP 的 SDP、仅 RTP、错误 cipher 都必须失败。
- SIP 服务器拒绝/401/403/480/486/5xx、注册过期、证书轮换、VPN/Wi-Fi/蜂窝数据切换。
- call token 过期、复用、跨账号复用、SIM mapping revision 过期均由服务端拒绝。
- 验证日志中不出现密码、token、短信正文、SDES key。

### 状态与稳定性

- 多次相同 push、乱序的 WSS 更新、重复 SIP INVITE、迟到的 BYE、接听与取消竞争、客户端离线接听竞争。
- SMS 请求成功但响应丢失后的幂等恢复。
- 同时两端发起呼叫、不同客户端同时应答、设备已有普通蜂窝通话。
- 屏幕锁定、系统进程回收、Doze、高优先级推送被降级、通知关闭、App force-stop、无 GMS。
- 服务器短暂不可用、网关重启、主机重启、DNS/TLS 证书更换。

### UI 与设备

- Fold 封面屏/内屏、折叠/展开、半开、横屏、display cutout、安全边距、分屏。
- 耳机、蓝牙切换、免提、来电时 SIM 电话插入、通话过程中有网络切换。
- 不能出现按钮触控区被打孔、系统栏、铰链区或软件键盘遮挡。

## 11. 与旧计划的调整

`[原网关旧计划](https://github.com/KiriKira/gsm2sip/blob/477b7e349891928dda7b87a9b55b72c52e7eab87/PLAN-selfhosted-gateway.md)` 把主机 App Phase 1 定为短信/状态、通话历史与启动外部 SIP 客户端，并把 Phase 2 的 SIP 集成列为“可选”。这与本项目现在要求的“自建 App 内完整双 SIM 短信与通话”不一致。

新计划仍保留外部 SIP 客户端作为临时调试工具，但将 PJSUA2/Linphone 的内嵌验证、Android Telecom 集成、后台呼入、通话中界面及真实设备验收纳入 V1 必做项。外部客户端不能代替任一完成门槛。

旧计划把网关到服务端的 SMS 流程作为 SIP MESSAGE 初版；跨仓库统一协议现由 HTTPS/WSS 承载 App 的短信、状态与控制，网关的旧 SIP MESSAGE 暂作迁移兼容基线。具体端点、字段和时序统一以 `plans/server/protocol-v1.md` 为准，本文件不复制协议定义。

## 12. 官方资料与选型依据

### Android

- [Core-Telecom 官方实现指南](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom) — `CallsManager`、`MANAGE_OWN_CALLS`、通话状态、音频端点、API 33/34 前后台差异。
- [Telecom framework 概览](https://developer.android.com/develop/connectivity/telecom) — standalone self-managed VoIP 和由系统管理的 PSTN/SIM 电话角色边界。
- [Core AndroidX Core-Telecom 发布记录](https://developer.android.com/jetpack/androidx/releases/core) — 1.0.1 稳定版、1.1 beta 发布状态及修复记录；选择依赖时重新核对最新稳定版本。
- [CallsManager API](https://developer.android.com/reference/kotlin/androidx/core/telecom/CallsManager) — `CallsManager` API 26 起可用。
- [phoneCall 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#phone-call) 和 [Android 14 前台服务类型要求](https://developer.android.com/about/versions/14/changes/fgs-types-required) — FGS 类型、声明权限与运行条件。
- [后台启动前台服务限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) 和 [Android 15 FGS 变更](https://developer.android.com/about/versions/15/changes/foreground-service-types) — 高优先级 FCM 豁免及启动限制。
- [通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission) 与 [Android 14 Full-screen Intent 变更](https://developer.android.com/about/versions/14/behavior-changes-14#secure-full-screen-intent-notifications)。
- [Doze 与 App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) — 空闲态网络暂停与 FCM 的推荐唤醒路径。
- [后台自启动 FGS 限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — FCM 高优先级消息可能被降级，启动后需检查实际消息优先级。
- [Firebase Android 对 Google Play 服务依赖](https://firebase.google.com/docs/android/android-play-services) 与 [FCM Android 客户端前置条件](https://firebase.google.com/docs/cloud-messaging/android/get-started) — Cloud Messaging 需要 Google Play 服务。
- [折叠屏适配指南](https://developer.android.com/develop/adaptive-apps/guides/foldables/learn-about-foldables)、[自适应窗口尺寸](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)、[显示开孔与安全区](https://developer.android.com/develop/ui/compose/system/cutouts)。

### SIP SDK 与安全

- [PJSIP Android 开发总览](https://docs.pjsip.org/en/2.17/get-started/android/index.html) 与 [Android 构建步骤](https://docs.pjsip.org/en/2.17/get-started/android/build_instructions.html) — 官方 Android Java/Kotlin、JNI/SWIG/NDK 样例及 16 KB 页说明。
- [PJSIP Android SIP/VoIP 示例](https://docs.pjsip.org/en/2.17/get-started/android/java-sip-client.html) — Java 样例及 TLS 注册/呼叫；Android App 层需另做自己的 Telecom 与推送逻辑。
- [PJSIP SDES/DTLS-SRTP 指南](https://docs.pjsip.org/en/latest/specific-guides/security/srtp.html) — mandatory 模式、SDES、`srtp_secure_signaling` 配置及不得降级。
- [PJSIP TLS 指南](https://docs.pjsip.org/en/latest/specific-guides/security/ssl.html) — hostname/SNI、`verify_server` 和验证失败行为。
- [PJSIP 项目许可证说明](https://docs.pjsip.org/en/2.17/overview/license_pjsip.html) 与 [发行版本](https://github.com/pjsip/pjproject/releases)。
- [PJSIP DNS 响应验证公告 GHSA-pvmg-ph43-54r2](https://github.com/pjsip/pjproject/security/advisories/GHSA-pvmg-ph43-54r2) — 2.17 及以下的异步 DNS response validation 问题，公告标注暂无 patched release；正式 pin 前重新核查。
- [PJSIP TLS hostname 验证公告 GHSA-382p-87mh-r3q8](https://github.com/pjsip/pjproject/security/advisories/GHSA-382p-87mh-r3q8) — 证书 SAN 中嵌入 NUL 的 hostname validation 问题，正式 pin 前重新核查修复。
- [Liblinphone Java API](https://download.linphone.org/releases/docs/liblinphone/latest/java/) 与 [Linphone licensing FAQ](https://www.linphone.org/en/faq/) — API 特征与不同官方页面的 license 表述需在选型时消歧。
- [baresip 当前项目说明](https://github.com/baresip/baresip) 与 [官方 baresip-android 构建示例](https://github.com/baresip/baresip-android) — 当前 core 的平台、TLS/SDES 和许可证，与 Android 示例实际依赖年代。

## 建议代码模块与首次发布

- `app/`：Compose UI、导航、远端SIM选择及通知权限/推送诊断；`data/`：HTTPS/WSS、Room、Keystore凭据和同步游标。
- `calls/`：一个 CallSessionCoordinator 管理服务器 call_id/state_revision 与实际 SIP INVITE、Telecom callbacks；`sip/`：唯一经验证的 PJSUA2 或替代 SDK 适配器及 native build pin；`push/`：FCM token、重复/迟到事件处理。初期可作为包保持单 module，勿先扩张大量Gradle modules。
- 权限：主机不申请 SEND_SMS/READ_SMS/CALL_PRIVILEGED/root；按实际API声明 RECORD_AUDIO、MANAGE_OWN_CALLS、通知/通话FGS、蓝牙等必要权限，拒绝某项时提供明确状态。麦克风权限在可见配对/设置流程中取得，不能指望锁屏后台首次弹授权。
- 配对后安全保存一次性 SIP bootstrap；凭据丢失走受控轮换，不重放配对码。依赖版本、原生架构、16KB页和信任CA按probe锁定。
- 会话分组键至少为 gateway_id+sim_id+对端号码，未知SIM事件独立标注；禁止仅按号码合并两卡对话。OTP复制与搜索是客户端功能，不上传给外部分析服务。
- 固定应用ID和 release signing key（APK签名keystore私钥存受控CI secret或安全发布环境），连续release可直接升级；不用debug key，每次发布验证签名证书指纹，不重新生成keystore。首个主机release以arm64-v8a为目标，其它ABI需明确设备测试后添加。
