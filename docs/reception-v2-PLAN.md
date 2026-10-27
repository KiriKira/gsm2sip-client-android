# PLAN：Reception v2 实施与交付

状态：待实施。设计依据及参考来源已完成并写入 [协议提案](reception-v2-protocol.md)。本 PLAN 只列实现、迁移、验证与发布任务，不把资料调研或再次选型作为前置工作。

适用：Madoka、SoulGem、Homura 和 Kyubey。本次提交只交付设计文档，不表示服务端或客户端功能已经落地。APK 由后续 PR CI 按仓库流程生成，不要求本地生成 APK。

## 1. 交付目标

- 手机空闲接收不持持续唤醒锁，不按分钟刷新接听资格；推送可用时不为来电维持后台长连接。
- 电话监听与短信同步在配置、协议、队列、运行时、诊断和开关行为上独立。电话关闭后仍能收发短信、查看通话记录和主动拨号。
- 真实来电使用固定截止时间、可恢复邀请、持久投递任务与真实设备回执；取消或过期后不再次响铃。
- 无 FCM 时同样删除常驻锁和空闲 RTC 轮询，采用有界 stream 降级，明确系统休眠导致的接收限制。
- 不改变现有 WebRTC 媒体、SIM 映射、Homura 一次蜂窝执行和 winner CAS 安全边界。
- 初版可在单节点和现有独立多节点上线，完整 mesh 继续作为独立后续项目。

## 2. PR 拆分与依赖

每一行可独立审查和回退，不能用一个跨四仓库大 PR 同时替换所有运行时。

| 阶段 | 仓库 / 交付 | 依赖 | 完成门槛 |
| --- | --- | --- | --- |
| P1 | SoulGem：规范、OpenAPI、fixtures、增量表结构 | 无 | 明确 topic、revision、deadline、错误码；client 提案改为 server 固定链接 |
| P2 | SoulGem：订阅/端点 API、协议代际与关闭事务 | P1 | 关闭电话不改短信；旧写入不覆盖 v2 意图；并发 mutation 幂等 |
| P3 | SoulGem：持久邀请、独立 call/SMS outbox 与 cursor | P2 | 邀请不依赖短在线租约；延迟/取消正确；业务与 outbox 原子提交 |
| P4 | Kyubey：Wake v2、TTL、类别、generation 与受理状态 | P1 | 真正 provider TTL；call/SMS互不吞消息；旧端点仍可服务旧客户端 |
| P5 | Homura：邀请起点与无订阅处理、终态上报 | P1、P3 | 真实蜂窝仍不提前接听；多节点同 ID/截止时间；无订阅可留未接记录 |
| P6 | Madoka：领域拆分、持久接收偏好、v1 adapter | P1 | 短信路径零 RTC 启动；电话开关状态重启后保持；旧服务可用 |
| P7 | Madoka：v2邀请/推送/独立同步与按需 CallSession | P2–P6 | push → 取状态 → 一次响铃；后台无空闲 RTC；短信正常 |
| P8 | Madoka：stream降级、网络恢复、资源计量 | P6、P7 | 无 FCM 下无持续锁/空闲 call poll；断流 cursor 恢复 |
| P9 | 三端 + Kyubey：迁移、必要真机场景、灰度开关 | P3–P8 | 满足第7节，能按设备回退且保留关闭意图 |
| P10 | Madoka：正式显示“接收来电”开关 | P7、P9 | 可独立版本发布；P6起已有完整语义，后续不再改协议 |

P4、P5、P6可在依赖契约落地后分别开发。P1–P9构成协议发布；P10是用户所述未来入口，不阻塞底层解耦。每阶段状态初始均为待实施，不将文档完成计作功能完成。

## 3. 服务端与中继具体改动

### P1：契约和迁移

