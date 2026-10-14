# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`015d853a2dbf414c1e6845e59295b2d722b4ab8b`
- GitHub Actions run：[38564352720](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38564352720)
- artifact ID：`11894432196`
- artifact digest：`sha256:b495114b2b69dfc87f8d10f0afbe566ed277a9aff1462f849220615d847a5daf`
- APK：`app-debug.apk`，SHA-256 `167a6051cc61d856d3654b578de460c37c27643858d1be2dba58baeddfff5be3`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-14T12:39:12.505409+00:00）
- artifact 内配对 fixture：`pass`（2026-10-14T12:43:09.825503+00:00）
- 报告生成时间：`2026-10-14T12:43:34Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `7845e2359cadff66bf4a5eb457942d20305432e9e3235916ea50df582a58995e` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `92e2ab645f8bd089fcb68483d44ae7d19f3b4f1f7df9b0d2d4cb651c2248adef` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `f59f6d99f3f5be84b462de480b238ebd0790f028c1c0b35109adb31b908ea6eb` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `3a963af36e11d481e1f1a3fa14ab4f532c1a546720b3e504c7eb3e67bed944cb` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `7807b1937a82e5cf03002fdae1370c05b14c1a9b4d2d23088deeec949c927889` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `d0ed1e0db7d732547c04d33ce3d741be40e31e5845e6408a1f5e910ac1c36013` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `2e3a2649a306b04c025c499d66e504c8a7225941cf0aba1f8f3d59c704effa31` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `e19e9243060077adc79cd1a7d12df65a6d63e0aed3b7a41ca0afa16c359c46cb` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `d12ca74525270e43b48f7e4cd127df979edc65ee9881226bf5f78ab0a22799f6` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `a6839effba85719e1a477279f869c3a998ca57c9d9a1dc68874119a475f90a1b` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `203727588ad0261d60522cc0b8b0d6def5e0f187be5783d2723f158de0c1da2c` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `4df5d31e3b7c652e37e2995b150fca864babdb93e6bda4a48ef5baca5c761dec` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `28ff8ab9b97f52ac0446350e1e9aedd599f6a39404f1a2cf67e0a7f34134259f` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `892eb0f9089a15d89841557ec92160a835e7c1028c4c3835d9f7ea0fc35a02c0` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `28ff8ab9b97f52ac0446350e1e9aedd599f6a39404f1a2cf67e0a7f34134259f` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `28ff8ab9b97f52ac0446350e1e9aedd599f6a39404f1a2cf67e0a7f34134259f` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `50df587ba5348a264e8d76c8724e4caea031299227fbbd2f4d3b7286fc8fc307` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `3626e0fbddd9b421eed4cc179d093d81d50470a92d6f8b8d0160fcb5a1558b44` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `d62eea91c6c2aae91796f47c3488163258c01c71d447f48d9431dfd2ef4cfc7f` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `e9d3d4cbadb5f90c05017f61116e7f000654f6d1ae29e9d19790f7fbe8d50540` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `378477ffa026f7ba43a8e014ca52cb95f516822d9b8830301d7185698457b68b` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `378477ffa026f7ba43a8e014ca52cb95f516822d9b8830301d7185698457b68b` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `f3f3af779637c0b5af2c163d29d30389fd29a46061b4180ecee969797334ee0d` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `41a0e6c4bc9c98ae7e9ad368712477ea05a4562e1c8498891c7314e4629392b1` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `f3f3af779637c0b5af2c163d29d30389fd29a46061b4180ecee969797334ee0d` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `2fb6a5962099637beeb278fbe5c3e7953fef80a0db3142011817ae23f6508dea` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `b10f9211becf7b901113fa8b026f35714c3eb6f35168839166cb39f03d8df239` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `fe4616077b98d81330c5c4d5678a4826c21c804272aeccbdf5205a34f0a42c68` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `d11f68e9e6fc28f8628d9d3cd2b66d7249459f26295a4219b50e058ca5bd529e` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `a7af8a025157fb6e06e943c922ec7e003872d23b059a79bd40789448cd895e78` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `3477879b3305ad8792bc7608e334cafd2b10100a2a497ac01cc3d4cd1bfc3708` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `9c30b14a4b2854f3286c8115c6c38e7ff06c7a1ebea3e198b5d748fc8a20c256` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `48aee10e174ea44d46edc565024c535ca4dd8ae7691ad16fb7f517a12122c30f` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `401290194096ac9a12526e1bddef253ec55a5c8bc28cc6615f3ffc24a87c5278` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `3747c906039733baa64a8cd49697311f672d14e0331588df81ca9b2221994c0f` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `c92e709b9c2d52d271d8a559b2d54fd3e7e1b97c39f24865069bc5d43fe24b21` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `ad0410f1c6de3a73825b69b8ec854abfadf5fb122e73f5768673b8a09228007b` |

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
| `results.json` | `b7ea0c64d5b192136c49c6ff2764243316b3b6ddb2b2acd6ae60ed224a9d925f` |
| `screenshot_manifest.json` | `6f4f840637d578c82667b086c4713e096366826f7839aeaff593d47b462631e6` |
| `paired-fixture/results.json` | `082e7c8c9e4a9bf39139ea386614db56a35ec50d5d983feac9abb53fe975a34e` |
| `paired-fixture/fixture_manifest.json` | `c76ba3fdfd5bb8d80200e6dd5d38908395f9ae26baf65a2060788b1223268863` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
