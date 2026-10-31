# Reception v2 R1 接手文档（2026-10-31）

## 停止点与当前结论

Kiri 因周额度不足要求立即停止所有测试。本交接只基于已保存的记录和只读环境盘点；没有启动模拟器、没有发起来电、没有运行 runner，也没有修改产品代码。E2 的两条专用 logcat 采集（GW `emulator-5554`、H3 `emulator-5560`）已收到 SIGTERM；模拟器、容器、数据库、配对与接收设置均未由此改动。仍有旧的通用 logcat/等待进程，见环境章节；不要把它们误认为本轮用例仍在运行。

E2 操作员在中断前报告正在进行熄屏来电流程，但没有给出终态；对应采集已停止。因此 E2 不评分，下一位接手者须先按原始证据确认停在哪一步、检查当前 modem/Telecom 是否空闲，再决定是否能继续。E3 的 Wi-Fi 关闭尝试只得到 `default network=NONE`，没有切到 MOBILE；该项也不评分。F1–F3 的干净构建已经准备好，但本轮没有启动 F AVD、创建新 owner 或写服务端状态。D3 已完成根因层级复核，产品缺陷仍为 FAIL；C3 只有 TURN 数据通道预检，没有跑接收切换用例。

长观察 H1/H3 仍按 Kiri 先前要求暂停。模拟器测试不证明真实蜂窝 HAL 音频。

## 分支和提交索引

四仓当前分支均为 `claude/vibrant-babbage-ye0ey3`。停测时各仓 HEAD：

| 仓库 | HEAD | 状态 |
| --- | --- | --- |
| Madoka / `gsm2sip-client-android` | `5a05e89` | 本文及既有验收报告已推送；另有未提交测试 runner/证据，详见下节 |
| Homura / `gsm2sip` | `a41dd19` | 有未提交 AndroidTest runner 修改 |
| SoulGem / `gsm2sip-server` | `1528210` | 工作区干净 |
| Kyubey | `1bb9a2b` | 工作区干净 |

本轮已推送的报告/审计提交：Madoka `0c8efeb`（扩盘后磁盘实测）、`ccbe3e9`（更正 Keystore 与 SIM/revision 解释）、`d02b94f`（同签名旧版/当前版 APK 构建证据）、`5a05e89`（D3 根因层级与未授权修复范围）。本文会单独提交并推送到同一分支。

## P0/A/B/C/D/E/F/G/H 用例状态

以下状态来自现有已提交记录；E2/E3/F1–F3/C3 的 10 月 31 日新工作在额度停止时没有终态。证据路径相对于 Madoka 仓库根目录。表中的提交是包含相应摘要或证据的最近相关提交，不代表所有原始数据都已上 GitHub。

