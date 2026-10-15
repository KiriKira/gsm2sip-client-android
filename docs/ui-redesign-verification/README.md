# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`4c82dcc06ad7a8b19fc64e37e18b326af2cb07d1`
- GitHub Actions run：[38734858852](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38734858852)
- artifact ID：`11973701799`
- artifact digest：`sha256:df6f076240cea55b03c5b9c98a37c3966de13211e9f9760afb8bcefeba45a4ee`
- APK：`app-debug.apk`，SHA-256 `dd6dbac4a6e2643337859786d24fa35df7d1234a7db38bc336b44573a31c88bf`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T12:36:05.142240+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T12:40:02.946085+00:00）
- 报告生成时间：`2026-10-15T12:40:18Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `5618a9becf90ccbd7a2d1be3920a363137b59f0318617db329d3a2939c6213eb` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `89f02edee4fb20d3fe2bfaf1be1b126baa5ae6d9c48aaf4a793409e6eb95c837` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `1960cc1f6a70e90e23e36300b04f9283c00daa817b3d556b46632f4d5ccfe529` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `562671d3d20d37db6fb8008bc7163b47c78c65ae8a75d4cd584c3583198da912` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `f4b4a7b5bc71eb18493a388ff3d41eaa7ae7708b23db267c217c2b3c1fbf5ed2` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `3ab96dd1edfebd929db763352e9046072558042a5e6544b7c3e543a52a64f356` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `fb8de1155859b0923a632cd2935cf4477a47eeca6babd8ebfbc962452ab3e90b` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `8b57de28112d03159a60613a6b354231b4e6b59a6791c85198763861b27954c2` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `20a84c09313cbb816ba4c2854a350ab39559ff31ffa21c09f31d1904a7b46227` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `3321e46302982f735a4e6855ba33a179c10f7a63b8aa9d636ffc15cf38616110` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `6a6e75041c2c61370f0a3889ea0459d12439a92dc1e72b117826abcc482d6fd9` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `e840cb8355cead04746f1282d0a26eb08430a91004d93ad93d243696ce3d3688` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `f890b953336bf4ec95590e3ab3d51cbf07b03cdf3bab7dc062e3b88b530821d5` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `a5f3812cb30d3023c4c72674feadb0bffb8115d60b0dcf12318d5418494c0f02` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `6df32c16b773a0675d557530a2768df8d9b6767cd624e5874693b98f1d5729bf` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `6df32c16b773a0675d557530a2768df8d9b6767cd624e5874693b98f1d5729bf` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `c9283c1fe52277659a8d037f508d0141497079e2e4536cce469bab2e0aebceee` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `104b27a88c05d6a921e9007edce47388116f5b7111c536da008a150f8118cf31` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `7307b651cf6e7d6f7b3dac85a755870ca465f9ffd2a8b4f016500771d6e244fc` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `d1c19c889df12c6476e14e3d6366be4981a09c17736590d132ee2e9771d21393` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `e57aed2aac02615a0e1dfbed51cb49c1a2bd62e7a87df3f9213889fe7039402d` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `e57aed2aac02615a0e1dfbed51cb49c1a2bd62e7a87df3f9213889fe7039402d` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `3af325bf81f12eabad30a76aaf82e5006ba080d295a6e91c109bd183d5bd86ed` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `da9186052753ccc0ba5f01c1f3157138ff31e17d30a264781794513ca0ac3ffb` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `3af325bf81f12eabad30a76aaf82e5006ba080d295a6e91c109bd183d5bd86ed` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `7667ffc52730fa312fd87280faa726430ff84b512fc7fe6e796e3f8f98d5731b` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `cd58cb107bb491a80aaf2635314f7bbdf068f192268ce6fb6220a1dd6fb527ab` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `fe71b6f14a657a90b2a0c28d79ae1713c771dda4a79c29046d1ba82f6d6d291e` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `6a545388134313bb59fc256d5b6d7c5abcddef9076854f56d5c6bea151561a49` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `0f73e51f0ad4e068f386bb511872331ad50f939b33d1c8782381ce0bd114c2bc` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `637f51f0b69ebe2edc380fe2b79a35521c8c0d2a4b267a5ecc9ad38706a072ad` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `f8b0e1fa514b18252164c152fa16a28b782251d8113446c9502b893ae8a08fa5` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `4a8876d13689958e92a7b826c78a325a1f15060990c65753e14e0a4862c78fea` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `283470c4a3a9599f08fab16f82b9c2732254eb1acddc1af75ab06e58078f71cf` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `ed3efd1cd9bc17fb9a3d17137d9f9d78c8d85f0c16546a135a4d0db2614fcae6` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `76e8f4d436d928cdc9b7c5bf9fa26a36aea8aef919dc8e3c3f84147edb6c85ba` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `fb07e6e0051340a7825cf54c540cd61f23a737febeddd2f1959162ec610cfff5` |

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
| `results.json` | `37fb99142098ca4d1a0ec9969bc33df0e3d94508f75a042ff26837d4f0bec3ec` |
| `screenshot_manifest.json` | `2de7af079561926115da326d1bc686bd8087682a0061f6ab40f75e99bd18b1c0` |
| `paired-fixture/results.json` | `ffd2cabdfb288c11aba7dbcf19cfa1a39e7fbd4dd5555d68eb3e017a78c8d869` |
| `paired-fixture/fixture_manifest.json` | `8f4a61ce795146eba2a72f6136a5eb9411ca9ca75691d71ba307fbc612e1f505` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
