# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`e0531f704c3949ae471ced3d46c0cbd9b8681b53`
- GitHub Actions run：[38744777415](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38744777415)
- artifact ID：`11978876806`
- artifact digest：`sha256:56446168dd187bd0859bf53fa396ae8038aeedf314d30c7cdf0d5ca83b0f7a78`
- APK：`app-debug.apk`，SHA-256 `aac765452049e40da0efe3d03e5f2e63900720826d6600272f18b845850f7448`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T13:48:44.645758+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T13:52:22.706759+00:00）
- 报告生成时间：`2026-10-15T13:52:35Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `fadaa4aaefff7522ecc4396d1ab040693a6e3da3ea9c62eb826b80ccc696d014` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `08b829c8ced40c2d3b2061beb32521bfb314fadc00780a0d0af56ee55dddd468` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `49e84aaa544dec62c6a9b3ef41986c6d84443e08b63bb83a6a0be964c04b7f58` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `d8c1bc9b4bec3ca790626a47059ee8fa7da9badf1eb380add11db455e5ef649a` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `5987c11c96afd8894258a6458d8244d03f334c6371e089171782c803325629c4` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `41796579c0623da3baae0d2607910a95234027b4337f9eb89ab4be8d0a5ec5a1` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `0e1e654aa61bc6f7df0565f87bfae70561addd99796ce695a6c2f2965e09293f` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `fed7fc22cce0d5b6f0e7f162bdb8fbcb3cd4287c9821132459d313f87fe6cc11` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `725c539b902442b65810ac43daf523901d9cf92f32bf4829f6f884e50def5533` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `f4e0f668feaa6e88128caf038f32bdb74aca1f077bc1154d0953104ef0090128` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `ec9b5c19b447ff2e6528e83d0459f59937e9c9a18e8839eef9327367a1ab2527` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `a6fed1e335559db01d90398e8b87416eef669a749a712e36ba09bd9c862c0d2a` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `8f38768056e6bc9ccbd42410e79a7acdc1be5c72072e8b490a2c94a66132bd02` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `5b345674da34723843de7e91a8ee6da732b765f9ef51df491738867a3cfb56f0` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `a31efa7e6656d8d095b41ae41561d3dcabaece1a51c4a3ab6d326295cb4d0ddd` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `a31efa7e6656d8d095b41ae41561d3dcabaece1a51c4a3ab6d326295cb4d0ddd` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `ab95b1ae369d0c17a8ed97e8d2a39db75d0f447bac2062a8cc71a23b08bed1a4` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `e8ee1f97c8e7f5cd44de2850721c22ebbd5f0b17b173dc08d9fef42bb1897b97` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `ed537a1e0b1ac16f38d6e51f04be36ea7d2e1a4c3b98b0b543d21b08710d0392` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `b0e0e247429278b22034d12a88800c5d4cb34b604a16ad811e364341e54b154a` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `5faf3ab4748df706d1796249e345c1976c9181e342ae38e4aab2605fdf6cfad0` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `5faf3ab4748df706d1796249e345c1976c9181e342ae38e4aab2605fdf6cfad0` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `90b6526e4c61d3ca17059a76d1d540ac2c70000a6d7ff479fd08fac72222d802` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `5feda1db8467efe2b08b3b1d0cacd871a5d32dae5ed0d99968d83c69f5ddeeaa` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `62c9c9dda36cc5a1932549ac698c6833c66c1b535388ace2bfc9b15a08e63b34` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `1a7da2c7c988c53baf3de53039ee4b831a02b8e796c7a9859e5100dcc1a0463a` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `93cfbe507ff638e8fe43ecba80f3e05c64fb194c9b26835821ee6bfd6b6a362a` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `f91f3295fb519743d61bdab9b05f370d332982d047519a47aaf359b7ca0338a0` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `ee08b7463ade4215ceb02a3a7f2aee0f4847bce115dcfd00b1e9c484f2a37057` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `b73a76b92d43d45eda8b8bdad76f6d6c3ee8dc61ff8af788cac3d1cfc6de68b2` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `828d8ac2e7fb5d24bfa1facf13caee7af55d12307d704c295ac899d7e3683a3b` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `4a38c25b3b3054ef3885bd636a6ccab836472e656cd4d9ecaa350a8ec48a745f` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `d830e8aa3cea11b2f61e4bc1b1ce6013fbd9af5c6991022028e31365c992335a` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `b322475770e5e28ff3876d3f769984216790605d69c3698fa4bf71c5cc3df065` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `ce58db9d999eb680043ff6134b40cde2480b4a08a675af5a4c83fbaf4e93708b` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `c45a22e901d111ace2288931999c1979da84c7b7624d61a163c2cdc758c527cc` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `dee357ce8aca1ecde8ba549c5e5892f0fe1c8a5bfabf616e02b612efcc4bdd4f` |

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
| `results.json` | `3a52746f64c24f83b6e863f3f6e27b0071702425c61d80b5773047c131a675ad` |
| `screenshot_manifest.json` | `e2182e2ba74f2854a716195eaf3a8d075b98e1474471a088fbd01c5e3cf0e771` |
| `paired-fixture/results.json` | `aad4555548ae9454952742e52a225ee5fff08ae00ded6492386e14b71caad635` |
| `paired-fixture/fixture_manifest.json` | `6078bdd1b2a7ddb2337f92f097e0268a78719526f5f5723a4477876078f1cd45` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
