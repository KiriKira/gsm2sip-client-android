# Reception v2：独立电话订阅、短信同步与按需唤醒

状态：精简协议提案，尚未实现；已按 PR #37 的 Claude 评论修订。配套实施顺序见 [PLAN](reception-v2-PLAN.md)。本提案中的 MUST 表示实现必须满足的约束；所有时间与并发默认值是本项目的初始设计值，不是参考项目的性能承诺。

核对基线（2026-10-27）：Madoka main `6e8aee8`（已合并 PR #36）；SoulGem main `494c420`；Homura main `31f65a7d`；Kyubey `28213b59`。服务端基线通过远端 main 源码核对。文档分支已合入该 Madoka main；本 PR 相对 main 仅修改文档。

本文件先在 Madoka 仓库保存完整设计。实施第一步将规范迁入 SoulGem `docs/reception-v2.md`，与服务端 OpenAPI/fixtures 一起成为唯一协议权威；本文件随后改为固定提交链接。现有 `protocol-pin.md` 在服务端实现可用前不变。

## 1. 已完成的参考与设计选择

以下来源已阅读。借鉴的是移动端接收模型，保留现有 HTTPS 呼叫控制和原生 WebRTC，不重新引入 SIP 引擎。

| 官方来源 | 已核对的实践 | 本项目采用的设计 |
| --- | --- | --- |
| [RFC 8599](https://datatracker.ietf.org/doc/html/rfc8599)，2019，尤其 §4、§5 | 推送唤醒休眠 UA，恢复注册后继续投递请求 | 接收身份与当前连接分离；邀请在服务器等待；不用短在线心跳证明接收资格。RFC 本身仍有注册刷新要求，本提案的持久订阅是我们的选择 |
| [Flexisip PushNotification 当前文档](https://wiki.linphone.org/xwiki/wiki/public/view/Flexisip/Core%20servers/Configuration%20reference%20guide/master/module/PushNotification)，文档注明 `2.7.0-alpha-97-g34521b37`；[2.4.2 文档](https://wiki.linphone.org/xwiki/wiki/public/view/Flexisip/Core%20servers/Configuration%20reference%20guide/2.4.2/module/PushNotification) | 来电与消息使用不同 TTL；可委托 HTTP 中继；移动端不必保持永久连接 | 短期来电邀请、持久短信事实；Kyubey 只负责投递；不让短信积压阻塞电话 |
| [Flexisip 对迟到推送的处理](https://www.linphone.org/en/news/the-end-of-ghost-calls-on-iphone-thanks-to-flexisip-2-2/) | 保留已取消呼叫上下文，处理手机醒来时呼叫已结束的情况 | 客户端先取权威状态再响铃；保留终态，迟到推送不能复活通话。文中的 iOS CallKit 行为不直接移植到 Android |
| [Kamailio TSILO](https://www.kamailio.org/docs/modules/stable/modules/tsilo.html) | 保存事务，在新 contact 出现后追加分支 | 来电事务允许已订阅但暂时断连的设备加入；仍受原截止时间约束。TSILO 内存事务本身不等于我们的持久 outbox |
| [Twilio Voice Android FAQ](https://www.twilio.com/docs/voice/sdks/android/faq)，Incoming Calls / Push notifications | 后台来电使用推送；设备 token 更新时重新登记；推送登记与短期访问令牌不同 | token 换代触发登记，不按分钟刷新接听资格；保持鉴权与推送端点分离 |
| [Android Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)、[FCM 优先级与处理生命周期](https://firebase.google.com/docs/cloud-messaging/android-message-priority) | Doze 限制网络/闹钟；紧急用户可见通知与后台同步不同；异步处理须有受支持的生命周期 | 不用一分钟精确闹钟维持协议；真实来电/新短信通知按平台规则投递；无声探测不用紧急推送 |

这些来源支持架构选择，不证明 Madoka 已达到这些项目的来电成功率或耗电水平。

## 2. 首版范围与已有存储

本协议保留“持久接收意图、电话/短信解耦、按需来电”的目标，采用现有 `/v1` API 的可协商扩展。名称 Reception v2 指接收语义版本，不要求新建整套 `/v2` API。完整 mesh 的取舍见 [整合决策](reception-mesh-decision.md)。

| 数据 | 首版做法 |
| --- | --- |
| 来电与参与者、取消/终态 | 复用 `call_sessions`、`rtc_control_calls`、`call_participants`、`call_events`；不另建邀请表 |
| 短信数据与增量 | 复用 `server_events` 与已有短信 API/cursor；当前没有清理机制，不增加30天到期、410、快照、bootstrap |
| 接收偏好 | 新增一张 `device_reception`，按设备保存 `protocol_version/incoming_enabled/sms_enabled/revision`；既有认证限制owner和角色 |
| 推送端点 | 扩展现有 `push_endpoints` 的端点generation、接收wire版本与call/SMS独立退避字段；保留已有两个推送水位 |
| 待投递事件 | 继续扫描已有持久事件与未推进的推送水位；不另建outbox、attempt和receipt表 |
| 通话记录 | 沿用现有历史API和缓存；获取记录不初始化RTC，不要求独立第三套订阅协议 |

既有事件是持久事实，进程重启后从已提交水位恢复。首版部署约束为每个独立节点/数据库一个推送调度实例；三节点不是同库三副本。明确部署同库多API推送副本时，才增加数据库领取互斥；不能声称当前方案已经提供多worker exactly-once。

## 3. 必须保持的语义

- 接收偏好与当前连接、最近一次成功推送分离。`incoming_enabled=true`、有效设备授权与RTC能力决定订阅；临时失败或休眠不自动取消。端点/在线连接决定可用投递路径，`failures>0` 不等于永久失效。
- 关闭来电只关闭本设备新呼入。短信收发、历史、已接通/已成功进入接听流程的电话及用户主动呼出继续工作；开页面和收到SMS不重新开启监听。
- 首版可为所有已订阅设备建立参与者，即使当时没有可用路由；路由缺失显示受限，真实来电按固定窗口到期，不假称已送达。
- **呼出必须拆开校验。** 当前 `rtcControlCreate` 的outgoing分支也调用 `rtcCandidateSQL`；新版本显式呼出改查会话、owner、SIM、网关占用与RTC能力，不要求incoming enabled。临时活跃capability不允许覆盖持久incoming偏好。
- 推送只是提示；手机认证查询真实call状态后才响铃。保持call_id去重、活动控制租约、WebRTC和Homura winner/dispatch账本。
- v2空闲无 `COMPAT_IDLE`、无周期call列表查询、无capability/sleep续约、无一分钟精确闹钟。FCM不可用也不恢复常驻锁。

## 4. 最小接口扩展

`GET /v1/capabilities` 增加 `reception_v2: true`、`push_wake_versions: [1,2]`。按节点协商；只有端点实际支持wire2时才能启用分类push路径。

只增加一项配置资源（GET/PUT），其余复用旧接口：

| 接口 | 扩展 |
| --- | --- |
| `GET /v1/devices/self/reception` | 返回version、两项偏好、revision；未迁移返回version=1和旧偏好来源 |
| `PUT /v1/devices/self/reception` | `version:2, expected_revision, incoming_enabled, sms_enabled`；首次revision=0；事务提交新偏好并切该设备接收语义 |
| 现有push-endpoint PUT/GET | 登记wire版本；返回服务端endpoint_generation；相同端点幂等返回同generation，实际替换才递增 |
| 现有 `/rtc/control/incoming` | 新Homura在能力协商后附可选 `remaining_ring_ms`；响应仍使用现有call快照；仅无订阅时返回明确的节点范围错误 |
| 现有 `/rtc/control/calls/{id}` 和operations | 复用权威快照、accept/reject等语义；响应补可选 `remaining_ms` 供单调计时 |
| 现有events API/WSS | HTTP短信cursor不改；wire2 WSS分类call/SMS hint，断线恢复按对应领域补一次数据 |

配置示例：

```json
{
  "version": 2,
  "expected_revision": 7,
  "incoming_enabled": false,
  "sms_enabled": true
}
```

PUT成功返回revision=8与完整当前配置。相同expected_revision重复写、响应丢失时，客户端GET：若内容一致即视为确认，否则按**最新本地意图**重算。409只表示修订冲突；401沿现有refresh；撤销按现有鉴权拒绝。不为配置另建操作历史表或迁移接口。

同一设备修改由本地持久队列串行发送；本地intent sequence屏蔽旧响应。新服务端保存version=2后，旧capability请求不能覆盖持久偏好。升级保留用户明确意图，未确认的默认值不能将电话自动开启。

`sms_enabled` 控制手机后台同步/通知，不控制Homura采集、不删除服务端数据、不禁止用户主动查询/发送。首版不增加短信开关UI，只给领域独立运行留下稳定语义。

## 5. 关闭监听与接听竞态

本地先持久写incoming=false，再停止接纳新邀请；远端离线时保存待同步状态。短信push、启动、切网、打开页面均先检查本地状态；远端尚未确认期间可能仍收到不透明call hint，但不启动RTC。

服务器关闭事务与现有accept按统一锁序串行：关闭先提交，未接听参与者退出且后续accept拒绝；accept先提交，已进入接听流程的这一通继续。仅关闭监听不强制终止已有通话，通话结束后电话运行时退出。取消待接邀请不得全局挂断其他设备，也不得释放实际仍占用的Homura蜂窝槽。

本地快速开/关只提交最新未发送意图；在途旧请求结果确认后再发送新revision。重新开启只参与新创建的来电，不复活关闭时退出的旧参与者。全局“接收来电”控制当前安装所有配对，远端分别确认；离线节点显示待同步，本机关闭立即生效。

端点共享给短信与电话；关闭电话不DELETE端点、不清短信cursor。备份恢复不复活旧token/会话；重新配对需要明确接收意图。正式开关UI可后续发布，底层关闭语义在首版实现。

## 6. 来电窗口与多节点分叉

复用现有邀请快照，无需新增invitation资源：

1. Homura真实蜂窝振铃生成已有全局call_id，并记录本地单调25秒预算。向每个节点尝试前重新计算remaining_ring_ms；过期或蜂窝结束后停止新offer。
2. SoulGem首次成功创建时使用 `expires_at = server_now + min(25000, remaining_ring_ms)`；已有call/同一操作重试返回原截止时间，不延长。旧网关缺字段时保留原服务端25秒窗口。取消仍以真实蜂窝END为准。
3. 各节点的deadline可以略有不同。相对剩余预算可能包含请求在途延迟，不声称跨节点精确同步；Homura发送终态与执行前核验保证真实结束后不接听。不加入5秒时钟偏差拒绝机制。
4. **`NO_INCOMING_SUBSCRIBERS` 只代表当前节点。** 新Homura将其归类为明确REJECTED，继续下一个ready节点；旧网关继续收到现有 `GATEWAY_UNAVAILABLE`。A无订阅不能终止B/C的offer。UNKNOWN创建仍按现有不确定结果/terminal队列处理。
5. 所有节点都明确无订阅时才结束本轮远端offer，保持原蜂窝振铃，不自动接听；继续现有最终通话记录。单个无订阅节点不创建伪造参与者或占用新的控制槽；不为此重写未接历史存储。
6. 同一次来电有可用WSS和push时立即同时发，不等500ms回执。客户端按call_id/revision去重，先查当前状态再创建一次Telecom。
7. 推送在截止前投递，手机预算取服务端remaining_ms并使用单调时钟；终态、已退出参与者、不存在或本地关闭均不响。取消hint可重复，已经响铃时也有本地截止计时。终态保存沿用现有策略，不新设保留/清理系统。
8. 接听继续现有accept→媒体准备→Homura winner CAS；真实ACTIVE只由蜂窝/媒体事实确认。活动控制轮询与20秒租约本次不改，空闲监听不承担该租约。

## 7. Wake wire2 与推送调度

Kyubey `28213b59` 的 `src/relay.js` 丢弃扩展字段，`src/fcm.js` 固定HIGH/60秒，因此中继改造是本轮上线依赖。[源码](https://github.com/KiriKira/Kyubey/blob/28213b59122936e3b44c7f0a2d41dc8cd43e19f0/src/fcm.js)。

最小wire2 envelope示例（送入FCM data时数值按要求转字符串）：

```json
{
  "v": 2,
  "kind": "call",
  "device_id": "origin-pairing-device-uuid",
  "wake_id": "call-event-187",
  "call_id": "call-uuid",
  "revision": 4,
  "endpoint_generation": 2,
  "ttl_ms": 18000
}
```

`kind=call/sms/check`；call查询快照区分邀请/取消，sms只运行短信同步，check保持provider validate-only而不唤醒手机。wake_id由来源现有事件标识构造，在 `(来源配对,device_id,kind)` 内唯一，不是全网序号。消息不含号码、短信正文、token、任意取数据URL。手机仅匹配已配对来源；来源代发也保留原device_id，不能换成出口节点的身份。

端点换代由服务端generation和手机登记结果识别；Kyubey无状态透传，不假装能判定全局最新代际。若新端点消息先于登记响应到达，手机可对已配对来源做一次有界端点GET确认后再取业务，不因登记响应竞态漏掉真实来电；短信无需重置已保存cursor。

中继可以在现有 `/v1/w/{sealed}` 加 `v:2` 分支与版本查询，保留v1。v2注册握手明确报告支持字段；不能仅“server支持”就给旧Kyubey发新字段。caller提供ttl_ms由服务器在每次实际发送前重算，中继再扣自身处理时间写入FCM android.ttl；调用总超时≤剩余预算，≤0直接丢弃。默认每次最多3秒，至少预留3秒手机取状态时间；wire2 call不使用跨call共用collapse key。

推送worker保留现有事件扫描，改动必须包括：

- call与SMS独立水位、退避、并发配额；call可用4个worker、SMS用2个，都是有界默认值。每个设备/领域进程内单任务，不用每个tick创建无界goroutine。
- call读取水位之后的事件并按call_id聚合到当前状态，处理该批每个call；全部已受理或明确过期/终态无需发送后才推进**已处理范围**。不能仅取MAX事件只发最后一通，就跳过另一通来电。
- SMS提示成功只推进SMS水位；call成功只推进call水位。事件在发送期间追加，下一轮继续；新事件不受上一通已结束call的长退避阻塞。
- 网络错误不推进；重启可重发但客户端幂等。call尝试受原deadline约束，初次后约1/3秒退避，不超时重发过期邀请；SMS沿独立指数退避到5分钟。provider受理仅表示handoff，不能标记设备已响铃。
- 替换endpoint时旧worker成功/失败只能更新自己读到的generation；旧410不能删除新端点。暂时失败不得以failures>0移除持久订阅。
- `received/fetched/presented/suppressed` 作为手机本地诊断阶段；首版不增加回执HTTP、持久receipt表，也不为拿回执额外唤醒。

无完整outbox不等于只存内存：已有call_events/server_events与水位承担恢复。未来需要跨节点**持久接管**任务或同库多调度实例时，才加入任务claim/lease；届时复用同一wake_id与deadline。

## 8. 短信/电话运行时解耦

HTTP短信feed/cursor与通知去重账本保持原格式；当前server_events已是短信事件，不凭空建立新feed/快照。旧缓存迁移只增加领域配置和wire版本，不迁移短信cursor。

`PushWake`/WSS按版本分发：wire2 call仅调CallReceiver查单call；wire2 sms仅调SmsSync；旧wire1混合提示允许继续双路，但本地incoming关闭时绝不启动电话。通话记录按原历史API在前台/已有数据同步任务更新，不能启动RTC。

提取一个进程级接收协调器，管理偏好、短任务与共享传输即可；不用为每个topic新建服务。`HostBackgroundService`不再拥有电话引擎生命周期；通话runtime只在有效新邀请或用户主动拨号时创建。`NodeMonitor.maintain`在v2停capability续约，SMS工作不能附带全节点电话维护。

有系统推送时空闲可以断开仅为来电维护的WSS；SMS按已有推送/系统任务/前台补拉，不让“短信默认开”迫使电话service常驻。WorkManager可用于延迟补齐，但不是来电定时器。新SMS用户可见通知可使用即时类别，静默状态同步用普通类别，避免所有hint都用HIGH。

## 9. 无FCM和混合节点

无系统推送时，保持一条每authority共享的分类WSS（多个独立authority可能各一条），无常驻CPU锁，非唤醒心跳默认120秒；依据真实连接断开可在60–300秒范围调整，单侧ping。只在路径变化/真实失败时合并重连，1秒到5分钟退避；信号强度抖动不重建连接。

重连后一次读取当前有效call和SMS增量，平时不空轮询calls、不精确闹钟续租。断流期间存在来电延迟风险；单纯WSS/FGS不能保证Doze下秒级到达，这个限制必须显示，不能退回持续持锁让测试通过。手机工作锁初值10秒，工作完成立即释放，活跃通话独立所有者。

v2接收选择只有自动路径：有合格push用push，否则尽力stream；用户明确关闭incoming高于一切。首版不增加三种产品模式配置。正式无GMS系统provider适配另开工作，接口抽象本身不算已解决手机唤醒。

按authority协商：旧server保留原adapter并标明旧协议限制；新server的旧设备继续原候选逻辑。用户关闭来电的本地拦截对任何版本都有效。一个旧节点不将其他v2节点降级；不承诺混合旧节点已达到全部v2省电指标。

## 10. 迁移与回退

先部署兼容server与Kyubey，再升级Homura和Madoka。配置PUT成功的同一事务使设备进入version=2；在已有call结束后切换，客户端停止旧空闲续约。短信cursor/执行账本不动；不增加独立migration API。

回退功能由配置资源/服务端设备级开关完成，保留关闭意图与短信偏好；只有incoming仍开启才恢复对应legacy接收。旧客户端不能通过自动capability请求抹掉v2关闭，必须由用户明确重新授权。v2新列/表为扩展迁移，不在回退时删除。

仅复用现有事件的首版不等于完成真正去中心化复制。未来mesh代发复用kind、来源device、wake_id、call_id、generation与deadline；手机合并到单入口还需要独立的授权路由，不能拿代发功能冒充。
