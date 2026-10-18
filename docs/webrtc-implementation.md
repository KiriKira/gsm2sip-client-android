# WebRTC 语音适配器

**迁移目标尚未完成。** 本批实现只替换音频，呼叫控制仍依赖 SIP/Asterisk；关闭 Asterisk 后不能完成通话。此前通过的联合验收仅证明这一过渡架构可用，不能作为“已替换 Asterisk”的证据。完整移除依赖的边界见[后续实施与验收条件](https://github.com/KiriKira/gsm2sip-server/blob/ci/webrtc-sip-debug-20261017/docs/webrtc-asterisk-removal.md)。

更新：2026-10-19，分支 `ci/webrtc-sip-debug-20261017`。

主机仅在设置页提供 **WebRTC 音频（过渡）** 开关，按配对 profile 保存。每通电话冻结其语音协议，通话期间不允许切换。`HostCallService`、Telecom、通知和用户接听/麦克风授权路径保留，实际 `SipCallEngine` 在开关开启时选择 native `RtcAudioSession`，并禁止 PJSUA2 打开 SIP 音频设备。

SIP/Asterisk 保留既有通话授权、注册、呼叫控制、占用和 winner；新语音使用 native ICE/DTLS-SRTP/Opus。SDP/ICE 通过已有身份会话的 HTTPS API 转发。主机只有在 SIP confirmed、原生连接成功和网关报告蜂窝 ACTIVE 后才报告连接完成。静音和 DTMF 路由到本次选择的媒体实现。RTC 失败明确结束本通电话，不静默回退 SIP、不再拨一次 GSM。

`rtcmedia` 使用与隔离探针相同的哈希固定 SDK 和外部 PCM Java 补丁。生产主机使用麦克风/播放设备；网关使用真实蜂窝 PCM 适配器，合成硬件实现只在 `androidTest` 中。测试可用同一 PJSIP source pin 的 x86_64 库覆盖模拟器，普通构建保留原有 ARM64 PJSUA2 AAR；不能把模拟器的合成 PCM 当作真机 modem/HAL 音频通过。

主机、网关及服务端均记录同一 `call_id` 对应的真实媒体选择和生命周期；不输出 SDP、会话令牌或 DTMF 数字。详细 API、Docker 与端口见 [服务端说明](https://github.com/KiriKira/gsm2sip-server/blob/ci/webrtc-sip-debug-20261017/docs/webrtc-audio.md)。

本批双模拟器联合验收已通过，并发布沿用原签名的 Debug Release；[结果、范围与下载](webrtc-joint-validation-20261019.md)。包括呼入/呼出双向测试音频、控制事件、TURN UDP/TCP 和 A→B→C→A 备用切换；物理蜂窝音频仍待真机。旧 `RemoteIceBuffer` / `RtcCommandCodec` 测试和独立协议探针属于组件证据，不能代替 `HostCallService → API/Asterisk → GatewayService → Telecom` 的完整通话验收。