| 用例 | 结论 | 证据与提交 |
| --- | --- | --- |
| P0 | 通过 | `docs/reception-v2-emulator-evidence-20261029/p0-ui-switches.txt` — `488d3ac` |
| A1 | 通过 | `.../run-observations-20261029.md` — `1ed36ac`；三次响铃/接听，时延 1.695/2.119/2.306 秒 |
| A2 | 通过 | 同上 — `1ed36ac`；60 秒后无常驻控制服务、信令通知、持锁或兼容续约 |
| A3 | 通过 | 同上 — `1ed36ac`；杀进程、熄屏、Doze 后由真实推送唤醒并接听 |
| A4 | 通过 | 同上 — `1ed36ac`；两种取消时机均停止响铃，无迟到响铃 |
| A5 | 仅记录 | 同上 — `1ed36ac`；restricted 桶可响铃接听，但未保存可提交的 FCM priority；计划明确不以此判失败 |
| B1 | 通过 | `.../run-observations-20261029.md` — `1ed36ac`；关闭 H1 来电后由 H2 响铃 |
| B2 | 通过 | 同上 — `1ed36ac`；H1/H2 各一条短信事件，无重复 |
| B3 | 通过（修复后） | `.../b3-outgoing-retest-20261030.txt` — `67ed8f2`；产品修复 `d60e3c9` |
| B4 | 通过 | `.../b4-reboot-retest-20261029.txt` — `1ed36ac`；划掉/重开及重启后设置保持 |
| B5 | 通过 | `.../b5-call-only-h2-20261030.txt` — `f516dfb`；离线同步和 H2 唯一来电路径通过 |
| B6 | 通过 | `.../b6-call-sms-retest-20261030.txt` — `3e56f8f`；清理 Telecom 后来电补测 `.../b6-call-clean-telecom-retest-20261030.txt` — `3f6991d`；关闭短信同步不阻止来电，恢复后短信只补一次 |
| C1 | 通过（修复后） | `.../c1-reject-retest-20261030.txt` — `58ddfbc`；产品修复 `299213b`，点击拒接后网关 0.905 秒结束 |
| C2 | 通过 | `.../c2-result-summary-20261030.md` — `664fbd1`；四次竞争呼叫均有一致终局 |
| C3 | 阻塞/未完成 | `.../c3-followup-20261030.md` — `55c3306`；新 TURN 双端数据通道预检 `c9de624` 通过，但真实来电接收切换/完整媒体断言没有通过；10/31 观察器仅准备、未执行，见未提交 `.../c3-observed-v2-preparation-20261031.md` |
| D1 | 通过（H2 替代 H1） | `.../d1-result-summary-20261031.txt` — `a4ceaa1`；计划角色偏差和合成 PCM 限制已披露 |
| D2 | 通过 | `.../d2-multinode-retest-20261030.txt` — `7da54a4`；单 Telecom 来电、赢家及 `answered_elsewhere` |
| D3 | 未通过（产品行为） | `.../d3-stop-a-retry-result-20261030.txt` — `dc387dd`；根因层级补充 `.../d3-root-cause-review-20261031.md` — `5a05e89`。B participant 收到 `reception_disabled` 后结束且未响铃；确切触发异常未由现存日志证明 |
| E1 | 通过 | `.../h3-e1-diagnostics.json`、`.../h3-recovery-20261029.md` — `1ed36ac`；stream、120 秒心跳及服务端 ping/pong 证据 |
| E2 | 阻塞（额度停测，未评分） | 既有环境记录 `.../h3-recovery-20261029.md` — `1ed36ac`；10/31 新尝试只留在远端私有目录 `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/e23-20261031/`，风险单仍为未提交 `.../e23-risk-before-20261031.md`。当时 H3 已连 Node B、`wss_live=true`；来电流程无终态，不能算通过或产品失败 |
| E3 | 阻塞（额度停测且移动数据前置未满足） | `.../e23-risk-before-20261031.md`（未提交）；Wi-Fi 关闭后 default network 变为 NONE 而非 MOBILE，未完成计划的双向切换。不能用“移动数据开关为 ON”替代网络 transport |
| E4 | 仅记录 | `.../h3-e4-segment.json`、`.../h3-e4-diagnostics.json` — `1ed36ac`；已有 30 分钟记录，不作严格通过判断 |
| F1 | 阻塞/未完成 | 10/30 旧 main 短信及真实 FCM 子项通过、来电/去电没完成，记录 `.../f1-call-rerun-20261030.md` 和 `.../f1-sms-rerun-20261030.md` — `c3f4f59`。10/28 历史 F1 曾失败（Telecom `Missing call details`），摘要在 `docs/reception-v2-emulator-validation-20261028.md` — `f516dfb`，原始证据文件仍未提交。10/31 APK 来源/签名见 `.../f123-fresh-baseline-decision-20261031.md`、`.../f123-fresh-build-artifacts-20261031.md` — `d02b94f`。新干净 AVD 和 APK 已准备，但没有启动 AVD、配对或跑业务断言 |
| F2 | 阻塞（本轮未开始） | 新版 Homura 产品 APK、旧版 Homura APK及测试 runner 已准备，来源见 `.../f123-fresh-build-artifacts-20261031.md` — `d02b94f`；本轮没有覆盖安装/来电/去电/短信断言。10/28 历史 F2 失败：旧版 Madoka 去电仍 `Missing call details` 且缺计划要求的 `remaining_ring_ms`，摘要 `docs/reception-v2-emulator-validation-20261028.md` — `f516dfb`；原始证据文件未提交。本轮不能把旧失败或构建成功记作升级 PASS |
| F3 | 阻塞（本轮未开始） | 同一配对身份的旧版 Host→新版 Host 覆盖升级尚未发生；新版 APK 的构建/签名证据见 `.../f123-fresh-build-artifacts-20261031.md` — `d02b94f` |
| G1 | 通过 | `.../g1-restart-retest-20261030.txt` — `f516dfb`；重启后真实 GSM/FCM 来电进入 Telecom `RINGING` |
| G2 | 通过 | `.../g2-retest-20261030.txt` — `10db8c3`；relay 恢复后真实下一通到达且失败计数归零；首次无新 modem edge 是旧呼叫仍为 INCOMING |
| H1 push 60 分钟 | 阻塞（Kiri 暂停） | 既有观察/状态在 `.../push-environment-status-20261029.txt`、主报告 `docs/reception-v2-emulator-validation-20261029.md` — `1ed36ac`；没有完成本轮 60 分钟观察。不得用模拟推送或加速时钟补算 |
| H3 stream 60 分钟 | 阻塞（Kiri 暂停） | `.../h3-hour-partial-segment.json` 与主报告 — `1ed36ac`；历史连续观察仅 43 分 21.210 秒，不满足 60 分钟，不能外推通过 |

