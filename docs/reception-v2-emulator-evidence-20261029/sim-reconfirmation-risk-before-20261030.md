# 保留 SIM ID 重新确认：操作前风险记录（2026-10-30）

## 目的与边界

G1 来电已进入模拟器 modem、Telecom 和 GsmCallManager，但 Homura 因 SIM 账户映射处于 `UNVERIFIED` 而拒绝处理。GPT-6.1 Sol 复核后建议只尝试既有 `GatewayJointInstrumentation` 中的 `reconfirm_retained_emulator_sim` 流程。该流程先校验当前本地映射，再经生产 SIM `propose` / `confirm` API 保留原服务端 SIM UUID，并调用生产 `SimRegistry.confirmMappings`。不修改产品代码，不写数据库直至 guard 全部通过，不使用 root/Magisk，不改变默认拨号应用。

这不是正常 UI 恢复，也不能用于证明 F 组升级时自动保留 SIM 映射。

## 操作前只读快照

- Homura API 34 模拟器仅有一张活动虚拟 SIM：slot 0、subscription ID 1；Telecom / Telephony 的双向 account 映射曾报告匹配。实验期间未更换或重建 AVD。
- Homura 本地 `sim_registry_v1` 映射与节点 A 的当前 SIM 绑定使用同一 UUID（对外以 MD5 摘要 `e8929d4f6127114e7d66c2edd2c8795f` 对照），相同 Gateway 身份摘要 `3e1e720d9e8b3feac035419ae8a4d16d`，slot 0、subscription ID 1，映射 revision 8。
- 当前映射身份状态为 `UNVERIFIED`；操作前本地记录没有已 pin 的 SIM fingerprint，也没有已保存的 PhoneAccountHandle fingerprint。之后的实时 `SimRegistry` snapshot 可能为当前 subscription 计算 salted fingerprint；由于操作前值缺失，不能做历史 fingerprint 相等比较。
- 节点 A 中该绑定当前为 `unverified`, `identity_verified=false`, slot 0, revision 8；同 Gateway 的旧行已标记 `removed`。现有 `joint-ready.json` 是旧快照，不作为当前身份依据。
- 普通 UI 不适用于此次恢复：当前本地身份为 `UNVERIFIED` 时，`MainActivity.proposeSimBindings()` 不发送 `existing_sim_id`；服务端会先将旧行置为 `removed` 并新建 UUID。为避免重映射，不点“同步 SIM”。

## 持久副作用和恢复

- 运行 runner 前会正常停止当前 GatewayService；测试 runner 结束时也会停止它。结束后需通过普通 Homura UI 恢复服务并核实控制连接、SIM 状态和无活动通话。
- 服务端 `propose` / `confirm` 会更新 A 的 SIM 映射 revision、绑定状态和审计记录；确认成功后预期状态从 `unverified` 变为 `active/identity_verified=true`，SIM UUID、Gateway、owner/group、slot 应保持不变。
- 若操作在服务端确认与本地落盘之间中断，可能产生客户端与服务端状态不一致。不得盲目重跑；先重新读取两端状态并保留日志。
- 已记录恢复方法：操作前保存 A 数据库一致性备份与 Homura app 私有状态快照。需要回退时先停 GatewayService/runner，再以对应快照恢复 A 与 Homura；恢复数据库会回滚备份后新增的事件/审计记录，因此仅在映射错误或本轮明确结束后使用。不会以本地单边恢复冒充成对回退。

## 写入前 guard

2026-10-29T18:58Z 停止了空闲的 runner。停止后重新读到本地 registry revision 8、local barrier 4、同一 SIM ID 摘要 `e8929d4f6127114e7d66c2edd2c8795f`、slot 0、subscription ID 1、`UNVERIFIED`、fingerprint/account key 缺失；节点 A 的当前行与 SIM ID、Gateway 摘要 `3e1e720d9e8b3feac035419ae8a4d16d`、owner 摘要 `c66592cc36beba342952a82010ef8f69`、slot 0、revision 8 一致。设备行仍是 active gateway，owner 与 SIM 行相同。活动虚拟 SIM 的当前 runner/Telecom 诊断为 `subId=1`, `sim_account=true`, `forward_matches=true`；`adb emu gsm list` 为空，runner 最近状态 `telecom_state=0`，GatewayService 已由 runner 正常停止。旧 `joint-ready.json` 仅是历史文件，未用作本次 guard 输入。

操作前指纹比较的结果是“没有历史值可比”，不是“已确认相同”。该限制已明确记录。只有在同一未重建、未重启的 AVD 内，subscription、slot、Telecom SIM account 与 `TelephonyManager` 双向关联均仍匹配，且生产 API 仍确认原 SIM UUID 属于同一 Gateway/owner 时，才使用 runner 的保留 ID guard。操作后取得的 salted fingerprint 单独记录，不能倒推历史 fingerprint 相同或声称 ICCID 级别的连续性。

写入前私有恢复点已保存到工作站 `/tmp/reception-r1-private-20261030/`，目录权限 0700、文件权限 0600：SoulGem A 完整一致性 pg_dump（176 KiB，SHA-256 `134950fbef5768227c4ff03b0522f2bfc3e6ddc8b419ebe7ac516d0f13f40799`）和 Homura registry/SQLite/WAL 快照（688 KiB，SHA-256 `859086c39e8d2878d20e5c07c0626b0d1c57b7f0efe2fbc8af47a79c31d43b36`）。两份文件含凭证/设备数据，不进仓库。备份完整性通过归档目录读取；操作失败时优先停止并评估 targeted recovery。恢复整库会回滚其后发生的事件/审计数据。

实际提交 `propose` 前仍须确认 runner 的实时 `snapshot` 与 server 行满足上述摘要、Gateway/device/owner、slot/subscription 和 Telecom 关联 guard；当前没有活动通话，且 A 仍持有同一条 `unverified` 绑定。任何差异、guard 失败、proposal 返回新 UUID 或身份无法核验时立即停止，不确认、不重试，记录实际原因。

## 成功后核对

成功后只允许预期验证状态/revision/更新时间变化。SIM UUID、Gateway/device/owner/group/node、slot、subscription、活动 Telephony/PhoneAccount 关联及其他绑定必须不变；客户端和服务端应一致显示该映射已验证。结果证据仅记录脱敏值与 ID 摘要，私有备份不提交。
