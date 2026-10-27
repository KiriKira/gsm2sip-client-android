# PLAN：精简接收改造与可选节点协作

状态：待实施；按 [Claude 对 PR #37 的评审](https://github.com/KiriKira/gsm2sip-client-android/pull/37#issuecomment-6447204378)和最新main重新收敛。协议见 [reception-v2-protocol.md](reception-v2-protocol.md)，完整mesh的成本/取舍见 [整合决策](reception-mesh-decision.md)。本文件替代原P1–P10实施顺序，不包含调研任务。

基线：Madoka `6e8aee8`、SoulGem `494c420`、Homura `31f65a7`、Kyubey `28213b59`。本轮仅设计文档；实际实现另开PR，APK走PR CI。

## 1. 交付边界

本次发布R1只解决持久接收意图、电话/短信解耦、按需来电和无FCM的尽力WSS。之后可独立交付R2单跳推送代发。单入口业务代理R3仅在确有手机与网关入口分离需求时启动。完整事件复制、自动成员发现、跨节点通话迁移不再作为当前路线的完成条件。

R1先拿到主要省电收益：无空闲持续锁、无每分钟续约/闹钟、短信不启动RTC；关闭电话后仍可主动拨号。保留现有事件/cursor、WebRTC、活动控制租约和Homura执行账本。

## 2. R1：四个仓库的实现顺序

| 工作包 | 交付 | 依赖 / 完成标准 |
| --- | --- | --- |
| R1-S SoulGem | 一张接收偏好表、一个GET/PUT资源、候选/呼出校验拆分、分类推送水位与有界并发、wire2协商 | 先落协议/OpenAPI样例；旧端保持兼容；偏好关闭不影响SMS和outgoing |
| R1-K Kyubey | wire2透传call_id/来源/wake_id/generation、真实剩余TTL、按kind类别、旧wire1兼容 | 按已冻结的小契约实现，不引入数据库或四阶段回执 |
| R1-H Homura | 节点范围无订阅继续分叉、单调剩余振铃预算、维持未知结果/终态队列 | 服务端支持后启用扩展；A无订阅而B有订阅时仍可响铃 |
| R1-M Madoka | 持久关闭意图、按kind分发、按需RTC、删除v2空闲续约/锁/闹钟、无FCM分类WSS | S/K就绪后灰度；正式UI入口可后发，关闭语义本轮必须可调用验证 |
| R1发布 | 设备级启用、下述有限回归和两段待机观察 | 不等待R2/R3，不承诺全部旧节点同样省电 |

这是四个逻辑工作包，不强制把Android改动塞进一个巨大PR；Madoka可拆“领域解耦”和“切v2”两次审查。S先确定样例，K/H可分别推进，M最后切换接收路径。

### R1-S：服务端

- 迁入规范到 `docs/reception-v2.md`，更新既有OpenAPI与少量样例；本仓库提案随后改固定提交链接，不维持双权威。
- 新增 `device_reception(device_id, protocol_version, incoming_enabled, sms_enabled, revision)`，引用现有设备/owner权限；扩展push_endpoints的generation、wire版本和分领域退避。复用既有两个水位，不新增邀请/outbox/receipt/快照表。
- GET/PUT配置使用expected_revision；首次迁移与偏好保存同事务；旧capability不能重写v2意图。重复/超时通过GET比对，不引入单独配置操作历史。
- `rtc_control.go` 拆开incoming eligibility与outgoing authorization。持久enabled控制来电；显式拨出仍查会话、SIM、占用、RTC能力。关闭监听不能导致呼出FORBIDDEN，也不能通过呼出临时capability重新打开监听。
- 关闭事务只退出未接听参与者，与accept同锁序；accept先成功则保留该次接听/通话。处理晚到worker时重查本设备偏好，不能影响其他参与者或释放真实蜂窝占用。
- `push.go` 用已有call_events/server_events扫描，电话/短信水位、退避和并发配额分开；每设备/领域单任务。取一批call事件须覆盖各call_id，不能只推最新一个后把前面的全部跳过。
- call发送前重算剩余TTL；WSS/push同时发，不等回执；已过期或终态不复活邀请。旧端点回调按generation条件更新。
- 新Homura无订阅返回明确 `NO_INCOMING_SUBSCRIBERS`；旧Homura保留 `GATEWAY_UNAVAILABLE`。其他权限/网关错误不滥用无订阅分类。
- 首版每独立数据库一个推送调度实例。重启允许重复，客户端去重；若当前部署要开同库多实例，再单独加互斥，不为假设中的集群先造任务系统。

### R1-K：中继

- 在 `src/relay.js` 支持wire2字段白名单与明确版本握手；在 `src/fcm.js` 写真实剩余TTL，call不使用跨call共用collapse key。
- 保留v1密封endpoint与validate-only check；无状态透传generation，不伪称能即时撤销所有旧密封URL。
- 新SMS通知可即时，静默状态补齐普通类别；真实来电紧急，诊断不周期性发送高优先级静默消息。
- provider accepted写服务端诊断；手机收到/取到/展示阶段留本地诊断，不新增HTTP回执和后台回执重试。

### R1-H：网关

- `IncomingForkCoordinator`/`mayHaveCommittedCreate`将新无订阅错误归为当前节点明确REJECTED，`offerIncoming`继续B/C；保留UNKNOWN不确定结果和terminal队列。
- 第一次蜂窝RINGING记录单调25秒预算，向每节点发送剩余值；不做跨设备5秒时钟偏差准入。服务端首次落盘deadline后重试不延长。
- 全部节点无订阅才结束本轮远端offer，蜂窝保持原行为；不创建伪参与者、不提前接听，不为此新建全量历史同步框架。
- 维持既有winner CAS、dispatch ledger和真实END处理；不动SMS采集服务。

### R1-M：客户端

- 复用 `CallPreferences` 改为持久本机意图和各节点确认revision；记录待同步请求与本地sequence，旧响应不能覆盖新开关。去除可见页面自动调用 `ensureEnabledFor` 的恢复监听路径。
- `PushWake`/WSS按wire版本和kind分派：v2 SMS仅SmsSync；v2 call查指定call_id的当前状态；旧混合提示先检查电话关闭标志。
- 已关闭时收到SMS、网络回调、进程重建都不得初始化RTC。主动拨号只创建单通CallSession，结束后仍保持关闭；已经接通的电话不被关闭监听误挂断。
- `HostBackgroundService`与电话runtime分离；共享传输按需求存在，历史API不启动电话。短信cursor/cache/notification journal继续原格式。
- 已确认切v2的节点停用 `RtcSleep`、`RtcSleepyListening`、`COMPAT_IDLE`、空闲call轮询和 `NodeMonitor` capability续约；保留独立工作锁、超时、现有活动通话租约。
- 没有合格push时复用共享WSS，非唤醒心跳、单侧ping、路径变化合并恢复、有限退避。Doze受限如实显示，不恢复永久持锁。
- 补充少量诊断字段：接收版本、incoming开关及待同步节点、实际推送模式、工作锁owner、按call/SMS计请求量。无需远端receipt链路。

## 3. R2：单跳推送出口代发（独立可选发布）

仅在A到Kyubey出口失败而B出口正常的真实部署需求存在时做。不等待完整mesh，也不声称A宕机后B能接管A业务。

- SoulGem静态配置2–3个同管理员peer：固定peer_id、mTLS证书/受信指纹、允许的origin/owner、固定Kyubey目的地址。没有自动发现/选主/gossip。
- 新增一项peer内部push-forward接口；A先按TTL预算尝试直接发送，局部出口失败时仅选一个B。请求保留来源配对、wake_id、call_id、generation、剩余预算，hop_limit=1，B不能继续转发。
- B校验peer权限、目的地址白名单及预算，再使用同一R1-K sender提交；返回provider accepted或明确失败。A只在成功时推进原call/SMS水位；超时仍未确认可重试，但客户端去重。
- 首版同步代发，不新增远程持久outbox：A重启依靠已有事件重试，B重启中断返回不确定，A宕机无法读取业务仍可能接不到电话。需要宕机接管时才另立持久复制需求。
- 局部网络/TLS超时可换出口；token永久失效、同一Kyubey/FCM全局失败不盲目扇出。撤销peer从两侧配置中移除，日志不写endpoint/token。
- 只增加两条集成场景：A出口断/B可达可代发且不重复响；全出口失败按预算结束不转发循环。加一个拒绝未授权peer的接口断言即可，不做大规模集群压测。

## 4. R3与原mesh计划的取舍

详见整合决策。原S0/S4/F1/F2的接收优化被R1替代，S1缩成R2；S2全量事件复制、S3完整控制委托、S5 TURN目录暂不排期，S6活跃迁移从当前路线移除。

有明确“Madoka只能到A、Homura只到B”的需求时，可独立设计R3一跳授权业务代理：原节点继续权威，手机保留各来源授权，代理不复制会话或假装接管控制。该能力涉及精确session绑定、媒体授权及撤销，不与R1一起做。无此需求就停在R1/R2。

## 5. 迁移与回退

1. S/K兼容版本先部署，wire1继续可用，按设备关闭新模式；H升级后识别新错误/剩余时间。
2. M先保留现有明确意图，成功PUT并确认wire2后，在无活跃call时切v2；短信cursor/已提醒ID不动。
3. 从一个设备/节点启用，再用现有三个独立节点做一次A无订阅→B成功场景。旧节点按adapter运行，不能强称混合环境已达到纯v2电耗。
4. 回退按设备保留incoming=false与sms=true，不清数据、不重配对。仅用户仍开启电话时恢复旧接收；旧代码自动续capability不得抹掉v2关闭意图。
5. 明确发布阻断：关闭后误响/自动开启、主动拨号被误禁、SMS丢失/重复发送、重复蜂窝接听、过期来电复活、v2空闲持续锁。出现问题修复相关场景，不重跑无关媒体/全机型矩阵。

## 6. 精简验收：覆盖真实风险，不做组合穷举

复用现有测试和既有设备。每个行为只在最合适的一层验证：SQL/状态竞态做自动测试；Doze和待机做真机。不为未实现的mesh/快照/outbox测试。以下都是后续实施验收，不是本次文档已经通过。

| 场景 | 最小验证与标准 |
| --- | --- |
| 现用手机、日常Wi-Fi/VPN熄屏与移动网络 | 每种网络3通真实呼入，其中一次确认进入Doze；记录逐通网关RINGING→展示时间，目标≤5秒，任何一次不得超25秒窗口；小样本不报可靠率/P95 |
| 电话关闭但SMS开启 | 一次入站SMS、一次显式短信发送、一次主动呼出；短信正常、呼出后仍关闭，空闲RTC初始化/续约/call轮询为0 |
| 离线关闭→进程重启→网络恢复 | 一次真实流程；旧回调/SMS不复活监听，服务端最终关闭；再开启后只接新来电 |
| 三节点实际分叉 | A无订阅、B有订阅，确认B响；并注入同call重复/迟到提示、取消后提示，确保只响一次、终态不复活。直接复用现有winner用例，不新造多分区矩阵 |
| 真正有副作用的边界 | 服务端accept与关闭两种顺序、call成功不推进SMS水位、旧endpoint失败不删新端点；各一个有意义自动用例。客户端保留一个旧响应不覆盖新意图用例 |
| 慢节点与无FCM | 一个endpoint超时不堵另一个正常来电；关闭FCM后WSS正常收一通，切VPN/网络一次能恢复；深睡延迟记录受限，不用持续锁掩盖 |
| 升级/回退 | 用现有一对旧新版本跑一次协议切换与回退，保留incoming关闭和SMS cursor；已有SMS去重/出站账本用例复用 |

待机只做两段有目的的观察：同一手机常用三节点环境，正常push静默8小时；无FCM stream另静默8小时。记录VPN、网络、充电/电池豁免、模式与版本。已有同条件旧版日志可作基线；条件不同则补一段匹配基线，不固定要求三轮重复。

- 纯v2期间 `COMPAT_IDLE=0`、周期capability/call列表请求=0、协议续订精确闹钟=0。单次控制工作锁≤10秒；无业务稳定环境累计应用工作锁目标≤60秒/8小时，超出先解释并修复来源。
- push组没有来电专用WSS；stream组允许声明过的必要连接与心跳。稳定网络不反复全节点探测；请求/连接按领域统计，不能把旧节点或真实通话混入idle结论。
- 报告实际CPU时间/锁/网络活动与系统电量估计，观察相对基线下降，不承诺硬件无关百分比。只有新失败或异常波动才加测相关场景。

删去原固定10/30次每场景、全网组合故障、多种拓扑重复8小时、100个慢端点、快照过期/乱序复制/克隆旧key等当前不实施能力的发布门槛。保留关闭、短信幂等和winner并发这些真实会发生且后果大的验证。