除表内特别注明外，详细历史结果和限制以 [完整验收报告](reception-v2-emulator-validation-20261029.md) 为准。当前只应把 A5/E4 看作计划定义的现象记录项。

## 已提交的产品改动、报告与回归

### 产品改动

| 仓库/分支 | 提交 | 改动范围 | 回归结果 |
| --- | --- | --- | --- |
| Madoka `gsm2sip-client-android` / `claude/vibrant-babbage-ye0ey3` | `d60e3c9` — `fix: start authorized outgoing calls with reception disabled` | `HostCallService.kt`、`HostCallServiceStartupTest.kt`；授权去电不再等待永不触发的来电 `READY`；保持 incoming disabled；无协议/服务端改动 | 相关 25 项测试通过；整套 258 项 unit tests 通过；lint 0 errors/58 warnings；assembleDebug 成功。端到端去电 `67ed8f2` 通过。详见 `.../b3-fix-regression-20261030.md` |
| Madoka 同分支 | `299213b` — `fix: show incoming call controls when Telecom requests UI` | `CallTelecomController.kt`、`CallTelecomControllerTest.kt`；Telecom 请求 UI 且来电仍 ringing 时打开现有接听/拒接 Activity；不自动接听 | 相关 27 项测试通过；整套 260 项 unit tests 通过；lint 0 errors/58 warnings；assembleDebug 成功。真实 FCM 拒接 `58ddfbc` 通过。详见 `.../c1-fix-regression-20261030.md` |
| Madoka 同分支 | `5b6db6b` | 只增 AndroidTest runner/mesh setup instrumentation 与记录，不改生产代码 | 用于 D1 测试；不作为产品修复 |

截至停测，未提交的变更不含 `app/src/main` 产品源文件；D3 修复没有授权、没有实施。

### 主要验收/审计报告提交

| 仓库/分支 | 提交 | 内容 |
| --- | --- | --- |
| Madoka `claude/vibrant-babbage-ye0ey3` | `488d3ac`, `1ed36ac`, `f516dfb`, `3e56f8f`, `58ddfbc`, `664fbd1`, `7da54a4`, `10db8c3`, `dc387dd`, `a4ceaa1` | P0/A/B/C/D/E/G 的摘要和逐项复测证据 |
| Madoka 同分支 | `c9de624`, `0c8efeb`, `ccbe3e9`, `d02b94f`, `5a05e89` | TURN 预检、磁盘、Keystore/SIM/APK 来源纠正、F 构建审计、D3 根因层级 |
| SoulGem `claude/vibrant-babbage-ye0ey3` | `1528210` | 服务端 R1 协议与实现状态摘要；本轮没有新增服务端改动 |
| Homura `claude/vibrant-babbage-ye0ey3` | `a41dd19` | 当前测试分支产品 HEAD；本轮没有新增生产代码提交 |
| Kyubey `claude/vibrant-babbage-ye0ey3` | `1bb9a2b` | 当前 Worker HEAD；本轮没有新 Worker 部署或代码修改 |

## 仅在工作区、未提交的改动

不要运行 `git add .`。工作区含测试 runner 源码、临时执行脚本、截图和旧轮次日志；其中部分内容带设备/呼叫元数据或原始日志，不应未经审查直接推送。

Madoka 仓库：

