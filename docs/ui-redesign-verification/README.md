# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`7c252c16328da712f4abcb7d01ec31b21baf43bb`
- GitHub Actions run：[40337208149](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40337208149)
- artifact ID：`12658531502`
- artifact digest：`sha256:84a4b33c6e8433f76238a045e2f57efc395a9f65e7fd537f213e92f8b42156f8`
- APK：`app-debug.apk`，SHA-256 `fdffe6f34f90db852a30e0ab196b4d4d6786c47f2a8e273966d64d9e19fe609d`，22299185 字节
- artifact 内未配对 smoke：`pass`（2026-10-26T14:18:57.226905+00:00）
- artifact 内配对 fixture：`pass`（2026-10-26T14:22:36.191316+00:00）
- 报告生成时间：`2026-10-26T14:22:50Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `270e01fbf36f488c302b491829c7471db0f6a26aa76fba22405775f40d3dfc85` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `7f3a5c4e76daa4abdcfe4a1962b7554b5563ef0568875e508dcb3f26d721abe5` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `2b1918c4f9e2dbbc85a5dde7f63f595bd65973e21c2e7ecdd29256c48302d108` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `ebfcc3673ceaced98adebdf524bf30b1aadb6d2a32c0b21fcd4467db6de6204a` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `be0021c1b403271d5bfafd4bc12583484d0326799842078491f60ae06fef7661` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `86215e9188bcbe4b2eae5efa3337f51200feb03bd4f84bbaba4b5fbe1ae961df` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `2e706a800efc985ae56f00e804389c7e5c71572b8314909c72ffa4c30fa9a899` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `93feca0fc03aa4a4e5a0e8ad179847b5f725592fdc17a3e3ca0b9b567d79f3e5` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `0da644c4b9eff6411ea63668192d47c82313943adfe08710d6f55c62755c48a4` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `0cb49cbb78d9292feeeaf069a57513a1d06b09c24bc26195c7fb9ca120d9f3c2` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `e6edf55fbcb72d1772707afb196edc0fd7835d8635503c7d5f1e8ce8be1173f0` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `5db7edee47bffcde443f287c15b1eca807c7be0a8dbeccb37cb1b900bf26ae11` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `216da8d7652167743796f28600b4559b17370e26859dee51d2fcc8bc1fa09080` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `272c3d7bc83eaf9853dfceeb8e94640b99afd53233c5a887eea1f0a180e23ce4` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `d71a27a9b4c4e8638c45613239b691c1173d427bfd756a7f473bfccb7245c4a7` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `d71a27a9b4c4e8638c45613239b691c1173d427bfd756a7f473bfccb7245c4a7` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `a609a69d2cf0e5707a317db6127749dc1fb82cb7d65398492c227b87e355bec5` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `9face9a316dde0d78b4230eebe2ebcac21332096a9b63ff6f6db5f8404ec5411` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `3d47a2b839589c5909340a77b272d3032663ecf0337217300856c65a46c1cf5b` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `35778fea2267ae461e75265a8e2ea69d002be814134fc6d31eee93df9bd17705` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `5b4977ec9aacef62ff693d72c1763368b6fc91f660f32ad0b9f427f15c2af2c2` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `5b4977ec9aacef62ff693d72c1763368b6fc91f660f32ad0b9f427f15c2af2c2` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `826c638cb42717067965f18084ea290d8ee25f20ee6e77b73ae4c03797968ce7` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `eb828895aaa8ab71d83a1f611b70c55ad2aff67db2fc59c1ea6cf3b81a3572a9` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `da7e2676d4f116530ca076382607156a3b536c9a86587870682d2b5cdf4e15b3` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `a2911b0355c7e1eadda09be691eee607ffbdf68e9f0b38fb4c53c77820ba7209` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `67368d8b8fd8c9fe992c727289d11f7a5f3c1def78659ce27279abe032dcf866` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `27689ed6bb2329614d6d811d7f126064a4fc8c186d8d97aee0ce4ce20accd40d` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `d4b5784429661cf865e68b018f15350389a226a73770a637f38ef2881c7ceffc` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `ad5a2087113383acd719d719008ed20b24548ec638c8bd3434c89d4388406ee6` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `70377a6ec6381d0391868afb58fab9cb7ae857950df2eaf0da157254081a5de9` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `163ffd24d18d4c66272320d608d03449680350e753367f03f891f268f4c42b63` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `37510f2eba8045965a89dabbae5ae4994b486663062b308c21e4e88b1a58a5e1` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `5bba7d6c9deac3ed6cd1c5a69d23928cd088ed7ecbac1e59d4d0c6a6f57c143d` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `7b2f79183bdf4ebde2001840cb22da6ba94c32b845e577417bb764702536b7f8` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `2ed5d9ab5898f28caa1d1f5cffece88b9af4181b919d916fdad3fd838df9faca` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `cb8ed3a4060c1c0522e9f69d26c59f0404ec5caf42dc2737c64caba31944aed0` |

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
| `results.json` | `4a5eb4d14a0940da8f7b6a58b85b4fd3583a37cfc59c7ca2f3ed278254d51bcc` |
| `screenshot_manifest.json` | `6167db91947faef7f2ce85461e9c4aee3df935fb1a8315ca32adf89f8e309768` |
| `paired-fixture/results.json` | `f4d717ff1678019c596498a581743dc88563457e3cb309269c791fc8c5fbfee6` |
| `paired-fixture/fixture_manifest.json` | `d83843ec0aa2b990e4c56648e24cd098f0dd36a80fa57f3c2c4b0b7e2142760a` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
