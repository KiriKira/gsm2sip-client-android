# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`44fd2d7d5fb231411bfa1450a2ef155c7aa8dde6`
- GitHub Actions run：[40502219903](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40502219903)
- artifact ID：`12733094893`
- artifact digest：`sha256:28c16727e12aeafc20cdb21de2b140380a91d764811c10f8f7c1bec9e909938e`
- APK：`app-debug.apk`，SHA-256 `d6ce3a5952624f2a93587cdb9f3c7c5249ff39da8bd058ffa11483e1de95dc42`，22348560 字节
- artifact 内未配对 smoke：`pass`（2026-10-27T12:23:17.293819+00:00）
- artifact 内配对 fixture：`pass`（2026-10-27T12:27:21.859176+00:00）
- 报告生成时间：`2026-10-27T12:27:37Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `f30c2ad9e8bf05b04160658e194e41b7ee38eb796c22a4b351aa52465fa5ce55` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `de2f1a322e7c490e19ebd7de6d15cfe7808f8f77291fe884e3c43cacdd214b6e` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `c6f183a9eecc1754589775c825d05da1123a63c607b06be3b64f71210fff3802` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `73df21b80c08bfa07017aac041fea62d0ed9d016541027ba25b77f0a3046e1fb` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `82ca51d917034791c617190214bd9a46d9ae038163f8c01e678652f959cbbd18` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `1cb9fcb9fbbd0767370590948b8bb5abc5fca9349ba04c77eb272fc191e9775e` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `2325ad253794c0ea2323cbf08c958156466064fdcd1ee6e0b763320ceebdabcd` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `a459473d487a5e5385357e0b1488e558aed3bd6ed9a395ebf3c278bcf081da4e` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `3dbd9743aa7ea1183bf5d5568e39dbb7a374718d04c21c0608d808ff783ca9dd` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `d9dda637bcf21ab9635c9a448fc878e2a769afff56005587d0f3f1d3012a0861` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `9c7ada247b072e24192849f378545194d34900dfb7bc438cc8f7ba91f57c95a0` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `9855afc6c36e93cc705231743084f605065ed0aefbe7371008cf4eca564620fa` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `1415dd97d377f8e779421a1f1aa54727d6ea29b3f43f5583c4aecdfa0cb1d3c7` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `e8161adc2212cbb9c39ee560217d28f7d1046c1af7e042181d4cd69d8a0f9b73` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `1415dd97d377f8e779421a1f1aa54727d6ea29b3f43f5583c4aecdfa0cb1d3c7` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `1415dd97d377f8e779421a1f1aa54727d6ea29b3f43f5583c4aecdfa0cb1d3c7` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `2791f375f224cbbdf8c845ce54ba6e7f1c6e38b8cb528119f3fa11cce098f468` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `b9550003a10a35ce210c1d01c158175a61d5617cc424e70e127ef5af6e196bbf` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `801427cdcedd76a4c94148058d0e81f7497d5ad9e4f45e602c6325365e460c82` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `df00ea55a2266d0f145563d003b000e830974fb78a625125104c6918c924f409` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `ca1e90d43d7ef3d7bedca4af7bcf04b3cd5a42b0cdf5349d9a856ffe9b9a4c59` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `a275f1c8fc0df39b94c5019436e312fd17085be5d78cc69834b558d6796f7a11` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `acc2f5405ae7d08481f6368d8c6af3aec299ab0b6b1cf1a70a1c91675d92d534` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `43cf6e82893af3736f23d2b4a0e740767437f21e789a01f993d4007cff70b47c` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `acc2f5405ae7d08481f6368d8c6af3aec299ab0b6b1cf1a70a1c91675d92d534` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `c6e7b62d5664402a0757a4fb90659d39eb6f9e50b8da492ef6118eca22f723dd` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `b3785565005814b175c4b432b784e35d180922a2a586f3852dc807c549ec1725` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `b65c71bdaf6b6c4e8285a46055e128eb50d63137be19bbc7607112df50d0f7ec` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `08686e488fddd9b5c6461ca7502923d8c801ddca8c017b45a6db0864a28a3b61` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `5fff75b2a2dbfa01b3d4c172cdddf8239d7c64993fbf1206a046969dcdaa7489` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `c4e80cfe6195335ef83e4415b1fb879537c6239dd4cd51f82e79e357249e4aaf` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `562165a16446a832675de9b9838193d496fa7ed20df3325d2e09d3be72fa0c4c` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `7c75b46dc8ec42515191571815596f8c63d90d14eb6629b128846948cbb07268` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `0667b718cecbabda34211b5f2b04b550b89e6a0fb5ba77a9c32b022a6e8d8491` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `d259c5ccb8db773e0ba62ef2cd45de51190745621727d3bb050b8be081c51d5b` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `71feae07d6f1107c439bee1e2fce8c5cb747cb0f6e3efb41ee63f03a99080bc6` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `561c2bc0274eaf6d13fd34898704bbd2ddb7918abbf4282ef4a3cfed9979a038` |

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
| `results.json` | `b44c41f163e121c0edd50fb9f626b55c575b97b18f8688c3086a6532c6552b5a` |
| `screenshot_manifest.json` | `aa59ae0c9fd091f8504ba89264264a6bae6a89484f55e8f5fa2ce97d44d20099` |
| `paired-fixture/results.json` | `c220725d159cc197293d66f2c5bd2dfc43a43f915f7c03d9fac0c9b4c77021d5` |
| `paired-fixture/fixture_manifest.json` | `bffca4cc1620645e0ab29e660703541517c554ff4a53ddf6592d252559f47b00` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