- 已修改：`app/src/androidTest/AndroidManifest.xml`、`app/src/androidTest/java/com/callagent/lab/HostJointInstrumentation.kt`、`docs/reception-v2-emulator-evidence-20261029/f123-fresh-build-artifacts-20261031.md`。
- 未跟踪 AndroidTest 源码：`app/src/androidTest/java/com/callagent/lab/D1ApiPreflightInstrumentation.kt`、`HostIdentitySnapshotInstrumentation.kt`、`TurnRelayProbeInstrumentation.kt`。
- 未跟踪旧轮次目录 `docs/reception-v2-emulator-evidence-20261028/`，内容有：`a1-attempt-1.txt`、`environment-h2-messaging-anr.txt`、`environment.txt`、`f1-gateway-logcat.txt`、`f1-host-logcat.txt`、`f1-kyubey-local-worker.log`、`f1-server-sql.txt`、`f1-telecom-error.txt`、`f2-a41-offer-log.txt`、`f2-attempt-kyubey.txt`、`f2-attempt-logs-sql.txt`、`f2-h1-diagnostics.json`、`f2-kyubey.txt`、`f2-logs-sql.txt`、`f2-second-kyubey.txt`、`f2-second-logs-sql.txt`、`f2-sms-ui.txt`、`f2-telecom-error.txt`、`f2-third-kyubey.txt`、`f2-third-logs-sql.txt`、`f3-after-upgrade.txt`、`f3-attempt-kyubey.txt`、`f3-attempt-logs-sql.txt`、`f3-before-upgrade.txt`、`f3-h1-diagnostics.json`、`f3-incoming-kyubey.txt`、`f3-incoming-logs-sql.txt`、`f3-second-kyubey.txt`、`f3-second-logs-sql.txt`、`f3-sms-retained.txt`、`f3-target-call-sms.txt`、`f3-target-kyubey.txt`、`f3-target-sms-ui.txt`、`firebase-config-preflight.txt`、`p0-current-20261029.txt`、`p0-h1-summary.json`、`p0-h2-diagnostics.json`、`p0-p0-h2-summary.json`、`p0-pairing-sql.txt`、`package-builds.txt`、`setup-heartbeat-defect.txt`、`setup-heartbeat-recovery.txt`。
- 未跟踪的 10/29 证据/脚本：`b3-h1-incoming-disabled-20261030.png`、`b6-switches-before-20261030.png`、`b6-switches-restored-20261030.png`、`c3-dual-host-ui-20261031.py`、`c3-dual-host-ui-observed-20261031.py`、`c3-dual-host-ui-observed-v2-20261031.py`、`c3-loopback-turn-forward-20261031.py`、`c3-observed-v2-preparation-20261031.md`、`c3-real-relay-recovery-risk-20261031.md`、`c3-stale-telecom-cleanup-risk-before-20261030.md`、`d1-complete-call-20261031.py`、`d1-current-owner-topology-20261031.txt`、`d1-dual-turn-probe-20261031.py`、`d1-fast-answer-20261031.py`、`d1-full-call-risk-before-20261031.md`、`d1-gateway-test-runner-risk-before-20261031.md`、`d1-h1-join-anr-20261030.png`、`d1-local-sim-confirm-risk-before-20261031.md`、`d1-node-b-backup-preflight-20261031.py`、`d1-node-b-private-turn-restore-20261031.py`、`d1-node-b-private-turn-switch-20261031.py`、`d1-private-turn-recovery-risk-before-20261031.md`、`d1-turn-probe-gateway-h2-20261031.txt`、`d1-turn-probe-instrumentation-source-20261031.kt`、`d2-add-b-risk-before-20261030.md`、`e23-risk-before-20261031.md`、`f1-call-rerun-risk-20261030.md`、`f123-fresh-control-20261031.py`、`f123-normal-ui-incoming-20261031.py`、`f123-normal-ui-outgoing-20261031.py`、`gw-after-whitelist-20261029.png`、`gw-battery-settings-20261029.png`、`gw-idle-main-20261029.png`、`gw-main-20261029.png`、`gw-running-20261029.png`、`gw-sim-sync-20261029.png`、`gw-status-20261029.png`、`h2-adb-authorization-risk-before-20261031.md`、`h3-diagnostics-dialog-20261029.png`、`h3-notification-settings-20261029.png`、`h3-post-pairing-20261029.png`、`h3-settings-bottom-20261029.png`、`h3-settings-lower-20261029.png`、`h3-settings-screen-20261029.png`、`h3-settings-verybottom-20261029.png`、`h3-share-chooser-20261029.png`。

Homura 仓库：已修改 `app/src/androidTest/java/com/callagent/lab/GatewayJointInstrumentation.kt`；未跟踪 `app/src/androidTest/java/com/callagent/lab/D1LocalSimConfirmInstrumentation.kt` 和 `TurnRelayProbeInstrumentation.kt`。SoulGem 与 Kyubey 工作区干净。以上未提交内容均保留在本机工作区；远端私有测试脚本/构建物与工作区文件不能假定彼此同步。

