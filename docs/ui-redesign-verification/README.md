# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`c0aa7c83abb041584062c407fab3273d6a268b24`
- GitHub Actions run：[40157578802](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40157578802)
- artifact ID：`12585358333`
- artifact digest：`sha256:672fad77f59bda1268a9049cd9d0f73f2f8e89637988481bed1762216f4efb26`
- APK：`app-debug.apk`，SHA-256 `b1e8262fb94a33ea3fab58c40b44a8e9203aeaff7f622d611c59c2d9e86b6d12`，22248139 字节
- artifact 内未配对 smoke：`pass`（2026-10-25T03:24:49.604292+00:00）
- artifact 内配对 fixture：`pass`（2026-10-25T03:28:52.931652+00:00）
- 报告生成时间：`2026-10-25T03:29:13Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `2fdc3187a9dd587d798377ad0203d25bc87764972593708d3f1079d275836d44` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `ea8d8a718dfcce3ff25b4c58a4a9c6f68267a924752de613818c03d84395f6c5` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `9d603205d358aa6a2c9b24ff381d5ced0ab649b2bad296270d5bde988cbf69c3` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `318192b2b52bfab0c6aa081f72c62effbcf8ae496961d3177579205ef6559731` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `8d97c8b2464438f5028048fcccdca4b58db8df9e64dbbd3bbe04542db99e7cb5` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `f93a29ba22c82fda35e2369a04f6f965eb0f2b2a312905f4e85fad14ba012119` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `7fe99d967000f470e40c7e4875f530b0a29496bd47c1f87011153f0a09f770cb` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `756f74e09daec6800630fb8108868ebffb660407e61eadcebfadc4054bf05dd1` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `35d653fb38d3efffc5ab3541ea32443eec701bd940a8fe928cd03662e75ba739` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `555a33b89c1427385f39e310a798a5c7d2e0c2f67b378a614551e35b8d05c70a` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `554435228d302b69e11c33762b57b96ada67f89f88b49feea78eebb080bf15ee` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `ad5a86843bf2ebbce84013a3f3a101e0b1364ab51d23867f35be2ed58d1ffddd` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `0afe0d9bb83b03c6ab80a7d54ce2da3cad3ab10378d6ac6fe350b19925126da0` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `093b5794b8b32bc354698b08bebfce19c00379a4cf488cfbb8038ddd5969de76` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `0363aa3d70b801f9c00a97867095480fd5b2dbbdf295dde21a705324ec85c61a` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `0363aa3d70b801f9c00a97867095480fd5b2dbbdf295dde21a705324ec85c61a` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `d16a01d31683220a03a9dec48d0bc0765d600392eb75a426b6913120eb429a15` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `bf42f5809722a2f4f62ebac2b97acc646de291c648f09d66a5be0350dee8bf51` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `3559375f31352ac423b4b180016d6fc505fb916436d3506e35d238afc3e4b551` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `f1eee9ad83f8e7eb07e99f6a3e2259bb2d3d41f3f127fb53a1935170bca8036a` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `252a6874312c314835ea01701f613f4e8efec5faf3e04e4869f13aac5f7637a5` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `252a6874312c314835ea01701f613f4e8efec5faf3e04e4869f13aac5f7637a5` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `3db80045e85d609bdc2bff4425ce1677113f402d0c2b9d3f4bfadd76a23a8a1c` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `7ffa473f009466cd1e4aff8fee3811815c9722072d0c36b6dbd5761ae6b48d39` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `3dd532b37daff798210a95d3199dd425f80de1e86a9606e58e63343603499170` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `a8534a303e0c41050bbea7f0270391bdda733744f5f1686352afae3f656f14f0` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `5d1fd6b52af7b7098a4f2a34af8d1d5021e7812237889f1baae6a99266fe802e` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `d55885b6b7542e0c288419cec080194d0548f95c4974505ba29eb199748a94af` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `efddc88c3bcdd6a1646d419e2c6c58216295ede8246de9b3598b42ddef696ef6` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `a35b3d859c962a8a7be00bfe22d902beb1d5f44e64ec51035af249fdf21b012a` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `bcb105402a7302903ac323cf966bf2c6fc30c71a184a9df762b38aec44048caf` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `779f8ce59cc3941cea12773f895aa77cce84da24ba72002919655a8528913234` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `27c0488a2f329e13be88161b77558025993b91d0bc9715b41c24f785dbc7c264` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `eb08f2c2d08dd7ca55b3ad47cd13dd646d79d7eac483358983903c08cc59f227` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `40da173a44226d1dfb14f3975e28a5e94cd1f063aa3517aa11237dbc11f0fdb1` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `19a5e0c4f9c85f7b61b6611f34cf01af78ba994acf30367ef43d7b292d7656cc` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `7d115e4486ce999a9a3e080838cb50c9790054dbb2dfa180ef24f192737a264a` |

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
| `results.json` | `70f5c6a6263d120a677c1fcda2897b7c108b92d005710b2d791cc4bd81ad8c95` |
| `screenshot_manifest.json` | `f939381e6e87de8308bad456b402ad402b47644077659d610cf74c1fbd62ec6d` |
| `paired-fixture/results.json` | `c8de9d85633c4c2a12f27f54a080a5efa8f2c14df02576e8c49e66dccc00d42d` |
| `paired-fixture/fixture_manifest.json` | `ba9a21aa45bae56b10c5d6979bf0ac09ce6b8918fa759b5bf7fded566dfae4b7` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
