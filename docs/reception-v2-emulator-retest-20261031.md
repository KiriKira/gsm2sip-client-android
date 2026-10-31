# Reception v2（R1）补测（2026-10-31）

被测代码（测试时的 HEAD）：

| 组件 | 提交 |
| --- | --- |
| Madoka（`gsm2sip-client-android`） | `ad7c6b8`（含 `0703e88` 修 D3、`ad7c6b8` 重写 C1） |
| Homura（`gsm2sip`） | `a41dd19`（网关 APK `0.4.2-dev`） |
| SoulGem（`gsm2sip-server`） | `1528210`（节点镜像标签 `reception-r1-1528210`） |
| Kyubey | `1bb9a2b` |

环境沿用上一轮：三个模拟器（网关 + 两台主机），节点 A/B 为独立容器，推送走真实 FCM +
Kyubey 中转，网关侧为合成 PCM。两台主机用覆盖安装换成本轮 APK，数据与配对保留（未清数据、
未重新配对）。

## 结果表

| 用例 | 结果 | 关键证据 |
| --- | --- | --- |
| D3（第 1 次） | PASS | 停活动节点后经另一节点响铃 9.66 s，`incoming=true` 与 revision 未变 |
| D3（第 2 次） | PASS | 同上，6.15 s；推送 13.979 → 响铃 16.775（主机时钟） |
| C1-a（亮屏、Madoka 在后台） | PASS | 只有带「接听/拒接」的通知，无整屏通话界面；拒接后网关 1.88 s 结束 |
| C1-b（熄屏锁屏） | PASS | 直接出现全屏来电界面；拒接后 1.12 s 结束 |
| C1-c（Madoka 在前台） | PASS（附观察） | 直接打开通话界面；拒接后 1.20 s 结束，两段文案顺序正确但第二段滞后 |
| C1 权限（全屏通知被撤销） | PASS | 设置页显示「未允许 · 锁屏时来电只显示为通知」，点击进入系统授权页，恢复后回到「已允许」 |
| B1（H1 关闭来电） | PASS | 只有 H2 响铃（3.14 s）；两台节点上 H1 的 `incoming=false` |
| B4（划掉应用 + 重启后仍关闭） | PASS | 两次之后本地开关仍是关闭，节点侧 `incoming=false` 且 revision 未变 |
| C3（响铃中关闭来电） | PASS | 关闭后 H1 立即停止（`reception_disabled`）、H2 继续响铃并可接听，通话未被挂断 |
| A1（熄屏 + force-idle 后来电） | PASS | 两轮都是 4.3–4.6 s 响铃；接听后服务端 `accept` → 媒体协商 → `cellular media active` → `active`（先前按 A 节点读成 `answered_elsewhere`，见正文更正） |

## D3：停掉主机所在的节点（验证 0703e88）

**拓扑说明（与计划的一处差异）**：计划要求「H1 当前活动节点是 A，然后停 A」。本轮的节点优先级
在 App 里只能通过长按拖拽调整，而合成触摸事件在这台 AVD 上不会触发拖拽（多次尝试只产生滚动），
因此保持现有优先级：**H1 的活动节点是 B，H1 同时配对 A 和 B，停 B、由 A 接单**。语义与计划一致
——被停的正是「主机此刻正在使用的那台节点」，另一台健康节点负责把来电送到。

每轮步骤：确认 A/B 健康、无进行中的呼叫、H1 在 A 与 B 上的 `device_reception` 都是
`incoming=true` → `docker stop` 活动节点 → 立刻注入一通真实 GSM 来电（同一秒内）→ 记录时间点 →
接听 → 结束 → 重启节点并确认恢复。

第 1 次（13:17 之前那一轮，`d3-1`）：

```
节点停止 / 注入            09.899 / 09.900 (lab UTC)
推送 kind=call            13.979 (主机时钟)
通话界面进入 RINGING       16.775 (主机时钟)
注入 → 主机 Telecom RINGING 9.66 s (lab 时钟，轮询粒度约 0.5 s)
```

第 2 次（`d3-2`）：

```
节点停止 / 注入            51.797 / 51.797
推送 kind=call            53.620 (主机时钟)
通话界面进入 RINGING       55.533 (主机时钟)   → 推送后 1.91 s
注入 → 主机 Telecom RINGING 6.15 s
```

两轮共同结果：

- 健康节点上 H1 的参与者在响铃前**没有**出现 `reception_disabled`，两轮都没有；
- 健康节点上 `device_reception` 全程 `incoming=true`，revision 保持 2，未因这通来电变化；
- H1 在活动节点停止后经健康节点响铃并接听（参与者 `accepted`，随后建立媒体会话，`cellular_active=t`）；
- 日志里可见第一次 `LISTEN` 前台服务启动后约 1 s 又发起一次，随后切到健康节点；`HostCall` 没有
  失败文本（`onListeningFailed` 不打日志），因此以「节点切换 + 无 `reception_disabled` + 按时响铃」
  作为修复生效的证据。

