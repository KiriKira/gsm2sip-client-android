# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`40f32b0439c83bf3ef57697ecab6c787d1dd3755`
- GitHub Actions run：[40495363853](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40495363853)
- artifact ID：`12730474682`
- artifact digest：`sha256:0c17462de9803c80bfbea4bd9154798a30ceb845a6eafd6393a04ac9b606f815`
- APK：`app-debug.apk`，SHA-256 `713eb44dfae76af7c1289113a52ea1a3861b6d5f65fbd7f6691dbcb2ff5e5b3c`，22348560 字节
- artifact 内未配对 smoke：`pass`（2026-10-27T11:29:28.023717+00:00）
- artifact 内配对 fixture：`pass`（2026-10-27T11:33:30.682537+00:00）
- 报告生成时间：`2026-10-27T11:33:47Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `c615e3313cd91a930ebbffd096764123826e3366ca682c37d4a6dd5c033e63c1` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `71afbc841413b775cbde8de4725d1d014d0fd9b5ee514695446da18e3626a60d` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `b5840563100cfd9e28ece4d8c43b7c5a0af142de50be72858f5b63f792c02888` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `6c7b61bea958f1573cae465dc7523c0890895dc6b3163a3e4fdd3d5d2b593d05` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `48d432203af85e5d3ce05f5582148aeb1d822d1e33a2cf67246e86495a1df95e` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `87169b6af24e8f1e62268ee5e74aa1cd1e605487740e14e31f00f7c10afae20d` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `a6999e3b11a1c0502db903a9a83fb9da36c9ad255dee091f27c4b56ee85fe5fc` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `661a190f794e6ea93c325ba90463906022b6cd47b63d6297bd6802f1ae4b500f` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `fb241f09f3554a96361083434ce2dcf334c6daeb4fae1caeb335c46d633e53a8` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `5f46b3670d0a50eaddcd2378bb8739dc6a541bc5c272ad6952270facc15e7560` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `fe2a844df1caabdaef91ee96b37bb6853ca653900fbfa74f39b44d14269d8df3` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `85d0d10e14b66d45be1449b5885f9c691abb20e4d4ef0daad78bced8194d32c7` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `645e1b283e562535a3b93bcd5185e5eb8ddd225f19d0083dbea31c42cf2f8d4d` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `1a57ad97b64b7d6b0526fb094607bba8eb1febfc14c964f49ae43f01fe078370` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `645e1b283e562535a3b93bcd5185e5eb8ddd225f19d0083dbea31c42cf2f8d4d` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `d25423f4b49421ae9acfe3bce603c5125d83e4cc195965e76ff558ff20d04495` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `092990ab704028197c6daaa3e7bc9b6a9fb33c971f021efd689f28df0cef6a2c` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `d33989f9204aac26ce0ddd36e15a9fb8acdce987a0bf91f5df5ac0c419b41e52` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `19456ac4dfc33f4b0d04f4923735dd9265c2ad662fd43274410a1a1bcd83add0` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `d43a3a87796ad2f411124a53b7208a44e82530ad3607b966b1c053a7c3591f18` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `d4cf188a9ea41723e8b770d687e438547da1ca89dee550391cabe5602d84e3b3` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `d4cf188a9ea41723e8b770d687e438547da1ca89dee550391cabe5602d84e3b3` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `848a185f6448c4dbeecd8e6e67a307c0fa3df7513e88b27a750033dd92235925` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `d8a6425aaf1aba35560fc8977023c98c3cac849fa6d811fd8f3687905f1515cb` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `848a185f6448c4dbeecd8e6e67a307c0fa3df7513e88b27a750033dd92235925` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `dba0e5d936f115457ff090182747b40ae186f6521ea7903ac200cbc9f3d9ef1a` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `0af8c5d6c6d446926c1dd595d4c034a4d6f4311f4400208cfe11d575e55e680b` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `f93ce7fa904b55d4f28801cd028ebb4047fdfeff80727b4de6bfdc91f38e0bcc` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `22c92dc86c807b651460be81a2e2396caeeae119e58c496c6ffebdb1ec004dca` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `167abd554ce1d3d9aa53a381c511051e54d66cb9080679bda79c5037ec28e74d` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `6dcf3dcb04a68df00e82b85dae4a4391f2ac881ff647ff17d181dcc4072eb5f3` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `650635eca3e51d9080c35d29f7e0549e2d89cbe4b2d06807161d7a1eb9e739da` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `dfd057b34c73d5d486eed43ac7655c5c98f11f591b24f3da2442411de3084727` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `f330771646d47113b967f887cb488c1539657f7050230c08b50b37c03eff3897` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `4cbf01ce2b556f5645db0ca582ca53e845d3cb9c631add723361e984db12b160` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `846b739e96e286e1fc1f111e26e4540cb0fa54883e697488ec42d435c6955897` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `1a69cca6562c26926a1c57f1b0b50332e7ee118737a211c991489bdcaaf13cf2` |

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
| `results.json` | `432568dadbf069065a99d97bbb5725b7849ae6f66ed019e1dcfc20bcd0fc397f` |
| `screenshot_manifest.json` | `8e158f25c04010a69e0f57f32594e48358bb0a63791893586f76e239e0b5f170` |
| `paired-fixture/results.json` | `9100bc381c6f62aebba08aae5165ca8f8de9f5371c309fc5d2f1a3e4ba0a7094` |
| `paired-fixture/fixture_manifest.json` | `cd51d8f837308a3628ecb3c176cee4f21c33cd13f7a3010ea85a532e1289db47` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