- 将协议规范迁入 `gsm2sip-server/docs/reception-v2.md`；在 `openapi/openapi.yaml` 声明全部 API 和错误响应，在 `fixtures/api/reception-v2/` 保存开启、关闭、短信、邀请取消、cursor失效、端点换代的样例。
- 增量增加 `reception_subscriptions`、`delivery_endpoints`、`call_invitations`、`wake_outbox`、`wake_attempts`、`wake_receipts` 和 topic feed/checkpoint 存储。可复用已有业务表，不能复制短信正文产生第二权威。
- 增加唯一键、revision CAS、claim lease、查询 deadline/next_attempt 索引和 endpoint 加密存储。topic feed的追加必须与业务写入同事务。
- 实现 per-device 协议代际和 cursor bootstrap 数据迁移；启用开关默认关闭，旧表/接口继续运行。迁移不自动启用用户未开启的监听。

### P2：接收与关闭事务

- 新增独立 reception handler/store，复用现有 paired bearer/owner 校验。订阅生命周期独立于访问 token 短期过期和连接状态。
- `enabled=false` 事务同步撤回未接听邀请、取消未发送 call jobs；SMS/历史资源不变。与 accept 使用固定锁序，胜出的操作有确定结果。
- endpoint登记与订阅分开；显式永久失效只更新匹配 generation；暂时不可达记路由故障。旧 callback/worker不得删除新端点。
- 实现 revision冲突、operation幂等、撤销与单设备协议切换；v2关闭后的旧 capability请求不重新开监听。

### P3：邀请和持久投递

- 修改 `internal/httpapi/rtc_control.go` 的**客户端空闲接收资格**：v2读持久订阅；v1保留原90秒/sleep规则；活动控制租约继续独立运行。
- incoming事务保存原call_id、起点、deadline、接收者与订阅revision；无可达路由不等于无订阅。响应区分 `accepted_for_delivery/no_subscribers`。
- 替换 `internal/httpapi/push.go` 的混合cursor推进方式：call/SMS/history独立任务；SQL事务提交后由worker投递。单节点慢请求不串行阻塞所有接收者。
- 实现claim过期恢复、网络超时、受理响应丢失、终态检查、截止时间预算、端点换代、最大重试和分队列公平调度。
- 新增接收WSS订阅和 topic增量API；业务事务提交后唤起dispatcher，保留有界补扫弥补进程中断。设备receipt不决定服务端业务日志是否保留。
- 实现终态保留、cursor超窗快照、旧新cursor bootstrap；迁移期间双写日志但每设备只由一个协议分发。

### P4：Kyubey

- 在现有endpoint请求处理和FCM sender中支持v2 envelope；返回 `provider_accepted` 与 provider message ID，不返回“设备已收到”。
- 根据kind映射 provider类别和真实TTL；call不共用全局collapse key；短信hint可合并。实际到期不再发送。
- 新增v2 register/wake入口，检查接收端版本、密封端点与schema；generation由SoulGem/Madoka核对，Kyubey透传。保留无状态中继边界及旧v1编码，不宣称即时撤销已泄露端点。拒绝未知topic/kind、超大payload和任意URL代理。
- 保留可移植provider adapter边界，首版实现已有FCM通道。厂商推送接入可以另开PR，不将“存在接口”标为“已解决无GMS”。
- 紧急推送仅用于用户可见事件；移除以周期性高优先级静默探测获取睡眠资格的要求。诊断测试由用户触发、限频、独立回执。

### P5：Homura

- 调整 `GatewayCallControl` / `IncomingForkCoordinator`：首次真实振铃固定call_id与截止时间，跨节点重试不重置窗口；支持时调用新的gateway incoming入口，否则保留v1请求形状。
- 区分服务器接收邀请、设备响铃和真实蜂窝ACTIVE；`no_subscribers`不转成自动接听/误挂断，不影响短信采集。
- 保留安装范围winner CAS、dispatch ledger、occupancy与terminal queue；无远端参与者时也能最终持久上报未接记录。
- v1节点继续现有兼容流程；新旧混合时同一次蜂窝来电仍共用执行身份，不能产生两次接听。

## 4. Madoka 具体改动

### P6：先完成解耦

