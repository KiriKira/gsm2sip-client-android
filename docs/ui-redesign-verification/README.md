# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`6e8aee894ae6b6fee9dde631810dc538c1873007`
- GitHub Actions run：[40537368694](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40537368694)
- artifact ID：`12750840610`
- artifact digest：`sha256:d2f53f59aebe8d4135b0c9642818177d891bba8b61d4fa4cefb34a48c0d608ba`
- APK：`app-debug.apk`，SHA-256 `932b110921fccaa97ae943e9e1618193508c32d89aa87dd56849fa3c913a5252`，22364944 字节
- artifact 内未配对 smoke：`pass`（2026-10-27T16:15:41.823187+00:00）
- artifact 内配对 fixture：`pass`（2026-10-27T16:19:40.271897+00:00）
- 报告生成时间：`2026-10-27T16:19:57Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `1da9832d7c639221423cff5decf6e8cc1c0973e9cbd45d2d03e12bfac80c37c2` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `0d7c3f401c2d34ffe5015e5e06a803202b32b30707982773d874c2fb61bab289` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `554f4d45c1062d9fb3064ee3aa948f21e09af6245ad9c50c8b421b4cf56537ff` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `a500a034e78b6f39926b8f649d7c83abcfcbfab85d91e04250b5bc17aa2813c9` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `6c67e4372791b62a494c750643a619770351e8765ed5fe31f0f3305caf667776` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `a704c2c755060f81efc43fd60b98180a6ccf8d8905b94bb13cf93ed4d047003d` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `f1d983b4ccc7f0aa3735a764d0a8c29c9bb0dd5e6fd6edc75669bdc52c93a60d` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `fa6433a2d53691982080ffbe542d69eb4b978d73637e76233070c091db0521e6` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `190f6c8c0d5ed4e9430a928765b17680fb27576e8fc7f1239bc97df831526dc4` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `8cb4121da5edb89ac18cb0b773a1c4be66aa38ff38557357d0a137d13c7fab63` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `c8e38d7b4ada97c3251b5043fd6a507ccc514271d782fd02816acf44839cd30f` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `342e462c384717f06346b9c18a00339c31efadb96e4fa49c232a846d07f4e72d` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `ede95eca042d1c0e676247d9a90f6e032baf0b291c482e3d9ff1168c42fb46f0` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `bec943350753ed0f1458271f92fb777a01879ce3eb62938f2f246a75925d6cd5` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `7fe8884f7976266a5a70abf6c52c1f737e47b6bf9a24acf71359d24eddbbc33e` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `7fe8884f7976266a5a70abf6c52c1f737e47b6bf9a24acf71359d24eddbbc33e` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `ffe957e49db57467bcd9573b4b06882be4b1a2fd8d460fee21b799387b9966a4` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `be01faf56b99680e499e6962e17a052e66ab1c79508d08778ac92f7c360b8b18` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `5ad77c7753f3b1cf005a6877ddb676ed6b959516909192ee4dfd31e59d398ddd` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `c290b9b53e659a2da89f5072b57de20d2882882b90ffec85a30815baa6197d38` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `92ab325cd92a4c23e596d118e1addd46585ba2bf9d3cf1b3f66b342757dacbc4` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `92ab325cd92a4c23e596d118e1addd46585ba2bf9d3cf1b3f66b342757dacbc4` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `51259e320ed6c4d64d8eee0e0fca365aa3947a3ca259c1f4839bc57d298e227d` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `8acbbf1f2718011c3e680433e6dd384aec6851b972e0db588975c662518a56ae` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `ea3e73a21dc00008c1c09f1753a03eecfb399ff3d55da2999a8347514adffa6a` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `81fb034ba5a571d835584f44277fe5ce3313b13a8dbcfe43c7421dc9115356ed` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `35385219428ad03c341e409d6f1800118b2ca65291070007e1be1493eabe48fa` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `4902ebce513490ce896c821c1b78256d1e1d25bffd820cd0226fcc0993fa89f1` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `ff173428930b0783031550925a59f6ac5afb86cf18826fc25914c79932f07682` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `f45405a3c18208bd8fc732fd912bd659b80f3abb370abbef090cbc2b26099687` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `0b7c8171d48029dbd7a3e6351da5866d8da2b16ce6a6da6adcbab07ee33b3b0e` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `cbae1c425e7caa18c95980172f89f310327a260fdd09555f04c6f31fefc97c60` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `d3d511b96647b5a4be88cc5ec389580d3f8c93d9e5f77f96edf5401276316d07` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `7e29007a0dfaa939aca176750ea9dd6ca7bd340ed46bbee88db4470009e1b524` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `b84038fd6700d0d16a4d6031377454f50643a40976d5bbeb25bbfb6132a27a4a` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `135fa66869745c1c5e9294e0eabe67aba1717d7f4eddf97dc4598b55b6b390d4` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `d1e71f4f5b75bf0b285d9ae6cdaa3dfbc589a69447e0f2ce7040cc979893a387` |

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
| `results.json` | `fff5486fb12cf673d0d3580eea7055373150d620e5cc271f96fb79adfc0116f7` |
| `screenshot_manifest.json` | `7c47f5e57330b6c57970e655dc52b51f86cee052c8db463cd2d8e50610473f24` |
| `paired-fixture/results.json` | `2eb7b2ad1616dbfa4081c5fd295e77829c5aefee9a728d23ef443c6fb92f1c35` |
| `paired-fixture/fixture_manifest.json` | `fa36043227b8e55814bb10d79b12ea7868a7f04e81285fc7c4749349ad5054d2` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
