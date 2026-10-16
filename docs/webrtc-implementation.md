# WebRTC 实施状态

更新：2026-10-16，分支 `feat/webrtc-migration`。

现有 HostCallService 通过 CallEngine/SipCallEngine 继续使用 SIP；页面、通知、Telecom 和用户设置的运行路径不变。
本仓库尚未加入 WebRTC SDK、WSS 客户端或 WebRTC 引擎，也未声明 WebRTC 已可用。
三端总计划与实时进度分别见服务端 `docs/plan-webrtc.md`、`docs/webrtc-migration-status.md`。

本批新增 `com.callagent.rtc.RemoteIceBuffer`：绑定节点/业务 call/leg/attempt/generation，验证远端设备，
在对应 native setRemoteDescription 成功前有界缓存 ICE，用实例/代次 token 隔离迟到回调。
处理重复/冲突 sequence、过期、EOC、资源上限和关闭，不能由新 generation 重开已关闭缓冲。
源码/测试暂与网关保持字节一致，由服务端 `scripts/check_rtc_contract.py` 做交叉校验。

组件没有服务/UI 调用点和 Android/native 依赖；它不是完整的信令客户端。
未来适配器必须保持同一 PeerConnection 串行执行器、执行前代次检查、服务器到期时间/本地时钟转换、
认证及授权续期、重连完整性检查、用户接听授权与音频焦点边界，不能由候选或 SDP 直接打开音频。

后续补充 `RtcCommandCodec` 与五项测试，使用从服务端复制的六种 canonical 命令 fixtures。
严格校验重复/未知字段、null、宽松 JSON、错误数值类型、UTF-8 字节上限、Unicode 代理对和 kind/payload 对应关系；
编码器也校验内部命令，不能输出带错误 SDP/ICE 的 close。当前没有网络调用点或服务端 snapshot/ACK 适配。

最新 `:app:assembleDebug :app:testDebugUnitTest` 通过：186 项测试，0 失败/错误/跳过，含 10 项缓冲竞态和 5 项严格 codec 测试。
构建产物未包含 WebRTC native 库。未执行真机通话、native PCM、TURN、UI 截图或仪器验收。
网关仓库的 `probes/webrtc/` 提供独立媒体探针；当前只完成构建和工具/JVM 测试，不代表蜂窝音频已验证。