## 环境清单

### 测试机和磁盘

- Ubuntu KVM 测试机：`ubuntu@18.119.132.34`；SSH 私钥路径在敏感文件表中。
- 远端项目根：`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1`；Android SDK `/home/ubuntu/gsm-webrtc-validation/android-sdk`；AVD_HOME `/home/ubuntu/gsm-webrtc-validation/avd`。
- 最近一次扩容后记录：`/dev/root` 92 GiB 总量、58 GiB 已用、35 GiB 可用、63%；精确可用字节 37,474,652,160（34.90 GiB）。该值是在 H3 启动前测得，不代表停测时重新测量。
- 资源：2 vCPU、约 7.6 GiB RAM、6 GiB swap。避免同时运行多个 Android 模拟器或 Gradle build。

### AVD

| AVD | 用途/镜像 | 停测时状态 |
| --- | --- | --- |
| `reception-r1-gw-api34` | GW / Homura，API 34 普通 x86_64 | 运行中，`emulator-5554` |
| `reception-r1-h3-api34-small` | H3 / Madoka，API 34 普通 x86_64、无 Play | 运行中，`emulator-5560`；已配到 Node B，stream 注册；E2 未评分 |
| `reception-r1-h1-api35-play` | H1 / Madoka，API 35 Play | 已停止，保留原数据 |
| `reception-r1-h2-api35-play` | H2 / Madoka，API 35 Play | 已停止，保留原数据 |
| `reception-r1-h3-api34` | 早期 H3 | 已停止；不需要 wipe |
| `reception-f1-gw-api34` | 干净 F1/F2 Homura | 已创建，未启动 |
| `reception-f1-h1-api35-play` | 干净 F1/F3 Madoka | 已创建，未启动 |
| `gsm-rtc-host-api29-gplay-041` | 历史 0.4 联测 Host | 未运行；保留 |

停测时远端 `adb devices -l` 只报告 `emulator-5554` 与 `emulator-5560` 在线。另有一个指向不存在/离线 `emulator-5556` 的旧 `adb wait-for-device` 进程；不要把它当作 H1 在线证明。E2 两条专用 logcat 已终止；旧的多个 5554 logcat 进程仍存在，来源混有旧轮次，未触碰。

### SoulGem / TURN 容器

| 容器 | 用途 | 地址/端口 |
| --- | --- | --- |
| `reception-r1-a` | PR #33 Node A，数据目录 `data-a` | API `127.0.0.1:18110`；TURN UDP/TCP 3478 与 UDP 49160–49259 |
| `reception-r1-b` | PR #33 Node B，F/C3 隔离身份使用；其镜像 config ID 以容器为准 | API `127.0.0.1:18111`；TURN UDP/TCP 3478 与 UDP 49160–49259 |
| `reception-r1-c` | PR #33 Node C，数据目录 `data-c` | API `127.0.0.1:18112`；TURN UDP/TCP 3478 与 UDP 49160–49259 |
| `reception-r1-private-turn` | B/C3 私网 coturn sidecar | 配置挂载 `private/c3-b-turn-20261031/turnserver.conf`；VPC 地址 `172.31.15.170`，测试 helper 曾用本机 13478/13479 |

A/B/C API 只绑定测试机 loopback；AVD 到宿主机 API 用 `10.0.2.2:<18110|18111|18112>`。旧容器也在运行：`gsm-041-d7d8a2d`（API 18100）、`gsm-041-join`（18099）、`gsm-rtc-joint`（18098；占用宿主机公网 TURN 3478 TCP/UDP 和 UDP 49160–49259）、`gsm-rtc-mesh-b-api`（18097）、`gsm-rtc-mesh-b-worker`、`gsm-rtc-mesh-c`、`gsm-rtc-mesh-b-db`、`gsm-vlab-postgres`。不要为 R1 清理或重启这些旧容器。

查看容器的只读入口：`sudo -n docker ps --format '{{.Names}} {{.Image}} {{.Ports}}'`。R1 数据目录分别为 `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-a`、`data-b`、`data-c`；里面有设备/接收/FCM endpoint 等数据库状态，权限受限。

### Kyubey / HTTPS relay

