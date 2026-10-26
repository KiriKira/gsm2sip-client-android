# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`bb83292228ff81418e3e1b5e4d143a79fdc8471f`
- GitHub Actions run：[40325753634](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40325753634)
- artifact ID：`12651667086`
- artifact digest：`sha256:3776a1a6ea02d1ddc403cc9ddbcdb0d8766f0e24a97d9886b156f866a28f1bac`
- APK：`app-debug.apk`，SHA-256 `483aeb847a00e6dac61ff2b2fe6ac62d08ce84b1a05bf58c7f4c672a1e7332f5`，22282745 字节
- artifact 内未配对 smoke：`pass`（2026-10-26T13:00:46.575214+00:00）
- artifact 内配对 fixture：`pass`（2026-10-26T13:05:03.341392+00:00）
- 报告生成时间：`2026-10-26T13:05:21Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `5b3b583a031bf5316d9c1c1f1934ca3f6ee40326ee46dd2b0f0b53c7fc7531e5` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `da2de1dc21923d7e6e23d9ea3568665635e637b02d4cffab12dc1e85335df4b0` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `ecf1118bcad1b2ee36d3213fde4a6cb4acebea0306ff986e6d460511a92aedaa` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `4a982727a98690f9f9ca92d033d7e6b51dd14b2a3d4dcab246c078d6bc38ee5e` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `94cc0cb42dea8d1f8ded29f6979ea481289d9a8f741090d67b7d8d6627bb1751` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `03d3957cc8f67b69af41a209d386e118f856f8345feb1d56eaeacc9dc904d3b8` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `6f31fb79ac3d69296bf6cc665315441b4602c6a834ae0fbfcf24012196a13799` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `21234ff64d34d5821df45b4bf858cec98d500ea8bf63ce5fa6fc64259c3afd2d` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `953d20c51a272cadf1830a41c900835cc14fe946f2d50d1ea9b59ec7dfd05277` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `afed9e2dbb7601afea13c8ea932faee313abf8bd284e00687597ca2473b5ee97` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `b0be59975b75b1126238e1e74ca9547d8e9e3a9fe9e73fd56bf8cdbc6fc6d8a9` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `b3c1a44a73c2675c814e3776029237470835937077883074ef0006088ba45bdc` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `f3ec3bb34a715db4fd3dcf7d95df79e1805c228d89ec466d7c9907965fccdf09` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `4538561e76bebf325b555de48af34ed826f937c673f104db482aa49f83f926c2` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `5b453b167c23d9a7919f4940255cb489a8e18879ab6b9f1919bb14900f8728cb` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `5b453b167c23d9a7919f4940255cb489a8e18879ab6b9f1919bb14900f8728cb` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `052eb07bc68f352f29560daf972816fea69d2f03c2da697bc1966a84c18daea8` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `263ba4de84de424824a2b5a8c6590c735c04a02b62c0910cd94defe7115c93fc` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `ea201d91f0cfec5a190c1c659b1d8fa666877f1da9b7ea041b438cccb3ce70b0` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `62af158aaff80b4fb881b00c64b7a8133f7197e3d10a5e98da86adbb6b62567f` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `62523968690f13cca942853aff1102e3063bdbeaeb68693918ee2eb8e4dccb71` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `62523968690f13cca942853aff1102e3063bdbeaeb68693918ee2eb8e4dccb71` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `4c70310857c47a98edea20ad77244667a10413baa7b878e432e5b453021351f0` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `e75ec5928b8b64f54eead13ad062c6d885d2dc58f27b9667532b6a36d2e5c06c` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `03a2625a20792c85f1aad8aec83f695c2acb63fb41f152a1fb0fa45118886baf` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `e03a49073dbdc42b1f5a6a21a9f2bb64954196d1d4847058b5fc6fb7a3f93ac9` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `95b77819ab7cd730554fb08401c830e5570c4fd4ecd11b595e248f3b18bebb7a` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `bb2a3efc95bb617779de210e570f424e2ebc3ecfec39e543467e6c33251d7165` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `56b93051baf75b934b2717a3a831dd6e64195394776f6df580c1bd78a6eeeedc` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `53fa2d15d709c9b3bcde534cac0b575cd6d1bc798fd1b9e0a4f09b6f169d2e19` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `31d821ad1a3a4d5f0261c64837bdb23b9c56ba2cc776e988b1b8b5aebd766012` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `c49161339d423a6f1eb071d137e718207f794c3f1421e02251b090e082a2780d` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `6953275a291cdd5e2fdf845dbb16090e75af98dd7f1a090044d9cf3844c7b17c` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `823897ff8c63807c2c9d4747de5d5c84eca07cc91e054c6726f8ff54e646ea29` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `505916a179109f0216bbfda30aea1ef72cf25752aafaf9df663ebd829021d78c` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `17da356a49b7e1c1fa89a117ad043ccb3a95d4b7905e5d646050adb6ff535a8d` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `4cc5826789b6c9e4d0f6780f296d4434e3cf4f0ac0eaca54b3501baf5224a1c0` |

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
| `results.json` | `2687388d5ecfe25995a914c28cc922ae4b2f4545b1201362c7e07eb1f7aaa77f` |
| `screenshot_manifest.json` | `3108345a28ff084a788376bfb11ff4114c05d7e15e7a23149fc8f1e5147d1448` |
| `paired-fixture/results.json` | `6c6e15ed14c2fd5f786a16e182c2cb15ed8bfa7f3ec2d3c28a97b7cf5ab1c39c` |
| `paired-fixture/fixture_manifest.json` | `1e81511945eb91533286d0bd8593f7cc0b541df1d5d79742a3e720e0d5eeabce` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