两轮结束后重启被停节点，三端恢复正常，节点列表回到「使用中」的健康节点。

## C1：拒接与来电界面（验证 ad7c6b8）

前置：`appops get com.callagent.host USE_FULL_SCREEN_INTENT` → `Uid mode: allow`；设置页「全屏来电」
显示「已允许」。每轮都是真实 FCM 推送 + 真实 GSM 来电，H2 开飞行模式。

| 情况 | 界面表现 | 拒接后 |
| --- | --- | --- |
| a. 亮屏、Madoka 在后台（桌面） | 焦点在桌面，没有整屏通话界面；通知栏有带「接听/拒接」的来电通知 | 1.88 s 网关结束；两台节点的参与者都是 `ended/declined` |
| b. 熄屏 + 锁屏 | 注入后 3 s 设备唤醒，焦点直接是通话界面（来电 / 接听 / 拒接） | 1.12 s 网关结束；显示「已拒接，正在等待网关让其他主机停止响铃…」 |
| c. Madoka 在前台 | 直接打开通话界面 | 1.20 s 网关结束；两段文案按顺序出现 |

补充观察（记为问题，见「遗留」）：c 场景里第二段文案「已拒接，所有主机已停止响铃。」在服务端
已经记为 `declined` 之后仍延迟了一到三分钟才替换掉第一段；b 场景同样先停在第一段（约 89 s 后才
变成第二段，且当时是重新进入界面触发刷新）。两台节点上的参与者终态都是 `ended/declined`，
没有迟到响铃、没有两次响铃、也没有两条来电通知（每轮 `dumpsys telecom` 去重后只有 1 个
`Call id=TC@…`）。

权限项：`cmd appops set --uid com.callagent.host USE_FULL_SCREEN_INTENT ignore` 之后，设置页的
「全屏来电」变为「未允许 · 锁屏时来电只显示为通知」，点击跳到系统「全屏通知」授权页；恢复为
`allow` 后回到「已允许」。

## B1 / B4：关闭来电的选择

B1：H1 关闭「后台来电接收」、H2 开启，注入来电 → 只有 H2 响铃（3.14 s），H1 的 Telecom 没有
RINGING；两台节点上 H1 的 `device_reception` 都是 `incoming=false`（revision 3 / 21）。

B4：接着 B1 的状态，`am kill` 后重开 → 本地开关仍是关闭；`adb reboot`（60 s 完成）后再打开
→ 仍是关闭；节点侧 `incoming=false`、revision 未变（3 / 21）。

## C3：响铃中关闭来电

两台主机同时响铃后，在 H1 的设置页把「后台来电接收」关掉（设置页预先停在开关所在位置、
把 App 切到后台再接来电，响铃后回到同一页面，2.49 s 内完成点击）。一轮结果：

```
注入 → 两台同时 RINGING      5.76 s
切回设置页并点击开关           注入后 7.2 s（resume 后 2.49 s）
关闭后 H1 RINGING            无（立刻停止）
关闭后 H2 RINGING            TC@8（继续响铃）
服务端通话状态                ringing（没有被挂断）
H1 参与者                     ended / reception_disabled（两台节点都是）
H2 参与者                     accepted（随后在 H2 上接听成功）
```

结论：PASS。这条同时验证了 0703e88 没有破坏「用户主动关闭」这条路径——关闭仍然只影响本机，
不会把整通电话挂掉，另一台继续响铃并能接听。

> 说明：这个用例在本实验室跑得最费劲。模拟器只有 2 vCPU，`uiautomator` 单次 dump 就要 1 s 以上，
> 在 26 s 的响铃窗口里「退出通话界面→滚动到开关→点击」来不及；尝试 4 次才在「设置页预先定位 +
> App 切后台 + 响铃后回前台一按」的方式下拿到有效结果。失败的那几次都是时序问题（点击落在通话
> 界面或窗口已过），不是产品行为。

## A1：熄屏 + force-idle 后来电

H2 关机（等效于「只有 H1 在线」），H1 熄屏后 `cmd deviceidle force-idle` 进入 IDLE
（`mState=IDLE`），保持 60 s 再注入真实来电：

```
第 1 轮：idle 60 s → 注入 → H1 RINGING 4.34 s → 接听
第 2 轮：idle 60 s → 注入 → H1 RINGING 4.60 s → 接听
```

接听后的服务端控制序列（两轮一致）：

```
13:41:54.27  RTC call created（节点 B）
13:42:01.42  control committed event=accept revision=2
13:42:01.47  call media selected media=webrtc
13:42:01.68  RTC signal offer / 13:42:01.90 answer
13:42:01.97  control committed event=winner revision=3
13:42:02.86  RTC cellular media active
13:42:02.96  control committed event=active revision=4
13:42:08.10  control committed event=ended revision=5（脚本主动终止 GSM 呼叫）
```

**判定：PASS。** 更正先前的记录：那两轮我把节点 A 的参与者读成 `ended/answered_elsewhere` 就判成
「接听未确认」，实际上答案走的是节点 B（`accept`/`winner`/`active` 都在 B 上），A 上出现
`answered_elsewhere` 是正确的语义。两轮通话都建立成功，只是脚本 6 s 后自己结束了呼叫。