- 最近已验证的真实 Firebase Worker HTTPS origin：`https://approaches-msgstr-allan-cached.trycloudflare.com`。Quick Tunnel 域名会变；停测时远端 `cloudflared-recovery` 与本地 Wrangler 进程仍在运行，接手后必须从私有 tunnel log 确认当前 URL，并核对 Madoka 构建内的 relay URL，不要假定这个域名仍有效。
- 本机 Wrangler HTTPS dev server 绑定 `127.0.0.1:8789`；SSH reverse forwarding 目标是测试机 loopback `127.0.0.1:18789`；测试机 cloudflared 将 `https://127.0.0.1:18789` 暴露为临时 HTTPS。配置、凭据与轮换方式见敏感文件表。
- 本地还运行了旧轮次 Wrangler 进程（8787/8791/8792/8788）；不能据此认定当前 FCM relay 使用它们。F/ C3 必须只使用上述真实 Kyubey/FCM 路径，不能用模拟 push 计为通过。

## 凭据和敏感文件（仅列路径与用途，不含值）

以下路径来自本机文件名盘点、远端容器挂载或既有构建记录；没有读取文件内容。附件目录在本机，不是测试机目录。轮换前先通知 Kiri，因为部分轮换会让现有 APK/模拟器无法覆盖安装或让现有节点暂时不可用。

| 文件/位置 | 用途与保管 | 轮换/失效方式 |
| --- | --- | --- |
| `/data0/codes/kiri-m7i-flex-large.pem` | 本机 SSH 私钥；由 Kiri 保管，用于 `ubuntu@18.119.132.34` | 在云主机加入新公钥并验证新连接后，撤销旧公钥并从本机安全删除旧私钥 |
| `/home/ubuntu/.ssh/authorized_keys` | 测试机保存 SSH 公钥；远端 ubuntu 账户 | 与上一项成对轮换；不复制私钥到服务器 |
| `/home/ubuntu/.android/debug.keystore` | Homura/Madoka 同签名 debug APK 的签名私钥；远端测试机 ubuntu 用户持有、Kiri 管理 | 更换会改变签名，旧 APK 不能正常 `install -r` 升级；需要新签名批次并在干净 AVD 卸载重装，不能无说明替换 |
| `/data0/codes/.codex-attachments/6302c65d-6488-4934-a5c8-559e84a42614/feef7eee-8f4f-4ec9-ba96-78152f059e30/mahoshoujo-gsm-2e867-firebase-adminsdk-fbsvc-2e85c36782.json` | Kiri 提供的 Firebase Admin 服务账号 key 文件；保留在本机附件目录，没有放进 Git；用于本地 Kyubey 的 FCM HTTP v1 凭据来源 | 在 Firebase/Google Cloud IAM 撤销该 key、签发新 key，安全更新本地 `.dev.vars` 并重启 Wrangler；不要通过聊天粘贴内容 |
| `/tmp/reception-r1-kyubey-20261030/.dev.vars` | 本机临时 Wrangler runtime secrets，含 Kyubey 连接 FCM 所需的服务账号配置；Kiri/当前本机运行环境持有 | 按上项轮换后重建此文件并重启对应 Worker；临时目录清理会丢失它 |
| `/tmp/reception-r1-kyubey-tls-20261030/key.pem`、`cert.pem` | 本机 Wrangler 本地 HTTPS listener 的临时 TLS key/cert；由 Kiri 的本机进程使用 | 重新生成一对证书并重启本地 Worker 与隧道；这不是 Firebase 服务账号 key |
| `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/google-services.json`；本机 `/data0/codes/reception-v2-r1/host/app/google-services.json`；远端构建副本 `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/build/host-main/app/google-services.json`、`build/host-current-5b6db6b/app/google-services.json`、`build/host-r1/app/google-services.json` | Madoka Firebase Android 客户端配置；包含项目/app 标识与客户端配置，不是 Admin 私钥；由 Kiri 项目管理员维护。该文件不应提交 | Firebase app/API 配置变化时从 Firebase 控制台重新下载，更新所有构建副本并重建 APK；对 key 使用项目限制，不用它替代 Admin service-account 轮换 |
| `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/a.env`、`b.env`、`c.env` 与 `soulgem/compose.yaml` | A/B/C SoulGem 部署运行变量，包含服务/数据库/TURN 运行配置；只由 Kiri/获授权测试机管理员管理 | 先按实际变量依赖生成新值、更新对应 env 和部署，再按节点顺序协调重启；绝不把文件复制到报告或 Git |
| `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/c3-b-turn-20261031/turnserver.conf` | 私网 coturn sidecar 配置及共享 TURN 凭据，挂载到 `reception-r1-private-turn` | 同步更换 sidecar 与对应节点/客户端配置后验证；只在维护窗口轮换，不能单独改一端 |
| `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/data-a`、`data-b`、`data-c` | PostgreSQL 持久目录，含 pairing、SIM 映射、push endpoint/token、会话与审计状态；服务端容器持有 | 不按文件方式轮换；撤销设备/FCM endpoint 用支持的 API，DB 口令按 env/Compose 维护流程协调轮换；不手写 SQL 清理 |
| `/home/ubuntu/gsm-webrtc-validation/avd/*.avd/` | AVD userdata 与 Android Keystore session keys、已安装应用数据/FCM token；由远端 ubuntu 持有 | 新建干净 AVD 或 wipe 会销毁身份与本机密钥；需要重新配对、重新登记并按服务端 API 撤销旧 endpoint，不能把它当作 APK 签名 key |
| `/home/kiri/.config/gh/hosts.yml` | 本机 GitHub CLI 登录凭据；由 Kiri 管理，供推送文档使用 | 在 GitHub 撤销旧 token 后重新 `gh auth login`；文件内容绝不复制到仓库 |

