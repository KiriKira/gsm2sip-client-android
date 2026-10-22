# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`4cef1c7d23a1776b99370408d244ba0742730981`
- GitHub Actions run：[39808043007](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/39808043007)
- artifact ID：`12440490350`
- artifact digest：`sha256:abf176b0d9a68735febc240e1d0267e99f640ca6f6e5b4b71f624d134e30b4bb`
- APK：`app-debug.apk`，SHA-256 `b2032549684ecff426a95a7236ee98227286f2c1783514f3d0e4726e2494f313`，20854717 字节
- artifact 内未配对 smoke：`pass`（2026-10-22T15:55:20.887796+00:00）
- artifact 内配对 fixture：`pass`（2026-10-22T15:59:10.192526+00:00）
- 报告生成时间：`2026-10-22T15:59:23Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `b4b7935e51abea3b05b02a3236e15e1f25a1036ccbc3edc6c58478e2d8dc6b21` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `269500208600c2b3148703e9d2000d70560e4ceaef274219f6f5233ff216da80` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `b6cccf2afeba6d1b1e6bef99ff7e24178b722e0bf9871e27ba6067704c333199` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `be6a43b88610745d7353dc5fcb455e420ff7155baedbb9e8bbdc9d8baf9a1e90` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `786c2f0e802b537894ea9fe20ca7b4b4995975ca8688abc4b6fef95088722121` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `d690adde710c9357e5c0b91b534fb6b2e7ef0395ad87c4da13b36f430f27d6a0` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `59cc6bf18895598b05a4448d29bb4f2f78e807b99a890edf72f969e2d16aab39` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `e468d3653d1258aee86839c26d94e5b637d7a4043bceedcb972f2b56da9a62cc` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `f988269376b252308a4a08b3d70a52c3697d87ae3b115030b339ba00ea8a1ff1` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `8391fa74a21608f64b3b5333b66b314ad1656fc05b2f7844fc02d5e446036c2a` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `245f7d9b54a4f3e434e7b1fbbca04ad28ebff87cd8e966536bf668231df67c78` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `1084d9ecd9bfbaa2fe2ad283cc4ca5e6cbc10027a50ad7d2404a33e3a50b00a7` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `9e3bea5883845541899728fa5f335d6094999df1a2400686391ca6f603c41108` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `6fa7f4e9655625eb79ef85f325db15ece59df4783cc88eea1606065bc90a0daf` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `39095cded2cbeeea6dc68174f681930b6cbd3ae0d601a4c0de1a40c05e66de2a` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `134b040df27eb7aeceb8711647e9f53d293eb9d945d2966f7a7e9b336735b7ed` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `181549eed6aeb50dd76abe251ed891aaa62fb66cab58e856509a5be731517de4` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `e5865e25d1753f45e2b0fd74fa566c166df790448c3db918bea4ff7853942b8d` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `0efc9152ad210029b4a17c5b165009677aef3674b38cf526de5e3a7b761c427f` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `2b15889d2c3692bd241926fb6c1f2a03e5e87e94d74737cd013d26bc16f44840` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `37e9203cad029906038bcd57fbe632f6ed48468f14f0847ad24e4e55da16bf1b` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `099b7bb051ffc9d75123c81457d328f662d56bf53838a0893650977edcf47383` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `c7dc2f65a1b9312835a0b74643b4f876bcd14b0056b5fe383add01a7c3b78238` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `91ce59d24a923545040514fd485899ab1272bf1bac0e121276b500bc88fcef94` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `c7dc2f65a1b9312835a0b74643b4f876bcd14b0056b5fe383add01a7c3b78238` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `3b403780364a3d8914ed2c835b5b4fe2004ccd12039d65ef67e26930ebf30fec` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `f6e362a57bbfd226a0d69835c783d9c42fcf248bcac73bc9235756bb2e01302e` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `748b15618ed60a843691e1970d064dedccc8a113e1a41ddae4fae0933f34f895` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `4b68965eacdad7703ad6b04f5a826deae25b06ee15e53763236e36dcae7918eb` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `a1ff9914ef10472df27c2aa3135cded21ea4bb71d8b838e1a9803655ec1f1bc7` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `0f7c919c45449904427ab352d8ce3889bb9516b79fe127cc62cade7f255b9e66` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `9aa32ad6f7a88e703a875db9b7cf635ae04ced4d7bc6ec4f3ce5bf79c6ef3153` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `0a754c203641d195f9fbf63b2bcefac205afc912f091547882df16f6053d8239` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `e90e532425fe65663cae7ef443ac8dcc0ddb630a9dec027fea179b997fa7a8bc` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `8d934d146ab02d78d472f8637634aef8d78411e28a8873a4052c6b875b52f262` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `20398b897ded14f665b454300398b52374c97c46d72e0b223f2c4876f1f4e992` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `f74609cf39a9f703dead87310f243d13617508439f2e20ddf4cae2039e41ed34` |

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
| `results.json` | `7619dd99738455e9128f8e5265cb4ab51a18c4dcf50ad88852461f708e196b41` |
| `screenshot_manifest.json` | `1f1825bd7c11e566e6e1571fcf46665f66b52e83c11a05d7559e3a8b90138c22` |
| `paired-fixture/results.json` | `67248bf9020176ab5b62db0149d154b8392d8197831c783ce9968c02239422a2` |
| `paired-fixture/fixture_manifest.json` | `a64d0b3580d2ed4f6d0ec288b73be37a5d6f542e0f8feeeae2e49ae484368ffc` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
