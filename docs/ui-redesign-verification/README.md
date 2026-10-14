# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`ce9391b99b3b40e557d8e48ff9a9f917ea50c4a9`
- GitHub Actions run：[38605596759](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38605596759)
- artifact ID：`11915921504`
- artifact digest：`sha256:b0316d81d5c296de7991ea7793a0def4a533dd324978e0e28b6d36877784fde3`
- APK：`app-debug.apk`，SHA-256 `85ffaf31a91ad2ee160628fd6c0e7dec48823cbb63d4310fbc1d0b1da1ccab47`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-14T17:27:43.390518+00:00）
- artifact 内配对 fixture：`pass`（2026-10-14T17:31:49.113970+00:00）
- 报告生成时间：`2026-10-14T17:32:03Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `1692153f61d196bad4fc98adf056f75d7c9f8fed21818754c887efdf61180b89` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `67c637687e79d361eb835989f69e4798334dfd97e0d83e6a2353ce3a0f060d16` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `efe60b85d12f3f9d8e6b323fee681ad43bb1132a07ee1e02d390c50ec73f3292` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `d340a4d0686080145ec48181bbd67fb9c437f617ed205e7353aa5441447ba9bd` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `1ee4fffb4c116fe9d9ab85df17fb7fe0c47ff0c9b2c89106607313d0f417ae81` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `7b5ca36043ade9e15726aa7ef8460f1d07f7345994a0ec36b6adc999e243df58` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `90da9403d828d3358cbe72554f0c2b45250ba3ce58af8f9133c3514222550d60` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `27b5323a04d820765680d8882f36462b989860608cb35568729d7556877e9e16` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `62369ed45a240c952dc834010a733b087bbdabd281e4e11ac0af1c6701f76417` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `322511323420e6d06f16853879083765dfbd9f28a64f18527f003dd85ca655c0` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `09133e6b747fb2523ecdf7f0c22d0a480c2d4c1dc70fe98edd0e729899e4a425` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `415b9c024b6335728085de5dbcaecaa9d3cc0c53c12d7386bf30deb6b88d56b7` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `806f9bc97812522716aee2cb208e0d21884267143a662ff0429bfe40c1cd6515` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `2ed77369a1b56d8571bde6fdc619e878fa3a69ad1d21bbd431aa81521770050c` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `35c9cb1d87dc30574137c7e4d24983635bd8931850b8a57c89f7d1652ca63ec5` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `35c9cb1d87dc30574137c7e4d24983635bd8931850b8a57c89f7d1652ca63ec5` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `fa402671a8692dbeb16c73459354242732ebef857bb3cbb610fc5d977eb596f9` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `d37d0fd1a82958b25467965a34b0b589ea48da1cc71468169dd6a9c18a6d22d2` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `04965649a97236e3d8a8af5c06e596959cfd85b92c54c16cf6f2733373c4447a` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `ddcb840c8942efa285f1df5be0d3b18628d03f24506c7d980340776a4ce428c7` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `f522b2105e5d4e70ac9c15edd43748b3762b909bac714cd160f7377c78d8bf3a` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `f522b2105e5d4e70ac9c15edd43748b3762b909bac714cd160f7377c78d8bf3a` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `596fdcc8a10256d8b132a4c154509e740e81d04dcdd426e7141a63534bf0009b` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `c50021181b290e5f6bc4c0c0f2d44841cb82080080a743e0ff06c46c07da48ab` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `ab751c387f480d9f5f16ebf462219a5d78945b9f7abea4baa1e692bed799752c` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `5657e4a04bde6fb631820cbec73880a71cf53bacf60ee701b63a6ce9a227cbc4` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `5eee7bbdd38ba68a334164a8abc3b1aa2e6b590ae8c80f0308a4ed00e9305326` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `c06d577c61bec897d319b4e289514e9ebb796597343edaed0648485ccf4cf0dc` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `5afa4076dd543a033ce275c4c56b01e7add488e1a00276bc97bf334cbb8befeb` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `cb44aca99b6bd894da70bf8384c5d0e4d349bf812041612794b3586e57f7132e` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `0a1dbd053da4855ade4a123db15a024bdd58dc585dac22cfa82e6800ac877095` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `d5db62da8581907f1ca5a44787ea13daedd857da5025b1d0c4d0f0fd1607ee99` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `dab8997b088ef0f2a3dc8e74bf8296cf9066c9624cc2505b628bb7832724353e` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `8e13ecc2b80c8185ba68c51a888a2a5f3d3d16a14fe2c73c97f4b1df6121868f` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `8b3f6f1e7c59da2615ba2a4b931790f3feb436acedb8087a3235bb321c750035` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `c3a2f3aa8f75a0158fa9aef48fc7ae7041ac96781a77f5da8832b8a92e5a4ad4` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `32f5eed1a252b530571fd1c4dcc1465127d470cfe2916dcc750c99d0414774ee` |

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
| `results.json` | `b099ce92aef09756a49c2cb20574ea79c6eeeb188bb1a1a1aa988bc1c8f260a2` |
| `screenshot_manifest.json` | `cd99ec6a359eece7636a1a6998b04d96920969c43c5d2e7fef26bbb32f5f13d5` |
| `paired-fixture/results.json` | `c973f8ba0d16ee38ab638cfc81df64945a8ae449ece43f1d76a7fa36db4f9d9c` |
| `paired-fixture/fixture_manifest.json` | `3d66735ec5223662ae0a743c89f82c23c36b0a8752a213ffaf5836779bdd38d4` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