## 限制与偏差

- 模拟器只有 2 vCPU，三台 AVD 同时跑时出现过 System UI ANR，脚本加了前台校验与重试；这不影响
  用例判定，但会把单轮时间拉长。
- 网关侧音频是合成 PCM，不证明真实蜂窝 HAL 音频；E3（移动数据切换）与长时待机观察按计划不做，
  留给真机。
- 每轮设备的屏幕、通知栏、键盘状态由脚本恢复，未清数据、未改节点数据库。
## 修复：拒接后的第二段文案

C1 里发现的那个问题已经修了（同一分支，随本报告提交）：

- **根因**：`confirmDeclineEverywhere` 的回读线程在呼叫控制服务已经停下之后才拿到结果，
  而 `CallRuntime.notifyChanged` 只在 `service` 非空时才发状态广播——于是回读结果的
  `updateEndedNotice` 写进了协调器却没有送到正在显示的通话界面，用户只能看到第一段
  「正在等待网关…」，要等重新进入界面（`onResume` 重新渲染）才变成第二段。
- **改动**（3 个文件，约 20 行）：
  1. `CallRuntime` 增加进程级的 `appContext`（由服务 `attach`、`MainActivity` 的偏好恢复、
     `CallActionActivity.onCreate` 提供），`notifyChanged` 在没有服务时回退到它，状态更新
     因此能继续送到还在屏幕上的界面。
  2. `confirmDeclineEverywhere` 从「4 s / 12 s 各查一次就定稿」改成「先 1.5 s、之后每 3 s
     复查，最多 35 s」，网关一结束通话就立刻换成终态文案；只有超时才用「网关尚未确认」。
     这样既不会长期停在等待文案，也不会因为网关还在等别的主机而提前说「尚未确认」。
  3. `CallActionActivity.onCreate` 把自身 context 交给 `CallRuntime`。
- **测试**：`CallActionActivityTest.endedNoticeStillReachesTheVisibleScreenAfterTheServiceStops`
  在没有服务的状态下写两段文案，断言屏幕上的文字随之更新。**修复前该用例失败**
  （2 个 SDK 变体都挂在第一段断言上），修复后通过。
- **门禁**：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL；
  新 APK 已覆盖安装到 H1（数据与配对保留）。

**还没有做的**：这条修复的端到端复测没能跑完，卡在网关侧而不是客户端。今天下午网关进程被
模拟器看门狗以「主线程停滞 313 s」杀掉之后，呼叫链路没有自己回来。修复过程与现状：

- 重新拉起网关测试 runner（合成 PCM）：`READY`，网关心跳正常，`device_reception` 仍是
  `incoming=true`；
- SIM 重新同步并在服务器确认（`sim_bindings` 新身份 active + `identity_verified=t`，
  `mapping_revision` 到 6），runner 也读到了新的 `sim_id`；
- 注入真实 GSM 呼叫后，网关能识别到来电（`Bridge: GSM_RINGING - Incoming GSM call`），但紧接着
  `Call not offered: SIM account mapping is unavailable` / `Incoming GSM call has no verified SIM
  account mapping`：`SimRegistry` 里这条映射没有 Telecom 电话账号（`phone_account=""`），而生成
  电话账号要走网关的语音服务，其「设备诊断」在本机报 `系统通话音频采集: needs Magisk`——这台
  AVD 只有 userdebug 的 `/system/xbin/su`、没有 Magisk，按任务规则也不做 Magisk 相关操作。

所以本节的结论是「代码与单测已验证，端到端待网关环境（语音服务/电话账号）恢复后补一次 C1-c」。
C1 的三种界面行为本身在修复前的构建上已经按原判据通过，这条修复只影响第二段文案的出现时机。

## 结论：真机阶段与待修

可以进入真机阶段的：D3（停活动节点后经健康节点响铃，两轮都在 10 s 内、没有再把接收设置写坏）、
C1 的三种界面形态与「全屏来电」权限项、B1 与 B4（关闭选择的持久性）、C3（关闭只影响本机、不挂断
通话）。这些在模拟器上都是真实 FCM + 真实 GSM 呼叫验证过的，剩下的差异主要在真实蜂窝音频、
真实锁屏与厂商的后台限制，属于只能在真机上看的部分。

还要先处理的：

1. **C1 第二段文案滞后**：已修（见上一节），代码与单测通过，端到端复测待网关环境恢复。
2. **网关侧的呼叫链路**在本轮最后崩过一次（模拟器 2 vCPU 上网关进程主线程停滞 313 s 被看门狗
   杀掉后，网关的拨号/语音服务没有自己回来，注入的呼叫不再进入 `call_sessions`）。真机上不会有
   这个组合，但说明网关侧缺少「进程被杀后自动恢复呼叫能力」的兜底，值得单独确认。
3. E3（移动数据切换）与全部长时待机观察仍需真机，模拟器上不成立。
