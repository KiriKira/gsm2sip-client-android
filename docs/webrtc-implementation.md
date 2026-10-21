# WebRTC 语音与独立呼叫控制

2026-10-19：独立 Go/HTTPS 呼叫控制与原生 WebRTC 已完成两台持续运行的 KVM 模拟器、三个真实 Docker 节点联合验收，并发布沿用原签名的 Debug Release。纯模式三节点不运行 Asterisk，SIP/ARI 抓包为 0；呼入/呼出双向测试 PCM、呼叫事件、TURN UDP/TCP、控制/媒体故障与 A→B→C→A 切换通过。WebRTC 开关关闭时的 SIP 来电、开启时的混合来电事件另行通过。物理蜂窝 HAL 和真实手机音频仍待真机验收。 [完整报告与下载](webrtc-independent-validation-20261019.md)。

设置页的 **WebRTC 语音** 开关按配对 profile 保存；拨号页保留联系人建议。
打开后新呼出使用 `RtcCallEngine`，通过已认证 Go/HTTPS 业务控制驱动网关真实
蜂窝执行和原生 ICE/DTLS-SRTP/Opus；不读取 SIP 凭据，不等待 SIP 注册或 confirmed。
通话页显示并冻结实际媒体和控制协议，终态保留；通话中不能更改开关。
媒体连接及网关真实蜂窝 ACTIVE 到齐才报告 ACTIVE。失败明确结束本通电话，
不回落 SIP、不重拨蜂窝电话。

迁移期服务端默认 `SIP_COMPATIBILITY_ENABLED=true`。关闭开关保留旧 SIP；
打开的新 Host 也可通过 `CompatibilityCallEngine` 接收服务端选定的完整 SIP 来电
分支。存在旧版或关闭 RTC 的授权候选时，整个来电 fork 选择 SIP，实际通话显示 SIP。
新呼出仍使用 Go/RTC。全部升级或撤销旧身份后可关闭服务端 SIP 兼容，纯 RTC 节点
不创建 PJSUA2、Asterisk 或 ARI。既有身份、SIM、短信和节点数据继续使用原存储。

原有 Telecom、前台服务、通知、接听/麦克风授权和逐节点会话保留。引擎绑定实际
配对节点与会话，旧节点回调不能停止新节点的监听。unknown 蜂窝占用等待网关真实
结束事件确认，不因控制租约过期自动释放并重拨。

`rtcmedia` 使用固定哈希的 SDK 和外部 PCM 桥。合成 PCM 实现仅在 `androidTest`，
发布 APK 不包含测试音频后端。Host 发布包为 arm64-v8a，模拟器使用同一 PJSIP
source pin 的 x86_64 构建。测试 PCM 只能证明软件闭环，不能代表物理 modem/HAL。

协议及可配置 TURN 端口见[服务端 RTC 控制 v1](https://github.com/KiriKira/gsm2sip-server/blob/ci/webrtc-sip-debug-20261017/docs/rtc-call-control-v1.md)。
