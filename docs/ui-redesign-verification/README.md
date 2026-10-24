# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`dffd084247aad2c013de04b7a3ab161ff2b55e5c`
- GitHub Actions run：[40114418450](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40114418450)
- artifact ID：`12568087734`
- artifact digest：`sha256:e383997a9d308b8944d4c5db2a77f13e301f8209e91f206c5cf3b7a183be810e`
- APK：`app-debug.apk`，SHA-256 `1ddf21a3187bcc04a60316ae3f61074c8fbf80192a6bf53c7705d864e695b0be`，22248139 字节
- artifact 内未配对 smoke：`pass`（2026-10-24T17:11:09.932291+00:00）
- artifact 内配对 fixture：`pass`（2026-10-24T17:14:45.200567+00:00）
- 报告生成时间：`2026-10-24T17:14:58Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `38da594a99085d539c135e0b125ce385aef083c02525ed48e3d2ac511c904d33` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `bacf32882c28cc9f9ef4f1c8ae6b3bf9ff4d79f35187fb21fa7846c6e5121160` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `1d2db1f7a972b122377ca510617b3c6002ab8c06d73d81a434a8f10d580bccef` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `720e4f8726da6ae7878373f3d9225c7ca9024355ec45ce1a64881ada51d1f304` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `21bbac04f1f735c01812d1c3e80a724139468383a93d4a4fe30be39b517a1794` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `683fdca6d513ecf3ee067deeb7dc0d68fe9bcc1cee9c5fce4ec00f99dec66c4e` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `f149bee88e23f185b98ebc02f030c724d5dc02eb019ecaa771f3c609eb81d901` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `f39c4bb28300ac58820dd5e1be48b4d8ce560288054fe6c371028a43d80de565` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `5f0894283680e55cec5b3bfe183ff1ff026313864f34e7d3b679b843daad8977` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `811b9822e849d619dda9645a0dc79b197e275735075955c8e9f678e8b480f96a` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `f1f706e3f891ef95809b614a737447300b2bf182340df6178ceb459b6602da1a` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `d80d16532467c43dfadcc5ea6635c223d3589e70449701c4f7f855149085ed5b` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `530529f1e7c47a755cf5692ea6ed1cd35eecf1f0daf5df62bedcc73c8b5652ea` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `e142533e78fe4c0502d91b92b0d50942bb35553560acbb74b220fe02385000c2` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `260ac7b0f1c57fb0f538b03761876ecc90c28704e2fdd5a593e04ce67ef3c5df` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `260ac7b0f1c57fb0f538b03761876ecc90c28704e2fdd5a593e04ce67ef3c5df` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `0ecfd5020eab13abf28c21873bf926fd29ebc6b4194f8b554d56bfd576bffb01` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `ed0b3e90889ab2b4859443296466bb84146f77ed7a95db28c5eb495c5e69d28f` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `f1a28d970fc89f72a4a7e0bad4611f6f402eff8ed6c8ee72a7309539fecabe50` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `8a17b751136415f71753d5c930d165682d53e5370c6ed74878e48e00a7f9e0b7` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `3cb312e1af897b2b520c21d3dc19693d91396df169d5bc89c78b5aee5f590e36` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `3cb312e1af897b2b520c21d3dc19693d91396df169d5bc89c78b5aee5f590e36` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `c7a82d0f283e955a3ed7cc47504619527b769a52a84334533c51d9a763cab40c` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `4254eb07bdd6e40c270f8e085715f03f53a6e88e9555b941d80d8d00c40952da` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `b15a5350bdbdf6d2cf909497a7f9f24f025d1b0b0f242c5df61aeebd94b3f0c8` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `6f32d11110264478b97061a56210948d1763d4321875857063d5b8f6ed92e213` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `98964c6a30d06dc46530afe3ce744adcd672783457a470c5eb23f043cbc1a24f` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `95036aef87317f26a21264502868d2fb23e316b2b390593c8e33404198e2ab77` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `e2c5a14452cdbd94cbc5bf58921540acb687a1f0ae31ba2691f7c2d27621c06d` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `a5cb8f4cfec9e62116e1555d3137148306894fd19f67d2f9215891ff00bb8b27` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `48f850a16fd8152544083f0cedf0fc85479df18945d372a2929c5e1e1867432d` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `6e9e9cf114eca2c216f7a48fa343692c24f7a495604c7fa442a92cc877980461` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `140fd4fa598e6c0715eade9207400394346cc67734094fec8dc5095327070a33` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `5e44b8438baace50ce598b18915d7f1d30a60858bcd21a2935017a43778261b2` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `38bcfff774645aa2f27e2c7c00fa1c1a04febb70b64121c4cf6f3bdcd0ff5e67` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `defd1f4f838363055317d632614ccba7243f7472275e0790d8f9f5b1fe011033` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `3b7bbc627fa6e959544271c29bc5cdbc468306fe70230ce15eb5b0aacd02686f` |

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
| `results.json` | `336639edc5f2421e9e65a3f843a221588280effd38d48127d54f71added3f56b` |
| `screenshot_manifest.json` | `e57b520e5618f9e4ae9e052e47eafeb6c3bcecbebb3bd5b3728424a85abb3c16` |
| `paired-fixture/results.json` | `d6e5c1721df2f0a62763cbb3565e218d30a778608c234ccd34038caa588c81aa` |
| `paired-fixture/fixture_manifest.json` | `3a9d4c0992cfa2abd54561aebd83f750eaa5d70b27974274873fd48a227f2f5b` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
