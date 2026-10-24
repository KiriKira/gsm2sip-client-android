# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`7725c07326bd1c955030f601810fb64af83403a9`
- GitHub Actions run：[40112213720](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40112213720)
- artifact ID：`12567062537`
- artifact digest：`sha256:608400e136fd1515e8cc05e3dd734fea94a73d5a7964b05ef870596d752dc1d9`
- APK：`app-debug.apk`，SHA-256 `955c476ce6e5da69f9a26fc20133710e6f285549f4ad1f76dd7ddd41362731d0`，22248027 字节
- artifact 内未配对 smoke：`pass`（2026-10-24T16:43:31.015490+00:00）
- artifact 内配对 fixture：`pass`（2026-10-24T16:47:28.737511+00:00）
- 报告生成时间：`2026-10-24T16:47:47Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `642efdd4987b61f2a0a5e6223b46ffc5fb461ec097adc72e9042206aa309bca7` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `fcd6b725155b611174bb87ac796cbe93005f4ef765b9d6b4574e3e31e0401b34` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `ebf2d9040e2e92d46b2dab59e2cac05c5256b9f1aca6e0f93e035e9b519712af` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `2253db9b1e94a7cc83496d80a1c480e7c73830d6fc3414321506bbc9e47d04ad` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `3b730e3312c1d6d4c14c95d3dda63f05015a4310330896b8c5f5fe3f46bfdaff` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `168d72a9d59a04bbbf6286a2036d12449fd54f264edbb8b6841c785a016e01ad` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `be64f0c5bfdbecfca916105276818b42bc8af186a499b4b361b4e8fea7778ccd` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `b5c93f1711e803d8b67ebbf67311c5735b17bf61571b4b5e79795d1660991ec0` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `0e47e7ea82eab20c5a270dfe860dc061dc8b672e42cf679dadc1212ec4af7da2` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `12ffd188e5658aa1078494d9da735649a75ecde9e6ceb649d705460e532adb2c` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `3a9243f75c4a6c6eff78bb481d7ca58fa4e4d7f25d72846da6cb8fb660e30223` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `26db986032c958921c84b256485c41d6ed4ecaac54494eba673ccf5ec23a5198` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `0cd67ac2c0d80a79bdc88e5ac417d0472a32132db457a3f1733a20b2840bd7ac` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `fa2dc4e56a2580fb979e6655c758dd1efbc4422fd2c9e80e834a19479ba2175a` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `6a701d9d55bd5f9302e88d5cb32a892925c0a20b3d16a164ad79ed6ad4cf4e19` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `6a701d9d55bd5f9302e88d5cb32a892925c0a20b3d16a164ad79ed6ad4cf4e19` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `c0d25a4a1134c5f3a3a6cd525724468f23fed921cd8f78f7892be0ffd4bb3895` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `7086c6682619f980a115d348a07f184a108240d44220572718850a59e14b7ca2` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `dd9c690a6dce61205438ca7805a6c895c20f441bb7a4252cd7a515f6dd66eeb3` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `73f8475363699f445968f63650bc5ecb498f71b9272a6d161fea52857f2f3195` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `f2fa436372839e8fa45a5f0bcc32e685cf2c4e42ab34c9adb1178facebfd2120` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `f2fa436372839e8fa45a5f0bcc32e685cf2c4e42ab34c9adb1178facebfd2120` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `66bcb17e63dd5257961f45af8fb07ecc0717975cfc940af0e6a9cff154f14a48` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `93c3f10d37b29f7eb3d33d0ffd90308718e38046220b65bbd3cea1b977e001df` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `66bcb17e63dd5257961f45af8fb07ecc0717975cfc940af0e6a9cff154f14a48` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `11a678a374848985a6e75a3dbb73451ac130cf8b35e3d6c70b3840210870c255` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `59f761a17c7648e350f7c12779c96e969cdf821f7e8854448fbceefd691b5cd6` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `5ca7066141e92bcd50f9b56ab466d5f91b527f83fe98660305780ff1454d6f32` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `1da4f31c42569b10299a44934815f8b087f0ac5ea6c2f317ff406b6f51b3b4cc` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `cf3d0012d8ac912fe7c71e721f738efcba220216bd81bc1b3747b4f144acc9b3` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `55d9d9fd372983c78ccbc0a7f546932dd73c4e7fca5343dbd5a35d9e19d9a156` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `56593dc106eace67988ceccd440f64d5c76b924891b67dd8ae9d3454f5ffd756` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `56c287516d9622e5c1514875e0c69b3da00368627c45637eb29c05ca950dbbc7` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `f8cbecc7391a0b7564c3bc511adbc00883d3db46affdfbe78083819db2d013a3` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `c4448c098720d6d439e30a9bff9aabeb6605b9ea94eb8c8a996c3d81d0fa8833` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `4d1f9147b9d360959adc9a90dfd75d3fc65f5a47723aaa16d2cbb219131d8edc` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `47b79b303c33c06b9c76bb747d7ec07e5e4ed2fe0306b82cd16ea93639d16a2f` |

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
| `results.json` | `94c98a7642243456cf50091e9d3781df192a2bde046c00826d9feb779d5fb638` |
| `screenshot_manifest.json` | `69da61977080761831e93bfd6ddcb9d7b32fdd1870ab3b9e69fdf19c940328b2` |
| `paired-fixture/results.json` | `445a88dc9a23c9312e500f76db8afb4a7643ac263577070f4c9a08bd09e81e81` |
| `paired-fixture/fixture_manifest.json` | `20a5437a947a0118ccfa17e3fc2afb2bbea6eecee3dbbf66c6880348d67942dd` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
