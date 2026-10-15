# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`526029971ee8e5b44b80190a11e0eb3879ed9a78`
- GitHub Actions run：[38768703544](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38768703544)
- artifact ID：`11991940631`
- artifact digest：`sha256:ace20d340e443af638841607370e647219f83d5a87017089def3e6f228e504e8`
- APK：`app-debug.apk`，SHA-256 `965020333ac8abf3a7b728ec4d49d27099aec44c06a49477ebb4875b64008954`，19744736 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T16:33:10.466128+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T16:37:08.528749+00:00）
- 报告生成时间：`2026-10-15T16:37:25Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `51656530d9d033d8932532fdcadd4e3a4f41c24a5755e2cffbf494130280bb8d` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `47ce947902c1e86e488d9c2d6d69a689251d4ff3a023c4c652e9f08a778b49e5` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `e55c35ccdff5ebae91afaf43ffc79d131e6a6924921b2fbdd379dc3a1eaba540` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `313565a3cf03e8b10a4cce85c487885254953651b48d3a1d76b408f34b3dd479` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `1faa4ba308bb274d96d8c5c7248a3303ee297270f641cd788ce9e0dadde909e0` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `90a9ffa5a859667212daa141778e05cefcad7bf03392711fb78618c408f60338` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `7639330cecf3c2d5fbc025de35f12ea7ce74ff5393aaf30c081dbcb38362c787` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `0a715a6b565eb2663da73ebe7febabff7afe85442fc6ff6b3a1899b369bd667d` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `f4311db436f1103e8ed2764add5206e74126c79fb5d183f78cc8fb6171153e06` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `7414b3548627353f75bac1b983f3cb2c574efb759b7532a870c6fde89a57a6a8` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `a00cc8c3ec598de5504143909ee1cedc318cd7c7368a929413012bc4a34acd3b` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `4a0d9385ab3a922720b2d67fa41bfd01e6e4fc79b06d86276c5320acdb9a6d0d` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `37fc2b8218e4505be23f552aae860415ec971b599f60aa48fb67d883a6ffbb2f` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `0229053f3b2fbaad5fb6e87eb921842b0a6b5334c985a3502c92422c393f266e` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `37fc2b8218e4505be23f552aae860415ec971b599f60aa48fb67d883a6ffbb2f` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `37fc2b8218e4505be23f552aae860415ec971b599f60aa48fb67d883a6ffbb2f` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `993dbbdd86bf63b0852f0a0b2aa492505340542ec363fd6cf245c3b14a01212d` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `8b78e600d8bf65cf0cf561aa7109f31178fbb9cefbe41abafc906f377ba20695` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `c5d5afa32cca31cbb10f8f6418385562f017bd6a643a41508a33bd088e11f5f0` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `5e4c870a9af71e2c873d1879bf905a3249d03dee80f783a0ef6c982f90888323` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `2c83567573ba59b110371e8203efb591a3fc23a2da3e6d9e31ee2f8122ac8e5c` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `2c83567573ba59b110371e8203efb591a3fc23a2da3e6d9e31ee2f8122ac8e5c` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `42dbaa2892ea84939fa0246486cae38f2473f8e11c1b85c4a2bb19280e4e8ecc` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `e6c2b4a11b0937d02ed0657aea9366093dba6fa2aba81b214341e51e744eb6fc` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `42dbaa2892ea84939fa0246486cae38f2473f8e11c1b85c4a2bb19280e4e8ecc` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `1c41c901572639eae911ae1aa48278747437bc3c6a6bac650a0a2e68d2536aba` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `9a7f15ce424801be138422ec86d94d6535a4a10aeb0e614dc2e7b0a14c81fd47` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `9092814b6ad9927f7309c682dd0cab799249ac925eb79edce509d003a8b6ee03` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `b240692e834dad052f6a18c91762e785168321af239054baccc00e0eee471643` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `e9fcf3bfda3388dc7f4438f2d02143ef003f00ade7f5382f4356ea71f56c62f8` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `3e3c0301f293b2b5e579196ef256cb085aac52eaf1814f0f11ba982afa511ac0` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `c6033832ae2f754fa63e6eadf2d0477cdb7f58e132dfee6d4042c8a3a414deb0` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `f5765b6cba372c42b2ee8364fbc98ba1081d81ee5905a745c7cab1795be468fe` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `b4eb57d6a565936bcda1cd242bbcaa9a305f23f3afe4ca9809421538e9a5fadd` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `301bd1ee13018694a67cb834452fc3209155f746cef1b4ea6d94a7c3d05c513b` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `5cf37f017a8c14a746b021c7e826fd11611a49d449ad3d80794a77a03d9a373c` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `dd929cbd8c663e370c2e24c13d37f61c7e5f74c206af2f9b2dd5c97941028997` |

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
| `results.json` | `5884f90ad47694c3ad49a63ebf1d7e353496ab47ffb6ee328219b51b4b8cbcf6` |
| `screenshot_manifest.json` | `d4fdafa9471820f67eb28b8065b56c21f81ae6f217b34105f2a082f53fe7fb3c` |
| `paired-fixture/results.json` | `57c0952b3d292dab72cbb0c2f4d39f3cffa8f2710a02fc385705c2842b8768b2` |
| `paired-fixture/fixture_manifest.json` | `c1cdcd35037302990d866076007a1d6ad63fd6a306402363c0334d57d087f041` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
