# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`a4beece516941920d972ca6cff955e63566adeb7`
- GitHub Actions run：[38715360472](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38715360472)
- artifact ID：`11962983410`
- artifact digest：`sha256:641d87ba328adb0ef423bad5cad714563a2c7530799a739f72b392e115a64890`
- APK：`app-debug.apk`，SHA-256 `a91816ad72cba87978253ae07b40405fef2c8d558ca68165f110c49728771d82`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T09:53:26.955837+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T09:57:03.295140+00:00）
- 报告生成时间：`2026-10-15T09:57:15Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `2d288fdec31fd5b392a74a151a8f80d1964065c3816fdddc99cb1d16e08be447` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `4503d8b70b5550087d0ed8bbea8bef3b87b05402c33a4336542f22ec4b0a069f` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `e28858e18bd169090e6ae5a26ff139468193484a2fc642eac6f36adb95039c4e` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `1e1300350244d74af9947518f6a94f620dc2cc50c4882e36a2ce0c58c6e920f4` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `eab490adf53459aac63638f2b349404c74892288f5b8b21093023ef0c226fcdd` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `ab22d6717993c44c7e8fd6b5381ca82293d7db0ac0e60e7961e527b945630a3d` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `ceaa714f305bdabd5509bf7c012f0b05a2da51f84f7d06b563c5e010099771ba` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `d7e73acc432aa725a38aa279d13024ac72d8bc37ec866eacd3ecbb98c61d8a27` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `e46cc1eefc2bc8198e5f0e7dca07eb2384a5c25aea694ce03cbb03093a8487bb` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `9890afd823f796ef1bd9aec7640e66a8467b8ba3e488aeff2c3df78ea170f9d4` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `a3309bbe7dac4eaa3df1aa5639a3a6a556dbf68364902a7854bfecbb4ab57b01` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `31853beec820424f98119e0bb7430924b4f5d2ca9022435aa96e7e1912ec7c4b` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `60f9c94f04329908d0362d5a8647736e2aa9b14380e900265159ed0d9ff7931b` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `38d4ea59a6e9456f7d280aaac852c1ce9a394f1a07ed09e6078470bb58d620d5` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `25e934025d74d5c245d7c67d6fcb46e8e17bebadec7eee4e7ef9b635a918c335` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `25e934025d74d5c245d7c67d6fcb46e8e17bebadec7eee4e7ef9b635a918c335` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `6177fda4b577d06d344f761828f72d0cd6b319bb4d81315b55eb6ef52a0f114b` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `1a4569c954b8a241df9195608c23e3b6b66402259566c7cc043b5353242b395e` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `7f0e6f6dacfce00429969b1e6cf4d2c0b56097aa8be388055648f6b92de2b1eb` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `3cee13d05b7a80e1b4a1d671e9e6a14f3a6616d2c9b7ce34043175251f86c5ea` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `52a9ee25564e104604abf97c514fd7c085317e000e11b082faca5a528f924335` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `52a9ee25564e104604abf97c514fd7c085317e000e11b082faca5a528f924335` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `56ae69875a0ded6376a6bfc0a6baa089d4be9606bfa6af904d1c49d62c6a807b` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `4b1c6cd6ec456d8cd45c121e8e3f3ab7af64c903abaccc47f643f2f4afd4a7ad` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `56ae69875a0ded6376a6bfc0a6baa089d4be9606bfa6af904d1c49d62c6a807b` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `f22d1956b3ccd40732aad15b6306b2ffcf096a789573c013d530b95f88bdd791` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `ec75490f030c7c807e4f866d614f4c232e406239802cce3a3447c70ce6c73436` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `a22d46a23cad444039f86dce299a40f74a64a58e0ba6c5d71b3caeb205fb91e5` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `86f43ea1c06ec54e29c3f9c62532d7ca097f0dbc408e26baab5d42e9e1f747bd` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `59c730050ae89a03cea09d6e92cb75c2be0ec0fe2e47e0dc7875cbc922b844c4` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `b42dc3cda276a07cbca336b34f6ac51f8683a2009891be770add8f0e2715d139` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `05b8735a37865aeb4e95211dc6881be28d77288aa2f708eca015f1dfa151f1b9` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `4369df28cb70975afdafcc03386b5e4754e73a8283947837f9bffdbcbc29910c` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `f1a8910f6ada78b3c621546dfe23b657fe270f19d13b02dd3a5b44f04c817aff` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `c52ea1033d01fadc41b070e545210c8900f88e73c1781a13e9797954da97b1b7` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `c1a96f02fa3b5095031397dd0a7d2ccf47efc199df39698b2733d868c82b97cd` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `7da0eb9a5a74e3cd15f89c810536ac13ec9973e533186ac6c926e2b65ee68ad8` |

## 截图

### 未配对界面

#### 三标签导航

![三标签导航](images/host_navigation_initial.png)

### 电话与拨号

#### 未配对电话记录页

![未配对电话记录页](images/host_call_history.png)

#### 独立拨号键盘

![独立拨号键盘](images/host_dialpad.png)

### 短信

#### 短信会话列表

![短信会话列表](images/host_sms_thread_list.png)

### 设置

#### 设置与配对表单

![设置与配对表单](images/host_settings_tab.png)

#### 设置中的短信备份入口

![设置中的短信备份入口](images/host_settings_backup_entry.png)

### 窗口适配

#### 主界面合拢窗口

![主界面合拢窗口](images/folded_ui.png)

#### 主界面展开窗口

![主界面展开窗口](images/unfolded_ui.png)

#### 显示挖孔时的主界面

![显示挖孔时的主界面](images/hole_cutout_enabled.png)

### 短信备份

#### 滚动后可见的备份入口

![滚动后可见的备份入口](images/main-backup-entry-host.png)

#### 短信备份与归档页面

![短信备份与归档页面](images/backup-overview.png)

#### 备份页面横屏

![备份页面横屏](images/backup-rotation-landscape.png)

#### 备份页面竖屏

![备份页面竖屏](images/backup-rotation-portrait.png)

#### 备份页面合拢

![备份页面合拢](images/backup-folded.png)

#### 备份页面展开

![备份页面展开](images/backup-unfolded.png)

#### 挖孔安全区中的备份页面

![挖孔安全区中的备份页面](images/backup-cutout.png)

#### 合成短信归档导入预览

![合成短信归档导入预览](images/backup-import-preview.png)

#### 导入后的归档历史

![导入后的归档历史](images/backup-imported-history.png)

### 配对数据界面

#### 配对状态下的三标签

![配对状态下的三标签](images/paired-fixture/paired_three_tabs.png)

#### 双 SIM 合成通话记录

![双 SIM 合成通话记录](images/paired-fixture/paired_call_history.png)

#### 从通话记录预填号码的拨号页

![从通话记录预填号码的拨号页](images/paired-fixture/paired_dialpad_from_history.png)

#### 配对状态下的独立拨号键盘

![配对状态下的独立拨号键盘](images/paired-fixture/paired_dialpad.png)

#### 三条合成短信会话

![三条合成短信会话](images/paired-fixture/paired_sms_threads.png)

#### 合拢窗口中的短信会话

![合拢窗口中的短信会话](images/paired-fixture/paired_sms_threads_folded.png)

#### 展开窗口中的短信会话

![展开窗口中的短信会话](images/paired-fixture/paired_sms_threads_unfolded.png)

#### 既有短信对话与回复框

![既有短信对话与回复框](images/paired-fixture/paired_sms_conversation.png)

#### 新短信编辑页

![新短信编辑页](images/paired-fixture/paired_new_message.png)

#### 选择 SIM 2 的新短信页

![选择 SIM 2 的新短信页](images/paired-fixture/paired_new_message_sim_b.png)

#### 横屏且键盘显示时的短信草稿

![横屏且键盘显示时的短信草稿](images/paired-fixture/paired_new_message_landscape.png)

#### 合拢窗口中的短信草稿

![合拢窗口中的短信草稿](images/paired-fixture/paired_new_message_folded.png)

#### 展开窗口中的短信草稿

![展开窗口中的短信草稿](images/paired-fixture/paired_new_message_unfolded.png)

#### 配对状态下的设置

![配对状态下的设置](images/paired-fixture/paired_settings.png)

#### 配对设置中的短信备份入口

![配对设置中的短信备份入口](images/paired-fixture/paired_settings_backup.png)

### 合成通话界面

#### 合成出站呼叫准备界面

![合成出站呼叫准备界面](images/paired-fixture/call-synthetic-dialing.png)

#### 合成通话中界面

![合成通话中界面](images/paired-fixture/call-synthetic-active.png)

#### 合成通话键盘界面

![合成通话键盘界面](images/paired-fixture/call-synthetic-keypad.png)

#### 合成来电界面

![合成来电界面](images/paired-fixture/call-synthetic-incoming.png)

## 结果文件校验

| artifact 文件 | SHA-256 |
|---|---|
| `results.json` | `3267710977c7288376c583087883189da2afda517bf590fd96e0d69cead47bb2` |
| `screenshot_manifest.json` | `4e51bf18794364d011b016f4282bc40a1338e755387ab960e020dd1e37a43081` |
| `paired-fixture/results.json` | `2ef75a6cf7e561f8f482ee539ece7916d5e9f91b87d1f702c84a212f48efc376` |
| `paired-fixture/fixture_manifest.json` | `cebec1cefca02e27951680da587de05ccc2e88dbc5764639d3ec44ff698b8d58` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
