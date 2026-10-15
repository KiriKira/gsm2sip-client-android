# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`74b398d8ee25318561d6285cddbe11df2e5013bb`
- GitHub Actions run：[38689317964](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38689317964)
- artifact ID：`11951422210`
- artifact digest：`sha256:aa1552d4c089846eb7efe12e93f1700d1a11f0e11262771bcf18c06005b7138d`
- APK：`app-debug.apk`，SHA-256 `554000ec5f3d3e92485a4ba8bd96df78434f261c87a3354e5d4e6c0557999984`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T06:02:01.623184+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T06:05:45.916873+00:00）
- 报告生成时间：`2026-10-15T06:05:57Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `87a5aa7339efd43c94f11d247e79179138e7491b56ae9e6c1d01e7d40a518a75` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `624a3dd3149100eb336358b637fc013dff309d305fecd3ac5b3bbd811ece0636` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `8f4994ee465dabdd46d5453e294a276ab0081bd457d1cdd472481d77dc0abc2e` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `ec3bf34800e41a42f6ebf3db2c9709b895f58d3481dacc6d2414a51990f1e269` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `3a0e0e34e4e0bb81b87faa4c649823c862df8fb39ad7fda110cc200d6ddeb70e` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `65cd68eed198057af07edb09f1a5bf7bb67dc83aa3d6aed50132ce91d72271ec` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `527cf03ffbd679fc875e6e6fa2d036aec747c5b8dfce6c9e26f5bf623f70329c` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `2389ae76cc0364229e8878b74afcb69991967d2ec8f02cd6ed9e4cd1cb6df78d` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `2a34ac293dfbeed86a1d78a2947b7a83ddd97f65d779b295dfad9bb3012e39e6` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `a76a6a5e4506e3d9d974f73bcfb6f8152c2004944177dbb3e20645ea01c065a1` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `baa373b566ca72bda55c7d2d6e835232659cfcc60b14984b66aaa0b2c1975cb7` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `2243437f167aeedfe53ccb30145fa5d5a8f85d08f9010a779e2262a7f0b6c219` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `1c86334c0b79a9e18b91b65828df9712ecce0d21b18a94e09f0b7a41b425894d` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `012ca5d854fbaa23d6f58e7c6d59f5e73de9c14c8a225c5060c9586383a12cb5` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `1c86334c0b79a9e18b91b65828df9712ecce0d21b18a94e09f0b7a41b425894d` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `1c86334c0b79a9e18b91b65828df9712ecce0d21b18a94e09f0b7a41b425894d` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `57b53684453eafcfbfa8a3022792b4ec9a9a7c94a1a1b6ed6d0fad77b3330900` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `d2667eb22b9cdfaf5fb07400164f3256acf2ab6c24b311b46b85a9e37a9278ce` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `118710052409779e22e2cd6995294fe41e2657b37a70b998add6a65cbf279ddd` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `472be25de60b633dc5c771aaf3975df3cfaef87330861d002c6cbb36594e1bb8` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `15d1b3511cf4007d4dfb7f9979860f765a13983e91df684aa7bb17c0c82105e6` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `15d1b3511cf4007d4dfb7f9979860f765a13983e91df684aa7bb17c0c82105e6` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `85fffe11b5dad7d2931a3f108da49b396a08949a364e815a35b059648fe298c4` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `2f5095046a8e8b228f0da4e7a6b1c83d1e2bc6dcd3350890dcf01bed109f0e24` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `85fffe11b5dad7d2931a3f108da49b396a08949a364e815a35b059648fe298c4` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `df46ae7ffdcf600eecf2360ab33646911b58c79198df352232cf34a4928a97d3` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `e39bdd905146ebef8ec021477991c5ec5d2f6d1f123568bac2a461a2ac19c1c4` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `def7896284f2ca2212ad197e20e0f73ea5d3559662794491bf637134e90d4372` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `532baefb3b475a78601b1f91a4c020b7bf4a562beb597e9edf829122dbe5d26e` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `c4b393505c641a8b18b459957d1e53bb132570c0da5e62c20999f55789c37ab4` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `48921910e3edf169502fcf57806419b4a9480d1ca212e4582b23b6167a57c5cb` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `91d44c4da2822c18bc9a37d30712b3417edc492dafa21eec483b3f601aad5821` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `5c71ee22345c27c093ff1b0e51daea08b1b66c2850e96894bc4aba9cc6050a68` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `6658fea99549c4a6c2f3457f239afd75563f4a48e91bceb3df5ea94bce4f1800` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `a5e764f170e6e7aff51bbd9c42178176a7d5c3f3ba3e1060d19f5e9daff7be93` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `bfc53bbb857e565821dbd618cf54143584a55fdb04d34d5b07ce38a257eb1d76` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `1ce3917d8afbf660c477d7514800fb36ddf67858500a81e47998ece9b7716029` |

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
| `results.json` | `8a7dfd22645245ae5cd37f98fcbe2d6de71fa2ca5a0b8752684b28628479e9c5` |
| `screenshot_manifest.json` | `98e350f8e26d20edbdf8ff104fc9970cda2655cd79f477b2bd88e4c712fe5d80` |
| `paired-fixture/results.json` | `730a9f3c08eb27b32aed469d2258cefb4c88688bf592c40404e3dd95ec97dc00` |
| `paired-fixture/fixture_manifest.json` | `3daf1e14178cb39c355f5c891c817921b707e8802d322a0f909c5f237a744108` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
