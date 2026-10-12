# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`1a39707502cc42c6b090aac518bb624c2ec51a18`
- GitHub Actions run：[38203509447](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38203509447)
- artifact ID：`11726667204`
- artifact digest：`sha256:593902c7a3117ad0771bceb530e6d6be4e3c84e0f3064b6df44093d3e7ed5de0`
- APK：`app-debug.apk`，SHA-256 `1c1c4ba1f4ec6a6fd52941b3bcbe46b42ec8e513f80230ce9b635acd48ff63d1`，19648105 字节
- artifact 内未配对 smoke：`pass`（2026-10-12T04:30:18.118065+00:00）
- artifact 内配对 fixture：`pass`（2026-10-12T04:34:18.634879+00:00）
- 报告生成时间：`2026-10-12T04:34:32Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `0de7bdaea5e8f9cc57f799f0455aed08e1d3a74b182e16e8a60a85b2d081637b` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `9614fe78fb8037e253163177d4226fb1c2bc8b79310f65a6430f99ef6c8bd3c2` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `6ac4b80ee1164de17a56cf534c391fcaf5b724d3d1c7f0cc9ad72150251cd206` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `4539f5ba840dfe5187b15c1431f67bbcffaf11e29904145d116e933653c9b17f` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `cef0b798ebac81fcc833f45ccb11388f48c80f7eddf98694f1c610a4c65e33fb` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `22ef298daf03f24c9d80a99adb92c7fbaf0f9d2b30772d98c2659d320f208d22` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `3319070483ae977cfd17df8a7f64868b5e1582281c0e8fe26e2c8d4e379f75bc` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `4caa18d13f84eae8fd9609adf750af6a68b43138f9fea55e0b0c4e0279771d94` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `e9d726429431aa8feb21efde2a5cb7ce4a93a477e53ef5ae340fe01d0f23f72d` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `8ec88cb96d82231bec245b171205440dbd0172cec45094c9301ac50a00ff6721` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `1ca3bc21487e8b91e7e624ec28fe84c82e2a56519f2083fa8aec436491fe830d` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `0e422eeed0a2d482e7e451fd61b038f9e34e21fd41e39750c5a0ed4497f43c52` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `21bcdbaae1b9e0f5394aa28d453add0747a241b24e5f3126ee944d90ec3d9f6a` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `66696f4353fe3927c071de2539c05d2655d97eb637920d21ef3394e14c3b6b6f` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `4658ba533c930d330441da6f414075bda3a139a031bcba15f011f3cf1c6ee1b4` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `4658ba533c930d330441da6f414075bda3a139a031bcba15f011f3cf1c6ee1b4` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `ea7ec95da2ebb155659c85ca53c85e133c4411c742f5c251a5a91a8e09f55404` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `21846ceb6b4dc03101b4a41ba64df807d83f9ebe45f9c2ca99ee2960b34ae62b` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `6bd39a1a106df631b51a903a85868c6cc1d9a8c76dfcfb5a3d0aa50bfa5b1c01` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `e097d95e88001b19dc8e4aa5071be58174e2dd70b49ba73d65ca0799a1d95eab` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `0908a696d61cebc47768d523c34348657b78511c4044f38af816af378c9390df` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `0908a696d61cebc47768d523c34348657b78511c4044f38af816af378c9390df` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `a44c6dfc21512768f05c341a863315d57d809ade6410b840c8a12c57fba3816d` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `712159afea810029f586f097beb426955f73ec1922273f262b8489429330ba06` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `a44c6dfc21512768f05c341a863315d57d809ade6410b840c8a12c57fba3816d` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `cf3855d3ae240359ec97425aac4fbac0eb93265b2c5f90fbe322dc2b2d064ad6` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `0bf113b4b9795113b55a9830333c2119516adb382469224b790bc543ac78c4c4` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `6003d914cd18d84b3f30fe8ad35da64c95851a8487eb04544308aef93266faca` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `f8768812ff83ce5a2b584618e4f1a8e4a1e09e4fd3e16718a1ba421418b46d42` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `cf30d13fb5ca4d021db266ffca349beb76479dc0ec1555835fb3c7581613b8d6` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `0c7158827f7a6f346733f9d49ea79279186f90d4e62cf44722079d0ced74a08f` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `0c28edb22695138683a46879ec1ca2b15968da0d4875a9f65e531287113ef562` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `56176c93aeba05e71a54e4f268f44116d25e39835f8a61df749691291fd462a8` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `5669e0ebca327c23e57a6d1bb36c2f94221052c258fc08799609378ffcfd4fbc` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `1ad2c2b9d7dfb5c6be13cf1ed3357b6072defe03202af027e7d4633d1e7abb3d` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `e9be7f6d5f44c33912e0af02e3028d50d8ce31aa9012c8b0ff6f9ffb474a377c` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `a1ffa4419097e256a8c556c01a0b44ead6d158bea98cf2c7fef7de5b8d28e77c` |

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
| `results.json` | `24847eb3908d11ad9ea045c151886259b103d30c6eec2bd340d1c07a5ec672b6` |
| `screenshot_manifest.json` | `43e69dbe084f822dcf65c6ac1e912edf4ef2f7e9ffce30e4ad566414b05930d5` |
| `paired-fixture/results.json` | `0b8c2b4f596cfd02a86062a9ff03bfa2bfc390618c2d21627277608c2e7871d1` |
| `paired-fixture/fixture_manifest.json` | `de8dd88f5d5b5bb160288074db6af021820665f3e382417856ba1c202fb45f9f` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
