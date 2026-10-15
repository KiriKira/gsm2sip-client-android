# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`0bda406ef88b8fba313154cff1732539be82d841`
- GitHub Actions run：[38701101232](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38701101232)
- artifact ID：`11957365529`
- artifact digest：`sha256:abdd463cea70fb338513ba22c70db596ce061549032fa3ebb40014898981d3d1`
- APK：`app-debug.apk`，SHA-256 `3c224f565ce21a78104fd229b3f525b4be67fc4ae226124c724262d96775fa84`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T07:55:24.413401+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T07:59:34.217268+00:00）
- 报告生成时间：`2026-10-15T07:59:51Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `e7922bc2a93893d9904f4b2c3b668bd33987c2e8ab0f86cffc60157508cf923e` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `f41ca4e3e06bc6052f69f4e6af57de8c7c3715e9e23db4a657b4f6b1830518fb` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `cdc893b7baba064d7b7f3e3edd089bab06ed435871488c8f892f577c43afd2da` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `1fa90ad3fd3d1279be4bdb2245b971544a6ad50c1e7d3f4949bfea660ce4c2f8` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `e0db889b94694698c4f2832cb156eba27b3090dd202cebacc936a42e8e06a227` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `1436c87a0d533badfedc6be9930a133f6b0f2233dc7639457a41612ae9b7f09a` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `33d426613e6842353e22db7f2de49bcc20d77fbcc86e675b4ec3f23a4aa96e1b` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `4c2dc06397d43ab27c668432e417a83efb77d42e9d50df32ffbef9b2ab775ea2` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `59b1fb2f18cb7827b8fd6e0f4e08692f273962f089e6e2832218710a5a678e50` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `15e6e3dec6acdb3869bee45b41d189d60efced6046dcbbee8aedd11116eb1771` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `36bd95148765177e995252aae833fc5f824b7bff0e26bc65f3e7210769789fc2` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `7343e99198cc8eefb6d9604a59237fff6bddae544fab6860f0d433a9c630a737` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `085641c55ab78fa2144c0257191672f6e78bcd49dece3eda3dfefad196393185` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `6df396dc706419b485d957670b6deb262aeeed81407aa46d99f3da5696508f2a` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `085641c55ab78fa2144c0257191672f6e78bcd49dece3eda3dfefad196393185` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `5271b139721b146f77f90eb2db531e5fa37ab4a74577b420d6ca6b86faf7ae84` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `ca7422e3ced71e2072fefd5cc177ea8f80df8a83311fea4139cc16dfa77ccb28` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `2841665749dc63d2406b7c1af191d71dd25bedda2eb2df4c1453f6ecdfa5025f` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `ad9aaa773cfe159ca846da949c0e0c5f796bbcae0a8a5b7d2ab72ad6177c2913` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `597042f34f7415137c55ab3fadee1c98b717a847520b624600332f8117bf5146` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `c36af687dea3825b0bf4902f388cf1de515070c83ec23d19334e06784eab9861` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `c36af687dea3825b0bf4902f388cf1de515070c83ec23d19334e06784eab9861` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `d34040782d5dfcff96b4b6dc4f77db301c0a83112f8c2c06dc014ca58491e5c1` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `1af69a824f47aa491dd9620b6746931429ddc49d96987ee0cf9e033328cfe30b` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `1d8374b75259582fb342dd4a4321baedb2bbcf678c4e04dcd047718991596793` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `7558cfcda60e9f524dcb07217e81bd77b35c210afa10bccb43f725e0b200fbe4` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `d58003cf73cd152029d6542cf8aff5b162d81ca277b793e009f5d042379a0f86` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `d94e87d0281ddac3c1fed5d7688f30eef167e655eee839bf2b222aeb72e6f84a` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `90775fcb0f3ed41f2ac3617421c184d236ec6c1b0bb88d07c26e389e89c99145` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `c72ae3aca3fa6d6ed10d67297d609f314c87b6dee2990bd06aeb300a6bbc71c8` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `69ec8e92194085934e57458ae49c7b859f3295bafb2261dde6d9392158ea35c4` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `264281a04b9397ddfb6b5ace3bb1f3c40fe70ec84776d9f276a4baccbc768d15` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `7db34422ac8c97915c6941f36e6c455899ff620707c8e6af6333f03c9b6111c8` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `07b2dff001d212e48ef672ebdabbc089afc1711d3bfe12c113b7bb5b348a2579` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `d1f5fb895f27d285211e52226cb3346120b984a9535ae0561f701a542f0986f5` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `61dbd20b16f3922477047cf26ecdf6c318d10c8e204281e56a398e0d7b8a4648` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `254065992d91296015e0dff7499e4fbe16e53286ec751cb36ecb3ca07c925821` |

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
| `results.json` | `565337cccf5d44f96d4a25327def84d355f6bc947f2fd0e2a3960de37e9f1df9` |
| `screenshot_manifest.json` | `fb456fac1ff2336f5aef75e6306b35b28c71f8a146d798ae9587be2b8b40867f` |
| `paired-fixture/results.json` | `bca0a018764b66dfe4c4f4c8b6ab85c9e630710a81b9d827f2f5afc4a76b12c1` |
| `paired-fixture/fixture_manifest.json` | `e85e24a27ed662120f855822e42d74c920559fd6bc183845949613d92a41e8f9` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
