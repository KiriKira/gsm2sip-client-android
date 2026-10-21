# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`fee2e2d96ec812204ea83c1709a4afe0a27c93a6`
- GitHub Actions run：[39569724590](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/39569724590)
- artifact ID：`12332212246`
- artifact digest：`sha256:5f2659517bd7a73ad30afd94d4fefb83d8aa635dfec120ca7dbe766542e01d2e`
- APK：`app-debug.apk`，SHA-256 `938f8a66677fdc203390a1bf4a5ce087c5392732cbcf5229746c8518630ae919`，32449604 字节
- artifact 内未配对 smoke：`pass`（2026-10-21T08:38:53.069776+00:00）
- artifact 内配对 fixture：`pass`（2026-10-21T08:43:04.801464+00:00）
- 报告生成时间：`2026-10-21T08:43:22Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `85f0e454ede8fa446ab0bc6b34a64393de0b31fab77a940e45ae0738e859f9be` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `0c0757a123da315d4f6da1b8f9ee227dd858b7d8c5a9c3a61651e3598244dc54` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `98f1a6aef9af94aef611b469db880b7d3cb9b83e52c6f2a2154d965d122feab3` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `3d8c99ed8a25b14d16a8731324cb30ba323da6efc92d55c2d4972c5a87c449e0` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `f19d83c307ac380c90c6d9b106dd7f1b8914c55d3d5c4ab82b9f23c106b8bdf9` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `e07c25eff32494eb2787856d46db72e6f7fc509797743d08e7ff4f225dc98cf4` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `b472c0300deeca6a9a300805a688bc7ccec677443bfe41dc42a45af7c153a5c8` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `664b58e9fc1d754620683e8d23952fe501b631a4b74e22255d93caf557d052e0` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `b97153e0031d1fbcb9fd710458fc5d65cd3ea7c7132fcfddda21d101dbf8aadc` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `63ac562134d185cfb8fad96694845c0376fb611840c1846890170758212e5175` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `9c62571d9fd9de459e8c3fc896a3b8998983e5e5dd32f2b6be6110d709f845bb` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `21b784b4530407f094a789e2fe9df52fabab3aa19487b8d35153aace8a8b7b18` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `4f5e809539e3e4e01313b841428273827bb8e1b1ff893db2017a5d77aba79fbb` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `d334dd34f2a59e375c37e088187daa27bf5eec978eab927d1cf116c7b2d19451` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `4f5e809539e3e4e01313b841428273827bb8e1b1ff893db2017a5d77aba79fbb` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `4f5e809539e3e4e01313b841428273827bb8e1b1ff893db2017a5d77aba79fbb` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `ccf98182f16c317a6b2706f7ca8511cdea2a2e1d95bd1cfe05574d23f63da959` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `a8d4fb98fd16e71a2255a6926d67caa8133b97dfb8b349ff064754d4de5adc50` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `4c4c8ab5935354dbf83019fb6226840f0c1eb47dea7b3c615f361db0d988c353` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `34b009c57268efe34ae75f1c9ceaa209312ce99571a04bcebfc3c52960918fca` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `017bf770e34e53425a400d937f3bad40d2d8293a5e4d8d2c71f9afaf24b7029f` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `017bf770e34e53425a400d937f3bad40d2d8293a5e4d8d2c71f9afaf24b7029f` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `a18aef210f0e4da38f65662a63635f2f8e3fe8174d2efeab65ae3ddb45138f00` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `5f84782d67d327e27d23817766d3977495c87adce920c41480067b6aed37b998` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `a18aef210f0e4da38f65662a63635f2f8e3fe8174d2efeab65ae3ddb45138f00` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `843cbed248f98e342c73fa1c13ec98c25624874da550538aeb8711adf7bffab2` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `9f361cc3d7c2e2dce9c0eb81822f5f6b0258f8b89761650f9c6e208a3677d7c1` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `0ecf82752955f67caa08339fa9d7f4d255c0cb53e867c8993f513de087b959cc` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `0b00f2045eb0c9955ef59d4631e662115054d98f1c214a862708f8871492ddd7` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `05766059a72c73febc66c1418a8e64a7c4537f45e793acfb33012986ee0797a2` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `99243276069722bff95dcd7cbe87eec929e0a95a9b59cda9e69d994b36b84cfd` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `322006efd6e20052b33b2e6723f02d51b212ebec2a6eb522dcb8cc8e89a93611` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `47232875679a3c62504fe1eb9375b35bbd215dc7d38d0c267210e8130cfc76a4` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `2d1327024dd1fbe45ccb71844fd98fe718d79d3a755a4c4a8a1d8779f1f01791` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `d747597c5582749c23c979bb0bd979f973cdee102d6d3c59968540c55f1d8b01` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `288aff52aaef10087e6feb6a144eeb61ab4d538d338dbbb63742aa6b2284847b` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `90f2d2f08dea476dd159d42a2b6eeb432b68c24069eee952e272ad4c522ffda5` |

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
| `results.json` | `76fc66a63e8ec53a5c9e8fab278d2ff6691ff0250eb4a73ca27b85e72d5d093d` |
| `screenshot_manifest.json` | `5f94cf78f30325a28842a6c58703fe138ff6619aa838193d8bd9e3043300170a` |
| `paired-fixture/results.json` | `42148eb2d0e1a9920c44f8386e85cb5410a08235c9bab85befe30350aa8b4b93` |
| `paired-fixture/fixture_manifest.json` | `93974cb934264d02dc4789e20830c10a6e263d8eef6496a3d1e732b74d26eee5` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