- 在 `CallPreferences` 基础上建立 `ReceptionPreferences`：全局本机接收意图、各authority确认revision、持久mutation队列；删除可由短信启动/普通打开页面隐式启用监听的路径。
- 将 `PushWake.handle()` 拆为类型明确的router。v1混合hint留在legacy adapter内；即便v1无法分类，也先查本地电话开关，关闭时不得启动RTC。
- 从 `HostBackgroundService` 移出 `CallRuntime.wakeChannelLive` 所有权，交给共享 `WakeTransport`；短信运行时只消费SmsSync需求。
- 将 `NodeMonitor.maintain()` 的capability续约放入v1电话adapter；普通SMS同步、节点列表刷新不能顺便全节点续订来电。
- 拆出 `CallHistorySync`；电话记录展示、SIM元数据读取不启动实时接收。新增组件路径见协议第10节，旧类逐步变为adapter。
- 实现关闭/开启、离线关闭、进程重建、活跃通话排空、关闭后主动拨出的完整行为。暂不显示正式开关时保留内部可调用入口与诊断状态。

### P7：按需接收

- 支持capability协商、v2订阅迁移与endpoint token换代。按authority选择adapter，一个旧节点不得把其他节点强制降级。
- `CallInvitationReceiver` 只在有效邀请时创建 `CallSession`； `RtcCallEngine` 收敛为活跃通话控制，不再承担常驻空闲接收。
- 服务端成功切v2后停止该authority的capability周期续约、`RtcSleep`授予请求、`RtcSleepyListening`精确闹钟与compat idle lease。
- push接力先读持久关闭意图与配对，再获取短工作锁，立即交给合法后台生命周期；过期、未配对、错误generation和关闭状态无RTC启动。
- 收到cancel或终态先清本地响铃；时间预算到期也清理。复用Telecom单通协调器，持久去重避免进程重建重复响。
- SmsSync/历史使用各自cursor、缓存事务和通知日志；显示通知失败可重试通知，不能伪造已展示回执或跳过数据持久化。
- 有推送时后台关闭来电专用WSS，只在前台/真实呼叫/独立stream需求时建连接。首次API401可执行一次合法refresh，仍受来电剩余预算限制。

### P8：无FCM、网络与资源

- 实现共享WSS的topic需求集合与统一重连退避；独立authority保留必要连接，未来mesh才收敛到一个授权入口。
- 网络回调只在有效路径变化时合并恢复。VPN存在但没有可用底层路径时暂停连接；信号强度波动不触发周期全节点探测。
- 使用非唤醒心跳和单侧ping；后台空闲不再HTTP拉call列表；重连后一次补有效邀请，再按topic补数据。
- 仅保留系统允许的WorkManager短信补齐；不把闹钟权限或电池豁免当协议前置条件，不自动打开常驻锁“可靠模式”。
- 诊断分别记录 `calls.incoming / calls.active / sms.sync / history.sync / transport` 的工作量、锁所有者、模式及切换原因。公开“无系统推送，休眠可能延迟”的真实能力状态。

## 5. 删除与保留清单

| 现有部分 | v2处理 |
| --- | --- |
| `COMPAT_IDLE` / 空闲锁续租 | 删除v2路径；兼容adapter不得悄悄替v2恢复 |
| 60秒精确闹钟、30分钟sleep grant、每小时sleep check | v2停用；不再构成接听资格 |
| 所有节点push可信才允许手机睡眠 | 改为每authority独立投递模式与成本 |
| 空闲calls轮询、全节点capability刷新 | v2停用；真实来电/活跃call才工作 |
| 混合call/SMS push + cursor | 仅legacy adapter保留；v2分领域 |
| `RequestBudget`、独立work owner、诊断、幂等与去重 | 保留并作为新组件基础 |
| 20秒活动控制租约、WebRTC/Telecom、Homura执行账本 | 保留；不得为了待机省电放松真实通话安全规则 |

## 6. 迁移、发布、回退

1. 先部署兼容服务端schema/API与Kyubey v2，功能门默认关闭；数据库回滚不删新增状态。
2. 部署Homura兼容更新，再发布支持v1/v2的Madoka；继承本地明确开关，保存SMS通知去重账本。
3. 单节点、单设备开启v2：同事务迁移订阅并切路由，现有call结束后切；确认实际provider消息为v2，不能仅看capabilities字段。
4. 完成SMS cursor bootstrap后再切读源；迁移期间双写不双通知。随后扩到现有三个独立节点和混合版本。
5. 任一发布门槛失败按设备停用v2；保留电话关闭与短信开启状态，再启用正确的legacy adapter。不能通过清除App数据“修复”。
6. 灰度结果记录commit、设备/系统、provider、接收模式与观察时段；主版本启用后保留旧端兼容一个明确发布周期，再单独删除旧代码。

