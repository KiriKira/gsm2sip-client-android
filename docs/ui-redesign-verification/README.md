# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`03cc2999a1248fbff522316887353ebd36e97c29`
- GitHub Actions run：[40354446987](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40354446987)
- artifact ID：`12665986893`
- artifact digest：`sha256:d7cf4a5a1cdd97af504efac234978a55a031370530befc0d11cf9ee372d49ca5`
- APK：`app-debug.apk`，SHA-256 `14bc0c030b9ed72209a6a1bb8d4787638cb0ebca97c7c67e04d48449d7fdcfc6`，22315628 字节
- artifact 内未配对 smoke：`pass`（2026-10-26T16:06:24.513549+00:00）
- artifact 内配对 fixture：`pass`（2026-10-26T16:10:12.730537+00:00）
- 报告生成时间：`2026-10-26T16:10:30Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `378827a3151dc9bb3ff53c04884a060445535c8b8c269ababc11051e3dc9f271` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `a824dfccb96de5104c9c54eb97ed3cb988d5eaefc1de16616bcb2b96fca7fb78` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `488a876e0f48cf557dcc26f7270683a47b5e0a5bfb3c5c8e32e7665f473e715d` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `10e19b9ff4e7defbd005ac35f9f032f6411dd7151af17fa19c3aa0487c3f6575` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `29da6a5e6feb59352063310c7151a35e3205f06d0bc796a0d472cc2d788de740` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `adef88b4f60a7f410751a11d2d9ecb3a61e339dec5b6685ff01d3ebb4a385696` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `7e860f56b31797c6839e8a9dbc51c3dc4d9371e60163a5c0c37af96049f4d318` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `5b7a117cc2e6f209fcd3f79a9d61968343835528e63d350e66187ecb22075a1e` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `46606bd73edd6ad0c942572c3c0da8dab613be9516e7c916f93c177dd63f0f98` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `69ad3b000961406de6619eb810257e52db7b2ec31d1915b4b1124b30dccbc1dc` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `b213861bb4a657fc2788fa1d71dd80f0219b131bfe8cc539bffd748efd4cf8f5` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `c6d360d78921507320221a679f9e0d95edf6b56312f589f9ef84a411354098b3` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `5e3a77f368ae66594892fa8a14f62ef58d8b695a94ff199aca913c953bc0debe` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `0c90ecfaa3c28548c5d5f51fc48d2b4a08ebe4ad521336076e4c59c219225718` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `5e3a77f368ae66594892fa8a14f62ef58d8b695a94ff199aca913c953bc0debe` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `acbfd5474d64ae643c08fb6e145157fc148860ca27a379eea90c3f0e8485fd95` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `5db09b1c3eb754a8ceb558bff10e7071dd42876e14f476ed8e2826bfea68e0af` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `c32ac4e7b8fd618aeeeb1cab366f18262c4bfca45b72fc9881a6f84e01d23933` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `f92fef207d04e7c6c6ccb91b5974db3b6b92527cfd396c5b2f7ba9110423ea8c` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `43556a1d578027e09c81f5e0f334097686186538860a89435810ac1365939000` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `e419b767ecc25839b4c253e9ac182b856d4099f906ce8c61f49a881a524d4dce` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `ffba623fe54f6701165b2a79eba3edd8c74c6b9dcac28284ffc86a0f6f250f39` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `00d2dd1b887b5a5cae77d6efd7f552215515e0a294ff418fb85e49ec40ddec67` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `ec135959952b03d535f83f66ce07954bde6ab95db4ae4278afee705b1cc539f1` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `00d2dd1b887b5a5cae77d6efd7f552215515e0a294ff418fb85e49ec40ddec67` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `264eda4fd8e6f72da7ecda5daaf23b9a3b3c66d915d52f888dfd3329fbdb9db3` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `abef4e27edf954fd39875a8f794d2f707bd9cb20faa77a7be7a29184c2577f43` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `82a4126e7402aeaba575dd6a1d5bba54084039bc320b5cc4dc3e65291a6925ac` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `ed422e6cc5066a28a9a1cd78762ae60d0935dce3be549a5db8063c5c0faa1817` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `09a61345b8de3e781dfb5287b517a0a9b84b79ca1d4a2ee6d92b8ce3d1fa48d8` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `35fcb27b2612dae39bb0590dd6ffbe19d65a05ce729744874dfbd73f25411a75` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `b1b1b517fff623fc3cab6c7b44394cd2cd860baf19bac2e20a10d0b8e2aa72c3` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `a21d531e6cf13b6ebd58f2b3515d8c3a493c59cc47732fbdce80f4af7cd70bf7` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `08be466787fb26b4e5fe38505a3453558048f931871396da1782d7e7cac6ae66` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `2ae0b3be74d3537c12174de1b0b3c396231afb2a43191ab94c789c0d6a97f66a` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `77436227137f3b364a0838884de349836ed5c1ea9eda464aa10cf761d3a9c7a6` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `742fbb30831ffb963a9c3b6f0e1feae34e5083fb50d59cd09cf81ee1bbe4cd30` |

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
| `results.json` | `b568181f1778cf4cccc512c2f1bef4524af353a05cd7c6dac6817cb6b0ae0c25` |
| `screenshot_manifest.json` | `bdfe9a8e2515c1c9d6ed230a094badaea941c3728eebf5b9a58c8aa06dd22b05` |
| `paired-fixture/results.json` | `efd876ff8d3f62d2fdd333bf6bc4157250454a72ef9e07e0506bfc10d924fd33` |
| `paired-fixture/fixture_manifest.json` | `5298541cc69c0fc3b916cc0ea2b49f7ffa741881e89f4bf8802fb3d19f3b161e` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
