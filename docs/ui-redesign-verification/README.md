# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`095ca8ce981ee06647b48c8028d20b4b81fdbac7`
- GitHub Actions run：[40451480322](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40451480322)
- artifact ID：`12710419977`
- artifact digest：`sha256:19e8a00c8a56777818a2206462bf206d3b94b009065ab8e298a8b83eb80c99bf`
- APK：`app-debug.apk`，SHA-256 `312d6b9c69fe8c1de880d5e8423e0096016fbb979ab2f0c290354cdac389d830`，22348436 字节
- artifact 内未配对 smoke：`pass`（2026-10-27T04:54:15.998414+00:00）
- artifact 内配对 fixture：`pass`（2026-10-27T04:58:16.975188+00:00）
- 报告生成时间：`2026-10-27T04:58:32Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `2e3170b49a96948a6b8f4a2b45390bb061591dbd07454ad549b1d1359ee13f55` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `77745d4dcefa95d7a114d1636215f634757ab4c275bacb9a6827a25c57cb03a7` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `b7559e82ee8e380c879fe564e26bbd0896e8c4d29086250984d817794530e38e` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `7d2cada735766619c470685b9c42d8da163ab5b17b500b1805b4c632f04705f6` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `b65ee790b473b3f3dc2b0bfe040a761b4b964c1ed9b98da6161c1ffb91db8f17` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `bdf0f2c000d868ddb70d5551d3e1fb7711ce257a99c03a75a3e16a5ea816ea2d` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `e5ef7e1c60dfae4828fb9ec444876121343570eab7523b2fb90c2c80bbeb672a` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `9ef2acd8950d80f82adccc73fb0d47869c685e0a69cc83e5295ade1de1b5a0bc` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `dea5b4eb25f00591cea8be3c7eb44fd3350e80d2d5a3dc6e1f5fddfb69eec416` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `40a3616935aa926e31ebf9c6e98b5779a03bf23a0b03f54f7e432de79c9edb02` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `c153e7a1b06003443185c3ec7d38a1cf6f2bee27b21a2857c4eb2334d0504ac4` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `38584b54e8fcfd9f3ba449014b0e7839e066d19ad9223cccd2f55aac41365260` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `eb20fba6133088da568e68b4a47c3cf5800be1204f0ca029294c45c2e1eacac6` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `68c3090790ad97ed0075555e002222a3d445c3422be887585ac6b1967e4280be` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `eb20fba6133088da568e68b4a47c3cf5800be1204f0ca029294c45c2e1eacac6` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `eb20fba6133088da568e68b4a47c3cf5800be1204f0ca029294c45c2e1eacac6` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `082f2adf1250696f13466180e2d383e5eb740756f58dc56e803525bbf2b5b44b` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `7a8b8d40ba659ee05bb404f5e66a9cd226d64234ceaecc160113dcb456ebbf0c` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `bc1ca4192f757fa0a806660bea7001eb2c908b3a48ca244fae55ef4192098459` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `2a5451d27f7b9d29f945ec1a6258eb5a4dce46633fe15bd32597745b884295bd` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `95449e7939b7829e38ee9e1759ce1da906c76beeebd446ba34397d5254a725f6` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `95449e7939b7829e38ee9e1759ce1da906c76beeebd446ba34397d5254a725f6` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `3fe430928667ec3cf8d2cfdb798b09af33b0c25fde040df8ea9ac2dde4624510` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `4a865164e2462d803d260805cf6dc5b05b3d337f81d51c468e52200e7448eeda` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `3fe430928667ec3cf8d2cfdb798b09af33b0c25fde040df8ea9ac2dde4624510` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `f97d3d25c9876ff4a82a8e047dc7329028c61dcd4d1d066dff5191d3e54f68b1` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `b9e912af5c30befd76b42b0d2895c6d976bf34e668b1b91688576eb45e6b3061` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `74ebaff4265ce962fa6dbccaf3cb3ae0750776e23ea61dd33796697d7de660d9` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `370c869c36cea5f13fa5d5abd5878535c48670fcf2ce24eee909cf3f8f5cb989` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `709c249c48df9f76ad02c1ec3487b3c88a8d310fd55e93483d93c53bd41f6753` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `8ba9398110c8cd9667baa855579d813a3c6cd171b4451075cd6ab4743e340472` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `c29eccbce6b09434b54246d813a47445e0630a38172f83124cbceb5e149ebf7e` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `88aebd71ddf89850f62ac64e3cd5b49856a58d5e437e28b731fb259a70b62caa` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `f93d1e7c981ecd7ef54ddc25df55e8a8ed88bc4b16f9aa52056a0e848c8974b1` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `674467e80c44c1f5324dd096c70bcdd437ae4e6511725773cdf01b772053bd42` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `b201c6c16b534816c1483922cf66b3f684afdc605502fb6a40044c6af48a4819` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `819f7b2fce0433f5dab1013b8723ec0888c324ac056ac8b6878e9e1269c4c777` |

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
| `results.json` | `3f480a28d324ac1aa547e4cd19c4979b4a2f6eb479992c39dc6a10888d11a27e` |
| `screenshot_manifest.json` | `a3e417e23f564b9ee6de9d3f96ebc0ec0c1ce0c6212258bf23b98b7254f39ee0` |
| `paired-fixture/results.json` | `ef5557b760905f3c14b5a1b1a51df30216e53619b9108b8a9a3b459c4c1ee59d` |
| `paired-fixture/fixture_manifest.json` | `2f965e82db86d37fb6e36df78c4521504bba38b03327165510a3a630b7265e3d` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