回退触发：关闭后再次响铃/被自动启用、重复蜂窝执行、SMS丢失/重复通知、过期来电复活、空闲持续锁、正常支持环境来电延迟明显退化。收到provider回执但手机没有收到，不足以单独归咎新协议；必须结合完整阶段诊断。

## 7. 必要验证与可量化发布门槛

只覆盖实际风险，不组合穷举所有Android版本/网络开关排列；已有媒体与SIM用例复用。单元用例验证纯状态边界，跨线程/数据库幂等用集成用例，Doze和耗电留给真机。验收在实施阶段执行，本次文档不声称通过。

| 必须覆盖的场景 | 判定标准 |
| --- | --- |
| 正常Wi-Fi/VPN、移动网络，FCM可用，屏幕关闭/Doze | 每类至少10次真实来电，测试样本内无漏接；网关振铃到手机展示P95目标≤5秒，全部在25秒邀请期内；记录原始样本，不据此声称生产可靠率 |
| 电话关闭，短信开启 | 新SMS可同步/通知；RTC初始化、capability续约、空闲call拉取、call锁均为0；通话历史可读；显式呼出成功且结束后仍关闭 |
| 离线关闭→杀进程→SMS push/重连→再开启 | 关闭期间不响铃；旧enable响应不能复活；远端最终关闭；开启不重放历史来电 |
| 振铃/接听/关闭竞态、多手机及三节点重复push | 仅一个真实接听winner；其他设备正确结束；已接通call不被关闭监听误挂断 |
| 延迟invite、cancel先到、过期push、token换代 | 无幽灵来电、无新generation被旧回执删除；重复push只展示一个Telecom实例 |
| provider受理响应丢失、worker重启、单坏endpoint | 重试有限、任务可恢复、好设备不被阻塞；SMS队列不挤占call预算 |
| SMS通知/进程中断、cursor超窗、切节点 | 业务记录不丢、通知不重复、无重发SMS；旧新feed迁移不跳数据 |
| 无FCM，稳定WSS与VPN切换、断网恢复 | 无持续空闲锁、无周期call HTTP轮询；合法运行环境内WSS到达即处理；Doze延迟如实记为受限，不靠加锁让测试变绿 |
| 老server/新client、新server/老client | 兼容且明确模式；不越权启用关闭订阅，不混用cursor |

静默8小时观测（单节点与三节点各一组，记录机型、系统、电池豁免、VPN、网络、是否充电）：

- 推送模式：空闲RTC capability请求=0、空闲call list定时请求=0、协议续订闹钟=0、`COMPAT_IDLE`=0、无电话专用后台WSS；非业务触发的电话网络请求目标=0。
- 无FCM stream模式：允许已声明数量的WSS心跳/重连；空闲RTC轮询=0、持续idle锁=0；任何单次控制工作锁≤10秒，异常退出也释放。
- 无业务稳定环境下，全部应用显式工作锁累计目标≤60秒/8小时；超标按owner解释并阻止发布，不能通过重命名锁隐藏。此为工程门槛，不等于硬件电池百分比承诺。
- 同手机、同网络/VPN与使用条件对照旧版，记录BatteryStats/应用诊断、CPU时间、锁、HTTP、WSS帧/重连和系统电量估计。三次重复观察用于判断方向；来电及时性不得为耗电指标让步。
- 所有结果按协议/模式分段，不能把通话时长和idle混算；provider accepted、received、fetched、presented分别记录，不把用户没有接听算投递失败。

## 8. 后续独立交付

- P10显示正式“接收来电”入口，提供本机已关闭/各节点待同步状态；保持短信默认体验，不增加不必要的技术开关。
- 真正系统可唤醒的其他provider接入，采用相同Wake v2；必须在对应机型验收，不能把普通自建长连接宣称为系统推送。
- mesh订阅代理、推送出口代发、事件复制和最优入口选择继续按服务端远期计划实施。只有完成授权路由后才减少跨authority连接；这不阻塞本次空闲锁根治。