远端本轮没有发现 Firebase Admin JSON 或 `.dev.vars`；它们在本机附件/临时 Wrangler 目录。测试机本地的 Admin JSON 路径不要误写成远端路径。原始日志、诊断 JSON、SQL 摘要、截图和临时脚本也可能含设备标识、FCM token 或通话元数据，按敏感证据处理。

## 接手时可用的入口与复现命令

仓库入口：`/data0/codes/reception-v2-r1/{host,gateway,server,kyubey}`；四仓当前分支见上表。主要计划：Madoka `docs/reception-v2-emulator-test-plan.md`；协议：SoulGem `docs/reception-v2.md`；当前进展：Madoka `docs/reception-v2-emulator-validation-20261029.md`。完整历史及尚未提交的脚本不要直接作为评分证据。

远端登录及只读 ADB 入口：

```bash
ssh -i /data0/codes/kiri-m7i-flex-large.pem ubuntu@18.119.132.34
export ANDROID_HOME=/home/ubuntu/gsm-webrtc-validation/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME=/home/ubuntu/gsm-webrtc-validation/avd
"$ANDROID_HOME/platform-tools/adb" devices -l
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 logcat -v epoch
```

当前 GW/H3 已在运行，不要重复启动相同端口。后续需要启动已准备的 F AVD 时，先确认资源和现有实例，再按单机串行启动，例如：

```bash
ANDROID_HOME=/home/ubuntu/gsm-webrtc-validation/android-sdk \
ANDROID_AVD_HOME=/home/ubuntu/gsm-webrtc-validation/avd \
/home/ubuntu/gsm-webrtc-validation/android-sdk/emulator/emulator \
  -avd reception-f1-gw-api34 -port 5580 -no-window -no-audio \
  -no-boot-anim -no-snapshot -memory 1024 -cores 1 -gpu swiftshader_indirect
```

模拟器来电/短信的标准入口（仅供恢复测试授权后使用，号码用测试计划提供的值）：

```bash
adb -s emulator-5554 emu gsm call <TEST_NUMBER>
adb -s emulator-5554 emu gsm cancel <TEST_NUMBER>
adb -s emulator-5554 emu sms send <TEST_NUMBER> <TEST_MESSAGE>
```

AndroidTest runner 使用自定义 instrumentation；构建参数在 `f123-fresh-build-artifacts-20261031.md`。安装测试 APK 后先以 `adb shell pm list instrumentation` 确认包/组件，再从远端私有目录安装正确版本。组件类为 Host `com.callagent.lab.HostJointInstrumentation`、Gateway `com.callagent.lab.GatewayJointInstrumentation`；不要从未提交脚本里复制 owner/token 参数。若用 shell 调用，形式为：

```bash
adb -s <SERIAL> shell am instrument -w -r <TEST_PACKAGE>/<INSTRUMENTATION_CLASS>
```

F 私有 APK 路径：`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f123-fresh-20261031/`。Host 构建命令与 SHA-256 在 `.../f123-fresh-build-artifacts-20261031.md`；旧版本是测试机本地 Gradle 构建，不是 GitHub Actions 下载。10/31 本轮真正的 F1/F2/F3 runner 尚未调用。

## 未完成项、前置和授权边界

