# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`94765b06fde7a97276bf8b746fb17c28dff322bf`
- GitHub Actions run：[38774616096](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38774616096)
- artifact ID：`11993223661`
- artifact digest：`sha256:7bed8ff14cdc8398a777dad810c8b108b14463e6a4e597ea757fcebdbd7a8308`
- APK：`app-debug.apk`，SHA-256 `b8481de210f80df0ed2163f29c2f8c683f41f7777b088d0b9642bfb70be03874`，19744736 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T17:15:19.913943+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T17:19:16.260119+00:00）
- 报告生成时间：`2026-10-15T17:19:33Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `f0a4c040c7294359b2c8fb45ad5f191530c0bde5c1b7679f66dc6e86efde1097` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `e73dbc52b3466cc6bf941bc72ff29b1c124f50a9c394ac86790e52726ffdd2af` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `1e28b97a3c2d415118b13582e6d77238bcea5766f22592f485930e1334d6d9c3` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `a3c51b9e6a182ace375a103a4a27596344a6dbbb27387f50f7298f521494acab` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `63f42600aa55f6b1f9de92139b0d0bb76a279962860cf5edc93ecaa4777a1e28` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `b9c89d340190a52aa9800ecce2d6f3d6361772da262e0657133ed109c67d8421` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `68a8f68acddcedb590281eefbae2162ae506be3788669a9442fb6432e9439339` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `63d35ee7226a9e388915ffebee58d6640d5d025d3a9e12fd5815c70a2087b5b9` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `2f926e05a6aed9dae305b8c3c66d242c5cddb99f42093ba4c017d8fd75f51f42` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `7e62ff8a8be0160dd395bf428cc949c7a5950d51b3f3edec40c829c0b708bfc8` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `97479358b7049cd24c6d56485de2dbca9856a4e4e10bcf59f01b06073c3a42a9` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `9fc33e2eab6bf1c39abed74fadbd2c07b4ea2992201ac91e0b15cfc13d9187e1` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `5d75eb942601920bcdd164f934dbc2ccfb920cfd187f0ee6570ef101fa9ae203` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `7a64e9bf547e21427ef4c4daae4f12c2bd3aba89c67739c24d2b374755b25e41` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `5d75eb942601920bcdd164f934dbc2ccfb920cfd187f0ee6570ef101fa9ae203` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `25027040e8f0ab00cbf25ea227f623da9c640ed0507f70004b17ba6711bd5ab3` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `9d728c8efcd2960330ce706e2edc0ee76f39f4d165f900e3080cb66c1e9a932b` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `a072e869084eb81838cdb2047607a7c0d2c0be513a4eb5890da8a22ef5f7d1ec` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `0d3b76a06a67d82d7442fc88f761c0e54c614625269669acb9b8da20c73750ce` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `8cb1b7ebeb54e5d850e8c34399fee36fd9c9755d09cc42d70a4a6947d258de0b` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `ce75a1632bdcacd177e86b47a378f26396be1bce2619d2b4f14d835c1c2fdfe0` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `ce75a1632bdcacd177e86b47a378f26396be1bce2619d2b4f14d835c1c2fdfe0` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `2aa373622943826ce7c872025dfadc4a9a9bf58cd795bbd0b212aab2a8ec7b34` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `a37b02e043dee5351dc64aaec132602145cdb4388fcd3a75f30217256a15ce11` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `2aa373622943826ce7c872025dfadc4a9a9bf58cd795bbd0b212aab2a8ec7b34` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `6404642e81d9f1bc871e63333301e43d67a80ecbd8c97aabcd6d5f94fca3807f` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `77afba1acdefce05ec9d6e86ee3d720cf7fb6aae0129113fe10a170ea2b16b79` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `b96699408849ac4565b62cb5afac204a47c2d0f49aaf5907d1bbf16cbdb7455b` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `7891bd63539d0b9d801474e3c44cd70303e47d58ba1d0d941a433e0395935104` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `256c576970bc5845a549f03674a7d79ce643271a66a17c5bc0de88d0a7a82194` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `e1a455a2ecc2d984931ec083e7a71fc97290344d608a17c163a5785d1876d139` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `4838d285520be97b70cc2a3e2c08f524a18815914d872bfba7d9ac030e4ad093` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `e37b8faa23ac1555d773a5d1896e88c6b034547bfeca08b1beb1c5f82f8bdada` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `48336d89f17e4dd7311a5dbe6eb6fae1f5f9f963be7ce8773ff8f9879614f593` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `81b7c206ae58cd24ef2a83a462605b4263c995e964bab3e882f5337807a98c49` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `188e85850490334532eda5b093573824fa32e6c60fec261c72edfafdfb1306c2` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `bc707124fb4059bcf4d3925c221f0ae343c3d598287568c0860e1833e5e9e4c9` |

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
| `results.json` | `b28f46c616db8bc78a4e9a0121bf6af68c391c03e48fa1fcf6680c466ebede46` |
| `screenshot_manifest.json` | `4f10d294fa0986ab75d107970a6268a02ae7fba7db5492b3461488d1fa113825` |
| `paired-fixture/results.json` | `f6f86b6cfbb0d0bbf1f41862a8f52bda1953becfa2d39146715f184d6aa51195` |
| `paired-fixture/fixture_manifest.json` | `8a5cceadad58d25d76b43c7a72d1a51f388d409e2e2f4289eea25018729511ce` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