| 项目 | 下一步 | 权限/为何暂停 |
| --- | --- | --- |
| E2 | 恢复测试后先复核远端私有采集文件和 GW/H3 当前 Telecom/modem 状态；若空闲，按计划重新或从已证据覆盖的边界继续一通真实 GSM 来电，不能扩展用例 | 本轮因 Kiri 的额度指令停测；H3 到 MOBILE 的网络前置不影响 E2。不要清数据/重配对 |
| E3 | 让 disposable H3 获得真实 MOBILE transport，再做 Wi-Fi→mobile→Wi-Fi 单次切换并记录每次 `wss_open`；若标准模拟器无 APN，可在该干净 AVD 用 Android Settings 增加虚拟 carrier APN，删除即可回退；禁止 root/SQL | 当前只是 `NONE`，不等于 mobile；因额度立即停。若要改外部网络/节点配置需 Kiri 另批 |
| F1–F3 | 启动已创建的 F AVD，使用本机 Gradle 旧/新同签名 APK；以 Node B 的隔离 owner 重新配对并走生产 UI/API；F1 来电/去电/短信完成后按顺序覆盖升级 GW、Host。旧版已知失败子项单独记 FAIL，不改变旧 main | 本轮只做了构建/来源审计；无 owner 或数据库写入。新 AVD/配对属于先前授权的干净身份测试；仍需额度恢复后执行。不要误用旧 Keystore 作阻塞 |
| D3 | 如需修复，先由 Kiri 明确授权 Madoka 生产代码修改；限定 CallRuntime/HostCallService/偏好边界，补回归并在通过后单独提交 | 目前产品行为 FAIL，精确异常原因未证实。此前授权只含 B3/C1；D3 修复需要新的明确授权。当前不改代码 |
| C3 | 重新启动保留数据的 H1/H2，更新 Host 后让生产 push registration 更新到当前真实 Kyubey；先核对 B TURN 配置、有效身份及所有节点无 live calls，再单次真实 FCM 来电，观察 H1 关闭接收后 H2 接听 | 观察器已准备但未执行；因额度停测。B 使用私网 TURN 配置，结束后只使用已审恢复脚本还原，需确认所有通话空闲 |
| H1/H3 60 分钟观察 | 需另开连续 60 分钟墙钟窗口，H1/H3 均保持计划状态；只记录真实 wall clock 和要求的 endpoint/event cursor 数据 | Kiri 之前要求暂缓，需 Kiri 后续通知恢复。不得模拟 FCM、加速 guest clock 或累计零散时段 |
| 未提交测试源/旧证据 | 接手后先审查敏感数据并逐文件决定是否需要提交；测试代码若要复用需确认分支/目标版本 | 额度停测，未清理/未提交；证据可能包含标识或原始日志 |

## 仅在临时目录或容易被清理的产物

- 远端 F APK、runner、build log：`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f123-fresh-20261031/`；清理 private/build 目录会删除。
- E2/E3 原始私有日志：`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/e23-20261031/`；两条专用采集进程已停，现存日志未转入 Git。
- C3 原始媒体/隧道材料：`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/c3-b-turn-20261031/`；TURN config、loopback helper、SDP/日志均属敏感临时材料。
- D3 原始大日志：`/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f-sequence-20261030/`。
- 本机 Kyubey secrets、TLS key/cert、Wrangler state 在 `/tmp/reception-r1-kyubey-20261030/`、`/tmp/reception-r1-kyubey-tls-20261030/`；机器重启或临时清理可能删除。不要提交整个 `/tmp` 目录。
- 旧轮次 F2/F3/P0 原始证据在本地未跟踪的 `docs/reception-v2-emulator-evidence-20261028/`；它目前不在 GitHub，可能被本地清理。它含原始日志/SQL 片段，不应整体推送。
- 未跟踪的 Python observer/control 脚本位于 `docs/reception-v2-emulator-evidence-20261029/`，部分含实验身份参数；需先脱敏/审阅，才可决定是否提交。

## 发布前安全自查

本文只记凭据文件位置、用途、保管人和轮换方式，不记录任何 token、密码、私钥内容、FCM endpoint 或账号标识值。提交前已对本文扫描常见私钥块、Firebase/Google token 格式和凭据赋值模式；预期无匹配。不要对私有凭据文件执行 `cat` 或把内容贴入报告。本文应作为单个文件提交到 Madoka `claude/vibrant-babbage-ye0ey3`；不要在此停测提交中附加任何其他工作区文件。
